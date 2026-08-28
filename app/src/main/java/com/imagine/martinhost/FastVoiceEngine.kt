package com.imagine.martinhost

import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.util.function.BooleanSupplier

/** Official upstream Android AAR; inference and release are serialized by the owner. */
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
  numThreads=2,debug=false,provider="cpu")))
 init {if(tts.numSpeakers()!=10){tts.release();throw IllegalStateException("Неверное число голосов")}}
 // Sherpa JNI looks up invoke(float[]): java.lang.Integer by its concrete signature.
 // Kotlin 2.x indy lambdas only expose the erased invoke(Object), which aborts JNI.
 class NativeCallback(private val cancelled:BooleanSupplier):(FloatArray)->Int {
  override fun invoke(samples:FloatArray):Int=if(cancelled.asBoolean)0 else 1
 }
 class Pcm(@JvmField val pcm16:ByteArray,@JvmField val sampleRate:Int)
 fun synthesize(text:String,voice:String,speed:Float,cancelled:BooleanSupplier):Pcm {
  val config=GenerationConfig(sid=speakerId(voice),speed=speed,numSteps=STEPS,silenceScale=.16f,extra=mapOf("lang" to "ru"))
  val audio=tts.generateWithConfigAndCallback(text,config,NativeCallback(cancelled))
  if(cancelled.asBoolean)return Pcm(ByteArray(0),tts.sampleRate())
  val bytes=ByteArray(audio.samples.size*2)
  for(i in audio.samples.indices){val v=(audio.samples[i].coerceIn(-1f,1f)*32767).toInt();bytes[i*2]=v.toByte();bytes[i*2+1]=(v shr 8).toByte()}
  return Pcm(bytes,audio.sampleRate)
 }
 override fun close(){tts.release()}
 companion object {
  const val STEPS=5
  // Upstream generate_voices_bin.py sorts F1..F5,M1..M5.
  @JvmStatic fun speakerId(voice:String):Int {val v=LocalVoiceProfiles.valid(voice);return (if(v[0]=='M')5 else 0)+(v[1]-'1')}
 }
}
