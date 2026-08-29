package com.imagine.martinhost;

import android.content.Context;
import android.os.*;
import android.speech.tts.*;
import java.util.*;

/** Explicit opt-in to a Russian voice already installed on the phone. */
public final class SystemMartinSpeaker implements MartinSpeaker,TextToSpeech.OnInitListener {
 private final Context app;private final Listener listener;private final Handler ui=new Handler(Looper.getMainLooper());
 private TextToSpeech tts;private boolean ready,closed;private final Runnable pulse=new Runnable(){public void run(){if(!ready||closed)return;listener.onLevel(.42f);ui.postDelayed(this,110);}};
 SystemMartinSpeaker(Context c,Listener l){app=c.getApplicationContext();listener=l;prepare();}
 public boolean isReady(){return ready;}
 public void prepare(){if(closed||tts!=null)return;listener.onPreparing("Готовлю установленный русский голос…");tts=new TextToSpeech(app,this);}
 @Override public void onInit(int status){
  if(closed)return;if(status!=TextToSpeech.SUCCESS){listener.onError("Системная озвучка недоступна");return;}
  String selected=app.getSharedPreferences("martin",0).getString("system_voice","");Voice chosen=null;
  for(Voice v:tts.getVoices())if(v.getName().equals(selected)&&"ru".equals(v.getLocale().getLanguage()))chosen=v;
  if(chosen==null)chosen=tts.getVoices().stream().filter(v->"ru".equals(v.getLocale().getLanguage()))
   .max(Comparator.comparingInt(Voice::getQuality).thenComparing(v->v.isNetworkConnectionRequired()?0:1)).orElse(null);
  if(chosen==null){tts.setLanguage(Locale.forLanguageTag("ru-RU"));}else tts.setVoice(chosen);
  tts.setSpeechRate(app.getSharedPreferences("martin",0).getFloat("system_voice_speed",1f));
  tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
   public void onStart(String id){ui.post(()->{listener.onStart();ui.removeCallbacks(pulse);ui.post(pulse);DiagnosticRecorder.get(app).event("system_tts_start",selected);});}
   public void onDone(String id){ui.post(()->{ui.removeCallbacks(pulse);listener.onLevel(0);listener.onDone();});}
   public void onError(String id){ui.post(()->{ui.removeCallbacks(pulse);listener.onError("Ошибка установленного голоса");});}
  });
  ready=true;listener.onReady();
 }
 public void speak(String text,String emotion,float energy){if(!ready){listener.onError("Голос ещё не готов");return;}float base=app.getSharedPreferences("martin",0).getFloat("system_voice_speed",1.08f),rate=base,pitch=1f;String e=emotion==null?"":emotion.toLowerCase(Locale.ROOT);if(e.contains("happy")||e.contains("excited")||e.contains("playful")){rate*=1.05f;pitch=1.04f;}else if(e.contains("warm")){rate*=.98f;pitch=.98f;}else if(e.contains("curious")){rate*=1.01f;pitch=1.01f;}tts.setSpeechRate(rate);tts.setPitch(pitch);DiagnosticRecorder.get(app).event("system_tts_prosody","emotion="+e+";rate="+rate+";pitch="+pitch+";stress_hints=true");Bundle b=new Bundle();b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,1f);tts.speak(RussianTtsNormalizer.prepare(text),TextToSpeech.QUEUE_FLUSH,b,UUID.randomUUID().toString());}
 public void stop(){ui.removeCallbacks(pulse);if(tts!=null)tts.stop();}
 public void releaseModel(){stop();ready=false;if(tts!=null){tts.shutdown();tts=null;}}
 public void close(){closed=true;releaseModel();}
}
