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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final int REQ=7;
    private static final Pattern SCORE=Pattern.compile("\\[\\[ASK_NAME_FOR_SCORE:([1-3])]]");
    private static final Pattern FRAGMENT=Pattern.compile("\\[\\[PLAY_FRAGMENT:(.+?)\\|([3-9])]]");
    private static final Pattern MUSIC=Pattern.compile("\\[\\[PLAY_MUSIC:(.+?)]]");

    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private GrokClient grok;
    private GuestStore guests;
    private YandexMusicClient music;
    private MartinCharacterView martin;
    private TextView state,lastHeard,lastReply;
    private Button main,pause,stop,next,musicStop;
    private boolean active,paused,speaking,gameMode;
    private int awaitingScoreDelta=0;
    private Runnable afterSpeech;
    private final LinkedHashSet<String> recentGames=new LinkedHashSet<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);buildUi();grok=new GrokClient(this);guests=new GuestStore(this);music=new YandexMusicClient(this);tts=new TextToSpeech(this,this);requestPerms();
    }

    private void requestPerms(){ArrayList<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);}
    private void setMartin(MartinCharacterView.State s,String label){martin.setState(s);state.setText(label);}

    private void startListening(){
        if(paused||speaking||!active)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}
        if(sr!=null)try{sr.destroy();}catch(Exception ignored){}
        sr=SpeechRecognizer.isOnDeviceRecognitionAvailable(this)?SpeechRecognizer.createOnDeviceSpeechRecognizer(this):SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        sr.startListening(i);
        if(awaitingScoreDelta>0)setMartin(MartinCharacterView.State.LISTENING,"🏆 Назовите имя ответившего");
        else if(gameMode)setMartin(MartinCharacterView.State.LISTENING,"🎮 Игра идёт — отвечайте без слова «Мартин»");
        else setMartin(MartinCharacterView.State.LISTENING,"🟢 Слушаю: скажите «Мартин, …»");
    }

    private MartinCharacterView.State stateForPrompt(String q){String s=q.toLowerCase(Locale.ROOT);if(s.contains("тост"))return MartinCharacterView.State.TOAST;if(s.contains("музык")||s.contains("песн")||s.contains("мелоди")||s.contains("dj")||s.contains("дидже"))return MartinCharacterView.State.DJ;if(s.contains("конкурс")||s.contains("игр")||s.contains("виктор")||s.contains("что? где? когда")||s.contains("цитат"))return MartinCharacterView.State.GAME;return MartinCharacterView.State.TALKING;}

    private void speakLocal(String text,MartinCharacterView.State visual){speakLocal(text,visual,null);}
    private void speakLocal(String text,MartinCharacterView.State visual,Runnable after){
        speaking=true;afterSpeech=after;if(sr!=null)try{sr.cancel();}catch(Exception ignored){}lastReply.setText(text);setMartin(visual,"🔊 Мартин говорит…");tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"martin");
    }

    private void ask(String q){
        speaking=true;if(sr!=null)try{sr.cancel();}catch(Exception ignored){}setMartin(MartinCharacterView.State.THINKING,"🟡 Мартин думает…");final MartinCharacterView.State replyState=stateForPrompt(q);
        grok.reply(q,new GrokClient.Callback(){
            public void onResult(String text){runOnUiThread(()->handleAiResult(text,replyState));}
            public void onError(String e){runOnUiThread(()->{speaking=false;martin.setState(MartinCharacterView.State.IDLE);state.setText("Ошибка AI");lastReply.setText(e);startListening();});}
        });
    }

    private void handleAiResult(String text,MartinCharacterView.State replyState){
        Matcher score=SCORE.matcher(text);if(score.find())awaitingScoreDelta=Integer.parseInt(score.group(1));
        Matcher frag=FRAGMENT.matcher(text);String fragmentQuery=null;int fragmentSeconds=6;if(frag.find()){fragmentQuery=frag.group(1).trim();fragmentSeconds=Integer.parseInt(frag.group(2));}
        Matcher full=MUSIC.matcher(text);String musicQuery=null;if(full.find())musicQuery=full.group(1).trim();
        String clean=SCORE.matcher(FRAGMENT.matcher(MUSIC.matcher(text).replaceAll("")).replaceAll("")).replaceAll("").trim();
        final String fQuery=fragmentQuery!=null?fragmentQuery:musicQuery;
        final int fSeconds=fragmentQuery!=null?fragmentSeconds:0;
        Runnable action=fQuery==null?null:()->playMusic(fQuery,fSeconds);
        if(clean.isBlank()){
            speaking=false;afterSpeech=null;if(action!=null)action.run();else startListening();
        }else speakLocal(clean,replyState,action);
    }

    private void playMusic(String query,int fragmentSeconds){
        if(sr!=null)try{sr.cancel();}catch(Exception ignored){}
        speaking=false;setMartin(MartinCharacterView.State.DJ,fragmentSeconds>0?"🎵 Готовлю фрагмент…":"🎵 Ищу в Яндекс Музыке…");lastReply.setText("Поиск: "+query);
        YandexMusicClient.Callback cb=new YandexMusicClient.Callback(){
            public void onStarted(YandexMusicClient.TrackInfo track){runOnUiThread(()->{lastReply.setText("🎵 "+track.label());setMartin(MartinCharacterView.State.DJ,fragmentSeconds>0?"🎶 Фрагмент играет":"🎶 Сейчас играет");if(fragmentSeconds==0&&active&&!paused)new android.os.Handler(getMainLooper()).postDelayed(MainActivity.this::startListening,900);});}
            public void onFinished(YandexMusicClient.TrackInfo track){runOnUiThread(()->{if(fragmentSeconds>0&&active&&!paused){setMartin(MartinCharacterView.State.LISTENING,"🎤 Ваш ответ");startListening();}});}
            public void onError(String e){runOnUiThread(()->speakLocal("Не получилось включить музыку. "+shortMusicError(e),MartinCharacterView.State.TALKING));}
        };
        if(fragmentSeconds>0)music.playFragment(query,fragmentSeconds,cb);else music.play(query,cb);
    }

    private String shortMusicError(String e){
        if(e==null)return "Проверьте подключение Яндекс Музыки в настройках.";
        String low=e.toLowerCase(Locale.ROOT);if(low.contains("войдите")||low.contains("oauth")||low.contains("401")||low.contains("403"))return "Откройте настройки и войдите в Яндекс Музыку заново.";
        return e.length()>150?e.substring(0,150):e;
    }

    private void startNextGame(){
        GameCatalog.Game g=GameCatalog.random(this,recentGames);recentGames.add(g.id);while(recentGames.size()>4){Iterator<String> it=recentGames.iterator();it.next();it.remove();}
        grok.clearHistory();gameMode=!g.id.equals("toast");
        martin.setState(g.id.equals("toast")?MartinCharacterView.State.TOAST:g.id.equals("melody")?MartinCharacterView.State.DJ:MartinCharacterView.State.GAME);
        ask("Следующая активность: «"+g.title+"». "+g.hostPrompt+" Учитывай подготовленные профили гостей и текущий режим проектора.");
    }

    private void hardStop(){
        active=false;paused=false;speaking=false;gameMode=false;awaitingScoreDelta=0;afterSpeech=null;grok.clearHistory();if(sr!=null)try{sr.cancel();}catch(Exception ignored){}if(tts!=null)tts.stop();if(music!=null)music.stop();setMartin(MartinCharacterView.State.SLEEPING,"⛔ Остановлено");main.setText("🎙 НАЧАТЬ СЛУШАТЬ");
    }

    @Override public void onInit(int s){
        if(s==TextToSpeech.SUCCESS){
            tts.setLanguage(new Locale("ru","RU"));tts.setSpeechRate(0.90f);
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){runOnUiThread(()->{speaking=false;martin.setState(MartinCharacterView.State.HAPPY);Runnable next=afterSpeech;afterSpeech=null;if(next!=null){new android.os.Handler(getMainLooper()).postDelayed(next,250);}else new android.os.Handler(getMainLooper()).postDelayed(()->{if(active&&!paused)startListening();else martin.setState(MartinCharacterView.State.IDLE);},450);});}
                public void onError(String id){runOnUiThread(()->{speaking=false;afterSpeech=null;if(active&&!paused)startListening();else martin.setState(MartinCharacterView.State.IDLE);});}
            });
        }
    }

    @Override public void onResults(Bundle b){
        ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String t=r==null||r.isEmpty()?"":r.get(0).trim();lastHeard.setText(t);String low=t.toLowerCase(Locale.ROOT);
        if(low.contains("мартин стоп")||low.equals("стоп")){hardStop();return;}
        if(low.contains("останови музыку")||low.contains("выключи музыку")||low.contains("стоп музыка")){music.stop();speakLocal("Музыку остановил.",MartinCharacterView.State.TALKING);return;}
        if(low.contains("музыку на пауз")||low.contains("пауза музыка")){music.pause();speakLocal("Музыка на паузе.",MartinCharacterView.State.TALKING);return;}
        if(low.contains("продолжи музыку")||low.contains("возобнови музыку")){music.resume();speakLocal("Продолжаю.",MartinCharacterView.State.DJ);return;}

        if(awaitingScoreDelta>0){
            int delta=awaitingScoreDelta;if(guests.addScore(t,delta)){awaitingScoreDelta=0;speakLocal("Записал. "+t+" получает "+delta+" "+scoreWord(delta)+"!",MartinCharacterView.State.HAPPY);}else{speakLocal("Не нашёл «"+t+"» в списке гостей. Назовите имя ещё раз.",MartinCharacterView.State.LISTENING);}return;
        }

        if(gameMode){
            if(low.contains("закончи игру")||low.contains("хватит играть")||low.contains("закончим игру")){gameMode=false;grok.clearHistory();speakLocal("Игра окончена. Счёт сохранён.",MartinCharacterView.State.HAPPY);return;}
            int x=low.indexOf("мартин");String answer=x>=0?t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+",""):t;if(answer.isBlank())answer=t;ask(answer);return;
        }

        int x=low.indexOf("мартин");
        if(x>=0){
            String q=t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+","");if(q.isBlank())q="Поздоровайся и спроси, чем помочь компании.";String ql=q.toLowerCase(Locale.ROOT);
            if(ql.contains("следующ")&&(ql.contains("игр")||ql.contains("конкурс"))){startNextGame();return;}
            String musicQuery=directMusicQuery(q);
            if(musicQuery!=null){speakLocal("Включаю «"+musicQuery+"».",MartinCharacterView.State.DJ,()->playMusic(musicQuery,0));return;}
            ask(q);
        }else if(active&&!paused)startListening();
    }

    private String directMusicQuery(String q){
        String low=q.toLowerCase(Locale.ROOT).trim();String[] verbs={"включи ","поставь ","запусти "};
        for(String v:verbs)if(low.startsWith(v)){
            String rest=q.substring(v.length()).trim();String rl=rest.toLowerCase(Locale.ROOT);
            if(rl.startsWith("музыку "))rest=rest.substring(7).trim();else if(rl.startsWith("песню "))rest=rest.substring(6).trim();else if(rl.startsWith("трек "))rest=rest.substring(5).trim();
            String check=rest.toLowerCase(Locale.ROOT);if(check.contains("игр")||check.contains("конкурс")||check.contains("режим")||check.contains("проектор"))return null;
            return rest.isBlank()?null:rest;
        }
        return null;
    }
    private String scoreWord(int n){return n==1?"балл":n<5?"балла":"баллов";}

    @Override public void onPartialResults(Bundle b){}@Override public void onReadyForSpeech(Bundle b){martin.setState(MartinCharacterView.State.LISTENING);}@Override public void onBeginningOfSpeech(){setMartin(MartinCharacterView.State.LISTENING,"🎤 Слышу речь…");}@Override public void onRmsChanged(float v){}@Override public void onBufferReceived(byte[] b){}@Override public void onEndOfSpeech(){setMartin(MartinCharacterView.State.THINKING,"🟡 Разбираю фразу…");}@Override public void onEvent(int t,Bundle b){}@Override public void onError(int e){if(active&&!paused&&!speaking)new android.os.Handler(getMainLooper()).postDelayed(this::startListening,350);}

    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(8,8,8,8);return v;}private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}

    private void buildUi(){
        getWindow().setStatusBarColor(0xFF0B0C10);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,30);root.setBackgroundColor(0xFF0B0C10);scroll.addView(root);
        TextView h=tv("МАРТИН",34,Color.WHITE);h.setGravity(Gravity.CENTER);root.addView(h);TextView sub=tv("AI-ведущий вечеринки • Катя 35",15,0xFFB9BECA);sub.setGravity(Gravity.CENTER);root.addView(sub);
        martin=new MartinCharacterView(this);root.addView(martin,new LinearLayout.LayoutParams(-1,dp(330)));state=tv("Готов",21,0xFFFFD166);state.setGravity(Gravity.CENTER);root.addView(state);
        main=btn("🎙 НАЧАТЬ СЛУШАТЬ");root.addView(main,new LinearLayout.LayoutParams(-1,dp(62)));
        LinearLayout row=new LinearLayout(this);pause=btn("☕ ПАУЗА");stop=btn("🛑 СТОП");row.addView(pause,new LinearLayout.LayoutParams(0,dp(54),1));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(54),1);sp.setMargins(dp(8),0,0,0);row.addView(stop,sp);root.addView(row);
        next=btn("✨ СЛЕДУЮЩАЯ ИГРА");root.addView(next,new LinearLayout.LayoutParams(-1,dp(58)));musicStop=btn("🎵 ОСТАНОВИТЬ МУЗЫКУ");root.addView(musicStop,new LinearLayout.LayoutParams(-1,dp(52)));
        root.addView(tv("УСЛЫШАЛ",11,0xFF8F96A3));lastHeard=tv("Скажите: «Мартин, включи Максим»",17,Color.WHITE);root.addView(lastHeard);root.addView(tv("МАРТИН",11,0xFFFFD166));lastReply=tv("Здесь появится последняя реплика AI",18,Color.WHITE);root.addView(lastReply);
        Button settings=btn("⚙ НАСТРОЙКИ / ГОСТИ / ЯНДЕКС МУЗЫКА");root.addView(settings,new LinearLayout.LayoutParams(-1,dp(52)));setContentView(scroll);
        main.setOnClickListener(v->{active=!active;if(active){paused=false;main.setText("🟢 СЛУШАЮ");startListening();}else hardStop();});
        pause.setOnClickListener(v->{paused=!paused;if(paused){if(sr!=null)sr.cancel();if(tts!=null)tts.stop();if(music!=null)music.pause();setMartin(MartinCharacterView.State.SLEEPING,"⏸ Пауза");}else{if(music!=null)music.resume();setMartin(MartinCharacterView.State.IDLE,"Возобновляю…");startListening();}});
        stop.setOnClickListener(v->hardStop());next.setOnClickListener(v->startNextGame());musicStop.setOnClickListener(v->{if(music!=null)music.stop();setMartin(MartinCharacterView.State.IDLE,"Музыка остановлена");if(active&&!paused)startListening();});settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(sr!=null)sr.destroy();if(tts!=null)tts.shutdown();if(music!=null)music.stop();super.onDestroy();}
}
