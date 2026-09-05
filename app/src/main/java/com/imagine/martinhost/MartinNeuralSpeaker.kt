package com.imagine.martinhost

import android.content.Context
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** IMA Voice runtime: expressive planner + seamless PCM pipeline; Piper/Irina is the current fallback backend. */
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
 private val pendingSpeak=AtomicInteger(0)
 private val responseOpen=AtomicBoolean(false)
 @Volatile private var responseEmotion="neutral"
 @Volatile private var responseEnergy=.55f
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
    ensureVoice(app.getSharedPreferences("martin",0).getString("local_voice","F1")?:"F1")
    if(!closed){ready=true;withContext(Dispatchers.Main){if(!closed)listener.onReady()}}
   }catch(e:Exception){ready=false;withContext(Dispatchers.Main){if(!closed)listener.onError("Не удалось подготовить локальный голос. Проверьте загрузку модели и свободное место. ${e.javaClass.simpleName}")}}
   finally{preparing.set(false)}
  }
 }
 override fun preArm(){
  DiagnosticRecorder.get(app).event("tts_prearm","ready=$ready;engine_hot=${engine!=null};generation=${generation.get()};ima_voice_runtime=v2;backend=fallback_irina")
  if(!ready)prepare()
 }
 private suspend fun ensureVoice(requested:String){
  val voice=LocalVoiceProfiles.valid(requested)
  if(engine!=null){loadedVoice=voice;return}
  val log=DiagnosticRecorder.get(app)
  log.event("tts_prepare_start","IMA-runtime-v2;backend=fallback-piper-vits;model=ru_RU-irina-medium;sherpa-onnx=1.13.2;threads=2;voice=$voice;pcm_stream=true;prefetch=true;prosody=true")
  val dir=FastVoiceModel.ensure(app.filesDir,{closed}){message->scope.launch(Dispatchers.Main){if(!closed)listener.onPreparing(message)}}
  if(closed)throw CancellationException()
  withContext(Dispatchers.Main){if(!closed)listener.onPreparing("Прогрев резервного голоса IMA…")}
  val next=FastVoiceEngine(dir)
  try {
   val start=android.os.SystemClock.elapsedRealtime()
   val warm=next.synthesize("Готова.",voice,1f){closed}
   if(closed)throw CancellationException()
   if(warm.pcm16.isEmpty())throw IllegalStateException("Пустой прогрев голоса")
   log.event("tts_warmup_end","ms=${android.os.SystemClock.elapsedRealtime()-start};samples=${warm.pcm16.size/2};backend=fallback_irina")
   synchronized(engineLock){engine=next;loadedVoice=voice}
  }catch(e:Throwable){next.close();throw e}
  ready=true
  log.event("tts_prepare_ready","voice=$voice;backend=fallback_irina;hot=true;prefetch=true;prosody=true")
 }
 override fun speak(text:String,emotion:String,energy:Float){
  val p=app.getSharedPreferences("martin",0)
  speakInternal(text,p.getString("local_voice","F1")?:"F1",p.getFloat("local_voice_speed",1f),emotion,energy)
 }
 override fun beginResponse(emotion:String,energy:Float){
  if(closed)return
  responseEmotion=emotion.ifBlank{"neutral"};responseEnergy=energy.coerceIn(0f,1f);responseOpen.set(true)
  DiagnosticRecorder.get(app).event("tts_session_begin","generation=${generation.get()};emotion=$responseEmotion;energy=$responseEnergy;backend=fallback_irina")
 }
 override fun appendResponse(text:String,emotion:String,energy:Float){
  if(text.isBlank()||closed)return
  if(!responseOpen.get())beginResponse(emotion,energy)
  val selectedEmotion=if(emotion.isBlank())responseEmotion else emotion
  val selectedEnergy=if(energy.isFinite())energy.coerceIn(0f,1f) else responseEnergy
  val p=app.getSharedPreferences("martin",0)
  DiagnosticRecorder.get(app).event("tts_session_append","generation=${generation.get()};chars=${text.length};queue_depth=${pendingSpeak.get()+1}")
  speakInternal(text,p.getString("local_voice","F1")?:"F1",p.getFloat("local_voice_speed",1f),selectedEmotion,selectedEnergy)
 }
 override fun finishResponse(){if(responseOpen.compareAndSet(true,false))DiagnosticRecorder.get(app).event("tts_session_finish","generation=${generation.get()};queued=${pendingSpeak.get()}")}
 override fun cancelResponse(){responseOpen.set(false);stop()}
 fun speak(text:String){speak(text,"neutral",.55f)}
 fun previewLocal(text:String,voice:String,speed:Float){stop();speakInternal(text,voice,speed,"warm",.55f)}
 private fun speakInternal(text:String,requestedVoice:String,requestedSpeed:Float,emotion:String,energy:Float){
  if(text.isBlank()||closed)return
  val token=generation.get();val voice=LocalVoiceProfiles.valid(requestedVoice);val baseSpeed=requestedSpeed.coerceIn(.85f,1.15f)
  val queueDepth=pendingSpeak.incrementAndGet()
  val log=DiagnosticRecorder.get(app)
  if(queueDepth>1)log.event("tts_queue_append","generation=$token;queue_depth=$queueDepth;chars=${text.length}")
  log.event("tts_request","generation=$token;provider=ima-runtime-v2;backend=fallback-piper-vits;model=irina;voice=$voice;pcm_stream=true;prefetch=true;emotion=$emotion;energy=$energy;queue_depth=$queueDepth")
  enqueue {
   var localTrack:AudioTrack?=null
   try {
    if(token!=generation.get()||closed)return@enqueue
    ensureVoice(voice)
    val e=engine?:throw IllegalStateException("Модель недоступна")
    if(token!=generation.get()||closed)return@enqueue
    val clean=text.replace(Regex("<[^>]+>"),"").replace(Regex("\\s+")," ").trim()
    if(clean.isBlank()){withContext(Dispatchers.Main){if(token==generation.get())listener.onDone()};return@enqueue}
    val plan=ImaProsodyPlanner.plan(clean,emotion,energy)
    val speed=(baseSpeed*plan.speed).coerceIn(.85f,1.15f)
    log.event("tts_prosody","style=${plan.style};speed=$speed;silence_scale=${plan.silenceScale};gain=${plan.gain};sentence_pause_ms=${plan.sentencePauseMs};emotion=$emotion;energy=$energy")
    val sentences=SpeechChunks.split(clean)
    val rate=e.sampleRate()
    val min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
    localTrack=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
     .setBufferSizeInBytes(min*3).setTransferMode(AudioTrack.MODE_STREAM).build()
    val playbackTrack=localTrack
    track=playbackTrack;bargePaused=false
    log.event("tts_chunks","count=${sentences.size};startup_chunk_chars=${sentences.firstOrNull()?.length?:0};unicode=NFKC;pcm_stream=true;prefetch=true;style=${plan.style}")

    // Synthesis and playback are deliberately decoupled. Previously the native TTS callback
    // wrote directly to AudioTrack, so synthesis of chunk N+1 only began after chunk N had
    // already finished playing. On real ARM hardware this caused 2-3 second random gaps.
    val pcmQueue=LinkedBlockingQueue<ByteArray>(48)
    val poison=ByteArray(0)
    val written=AtomicLong(0L)
    val streamChunkCount=AtomicInteger(0)
    val playbackStarted=AtomicBoolean(false)
    val playbackThread=Thread({
     try {
      while(token==generation.get()&&!closed){
       val pcm=pcmQueue.poll(60,TimeUnit.MILLISECONDS)?:continue
       if(pcm===poison)break
       if(pcm.isEmpty())continue
       if(playbackStarted.compareAndSet(false,true)){
        try{playbackTrack.play()}catch(_:Exception){break}
        app.mainExecutor.execute{if(token==generation.get()&&!closed)listener.onStart()}
        log.event("playback_start","ima-voice-prefetch-${android.os.SystemClock.elapsedRealtime()}")
       }
       while(bargePaused&&token==generation.get()&&!closed){try{Thread.sleep(8)}catch(_:InterruptedException){return@Thread}}
       if(token!=generation.get()||closed)break
       var off=0
       while(off<pcm.size&&token==generation.get()&&!closed){
        val n=playbackTrack.write(pcm,off,minOf(4096,pcm.size-off),AudioTrack.WRITE_BLOCKING)
        if(n<=0)break
        off+=n;written.addAndGet(n.toLong());streamChunkCount.incrementAndGet()
       }
       val level=rms(pcm,0,pcm.size)
       val bands=spectrum(pcm,0,rate)
       app.mainExecutor.execute{if(token==generation.get()&&!closed){listener.onLevel(level);listener.onSpectrum(bands)}}
      }
     }catch(_:Throwable){}
    },"ima-pcm-playback-$token")
    playbackThread.priority=Thread.NORM_PRIORITY+1
    playbackThread.start()

    fun offerPcm(bytes:ByteArray):Boolean{
     while(token==generation.get()&&!closed){
      if(pcmQueue.offer(bytes,40,TimeUnit.MILLISECONDS))return true
     }
     return false
    }

    for((index,sentence) in sentences.withIndex()){
     if(token!=generation.get()||closed)throw CancellationException()
     val synthStart=android.os.SystemClock.elapsedRealtime()
     var firstPcmAt=0L
     log.event("tts_synthesis_start","chunk=$index;prefetch_depth=${pcmQueue.size};style=${plan.style};$sentence")
     e.synthesizeStreaming(sentence,voice,speed,plan.silenceScale,{token!=generation.get()||closed}){pcm->
      if(token!=generation.get()||closed)return@synthesizeStreaming false
      if(pcm.isEmpty())return@synthesizeStreaming true
      if(firstPcmAt==0L){
       firstPcmAt=android.os.SystemClock.elapsedRealtime()
       log.event("tts_first_pcm","chunk=$index;ms=${firstPcmAt-synthStart};bytes=${pcm.size};style=${plan.style};backend=fallback_irina")
      }
      val voiced=ImaProsodyPlanner.applyGain(pcm,plan.gain)
      val ok=offerPcm(voiced)
      if(ok)log.event("tts_pcm_prefetch","chunk=$index;bytes=${voiced.size};queue=${pcmQueue.size};callbacks=${streamChunkCount.get()};style=${plan.style}")
      ok
     }
     val pause=boundaryPauseMs(sentence,plan.sentencePauseMs)
     if(index<sentences.lastIndex&&pause>0&&token==generation.get()&&!closed){
      offerPcm(ByteArray((rate*pause/1000)*2))
     }
     log.event("tts_synthesis_end","chunk=$index;ms=${android.os.SystemClock.elapsedRealtime()-synthStart};first_pcm_ms=${if(firstPcmAt==0L)-1 else firstPcmAt-synthStart};prefetch_depth=${pcmQueue.size};written_total=${written.get()};style=${plan.style}")
    }
    offerPcm(poison)
    while(playbackThread.isAlive&&token==generation.get()&&!closed){playbackThread.join(80)}

    if(playbackStarted.get()&&token==generation.get()&&!closed){
     val frames=written.get()/2L
     val deadline=android.os.SystemClock.elapsedRealtime()+7000L
     while(token==generation.get()&&!closed&&(playbackTrack.playbackHeadPosition.toLong() and 0xffffffffL)<frames){
      if(bargePaused){delay(10);continue}
      if(android.os.SystemClock.elapsedRealtime()>deadline)break
      delay(12)
     }
    }
    withContext(Dispatchers.Main){if(token==generation.get()&&!closed){listener.onLevel(0f);listener.onDone()}}
   }catch(e:CancellationException){log.event("tts_cancelled","generation=$token;stream=true;backend=fallback_irina")}
   catch(e:Exception){log.event("tts_error",android.util.Log.getStackTraceString(e));withContext(Dispatchers.Main){if(token==generation.get()&&!closed)listener.onError("Ошибка локального синтеза: ${e.javaClass.simpleName}. Текст ответа доступен на экране.")}}
   finally{
    pendingSpeak.updateAndGet{v->if(v>0)v-1 else 0}
    bargePaused=false
    localTrack?.let{t->
     log.event("playback_end","stream=true;consumed_frames=${t.playbackHeadPosition};cancelled=${token!=generation.get()};backend=fallback_irina")
     if(track===t)track=null
     try{t.stop()}catch(_:Exception){}
     t.release()
    }
   }
  }
 }
 private fun boundaryPauseMs(sentence:String,planned:Int):Int{
  val s=sentence.trim()
  if(s.isEmpty())return 0
  return when{
   s.endsWith("…")||s.endsWith("...")->planned.coerceIn(90,150)
   s.endsWith("?")||s.endsWith("!")->planned.coerceIn(55,105)
   s.endsWith(".")->planned.coerceIn(45,90)
   s.endsWith(",")||s.endsWith(";")||s.endsWith(":")->planned.coerceIn(25,60)
   else->0 // never create a pause merely because a transport chunk ended
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
 override fun stop(){responseOpen.set(false);bargePaused=false;pendingSpeak.set(0);DiagnosticRecorder.get(app).event("tts_stop_requested","stream=true;ima_voice_runtime=v2");generation.incrementAndGet();val t=track;if(t!=null){try{t.pause();t.flush()}catch(_:Exception){}}}
 override fun releaseModel(){stop();ready=false;enqueue{synchronized(engineLock){engine?.close();engine=null;loadedVoice=""};ready=false}}
 override fun close(){if(closed)return;closed=true;stop();ready=false;enqueue{try{synchronized(engineLock){engine?.close();engine=null;loadedVoice=""}}finally{scope.cancel();worker.close()}}}
}
