package com.imagine.martinhost

import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.util.function.BooleanSupplier

/** Official upstream Android AAR; model stays hot and can emit PCM while inference is still running. */
class FastVoiceEngine(dir:File):AutoCloseable {
 private val tts=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
  supertonic=OfflineTtsSupertonicModelConfig(
   durationPredictor=File(dir,"duration_predictor.int8.onnx").absolutePath,
   textEncoder=File(dir,"text_encoder.int8.onnx").absolutePath,
   vectorEstimator=File(dir,"vector_estimator.int8.onnx").absolutePath,
   vocoder=File(dir,"vocoder.int8.onnx").absolutePath,
   ttsJson=File(dir,"tts.json").absolutePath,
   unicodeIndexer=File(dir,"unicode_indexer.bin").absolutePath,
   voiceStyle=File(dir,"voice.bin").absolutePath),
  numThreads=4,debug=false,provider="cpu")))
 init {if(tts.numSpeakers()!=10){tts.release();throw IllegalStateException("Неверное число голосов")}}
 class NativeCallback(private val cancelled:BooleanSupplier):(FloatArray)->Int {
  override fun invoke(samples:FloatArray):Int=if(cancelled.asBoolean)0 else 1
 }
 class StreamingCallback(private val cancelled:BooleanSupplier,private val sink:(ByteArray)->Boolean):(FloatArray)->Int {
  override fun invoke(samples:FloatArray):Int {
   if(cancelled.asBoolean)return 0
   if(samples.isEmpty())return 1
   val bytes=toPcm16(samples)
   return if(!cancelled.asBoolean&&sink(bytes))1 else 0
  }
 }
 class Pcm(@JvmField val pcm16:ByteArray,@JvmField val sampleRate:Int)
 fun sampleRate():Int=tts.sampleRate()
 fun synthesize(text:String,voice:String,speed:Float,cancelled:BooleanSupplier):Pcm {
  val config=generationConfig(voice,speed)
  val audio=tts.generateWithConfigAndCallback(normalizeText(text),config,NativeCallback(cancelled))
  if(cancelled.asBoolean)return Pcm(ByteArray(0),tts.sampleRate())
  return Pcm(toPcm16(audio.samples),audio.sampleRate)
 }
 /** Emits PCM chunks as soon as sherpa produces them. The returned final audio is intentionally ignored. */
 fun synthesizeStreaming(text:String,voice:String,speed:Float,cancelled:BooleanSupplier,sink:(ByteArray)->Boolean):Int {
  val rate=tts.sampleRate()
  tts.generateWithConfigAndCallback(normalizeText(text),generationConfig(voice,speed),StreamingCallback(cancelled,sink))
  return rate
 }
 private fun generationConfig(voice:String,speed:Float)=GenerationConfig(
  sid=speakerId(voice),speed=speed,numSteps=STEPS,silenceScale=.16f,
  extra=mapOf("lang" to "ru", "silence_duration" to "0.16"))
 override fun close(){tts.release()}
 companion object {
  const val STEPS=5
  @JvmStatic fun normalizeText(text:String):String = java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKD)
  @JvmStatic fun speakerId(voice:String):Int {val v=LocalVoiceProfiles.valid(voice);return (if(v[0]=='M')5 else 0)+(v[1]-'1')}
  @JvmStatic fun toPcm16(samples:FloatArray):ByteArray {
   val bytes=ByteArray(samples.size*2)
   for(i in samples.indices){val v=(samples[i].coerceIn(-1f,1f)*32767).toInt();bytes[i*2]=v.toByte();bytes[i*2+1]=(v shr 8).toByte()}
   return bytes
  }
 }
}
