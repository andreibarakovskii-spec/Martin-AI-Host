package com.imagine.martinhost

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import audio.soniqo.speech.ModelManager
import audio.soniqo.speech.SpeechSynthesizer
import audio.soniqo.speech.SpeechSynthesizerConfig
import audio.soniqo.speech.TtsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Free, on-device Russian neural voice for Martin.
 * Supertonic models are downloaded once into app-private storage.
 */
class MartinNeuralSpeaker(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onPreparing(message: String)
        fun onReady()
        fun onStart()
        fun onLevel(level: Float)
        fun onDone()
        fun onError(message: String)
    }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var synthesizer: SpeechSynthesizer? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var ready = false
    private var speakJob: Job? = null

    fun isReady(): Boolean = ready

    fun prepare() {
        if (ready || synthesizer != null) return
        scope.launch {
            try {
                withContext(Dispatchers.Main) { listener.onPreparing("Готовлю живой голос Мартина…") }
                val dir = ModelManager.ensureTtsModels(app, TtsModel.SUPERTONIC) { p ->
                    val total = p.totalBytes
                    if (total > 0L) {
                        val percent = ((p.totalBytesDownloaded * 100L) / total).coerceIn(0L, 100L)
                        scope.launch(Dispatchers.Main) { listener.onPreparing("Голос Мартина: $percent%") }
                    }
                }
                synthesizer = SpeechSynthesizer(
                    SpeechSynthesizerConfig(
                        modelDir = dir,
                        useNnapi = false,
                        ttsModel = TtsModel.SUPERTONIC
                    )
                )
                ready = true
                withContext(Dispatchers.Main) { listener.onReady() }
            } catch (t: Throwable) {
                ready = false
                synthesizer = null
                withContext(Dispatchers.Main) { listener.onError("Neural TTS: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    @JvmOverloads
    fun speak(text: String, emotion: String = "neutral", energy: Float = 0.55f) {
        if (text.isBlank()) return
        val engine = synthesizer
        if (!ready || engine == null) {
            listener.onError("Живой голос ещё загружается")
            return
        }
        stopPlaybackOnly()
        speakJob = scope.launch {
            try {
                withContext(Dispatchers.Main) { listener.onStart() }
                val styled = styleText(text, emotion, energy)
                val result = engine.synthesize(styled, "ru")
                if (result.pcm16.isEmpty()) throw IllegalStateException("TTS вернул пустой звук")
                playPcm(result.pcm16, result.sampleRate)
                withContext(Dispatchers.Main) { listener.onDone() }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { listener.onError("Голос: ${t.message ?: t.javaClass.simpleName}") }
            }
        }
    }

    private fun styleText(raw: String, emotionRaw: String, energyRaw: Float): String {
        val emotion = emotionRaw.lowercase()
        val energy = energyRaw.coerceIn(0f, 1f)
        var text = raw.trim()
            .replace("...", "…")
            .replace(Regex("\\s+"), " ")
            // Never pass SSML-like cues to Supertonic: its direct synthesizer
            // accepts plain text, and unknown tags may be spoken literally.
            .replace(Regex("<[^>]+>"), "")
            .trim()

        text = when (emotion) {
            "happy", "celebrate", "excited" -> {
                if (energy > .72f && text.lastOrNull() !in listOf('!', '?')) "$text!" else text
            }
            "playful", "sarcastic" -> text.replace(". ", ". — ")
            "warm", "toast" -> text.replace(". ", "… ")
            "sad", "disappointed" -> if (text.endsWith(".")) text.dropLast(1) + "…" else "$text…"
            else -> text
        }
        return text
    }

    private suspend fun playPcm(pcm: ByteArray, sampleRate: Int) {
        val min = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(min)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack
        audioTrack.play()
        var offset = 0
        val chunk = 4096
        while (offset < pcm.size) {
            val n = minOf(chunk, pcm.size - offset)
            val written = audioTrack.write(pcm, offset, n, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) throw IllegalStateException("AudioTrack write=$written")
            offset += written
            val level = rms16(pcm, offset - written, written)
            withContext(Dispatchers.Main) { listener.onLevel(level) }
        }
        try { audioTrack.stop() } catch (_: Exception) {}
        audioTrack.release()
        if (track === audioTrack) track = null
    }

    private fun rms16(data: ByteArray, offset: Int, len: Int): Float {
        var i = offset
        val end = (offset + len - 1).coerceAtMost(data.size - 1)
        var sum = 0.0
        var count = 0
        while (i < end) {
            val s = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xff)).toShort().toInt()
            val v = s / 32768.0
            sum += v * v
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = kotlin.math.sqrt(sum / count)
        return (rms * 3.2).toFloat().coerceIn(0.08f, 1f)
    }

    fun stop() {
        synthesizer?.stop()
        stopPlaybackOnly()
    }

    private fun stopPlaybackOnly() {
        speakJob?.cancel()
        speakJob = null
        val t = track
        track = null
        if (t != null) {
            try { t.pause() } catch (_: Exception) {}
            try { t.flush() } catch (_: Exception) {}
            try { t.stop() } catch (_: Exception) {}
            try { t.release() } catch (_: Exception) {}
        }
    }

    fun close() {
        stop()
        try { synthesizer?.close() } catch (_: Exception) {}
        synthesizer = null
        ready = false
        scope.cancel()
    }
}
