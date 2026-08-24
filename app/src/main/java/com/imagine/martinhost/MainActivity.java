package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final int REQ=7;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private GrokClient grok;
    private MartinCharacterView martin;
    private TextView state,lastHeard,lastReply;
    private Button main,pause,stop,next;
    private boolean active,paused,speaking;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        grok=new GrokClient(this);
        tts=new TextToSpeech(this,this);
        requestPerms();
    }

    private void requestPerms(){
        ArrayList<String> p=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);
        if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);
    }

    private void setMartin(MartinCharacterView.State s,String label){
        martin.setState(s);
        state.setText(label);
    }

    private void startListening(){
        if(paused||speaking||!active)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}
        if(sr!=null)try{sr.destroy();}catch(Exception ignored){}
        sr=SpeechRecognizer.isOnDeviceRecognitionAvailable(this)?SpeechRecognizer.createOnDeviceSpeechRecognizer(this):SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        sr.startListening(i);
        setMartin(MartinCharacterView.State.LISTENING,"🟢 Слушаю: скажите «Мартин, …»");
    }

    private MartinCharacterView.State stateForPrompt(String q){
        String s=q.toLowerCase(Locale.ROOT);
        if(s.contains("тост"))return MartinCharacterView.State.TOAST;
        if(s.contains("музык")||s.contains("песн")||s.contains("мелоди")||s.contains("dj")||s.contains("дидже"))return MartinCharacterView.State.DJ;
        if(s.contains("конкурс")||s.contains("игр")||s.contains("виктор")||s.contains("что? где? когда")||s.contains("цитат"))return MartinCharacterView.State.GAME;
        return MartinCharacterView.State.TALKING;
    }

    private void ask(String q){
        speaking=true;
        if(sr!=null)try{sr.cancel();}catch(Exception ignored){}
        setMartin(MartinCharacterView.State.THINKING,"🟡 Мартин думает…");
        final MartinCharacterView.State replyState=stateForPrompt(q);
        grok.reply(q,new GrokClient.Callback(){
            public void onResult(String text){
                runOnUiThread(()->{
                    lastReply.setText(text);
                    setMartin(replyState,"🔊 Мартин говорит…");
                    tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"martin");
                });
            }
            public void onError(String e){
                runOnUiThread(()->{
                    speaking=false;
                    martin.setState(MartinCharacterView.State.IDLE);
                    state.setText("Ошибка AI");
                    lastReply.setText(e);
                    startListening();
                });
            }
        });
    }

    private void hardStop(){
        active=false;paused=false;speaking=false;
        if(sr!=null)try{sr.cancel();}catch(Exception ignored){}
        if(tts!=null)tts.stop();
        setMartin(MartinCharacterView.State.SLEEPING,"⛔ Остановлено");
        main.setText("🎙 НАЧАТЬ СЛУШАТЬ");
    }

    @Override public void onInit(int s){
        if(s==TextToSpeech.SUCCESS){
            tts.setLanguage(new Locale("ru","RU"));
            tts.setSpeechRate(1.05f);
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){runOnUiThread(()->{speaking=false;martin.setState(MartinCharacterView.State.HAPPY);new android.os.Handler(getMainLooper()).postDelayed(()->{if(active&&!paused)startListening();else martin.setState(MartinCharacterView.State.IDLE);},500);});}
                public void onError(String id){runOnUiThread(()->{speaking=false;if(active&&!paused)startListening();else martin.setState(MartinCharacterView.State.IDLE);});}
            });
        }
    }

    @Override public void onResults(Bundle b){
        ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String t=r==null||r.isEmpty()?"":r.get(0);
        lastHeard.setText(t);
        String low=t.toLowerCase(Locale.ROOT);
        if(low.contains("мартин стоп")||low.equals("стоп")){hardStop();return;}
        int x=low.indexOf("мартин");
        if(x>=0){
            String q=t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+","");
            if(q.isBlank())q="Поздоровайся и спроси, чем помочь компании.";
            ask(q);
        } else if(active&&!paused) startListening();
    }

    @Override public void onPartialResults(Bundle b){}
    @Override public void onReadyForSpeech(Bundle b){martin.setState(MartinCharacterView.State.LISTENING);}
    @Override public void onBeginningOfSpeech(){setMartin(MartinCharacterView.State.LISTENING,"🎤 Слышу речь…");}
    @Override public void onRmsChanged(float v){}
    @Override public void onBufferReceived(byte[] b){}
    @Override public void onEndOfSpeech(){setMartin(MartinCharacterView.State.THINKING,"🟡 Разбираю фразу…");}
    @Override public void onEvent(int t,Bundle b){}
    @Override public void onError(int e){if(active&&!paused&&!speaking)new android.os.Handler(getMainLooper()).postDelayed(this::startListening,350);}

    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(8,8,8,8);return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    private void buildUi(){
        getWindow().setStatusBarColor(0xFF0B0C10);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,30);root.setBackgroundColor(0xFF0B0C10);scroll.addView(root);

        TextView h=tv("МАРТИН",34,Color.WHITE);h.setGravity(Gravity.CENTER);root.addView(h);
        TextView sub=tv("AI-ведущий вечеринки • Катя 35",15,0xFFB9BECA);sub.setGravity(Gravity.CENTER);root.addView(sub);

        martin=new MartinCharacterView(this);
        root.addView(martin,new LinearLayout.LayoutParams(-1,dp(330)));

        state=tv("Готов",21,0xFFFFD166);state.setGravity(Gravity.CENTER);root.addView(state);
        main=btn("🎙 НАЧАТЬ СЛУШАТЬ");root.addView(main,new LinearLayout.LayoutParams(-1,dp(62)));

        LinearLayout row=new LinearLayout(this);pause=btn("☕ ПАУЗА");stop=btn("🛑 СТОП");row.addView(pause,new LinearLayout.LayoutParams(0,dp(54),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1);sp.setMargins(dp(8),0,0,0);row.addView(stop,sp);root.addView(row);
        next=btn("✨ СЛЕДУЮЩАЯ ИГРА");root.addView(next,new LinearLayout.LayoutParams(-1,dp(58)));

        root.addView(tv("УСЛЫШАЛ",11,0xFF8F96A3));lastHeard=tv("Скажите: «Мартин, давай игру»",17,Color.WHITE);root.addView(lastHeard);
        root.addView(tv("МАРТИН",11,0xFFFFD166));lastReply=tv("Здесь появится последняя реплика AI",18,Color.WHITE);root.addView(lastReply);
        Button settings=btn("⚙ НАСТРОЙКИ / ГОСТИ / ПРОЕКТОР");root.addView(settings,new LinearLayout.LayoutParams(-1,dp(52)));
        setContentView(scroll);

        main.setOnClickListener(v->{active=!active;if(active){paused=false;main.setText("🟢 СЛУШАЮ");startListening();}else hardStop();});
        pause.setOnClickListener(v->{paused=!paused;if(paused){if(sr!=null)sr.cancel();if(tts!=null)tts.stop();setMartin(MartinCharacterView.State.SLEEPING,"⏸ Пауза");}else{setMartin(MartinCharacterView.State.IDLE,"Возобновляю…");startListening();}});
        stop.setOnClickListener(v->hardStop());
        next.setOnClickListener(v->ask("Предложи следующую короткую веселую активность на 5-10 минут. Выбирай между викториной, Что? Где? Когда?, угадай мелодию, цитатами, логикой, импровизацией, тостом или другой игрой. ОБЯЗАТЕЛЬНО сначала объясни правила, начисление баллов, покажи один понятный пример, затем спроси готовы ли все, и только после подтверждения начинай игру."));
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(sr!=null)sr.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
