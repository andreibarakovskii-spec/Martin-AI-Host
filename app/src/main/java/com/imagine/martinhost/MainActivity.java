package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity implements RecognitionListener, TextToSpeech.OnInitListener {
    private static final int REQ=7;
    private SpeechRecognizer sr; private TextToSpeech tts; private GrokClient grok; private GuestStore guests;
    private MartinCharacterView martin; private TextView state,lastHeard,lastReply,providerBadge;
    private Button main,pause,stop,next; private boolean active,paused,speaking,ttsReady; private int awaitingScoreDelta=0;
    private final LinkedHashSet<String> recentGames=new LinkedHashSet<>();

    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();grok=new GrokClient(this);guests=new GuestStore(this);tts=new TextToSpeech(this,this);requestPerms();}
    @Override protected void onResume(){super.onResume();refreshProviderBadge();}

    private void requestPerms(){ArrayList<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);}
    private void setMartin(MartinCharacterView.State s,String label){martin.setState(s);state.setText(label);}

    private void startListening(){if(paused||speaking||!active)return;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}if(sr!=null)try{sr.destroy();}catch(Exception ignored){}sr=SpeechRecognizer.isOnDeviceRecognitionAvailable(this)?SpeechRecognizer.createOnDeviceSpeechRecognizer(this):SpeechRecognizer.createSpeechRecognizer(this);sr.setRecognitionListener(this);Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);sr.startListening(i);if(awaitingScoreDelta>0)setMartin(MartinCharacterView.State.LISTENING,"Назовите имя ответившего");else setMartin(MartinCharacterView.State.LISTENING,"Слушаю тебя…");}

    private MartinCharacterView.State stateForPrompt(String q){String s=q.toLowerCase(Locale.ROOT);if(s.contains("тост"))return MartinCharacterView.State.TOAST;if(s.contains("музык")||s.contains("песн")||s.contains("мелоди")||s.contains("dj")||s.contains("дидже"))return MartinCharacterView.State.DJ;if(s.contains("конкурс")||s.contains("игр")||s.contains("виктор")||s.contains("что? где? когда")||s.contains("цитат"))return MartinCharacterView.State.GAME;return MartinCharacterView.State.TALKING;}

    private void speak(String text,MartinCharacterView.State visual){if(text==null||text.isBlank()){speaking=false;startListening();return;}speaking=true;if(sr!=null)try{sr.cancel();}catch(Exception ignored){}lastReply.setText(text);setMartin(visual,"Говорю…");if(!ttsReady){lastReply.setText(text+"\n\n⚠ Голос Android ещё не готов. Открой настройки и нажми «Проверить голос». ");speaking=false;return;}int r=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"martin");if(r==TextToSpeech.ERROR){speaking=false;state.setText("Ошибка озвучки Android TTS");}}
    private void ask(String q){speaking=true;if(sr!=null)try{sr.cancel();}catch(Exception ignored){}setMartin(MartinCharacterView.State.THINKING,"Думаю…");final MartinCharacterView.State replyState=stateForPrompt(q);grok.reply(q,new GrokClient.Callback(){public void onResult(String text){runOnUiThread(()->{String marker="[[ASK_NAME_FOR_SCORE:1]]";boolean score=text.contains(marker);String clean=text.replace(marker,"").trim();if(score)awaitingScoreDelta=1;speak(clean,replyState);});}public void onError(String e){runOnUiThread(()->{speaking=false;martin.setState(MartinCharacterView.State.IDLE);state.setText("AI не ответил");lastReply.setText(e);if(active&&!paused)startListening();});}});}

    private void startNextGame(){GameCatalog.Game g=GameCatalog.random(this,recentGames);recentGames.add(g.id);while(recentGames.size()>4){Iterator<String> it=recentGames.iterator();it.next();it.remove();}ask("Следующая активность: «"+g.title+"». "+g.hostPrompt+" Учитывай профили гостей и режим проектора. Сначала только правила, пример и вопрос готовности — само первое задание не начинай до подтверждения.");}
    private void hardStop(){active=false;paused=false;speaking=false;awaitingScoreDelta=0;if(sr!=null)try{sr.cancel();}catch(Exception ignored){}if(tts!=null)tts.stop();setMartin(MartinCharacterView.State.SLEEPING,"Остановлено");main.setText("НАЧАТЬ СЛУШАТЬ");}

    @Override public void onInit(int s){if(s==TextToSpeech.SUCCESS){int lang=tts.setLanguage(new Locale("ru","RU"));ttsReady=lang!=TextToSpeech.LANG_MISSING_DATA&&lang!=TextToSpeech.LANG_NOT_SUPPORTED;tts.setSpeechRate(1.02f);tts.setPitch(.98f);tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){public void onStart(String id){}public void onDone(String id){runOnUiThread(()->{speaking=false;martin.setState(MartinCharacterView.State.HAPPY);new android.os.Handler(getMainLooper()).postDelayed(()->{if(active&&!paused)startListening();else martin.setState(MartinCharacterView.State.IDLE);},450);});}public void onError(String id){runOnUiThread(()->{speaking=false;state.setText("Ошибка TTS");if(active&&!paused)startListening();});}});}else{ttsReady=false;state.setText("Android TTS недоступен");}}

    @Override public void onResults(Bundle b){ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);String t=r==null||r.isEmpty()?"":r.get(0).trim();lastHeard.setText(t);String low=t.toLowerCase(Locale.ROOT);if(low.contains("мартин стоп")||low.equals("стоп")){hardStop();return;}if(awaitingScoreDelta>0){int delta=awaitingScoreDelta;if(guests.addScore(t,delta)){awaitingScoreDelta=0;speak("Записал. "+t+" получает "+delta+" балл!",MartinCharacterView.State.HAPPY);}else{speak("Не нашёл «"+t+"» в списке гостей. Назови имя ещё раз.",MartinCharacterView.State.LISTENING);}return;}int x=low.indexOf("мартин");if(x>=0){String q=t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+","");if(q.isBlank())q="Поздоровайся и спроси, чем помочь компании.";String ql=q.toLowerCase(Locale.ROOT);if(ql.contains("следующ")&&(ql.contains("игр")||ql.contains("конкурс"))){startNextGame();return;}ask(q);}else if(active&&!paused)startListening();}
    @Override public void onPartialResults(Bundle b){} @Override public void onReadyForSpeech(Bundle b){martin.setState(MartinCharacterView.State.LISTENING);} @Override public void onBeginningOfSpeech(){setMartin(MartinCharacterView.State.LISTENING,"Слышу тебя…");} @Override public void onRmsChanged(float v){} @Override public void onBufferReceived(byte[] b){} @Override public void onEndOfSpeech(){setMartin(MartinCharacterView.State.THINKING,"Разбираю фразу…");} @Override public void onEvent(int t,Bundle b){} @Override public void onError(int e){if(active&&!paused&&!speaking)new android.os.Handler(getMainLooper()).postDelayed(this::startListening,350);}

    private void refreshProviderBadge(){if(providerBadge==null)return;var p=getSharedPreferences("martin",0);String provider=p.getString("ai_provider","auto"),key=p.getString("ai_key",p.getString("xai_key",""));if("auto".equals(provider))provider=key.startsWith("gsk_")?"groq":"xai";providerBadge.setText("groq".equals(provider)?"GROQ • GSK":"xAI • GROK");}
    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;}
    private GradientDrawable bg(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp((int)radius));return g;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setBackground(bg(color,18));b.setPadding(dp(10),0,dp(10),0);return b;}

    private void buildUi(){getWindow().setStatusBarColor(0xFF07080D);getWindow().setNavigationBarColor(0xFF07080D);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(16),dp(18),dp(30));root.setBackgroundColor(0xFF07080D);scroll.addView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView brand=tv("MARTIN",30,Color.WHITE);brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));providerBadge=tv("AI",11,0xFFB39BFF);providerBadge.setGravity(Gravity.CENTER);providerBadge.setBackground(bg(0xFF21163D,16));top.addView(providerBadge,new LinearLayout.LayoutParams(dp(96),dp(38)));root.addView(top);TextView sub=tv("AI-ведущий • Катя 35",14,0xFF8F96A8);root.addView(sub);
        martin=new MartinCharacterView(this);GradientDrawable heroBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF12101D,0xFF090A10,0xFF180C2B});heroBg.setCornerRadius(dp(28));martin.setBackground(heroBg);LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,dp(390));heroLp.setMargins(0,dp(10),0,dp(12));root.addView(martin,heroLp);
        state=tv("Готов к вечеринке",20,0xFFE8E4F4);state.setGravity(Gravity.CENTER);state.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(state);
        main=button("НАЧАТЬ СЛУШАТЬ",0xFF6D35F2);root.addView(main,new LinearLayout.LayoutParams(-1,dp(60)));
        LinearLayout row=new LinearLayout(this);row.setPadding(0,dp(10),0,0);pause=button("ПАУЗА",0xFF181B24);stop=button("СТОП",0xFF2A171E);row.addView(pause,new LinearLayout.LayoutParams(0,dp(52),1));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(52),1);slp.setMargins(dp(10),0,0,0);row.addView(stop,slp);root.addView(row);
        next=button("✨ СЛЕДУЮЩАЯ ИГРА",0xFF231745);LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(-1,dp(56));nlp.setMargins(0,dp(10),0,0);root.addView(next,nlp);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(bg(0xFF11131A,18));LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2);clp.setMargins(0,dp(14),0,0);root.addView(card,clp);card.addView(tv("УСЛЫШАЛ",11,0xFF777F91));lastHeard=tv("Скажи: «Мартин, давай игру»",16,Color.WHITE);card.addView(lastHeard);card.addView(tv("МАРТИН",11,0xFF9A6BFF));lastReply=tv("Здесь появится мой ответ",17,0xFFE4E5EA);card.addView(lastReply);
        Button settings=button("⚙  НАСТРОЙКИ • ГОСТИ • ПРОЕКТОР",0xFF171922);LinearLayout.LayoutParams setLp=new LinearLayout.LayoutParams(-1,dp(54));setLp.setMargins(0,dp(12),0,0);root.addView(settings,setLp);Button testVoice=button("🔊  ПРОВЕРИТЬ ГОЛОС",0xFF171922);LinearLayout.LayoutParams tvlp=new LinearLayout.LayoutParams(-1,dp(50));tvlp.setMargins(0,dp(8),0,0);root.addView(testVoice,tvlp);setContentView(scroll);
        main.setOnClickListener(v->{active=!active;if(active){paused=false;main.setText("●  СЛУШАЮ");startListening();}else hardStop();});pause.setOnClickListener(v->{paused=!paused;if(paused){if(sr!=null)sr.cancel();if(tts!=null)tts.stop();setMartin(MartinCharacterView.State.SLEEPING,"Пауза");}else{setMartin(MartinCharacterView.State.IDLE,"Возобновляю…");startListening();}});stop.setOnClickListener(v->hardStop());next.setOnClickListener(v->startNextGame());settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));testVoice.setOnClickListener(v->speak("Привет! Я Мартин. Голос работает, и я готов вести вечеринку.",MartinCharacterView.State.HAPPY));refreshProviderBadge();}

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(sr!=null)sr.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}
