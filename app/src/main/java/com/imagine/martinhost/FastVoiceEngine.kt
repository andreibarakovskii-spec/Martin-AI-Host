package com.imagine.martinhost

import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.util.function.BooleanSupplier

/** App-owned Russian Piper/VITS backend; prosody is planned above this layer by IMA Voice. */
class FastVoiceEngine(dir:File):AutoCloseable {
 private val tts=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
  vits=OfflineTtsVitsModelConfig(
   model=File(dir,FastVoiceModel.MODEL).absolutePath,
   tokens=File(dir,"tokens.txt").absolutePath,
   dataDir=File(dir,"espeak-ng-data").absolutePath,
   noiseScale=.667f,noiseScaleW=.8f,lengthScale=1f),
  numThreads=2,debug=false,provider="cpu")))
 init {if(tts.numSpeakers()!=1){tts.release();throw IllegalStateException("Неверное число голосов Piper")}}
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
  val audio=tts.generateWithConfigAndCallback(normalizeText(text),generationConfig(speed,.12f),NativeCallback(cancelled))
  if(cancelled.asBoolean)return Pcm(ByteArray(0),tts.sampleRate())
  return Pcm(toPcm16(audio.samples),audio.sampleRate)
 }
 fun synthesizeStreaming(text:String,voice:String,speed:Float,cancelled:BooleanSupplier,sink:(ByteArray)->Boolean):Int =
  synthesizeStreaming(text,voice,speed,.12f,cancelled,sink)
 fun synthesizeStreaming(text:String,voice:String,speed:Float,silenceScale:Float,cancelled:BooleanSupplier,sink:(ByteArray)->Boolean):Int {
  val rate=tts.sampleRate()
  tts.generateWithConfigAndCallback(normalizeText(text),generationConfig(speed,silenceScale),StreamingCallback(cancelled,sink))
  return rate
 }
 private fun generationConfig(speed:Float,silenceScale:Float)=GenerationConfig(
  sid=0,
  speed=speed.coerceIn(.85f,1.15f),
  silenceScale=silenceScale.coerceIn(.06f,.22f))
 override fun close(){tts.release()}
 companion object {
  @JvmStatic fun normalizeText(text:String):String = java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)
  @JvmStatic fun speakerId(voice:String):Int=0
  @JvmStatic fun toPcm16(samples:FloatArray):ByteArray {
   val bytes=ByteArray(samples.size*2)
   for(i in samples.indices){val v=(samples[i].coerceIn(-1f,1f)*32767).toInt();bytes[i*2]=v.toByte();bytes[i*2+1]=(v shr 8).toByte()}
   return bytes
  }
 }
}
