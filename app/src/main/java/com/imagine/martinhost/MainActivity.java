package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** Martin MVP: continuous Meeting audio -> Whisper -> Groq -> neural TTS -> NPC. */
public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int REQ=7;
    private TextToSpeech fallbackTts;
    private MartinNeuralSpeaker neural;
    private GrokClient grok; private GroqTranscriber stt; private GuestStore guests;
    private TurnManager turns; private ContinuousSpeechEngine audio;
    private MartinSpriteView martin; private TextView state,lastHeard,lastReply,providerBadge,voiceBadge;
    private Button main,pause,stop,next; private boolean active,paused,fallbackReady,neuralReady; private int awaitingScoreDelta=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); buildUi(); PartyAudioRouter.prepare(this);
        grok=new GrokClient(this); stt=new GroqTranscriber(this); guests=new GuestStore(this);
        turns=new TurnManager(s->runOnUiThread(()->onTurnState(s)));
        audio=new ContinuousSpeechEngine(this,turns,new ContinuousSpeechEngine.Listener(){
            public void onSpeechChunk(byte[] wav){ turns.onUserFinal(); stt.transcribe(wav,new GroqTranscriber.Callback(){
                public void onText(String text){runOnUiThread(()->handleTranscript(text));}
                public void onError(String e){runOnUiThread(()->{turns.forceListen();state.setText("STT: "+e);});}
            }); }
            public void onLevel(float rms,float noise,boolean speech){runOnUiThread(()->{if(turns.acceptMicForStt())martin.setSpeechLevel(Math.max(0f,Math.min(1f,(rms+48f)/36f)));});}
            public void onStatus(String s){runOnUiThread(()->{if(active&&!paused&&turns.state()!=TurnManager.State.SPEAKING)state.setText(s);});}
            public void onError(String e){runOnUiThread(()->state.setText("Микрофон: "+e));}
        });
        neural=new MartinNeuralSpeaker(this,new MartinNeuralSpeaker.Listener(){
            public void onPreparing(String m){runOnUiThread(()->{voiceBadge.setText("ГОЛОС • ЗАГРУЗКА");state.setText(m);});}
            public void onReady(){runOnUiThread(()->{neuralReady=true;voiceBadge.setText("ГОЛОС • NEURAL");if(!active)state.setText("Живой голос готов");});}
            public void onStart(){runOnUiThread(()->{martin.setSpeechLevel(.25f);state.setText("Мартин говорит…");});}
            public void onLevel(float level){runOnUiThread(()->martin.setSpeechLevel(level));}
            public void onDone(){runOnUiThread(()->finishSpeech());}
            public void onError(String message){runOnUiThread(()->{voiceBadge.setText("ГОЛОС • FALLBACK");if(fallbackReady&&turns.state()==TurnManager.State.SPEAKING)speakFallback(lastReply.getText().toString());else{state.setText(message);turns.onAiSpeechDone();}});}
        });
        neural.prepare();
        fallbackTts=new TextToSpeech(this,this);
        requestPerms();
    }

    @Override protected void onResume(){super.onResume();refreshProviderBadge();}
    private void requestPerms(){ArrayList<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&active&&!paused)startAudio();}

    private void setMartin(MartinSpriteView.State s,String label){martin.setState(s);state.setText(label);}
    private void onTurnState(TurnManager.State s){if(!active||paused)return;switch(s){case LISTENING:setMartin(MartinSpriteView.State.LISTENING,awaitingScoreDelta>0?"Назовите имя ответившего":"Слушаю…");break;case THINKING:setMartin(MartinSpriteView.State.THINKING,"Думаю…");break;case SPEAKING:state.setText("Мартин говорит…");break;case COOLDOWN:state.setText("Снова слушаю…");break;}}
    private void startAudio(){if(!active||paused)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}turns.forceListen();audio.start();main.setText("●  СЛУШАЮ");}

    private MartinSpriteView.State stateForPrompt(String q){String s=q.toLowerCase(Locale.ROOT);if(s.contains("тост"))return MartinSpriteView.State.TOAST;if(s.contains("музык")||s.contains("dj"))return MartinSpriteView.State.DJ;if(s.contains("игр")||s.contains("вопрос")||s.contains("правил"))return MartinSpriteView.State.GAME;return MartinSpriteView.State.TALKING;}
    private String emotionFor(MartinSpriteView.State visual){switch(visual){case HAPPY:return "celebrate";case TOAST:return "warm";case GAME:return "playful";case DJ:return "excited";case LISTENING:return "neutral";default:return "neutral";}}
    private float energyFor(MartinSpriteView.State visual){switch(visual){case HAPPY:return .9f;case DJ:return .95f;case GAME:return .7f;case TOAST:return .5f;default:return .58f;}}

    private void speak(String text,MartinSpriteView.State visual){
        if(text==null||text.isBlank()){turns.forceListen();return;}
        lastReply.setText(text);turns.onAiWillSpeak();setMartin(visual,"Мартин говорит…");
        if(neuralReady&&neural.isReady()){neural.speak(text,emotionFor(visual),energyFor(visual));return;}
        if(fallbackReady){voiceBadge.setText("ГОЛОС • FALLBACK");speakFallback(text);return;}
        state.setText("Готовлю живой голос…");turns.onAiSpeechDone();
    }
    private void speakFallback(String text){if(fallbackTts==null||!fallbackReady){turns.onAiSpeechDone();return;}fallbackTts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"martin-fallback");}
    private void finishSpeech(){martin.setSpeechLevel(0f);martin.setState(MartinSpriteView.State.HAPPY);turns.onAiSpeechDone();}

    private void ask(String q){turns.onUserFinal();setMartin(MartinSpriteView.State.THINKING,"Думаю…");final MartinSpriteView.State replyState=stateForPrompt(q);grok.reply(q,new GrokClient.Callback(){public void onResult(String text){runOnUiThread(()->{String marker="[[ASK_NAME_FOR_SCORE:1]]";boolean score=text.contains(marker);String clean=text.replace(marker,"").trim();if(score)awaitingScoreDelta=1;speak(clean,replyState);});}public void onError(String e){runOnUiThread(()->{lastReply.setText(e);turns.forceListen();});}});}
    private void startGame(){ask("Начни единственную тестовую игру «Что? Где? Когда?». Коротко объясни правила, приведи один пример и спроси, готовы ли гости. Сам первый вопрос пока не задавай. Когда затем услышишь правильный ответ, обязательно добавь маркер [[ASK_NAME_FOR_SCORE:1]] и спроси, кто угадал.");}
    private void handleTranscript(String t){if(t==null||t.isBlank()){turns.forceListen();return;}lastHeard.setText(t);String low=t.toLowerCase(Locale.ROOT);if(low.contains("мартин стоп")||low.equals("стоп")){hardStop();return;}if(awaitingScoreDelta>0){int delta=awaitingScoreDelta;if(guests.addScore(t,delta)){awaitingScoreDelta=0;speak("Записал. "+t+" получает "+delta+" балл!",MartinSpriteView.State.HAPPY);}else speak("Не нашёл «"+t+"» в списке гостей. Назови имя ещё раз.",MartinSpriteView.State.LISTENING);return;}int x=low.indexOf("мартин");if(x>=0){String q=t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+","");if(q.isBlank())q="Поздоровайся и спроси, чем помочь компании.";String ql=q.toLowerCase(Locale.ROOT);if(ql.contains("игр")||ql.contains("что где когда")){startGame();return;}ask(q);}else turns.forceListen();}
    private void hardStop(){active=false;paused=false;awaitingScoreDelta=0;audio.stop();if(neural!=null)neural.stop();if(fallbackTts!=null)fallbackTts.stop();turns.forceListen();setMartin(MartinSpriteView.State.SLEEPING,"Остановлено");main.setText("НАЧАТЬ СЛУШАТЬ");}

    @Override public void onInit(int s){
        if(s!=TextToSpeech.SUCCESS){fallbackReady=false;return;}
        int lang=fallbackTts.setLanguage(new Locale("ru","RU")); fallbackReady=lang!=TextToSpeech.LANG_MISSING_DATA&&lang!=TextToSpeech.LANG_NOT_SUPPORTED;
        fallbackTts.setSpeechRate(1.02f); fallbackTts.setPitch(.98f);
        fallbackTts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
            public void onStart(String id){runOnUiThread(()->martin.setSpeechLevel(.65f));}
            public void onDone(String id){runOnUiThread(()->finishSpeech());}
            public void onError(String id){runOnUiThread(()->{state.setText("Ошибка резервного TTS");finishSpeech();});}
        });
    }

    private void refreshProviderBadge(){if(providerBadge==null)return;var p=getSharedPreferences("martin",0);String key=p.getString("ai_key",p.getString("xai_key",""));providerBadge.setText(key.startsWith("gsk_")?"GROQ • WHISPER":"НУЖЕН GSK");}
    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;}
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setBackground(bg(color,18));return b;}

    private void buildUi(){
        getWindow().setStatusBarColor(0xFF07080D);getWindow().setNavigationBarColor(0xFF07080D);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(30));root.setBackgroundColor(0xFF07080D);scroll.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView brand=tv("MARTIN",30,Color.WHITE);brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));providerBadge=tv("AI",11,0xFFB39BFF);providerBadge.setGravity(Gravity.CENTER);providerBadge.setBackground(bg(0xFF21163D,16));top.addView(providerBadge,new LinearLayout.LayoutParams(dp(120),dp(38)));root.addView(top);
        LinearLayout badges=new LinearLayout(this);badges.setGravity(Gravity.CENTER_VERTICAL);badges.addView(tv("AI-ведущий • Катя 35",14,0xFF8F96A8),new LinearLayout.LayoutParams(0,-2,1));voiceBadge=tv("ГОЛОС • ЗАГРУЗКА",10,0xFFFFB65C);voiceBadge.setGravity(Gravity.CENTER);voiceBadge.setBackground(bg(0xFF281A12,14));badges.addView(voiceBadge,new LinearLayout.LayoutParams(dp(132),dp(34)));root.addView(badges);
        martin=new MartinSpriteView(this);GradientDrawable heroBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF12101D,0xFF090A10,0xFF180C2B});heroBg.setCornerRadius(dp(28));martin.setBackground(heroBg);LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,dp(410));heroLp.setMargins(0,dp(10),0,dp(12));root.addView(martin,heroLp);
        state=tv("Готовлю живой голос…",20,0xFFE8E4F4);state.setGravity(Gravity.CENTER);state.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(state);
        main=button("НАЧАТЬ СЛУШАТЬ",0xFF6D35F2);root.addView(main,new LinearLayout.LayoutParams(-1,dp(60)));
        LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(10),0,0);pause=button("ПАУЗА",0xFF181B24);stop=button("СТОП",0xFF2A171E);row.addView(pause,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(52),1);slp.setMargins(dp(10),0,0,0);row.addView(stop,slp);root.addView(row);
        next=button("🧠  ЧТО? ГДЕ? КОГДА?",0xFF231745);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,dp(56));nlp.setMargins(0,dp(10),0,0);root.addView(next,nlp);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(bg(0xFF11131A,18));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);clp.setMargins(0,dp(14),0,0);root.addView(card,clp);card.addView(tv("УСЛЫШАЛ",11,0xFF777F91));lastHeard=tv("Скажи: «Мартин, привет»",16,Color.WHITE);card.addView(lastHeard);card.addView(tv("МАРТИН",11,0xFF9A6BFF));lastReply=tv("Здесь появится ответ",17,0xFFE4E5EA);card.addView(lastReply);
        Button settings=button("⚙  НАСТРОЙКИ • ГОСТИ • ПРОЕКТОР",0xFF171922);LinearLayout.LayoutParams setLp=new LinearLayout.LayoutParams(-1,dp(54));setLp.setMargins(0,dp(12),0,0);root.addView(settings,setLp);setContentView(scroll);
        main.setOnClickListener(v->{active=!active;if(active){paused=false;startAudio();}else hardStop();});pause.setOnClickListener(v->{paused=!paused;if(paused){audio.stop();if(neural!=null)neural.stop();if(fallbackTts!=null)fallbackTts.stop();setMartin(MartinSpriteView.State.SLEEPING,"Пауза");}else startAudio();});stop.setOnClickListener(v->hardStop());next.setOnClickListener(v->startGame());settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));refreshProviderBadge();
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(audio!=null)audio.stop();if(stt!=null)stt.close();if(neural!=null)neural.close();if(fallbackTts!=null)fallbackTts.shutdown();super.onDestroy();}
}
