package com.imagine.martinhost

import android.content.Context
import android.media.*
import audio.soniqo.speech.*
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Russian neural TTS, one engine at a time. The playback owner releases AudioTrack. */
class MartinNeuralSpeaker(context: Context, private val listener: Listener) {
 interface Listener {
  fun onPreparing(message: String)
  fun onReady()
  fun onStart()
  fun onLevel(level: Float)
  fun onSpectrum(bands: FloatArray)
  fun onDone()
  fun onError(message: String)
 }
 private val app=context.applicationContext
 private val worker=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
 private val scope=CoroutineScope(SupervisorJob()+worker)
 private val preparing=AtomicBoolean(false)
 private val generation=AtomicInteger(0)
 @Volatile private var engine: SpeechSynthesizer?=null
 @Volatile private var track: AudioTrack?=null
 @Volatile private var ready=false
 @Volatile private var closed=false
 fun isReady()=ready
 fun prepare(){
  if(closed||ready||!preparing.compareAndSet(false,true))return
  scope.launch {
   try {
    withContext(Dispatchers.Main){if(!closed)listener.onPreparing("Загрузка русского нейроголоса — около 380 МБ, нужен Wi-Fi")}
    val dir=ModelManager.ensureTtsModels(app,TtsModel.SUPERTONIC){p->
     if(p.totalBytes>0)scope.launch(Dispatchers.Main){if(!closed)listener.onPreparing("Нейроголос: ${(p.totalBytesDownloaded*100/p.totalBytes).coerceIn(0,100)}%")}
    }
    if(closed)return@launch
    engine=SpeechSynthesizer(SpeechSynthesizerConfig(modelDir=dir,useNnapi=false,ttsModel=TtsModel.SUPERTONIC))
    ready=true
    withContext(Dispatchers.Main){if(!closed)listener.onReady()}
   }catch(e:Exception){ready=false;withContext(Dispatchers.Main){if(!closed)listener.onError("Не удалось загрузить нейроголос. Проверьте сеть и свободное место. ${e.javaClass.simpleName}")}}
   finally{preparing.set(false)}
  }
 }
 @JvmOverloads fun speak(text:String,emotion:String="neutral",energy:Float=.55f){
  if(text.isBlank()||closed)return
  stop();val token=generation.get()
  scope.launch {
   try {
    val e=engine ?: throw IllegalStateException("Сначала загрузите модель голоса")
    if(token!=generation.get()||closed)return@launch
    // Sentence-sized synthesis lowers first-audio latency and bounds memory.
    val clean=text.replace(Regex("<[^>]+>"),"").replace(Regex("\\s+")," ").trim()
    val sentences=clean.split(Regex("(?<=[.!?…])\\s+"))
    var started=false
    for(sentence in sentences){
     if(token!=generation.get()||closed)return@launch
     DiagnosticRecorder.get(app).event("tts_synthesis_start",sentence)
     val pcm=e.synthesize(sentence,"ru")
     DiagnosticRecorder.get(app).event("tts_synthesis_end","samples=${pcm.pcm16.size/2};rate=${pcm.sampleRate}")
     if(token!=generation.get()||closed)return@launch
     if(pcm.pcm16.isEmpty())throw IllegalStateException("Пустой результат синтеза")
     if(!started){withContext(Dispatchers.Main){if(token==generation.get())listener.onStart()};started=true}
     val audioFile=DiagnosticRecorder.get(app).audio("tts",pcm.pcm16,pcm.sampleRate,false)
     play(pcm.pcm16,pcm.sampleRate,token,audioFile)
    }
    withContext(Dispatchers.Main){if(token==generation.get()&&!closed){listener.onLevel(0f);listener.onDone()}}
   }catch(e:Exception){DiagnosticRecorder.get(app).event("tts_error",e.javaClass.simpleName);withContext(Dispatchers.Main){if(token==generation.get()&&!closed)listener.onError("Ошибка синтеза: ${e.javaClass.simpleName}. Текст ответа доступен на экране.")}}
  }
 }
 private suspend fun play(pcm:ByteArray,rate:Int,token:Int,audioFile:String){
  val min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
  val t=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
   .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(min).setTransferMode(AudioTrack.MODE_STREAM).build()
  track=t
  try {
   if(token!=generation.get())return
   t.play();DiagnosticRecorder.get(app).event("playback_start",audioFile);var offset=0;var lastReport=0L
   while(offset<pcm.size&&token==generation.get()&&!closed){
    val n=t.write(pcm,offset,minOf(2048,pcm.size-offset),AudioTrack.WRITE_BLOCKING)
    if(n<=0)throw IllegalStateException("AudioTrack write failed")
    offset+=n
    val now=android.os.SystemClock.elapsedRealtime()
    if(now-lastReport>=200){DiagnosticRecorder.get(app).event("playback_progress","$audioFile;consumed_frames=${t.playbackHeadPosition};written_bytes=$offset");lastReport=now}
    val audible=((t.playbackHeadPosition.toLong() and 0xffffffffL)*2).toInt().coerceIn(0,pcm.size-2)
    val level=rms(pcm,audible,minOf(2048,pcm.size-audible))
    val bands=spectrum(pcm,audible,rate)
    withContext(Dispatchers.Main){if(token==generation.get()){listener.onLevel(level);listener.onSpectrum(bands)}}
   }
   // Writing completion is not playback completion: drain the device buffer first.
   val frames=pcm.size/2L
   val deadline=android.os.SystemClock.elapsedRealtime()+5000L
   while(token==generation.get()&&!closed&&(t.playbackHeadPosition.toLong() and 0xffffffffL)<frames){
    if(android.os.SystemClock.elapsedRealtime()>deadline)throw IllegalStateException("Playback drain timeout")
    delay(15)
   }
  }finally{DiagnosticRecorder.get(app).event("playback_end","$audioFile;consumed_frames=${t.playbackHeadPosition};cancelled=${token!=generation.get()}");if(track===t)track=null;try{t.stop()}catch(_:Exception){};t.release()}
 }
 private fun spectrum(pcm:ByteArray,offset:Int,rate:Int):FloatArray {
  val size=minOf(512,(pcm.size-offset)/2)
  if(size<2)return FloatArray(24)
  return FloatArray(24){b->
   val frequency=90.0*Math.pow(60.0,b/23.0)
   val k=(.5+size*frequency/rate).toInt().coerceIn(1,size/2)
   val coefficient=2*kotlin.math.cos(2*Math.PI*k/size)
   var q1=0.0;var q2=0.0
   for(i in 0 until size){val p=offset+2*i;val value=((pcm[p+1].toInt() shl 8) or (pcm[p].toInt() and 255)).toShort()/32768.0
    val q=value*(.5-.5*kotlin.math.cos(2*Math.PI*i/(size-1)))+coefficient*q1-q2;q2=q1;q1=q}
   val power=kotlin.math.sqrt(kotlin.math.max(0.0,q1*q1+q2*q2-coefficient*q1*q2))/size
   (kotlin.math.ln(1+power*100)/3).toFloat().coerceIn(0f,1f)
  }
 }
 private fun rms(pcm:ByteArray,offset:Int,n:Int):Float{var sum=0.0;var count=0;var i=offset;while(i+1<offset+n){val x=((pcm[i+1].toInt() shl 8) or (pcm[i].toInt() and 255)).toShort().toDouble()/32768;sum+=x*x;count++;i+=2};return if(count==0)0f else (kotlin.math.sqrt(sum/count)*4).toFloat().coerceIn(0f,1f)}
 fun stop(){DiagnosticRecorder.get(app).event("tts_stop_requested","");generation.incrementAndGet();engine?.stop();val t=track;if(t!=null){try{t.pause();t.flush()}catch(_:Exception){}}}
 fun close(){if(closed)return;closed=true;stop();ready=false;scope.launch{try{engine?.close();engine=null}finally{scope.cancel();worker.close()}}}
}
