package com.imagine.martinhost

import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Hot local neural TTS. PCM is played while sherpa inference is still running. */
class MartinNeuralSpeaker(context: Context, private val listener: MartinSpeaker.Listener):MartinSpeaker {
 private val app=context.applicationContext
 private var loadedVoice=""
 private val engineLock=Any()
 private val worker=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
 private val scope=CoroutineScope(SupervisorJob()+worker)
 private val operationLock=Mutex()
 private fun enqueue(action:suspend ()->Unit){scope.launch{operationLock.withLock{action()}}}
 private val preparing=AtomicBoolean(false)
 private val generation=AtomicInteger(0)
 @Volatile private var engine: FastVoiceEngine?=null
 @Volatile private var track: AudioTrack?=null
 @Volatile private var ready=false
 @Volatile private var closed=false
 @Volatile private var bargePaused=false
 override fun isReady()=ready
 override fun prepare(){
  if(closed||ready||!preparing.compareAndSet(false,true))return
  enqueue {
   try {
    ensureVoice(app.getSharedPreferences("martin",0).getString("local_voice","M1")?:"M1")
    if(!closed){ready=true;withContext(Dispatchers.Main){if(!closed)listener.onReady()}}
   }catch(e:Exception){ready=false;withContext(Dispatchers.Main){if(!closed)listener.onError("Не удалось подготовить локальный голос. Проверьте загрузку модели и свободное место. ${e.javaClass.simpleName}")}}
   finally{preparing.set(false)}
  }
 }
 private suspend fun ensureVoice(requested:String){
  val voice=LocalVoiceProfiles.valid(requested)
  if(engine!=null){loadedVoice=voice;return}
  val log=DiagnosticRecorder.get(app)
  log.event("tts_prepare_start","Supertonic3;sherpa-onnx=1.13.2;INT8;threads=4;steps=5;voice=$voice;pcm_stream=true")
  val dir=FastVoiceModel.ensure(app.filesDir,{closed}){message->scope.launch(Dispatchers.Main){if(!closed)listener.onPreparing(message)}}
  if(closed)throw CancellationException()
  withContext(Dispatchers.Main){if(!closed)listener.onPreparing("Прогрев нового голосового движка…")}
  val next=FastVoiceEngine(dir)
  try {
   val start=android.os.SystemClock.elapsedRealtime()
   val warm=next.synthesize("Готов.",voice,1f){closed}
   if(closed)throw CancellationException()
   if(warm.pcm16.isEmpty())throw IllegalStateException("Пустой прогрев голоса")
   log.event("tts_warmup_end","ms=${android.os.SystemClock.elapsedRealtime()-start};samples=${warm.pcm16.size/2}")
   synchronized(engineLock){engine=next;loadedVoice=voice}
  }catch(e:Throwable){next.close();throw e}
  ready=true
  log.event("tts_prepare_ready","voice=$voice;hot=true")
 }
 override fun speak(text:String,emotion:String,energy:Float){
  val p=app.getSharedPreferences("martin",0)
  val e=emotion.lowercase();val prosody=when{e.contains("excited")||e.contains("happy")||e.contains("playful")->1.05f;e.contains("warm")->.96f;e.contains("curious")->1.01f;else->1f}
  speakInternal(text,p.getString("local_voice","M1")?:"M1",p.getFloat("local_voice_speed",1f)*prosody)
 }
 fun speak(text:String){speak(text,"neutral",.55f)}
 fun previewLocal(text:String,voice:String,speed:Float){speakInternal(text,voice,speed)}
 private fun speakInternal(text:String,requestedVoice:String,requestedSpeed:Float){
  if(text.isBlank()||closed)return
  stop();val token=generation.get();val voice=LocalVoiceProfiles.valid(requestedVoice);val speed=requestedSpeed.coerceIn(.85f,1.15f)
  val log=DiagnosticRecorder.get(app)
  log.event("tts_request","generation=$token;provider=sherpa-onnx-int8;steps=5;voice=$voice;synthesis_speed=$speed;pcm_stream=true")
  enqueue {
   var localTrack:AudioTrack?=null
   try {
    if(token!=generation.get()||closed)return@enqueue
    ensureVoice(voice)
    val e=engine?:throw IllegalStateException("Модель недоступна")
    if(token!=generation.get()||closed)return@enqueue
    val clean=text.replace(Regex("<[^>]+>"),"").replace(Regex("\\s+")," ").trim()
    if(clean.isBlank()){withContext(Dispatchers.Main){if(token==generation.get())listener.onDone()};return@enqueue}
    val sentences=SpeechChunks.split(clean)
    val rate=e.sampleRate()
    val min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
    localTrack=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
     .setBufferSizeInBytes(min*2).setTransferMode(AudioTrack.MODE_STREAM).build()
    track=localTrack;bargePaused=false
    log.event("tts_chunks","count=${sentences.size};startup_chunk_chars=${sentences.firstOrNull()?.length?:0};unicode=NFKD;pcm_stream=true")
    var started=false
    var written=0L
    var streamChunkCount=0
    val audioName="tts-stream-${android.os.SystemClock.elapsedRealtime()}"
    for((index,sentence) in sentences.withIndex()){
     if(token!=generation.get()||closed)throw CancellationException()
     val synthStart=android.os.SystemClock.elapsedRealtime()
     var firstPcmAt=0L
     log.event("tts_synthesis_start","chunk=$index;$sentence")
     e.synthesizeStreaming(sentence,voice,speed,{token!=generation.get()||closed}){pcm->
      if(token!=generation.get()||closed)return@synthesizeStreaming false
      if(pcm.isEmpty())return@synthesizeStreaming true
      if(firstPcmAt==0L){
       firstPcmAt=android.os.SystemClock.elapsedRealtime()
       log.event("tts_first_pcm","chunk=$index;ms=${firstPcmAt-synthStart};bytes=${pcm.size}")
      }
      if(!started){
       try{localTrack.play()}catch(_:Exception){return@synthesizeStreaming false}
       started=true
       app.mainExecutor.execute{if(token==generation.get()&&!closed)listener.onStart()}
       log.event("playback_start",audioName)
      }
      while(bargePaused&&token==generation.get()&&!closed){try{Thread.sleep(8)}catch(_:InterruptedException){return@synthesizeStreaming false}}
      if(token!=generation.get()||closed)return@synthesizeStreaming false
      var off=0
      while(off<pcm.size&&token==generation.get()&&!closed){
       val n=localTrack.write(pcm,off,minOf(4096,pcm.size-off),AudioTrack.WRITE_BLOCKING)
       if(n<=0)return@synthesizeStreaming false
       off+=n;written+=n;streamChunkCount++
      }
      val level=rms(pcm,0,pcm.size)
      val bands=spectrum(pcm,0,rate)
      app.mainExecutor.execute{if(token==generation.get()&&!closed){listener.onLevel(level);listener.onSpectrum(bands)}}
      log.event("tts_pcm_push","chunk=$index;bytes=${pcm.size};written_total=$written;callback=$streamChunkCount;underruns=${localTrack.underrunCount}")
      token==generation.get()&&!closed
     }
     log.event("tts_synthesis_end","chunk=$index;ms=${android.os.SystemClock.elapsedRealtime()-synthStart};first_pcm_ms=${if(firstPcmAt==0L)-1 else firstPcmAt-synthStart};written_total=$written")
    }
    if(started&&token==generation.get()&&!closed){
     val frames=written/2L
     val deadline=android.os.SystemClock.elapsedRealtime()+6000L
     while(token==generation.get()&&!closed&&(localTrack.playbackHeadPosition.toLong() and 0xffffffffL)<frames){
      if(bargePaused){delay(10);continue}
      if(android.os.SystemClock.elapsedRealtime()>deadline)break
      delay(12)
     }
    }
    withContext(Dispatchers.Main){if(token==generation.get()&&!closed){listener.onLevel(0f);listener.onDone()}}
   }catch(e:CancellationException){log.event("tts_cancelled","generation=$token;stream=true")}
   catch(e:Exception){log.event("tts_error",android.util.Log.getStackTraceString(e));withContext(Dispatchers.Main){if(token==generation.get()&&!closed)listener.onError("Ошибка локального синтеза: ${e.javaClass.simpleName}. Текст ответа доступен на экране.")}}
   finally{
    bargePaused=false
    localTrack?.let{t->
     log.event("playback_end","stream=true;consumed_frames=${t.playbackHeadPosition};cancelled=${token!=generation.get()}")
     if(track===t)track=null
     try{t.stop()}catch(_:Exception){}
     t.release()
    }
   }
  }
 }
 override fun pauseForBargeIn():Boolean{
  val t=track?:return false
  return try{if(t.playState==AudioTrack.PLAYSTATE_PLAYING){t.pause();bargePaused=true;DiagnosticRecorder.get(app).event("barge_playback_pause","frames=${t.playbackHeadPosition};stream=true");true}else false}catch(_:Exception){false}
 }
 override fun resumeAfterBargeIn():Boolean{
  val t=track?:return false
  return try{if(bargePaused){t.play();bargePaused=false;DiagnosticRecorder.get(app).event("barge_playback_resume","frames=${t.playbackHeadPosition};stream=true");true}else false}catch(_:Exception){false}
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
 override fun stop(){bargePaused=false;DiagnosticRecorder.get(app).event("tts_stop_requested","stream=true");generation.incrementAndGet();val t=track;if(t!=null){try{t.pause();t.flush()}catch(_:Exception){}}}
 override fun releaseModel(){stop();ready=false;enqueue{synchronized(engineLock){engine?.close();engine=null;loadedVoice=""};ready=false}}
 override fun close(){if(closed)return;closed=true;stop();ready=false;enqueue{try{synchronized(engineLock){engine?.close();engine=null;loadedVoice=""}}finally{scope.cancel();worker.close()}}}
}
