package com.imagine.martinhost

import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Russian neural TTS, one engine at a time. The playback owner releases AudioTrack. */
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
  log.event("tts_prepare_start","Supertonic3;sherpa-onnx=1.13.2;INT8;threads=4;steps=5;voice=$voice")
  val dir=FastVoiceModel.ensure(app.filesDir,{closed}){message->
   scope.launch(Dispatchers.Main){if(!closed)listener.onPreparing(message)}
  }
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
  log.event("tts_prepare_ready","voice=$voice")
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
  DiagnosticRecorder.get(app).event("tts_request","generation=$token;provider=sherpa-onnx-int8;steps=5;voice=$voice;synthesis_speed=$speed;emotion_controls=unsupported")
  enqueue {
   try {
    if(token!=generation.get()||closed)return@enqueue
    ensureVoice(voice)
    val e=engine?:throw IllegalStateException("Модель недоступна")
    if(token!=generation.get()||closed)return@enqueue
    val clean=text.replace(Regex("<[^>]+>"),"").replace(Regex("\\s+")," ").trim()
    if(clean.isBlank()){withContext(Dispatchers.Main){if(token==generation.get())listener.onDone()};return@enqueue}
    val sentences=SpeechChunks.split(clean)
    DiagnosticRecorder.get(app).event("tts_chunks","count=${sentences.size};startup_chunk_chars=${sentences.firstOrNull()?.length?:0};unicode=NFKD")
    var started=false
    var lastPlaybackEnd=0L
    coroutineScope {
     suspend fun synthesize(sentence:String):FastVoiceEngine.Pcm=withContext(Dispatchers.IO){
      if(token!=generation.get()||closed)throw CancellationException()
      val start=android.os.SystemClock.elapsedRealtime()
      DiagnosticRecorder.get(app).event("tts_synthesis_start",sentence)
      val pcm=e.synthesize(sentence,voice,speed){token!=generation.get()||closed}
      DiagnosticRecorder.get(app).event("tts_synthesis_end","samples=${pcm.pcm16.size/2};rate=${pcm.sampleRate};ms=${android.os.SystemClock.elapsedRealtime()-start};audio_ms=${pcm.pcm16.size*500L/pcm.sampleRate}")
      if(token!=generation.get()||closed)throw CancellationException()
      if(pcm.pcm16.isEmpty())throw IllegalStateException("Пустой результат синтеза")
      pcm
     }
     var pending=async{ synthesize(sentences[0]) }
     for(i in sentences.indices){
      val waitStart=android.os.SystemClock.elapsedRealtime()
      val pcm=pending.await()
      DiagnosticRecorder.get(app).event("tts_buffer_wait","chunk=$i;wait_ms=${android.os.SystemClock.elapsedRealtime()-waitStart};since_last_playback_ms=${if(lastPlaybackEnd==0L)0L else android.os.SystemClock.elapsedRealtime()-lastPlaybackEnd}")
      if(token!=generation.get()||closed)throw CancellationException()
      if(i+1<sentences.size)pending=async{ synthesize(sentences[i+1]) }
      if(!started){withContext(Dispatchers.Main){if(token==generation.get())listener.onStart()};started=true}
      val audioFile=DiagnosticRecorder.get(app).audio("tts",pcm.pcm16,pcm.sampleRate,false)
      play(pcm.pcm16,pcm.sampleRate,token,audioFile)
      lastPlaybackEnd=android.os.SystemClock.elapsedRealtime()
     }
    }
    withContext(Dispatchers.Main){if(token==generation.get()&&!closed){listener.onLevel(0f);listener.onDone()}}
   }catch(e:CancellationException){DiagnosticRecorder.get(app).event("tts_cancelled","generation=$token")}catch(e:Exception){DiagnosticRecorder.get(app).event("tts_error",android.util.Log.getStackTraceString(e));withContext(Dispatchers.Main){if(token==generation.get()&&!closed)listener.onError("Ошибка локального синтеза: ${e.javaClass.simpleName}. Текст ответа доступен на экране.")}}
  }
 }
 private suspend fun play(pcm:ByteArray,rate:Int,token:Int,audioFile:String){
  val min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
  val t=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
   .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(min).setTransferMode(AudioTrack.MODE_STREAM).build()
  track=t;bargePaused=false
  try {
   if(token!=generation.get())return
   t.play();DiagnosticRecorder.get(app).event("playback_start",audioFile);var offset=0;var lastReport=0L
   while(offset<pcm.size&&token==generation.get()&&!closed){
    while(bargePaused&&token==generation.get()&&!closed)delay(10)
    if(token!=generation.get()||closed)break
    val n=t.write(pcm,offset,minOf(2048,pcm.size-offset),AudioTrack.WRITE_BLOCKING)
    if(n<=0)throw IllegalStateException("AudioTrack write failed")
    offset+=n
    val now=android.os.SystemClock.elapsedRealtime()
    if(now-lastReport>=200){DiagnosticRecorder.get(app).event("playback_progress","$audioFile;consumed_frames=${t.playbackHeadPosition};written_bytes=$offset;underruns=${t.underrunCount};route_type=${t.routedDevice?.type ?: -1};buffer_frames=${t.bufferSizeInFrames};barge_paused=$bargePaused");lastReport=now}
    val audible=((t.playbackHeadPosition.toLong() and 0xffffffffL)*2).toInt().coerceIn(0,pcm.size-2)
    val level=rms(pcm,audible,minOf(2048,pcm.size-audible))
    val bands=spectrum(pcm,audible,rate)
    withContext(Dispatchers.Main){if(token==generation.get()){listener.onLevel(level);listener.onSpectrum(bands)}}
   }
   val frames=pcm.size/2L
   val deadline=android.os.SystemClock.elapsedRealtime()+5000L
   while(token==generation.get()&&!closed&&(t.playbackHeadPosition.toLong() and 0xffffffffL)<frames){
    if(bargePaused){delay(10);continue}
    if(android.os.SystemClock.elapsedRealtime()>deadline)throw IllegalStateException("Playback drain timeout")
    delay(15)
   }
  }finally{bargePaused=false;DiagnosticRecorder.get(app).event("playback_end","$audioFile;consumed_frames=${t.playbackHeadPosition};cancelled=${token!=generation.get()}");if(track===t)track=null;try{t.stop()}catch(_:Exception){};t.release()}
 }
 override fun pauseForBargeIn():Boolean{
  val t=track?:return false
  return try{if(t.playState==AudioTrack.PLAYSTATE_PLAYING){t.pause();bargePaused=true;DiagnosticRecorder.get(app).event("barge_playback_pause","frames=${t.playbackHeadPosition}");true}else false}catch(_:Exception){false}
 }
 override fun resumeAfterBargeIn():Boolean{
  val t=track?:return false
  return try{if(bargePaused){t.play();bargePaused=false;DiagnosticRecorder.get(app).event("barge_playback_resume","frames=${t.playbackHeadPosition}");true}else false}catch(_:Exception){false}
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
 override fun stop(){bargePaused=false;DiagnosticRecorder.get(app).event("tts_stop_requested","");generation.incrementAndGet();val t=track;if(t!=null){try{t.pause();t.flush()}catch(_:Exception){}}}
 override fun releaseModel(){stop();ready=false;enqueue{synchronized(engineLock){engine?.close();engine=null;loadedVoice=""};ready=false}}
 override fun close(){if(closed)return;closed=true;stop();ready=false;enqueue{try{synchronized(engineLock){engine?.close();engine=null;loadedVoice=""}}finally{scope.cancel();worker.close()}}}
}
