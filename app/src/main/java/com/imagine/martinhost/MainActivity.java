package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

/** Party-first Martin home: hearing -> Whisper -> Groq -> neural voice -> animated avatar. */
public final class MainActivity extends Activity {
    private static final int REQ=7;
    private MartinNeuralSpeaker neural; private GrokClient grok; private GroqTranscriber stt; private GuestStore guests;
    private TurnManager turns; private ContinuousSpeechEngine audio; private MartinSpriteView martin;
    private TextView state,heard,reply,aiDot,voiceDot; private Button mic; private boolean active,paused,neuralReady; private int awaitingScoreDelta=0;

    @Override public void onCreate(Bundle b){super.onCreate(b);buildUi();PartyAudioRouter.prepare(this);grok=new GrokClient(this);stt=new GroqTranscriber(this);guests=new GuestStore(this);
        turns=new TurnManager(s->runOnUiThread(()->onTurnState(s)));
        audio=new ContinuousSpeechEngine(this,turns,new ContinuousSpeechEngine.Listener(){
            public void onSpeechChunk(byte[] wav){turns.onUserFinal();stt.transcribe(wav,new GroqTranscriber.Callback(){public void onText(String text){runOnUiThread(()->handleTranscript(text));}public void onError(String e){runOnUiThread(()->{turns.forceListen();state.setText("Не расслышал. Попробуй ещё раз");});}});}
            public void onLevel(float rms,float noise,boolean speech){runOnUiThread(()->{if(turns.acceptMicForStt())martin.setSpeechLevel(Math.max(0f,Math.min(1f,(rms+48f)/36f)));});}
            public void onStatus(String s){} public void onError(String e){runOnUiThread(()->state.setText("Проверь доступ к микрофону"));}
        });
        neural=new MartinNeuralSpeaker(this,new MartinSpeaker.Listener(){
            public void onPreparing(String m){runOnUiThread(()->{voiceDot.setText("● голос");voiceDot.setTextColor(0xFFFFA84D);});}
            public void onReady(){runOnUiThread(()->{neuralReady=true;voiceDot.setText("● голос");voiceDot.setTextColor(0xFF56E39F);});}
            public void onStart(){runOnUiThread(()->{martin.setState(MartinSpriteView.State.TALKING);state.setText("Говорю…");});}
            public void onSpectrum(float[] bands){}
            public void onLevel(float level){runOnUiThread(()->martin.setSpeechLevel(level));}
            public void onDone(){runOnUiThread(()->finishSpeech());}
            public void onError(String message){runOnUiThread(()->{neuralReady=false;voiceDot.setText("● голос");voiceDot.setTextColor(0xFFFF6B6B);state.setText("Установи модель голоса в настройках");turns.onAiSpeechDone();});}
        });
        if(getSharedPreferences("martin",0).getBoolean("voice_model_ready",false))neural.prepare();
        requestPerms();refreshStatus();
    }

    private String cleanSpeech(String raw){if(raw==null)return "";String s=raw.replaceAll("\\[\\[[^\\]]*\\]\\]","");s=s.replaceAll("[*#_`~>]","");s=s.replaceAll("\\[(.*?)\\]\\((.*?)\\)","$1");s=s.replaceAll("\\s+"," ").trim();return s;}
    private void requestPerms(){ArrayList<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&active&&!paused)startAudio();}
    @Override protected void onResume(){super.onResume();refreshStatus();if(!neuralReady&&getSharedPreferences("martin",0).getBoolean("voice_model_ready",false))neural.prepare();}

    private void refreshStatus(){String key=getSharedPreferences("martin",0).getString("ai_key","");long ok=getSharedPreferences("martin",0).getLong("groq_last_ok",0);boolean g=key.startsWith("gsk_")&&ok>0;aiDot.setText("● AI");aiDot.setTextColor(g?0xFF56E39F:0xFFFFA84D);boolean vr=getSharedPreferences("martin",0).getBoolean("voice_model_ready",false);voiceDot.setText("● голос");voiceDot.setTextColor(vr?0xFF56E39F:0xFFFFA84D);}
    private void onTurnState(TurnManager.State s){if(!active||paused)return;switch(s){case LISTENING:martin.setState(MartinSpriteView.State.LISTENING);state.setText(awaitingScoreDelta>0?"Кто ответил?":"Слушаю…");break;case THINKING:martin.setState(MartinSpriteView.State.THINKING);state.setText("Думаю…");break;case SPEAKING:martin.setState(MartinSpriteView.State.TALKING);state.setText("Говорю…");break;case COOLDOWN:state.setText("Слушаю…");break;}}
    private void startAudio(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}active=true;paused=false;turns.forceListen();audio.start();martin.setState(MartinSpriteView.State.LISTENING);state.setText("Слушаю…");mic.setText("●  СЛУШАЮ");}
    private void stopAll(){active=false;paused=false;audio.stop();if(neural!=null)neural.stop();turns.forceListen();martin.setState(MartinSpriteView.State.IDLE);state.setText("Готов к вечеринке");mic.setText("🎙  НАЧАТЬ");}

    private MartinSpriteView.State stateForPrompt(String q){String s=q.toLowerCase(Locale.ROOT);if(s.contains("тост"))return MartinSpriteView.State.TOAST;if(s.contains("музык")||s.contains("dj"))return MartinSpriteView.State.DJ;if(s.contains("игр")||s.contains("вопрос")||s.contains("правил"))return MartinSpriteView.State.GAME;return MartinSpriteView.State.TALKING;}
    private String emotionFor(MartinSpriteView.State v){if(v==MartinSpriteView.State.TOAST)return "warm";if(v==MartinSpriteView.State.GAME)return "playful";if(v==MartinSpriteView.State.DJ)return "excited";if(v==MartinSpriteView.State.HAPPY)return "celebrate";return "neutral";}
    private float energyFor(MartinSpriteView.State v){if(v==MartinSpriteView.State.DJ)return .95f;if(v==MartinSpriteView.State.GAME)return .72f;if(v==MartinSpriteView.State.HAPPY)return .9f;if(v==MartinSpriteView.State.TOAST)return .5f;return .58f;}
    private void speak(String raw,MartinSpriteView.State visual){String text=cleanSpeech(raw);if(text.isBlank()){turns.forceListen();return;}reply.setText(text);turns.onAiWillSpeak();martin.setState(visual);if(neuralReady&&neural.isReady()){neural.speak(text,emotionFor(visual),energyFor(visual));}else{state.setText("Установи живой голос в настройках");turns.onAiSpeechDone();}}
    private void finishSpeech(){martin.setSpeechLevel(0);martin.setState(MartinSpriteView.State.HAPPY);turns.onAiSpeechDone();}
    private void ask(String q){turns.onUserFinal();martin.setState(MartinSpriteView.State.THINKING);state.setText("Думаю…");MartinSpriteView.State v=stateForPrompt(q);grok.reply(q,new GrokClient.Callback(){public void onResult(String text){runOnUiThread(()->{String marker="[[ASK_NAME_FOR_SCORE:1]]";if(text.contains(marker))awaitingScoreDelta=1;speak(text.replace(marker,""),v);});}public void onError(String e){runOnUiThread(()->{state.setText("Проверь Groq в настройках");turns.forceListen();});}});}
    private void startGame(){ask("Ты Мартин, живой весёлый ведущий. Начни игру «Что? Где? Когда?»: очень коротко объясни правила, дай один пример и спроси, готовы ли гости. Не используй markdown, звёздочки, решётки или служебные символы.");}
    private void handleTranscript(String t){if(t==null||t.isBlank()){turns.forceListen();return;}heard.setText("Вы: "+t);String low=t.toLowerCase(Locale.ROOT);if(low.contains("мартин стоп")||low.equals("стоп")){stopAll();return;}if(awaitingScoreDelta>0){if(guests.addScore(t,awaitingScoreDelta)){awaitingScoreDelta=0;speak("Записал. "+t+" получает балл!",MartinSpriteView.State.HAPPY);}else speak("Не нашёл это имя в гостях. Назови имя ещё раз.",MartinSpriteView.State.LISTENING);return;}int x=low.indexOf("мартин");if(x>=0){String q=t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+","");if(q.isBlank())q="Поздоровайся с компанией естественно и коротко.";if(q.toLowerCase(Locale.ROOT).contains("игр")||q.toLowerCase(Locale.ROOT).contains("что где когда")){startGame();return;}ask(q);}else turns.forceListen();}

    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(dp(8),dp(7),dp(8),dp(7));return v;}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setBackground(bg(0xFF181522,18));return b;}
    private void buildUi(){getWindow().setStatusBarColor(0xFF07080D);getWindow().setNavigationBarColor(0xFF07080D);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(10),dp(14),dp(12));root.setBackgroundColor(0xFF07080D);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);Button menu=btn("☰");top.addView(menu,new LinearLayout.LayoutParams(dp(52),dp(48)));TextView title=tv("MARTIN",27,Color.WHITE);title.setGravity(Gravity.CENTER);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));Button gear=btn("⚙");top.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(48)));root.addView(top);
        LinearLayout dots=new LinearLayout(this);dots.setGravity(Gravity.CENTER);aiDot=tv("● AI",11,0xFFFFA84D);voiceDot=tv("● голос",11,0xFFFFA84D);dots.addView(aiDot);dots.addView(voiceDot);root.addView(dots);
        martin=new MartinSpriteView(this);LinearLayout.LayoutParams hero=new LinearLayout.LayoutParams(-1,0,1);hero.setMargins(0,dp(4),0,0);root.addView(martin,hero);
        state=tv("Готов к вечеринке",19,Color.WHITE);state.setGravity(Gravity.CENTER);state.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);root.addView(state);
        heard=tv("Скажи: «Мартин, привет»",12,0xFF9690A4);heard.setGravity(Gravity.CENTER);root.addView(heard);reply=tv("",12,0xFFB8A8D9);reply.setGravity(Gravity.CENTER);reply.setMaxLines(2);root.addView(reply);
        mic=btn("🎙  НАЧАТЬ");mic.setTextSize(17);mic.setBackground(bg(0xFF6930F5,24));root.addView(mic,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);String[] names={"⌂\nГлавная","🎮\nИгры","♫\nМузыка","☵\nЧат","⚙\nНастройки"};for(String n:names){Button b=btn(n);b.setTextSize(11);nav.addView(b,new LinearLayout.LayoutParams(0,dp(64),1));if(n.contains("Игры"))b.setOnClickListener(v->startGame());if(n.contains("Настройки"))b.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));}root.addView(nav);setContentView(root);
        mic.setOnClickListener(v->{if(active)stopAll();else startAudio();});gear.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));menu.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){if(audio!=null)audio.stop();if(stt!=null)stt.close();if(neural!=null)neural.close();super.onDestroy();}
}
