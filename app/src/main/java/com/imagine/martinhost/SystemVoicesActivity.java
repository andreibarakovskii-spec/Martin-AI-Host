package com.imagine.martinhost;

import android.app.*;import android.os.*;import android.speech.tts.*;import android.widget.*;import java.util.*;

/** Lists only Russian voices reported by the installed Android TTS engine. */
public final class SystemVoicesActivity extends Activity implements TextToSpeech.OnInitListener {
 private LinearLayout root;private TextToSpeech tts;private TextView status;
 public void onCreate(Bundle b){super.onCreate(b);root=PartyScreen.root(this,"Русские голоса телефона");PartyScreen.text(this,root,"Это голоса уже установленного на телефоне движка. Они работают без API и обычно отвечают заметно быстрее. Значок «сеть» означает, что конкретному голосу может понадобиться интернет.",14);status=PartyScreen.text(this,root,"Получаю список…",16);tts=new TextToSpeech(this,this);}
 public void onInit(int code){runOnUiThread(()->render(code));}
 private void render(int code){if(code!=TextToSpeech.SUCCESS){status.setText("Системный голосовой движок недоступен");return;}List<Voice> all=new ArrayList<>();for(Voice v:tts.getVoices())if("ru".equals(v.getLocale().getLanguage()))all.add(v);all.sort(Comparator.comparing((Voice v)->v.isNetworkConnectionRequired()).thenComparing(Voice::getName));status.setText(all.isEmpty()?"Русские голоса не установлены":"Найдено русских голосов: "+all.size());for(Voice v:all){String label=v.getName()+" • качество "+v.getQuality()+(v.isNetworkConnectionRequired()?" • сеть":" • локальный");PartyScreen.button(this,root,"▶ "+label,()->preview(v));PartyScreen.button(this,root,"✓ Выбрать этот голос",()->{getSharedPreferences("martin",0).edit().putString("voice_provider","system").putString("system_voice",v.getName()).apply();status.setText("Выбран: "+label);});}PartyScreen.button(this,root,"Вернуться к 10 голосам Supertonic",()->{getSharedPreferences("martin",0).edit().putString("voice_provider","local").apply();finish();});}
 private void preview(Voice v){tts.stop();tts.setVoice(v);tts.setSpeechRate(1f);tts.speak("Привет, Андрей! Я Мартин. Сегодня говорю по-русски без спешки и с хорошим настроением.",TextToSpeech.QUEUE_FLUSH,null,"preview");}
 protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
