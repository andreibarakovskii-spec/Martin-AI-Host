package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;

/** Premium party-first launcher matching the approved Martin AI Host concept. */
public final class PremiumMainActivity extends Activity {
    private static final int REQ=17;
    private MartinNeuralSpeaker neural;
    private GrokClient grok;
    private GroqTranscriber stt;
    private TurnManager turns;
    private ContinuousSpeechEngine audio;
    private MartinSpriteView martin;
    private TextView state, heard, reply, aiDot, voiceDot, subline;
    private Button mic;
    private boolean active, neuralReady;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF05060B);
        getWindow().setNavigationBarColor(0xFF05060B);
        buildUi();
        PartyAudioRouter.prepare(this);
        grok=new GrokClient(this);
        stt=new GroqTranscriber(this);
        turns=new TurnManager(s->runOnUiThread(()->onTurnState(s)));
        audio=new ContinuousSpeechEngine(this,turns,new ContinuousSpeechEngine.Listener(){
            public void onSpeechChunk(byte[] wav){
                turns.onUserFinal();
                stt.transcribe(wav,new GroqTranscriber.Callback(){
                    public void onText(String text){runOnUiThread(()->handleTranscript(text));}
                    public void onError(String e){runOnUiThread(()->{state.setText("Не расслышал");subline.setText("Попробуй сказать ещё раз");turns.forceListen();});}
                });
            }
            public void onLevel(float rms,float noise,boolean speech){
                runOnUiThread(()->{if(turns.acceptMicForStt())martin.setSpeechLevel(Math.max(0f,Math.min(1f,(rms+48f)/36f)));});
            }
            public void onStatus(String s){}
            public void onError(String e){runOnUiThread(()->subline.setText("Нужен доступ к микрофону"));}
        });
        neural=new MartinNeuralSpeaker(this,new MartinNeuralSpeaker.Listener(){
            public void onPreparing(String m){runOnUiThread(()->setVoiceDot(false));}
            public void onReady(){runOnUiThread(()->{neuralReady=true;setVoiceDot(true);});}
            public void onStart(){runOnUiThread(()->{martin.setState(MartinSpriteView.State.TALKING);state.setText("Говорю…");});}
            public void onLevel(float level){runOnUiThread(()->martin.setSpeechLevel(level));}
            public void onDone(){runOnUiThread(()->{martin.setSpeechLevel(0);martin.setState(MartinSpriteView.State.HAPPY);turns.onAiSpeechDone();});}
            public void onError(String message){runOnUiThread(()->{neuralReady=false;setVoiceDot(false);state.setText("Голос не установлен");subline.setText("Скачай модель в настройках");turns.onAiSpeechDone();});}
        });
        if(getSharedPreferences("martin",0).getBoolean("voice_model_ready",false))neural.prepare();
        requestPerms();
        refreshStatus();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(8),dp(18),dp(12));
        root.setBackground(gradient(0xFF05060B,0xFF090612,0xFF05060B,0));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button menu=iconButton("☰");
        top.addView(menu,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout titleBox=new LinearLayout(this);titleBox.setOrientation(LinearLayout.VERTICAL);titleBox.setGravity(Gravity.CENTER);
        TextView title=text("MARTIN",29,Color.WHITE,Typeface.BOLD);title.setLetterSpacing(.06f);title.setGravity(Gravity.CENTER);
        TextView tag=text("AI HOST",10,0xFF9B5CFF,Typeface.BOLD);tag.setLetterSpacing(.18f);tag.setGravity(Gravity.CENTER);
        titleBox.addView(title);titleBox.addView(tag);
        top.addView(titleBox,new LinearLayout.LayoutParams(0,dp(58),1));
        Button gear=iconButton("⚙");
        top.addView(gear,new LinearLayout.LayoutParams(dp(54),dp(54)));
        root.addView(top);

        LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER);statusRow.setPadding(0,dp(5),0,dp(6));
        aiDot=pill("●  AI",0xFF123E31,0xFF58E6A9);voiceDot=pill("●  ГОЛОС",0xFF3A2A15,0xFFFFB35C);
        statusRow.addView(aiDot,new LinearLayout.LayoutParams(dp(86),dp(30)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(10),1);statusRow.addView(new Space(this),sp);statusRow.addView(voiceDot,new LinearLayout.LayoutParams(dp(108),dp(30)));
        root.addView(statusRow);

        LinearLayout heroCard=new LinearLayout(this);heroCard.setOrientation(LinearLayout.VERTICAL);heroCard.setGravity(Gravity.CENTER_HORIZONTAL);heroCard.setPadding(dp(8),dp(4),dp(8),dp(8));heroCard.setBackground(gradient(0xFF0A0B12,0xFF151027,0xFF080910,28));
        martin=new MartinSpriteView(this);
        LinearLayout.LayoutParams hero=new LinearLayout.LayoutParams(-1,0,1);hero.setMargins(0,0,0,0);heroCard.addView(martin,hero);
        root.addView(heroCard,new LinearLayout.LayoutParams(-1,0,1));

        state=text("Готов к вечеринке",21,Color.WHITE,Typeface.BOLD);state.setGravity(Gravity.CENTER);state.setPadding(0,dp(9),0,0);root.addView(state);
        subline=text("Скажи: «Мартин, привет»",12,0xFF8D8997,Typeface.NORMAL);subline.setGravity(Gravity.CENTER);root.addView(subline);
        heard=text("",11,0xFF6F6A7A,Typeface.NORMAL);heard.setGravity(Gravity.CENTER);heard.setMaxLines(1);root.addView(heard);
        reply=text("",12,0xFFCDBAF7,Typeface.NORMAL);reply.setGravity(Gravity.CENTER);reply.setMaxLines(2);reply.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(reply);

        mic=new Button(this);mic.setText("🎙  НАЧАТЬ");mic.setTextColor(Color.WHITE);mic.setTextSize(17);mic.setTypeface(Typeface.DEFAULT_BOLD);mic.setAllCaps(false);mic.setBackground(gradient(0xFF6A28FF,0xFF8A3DFF,0xFF5622DD,24));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(62));mp.setMargins(0,dp(7),0,dp(8));root.addView(mic,mp);

        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(dp(2),dp(3),dp(2),dp(3));nav.setBackground(round(0xFF11121A,22));
        addNav(nav,"⌂","Главная",true,null);
        addNav(nav,"🎮","Игры",false,v->startActivity(new Intent(this,GamesActivity.class)));
        addNav(nav,"♫","Музыка",false,v->startActivity(new Intent(this,MusicActivity.class)));
        addNav(nav,"☵","Чат",false,v->{});
        addNav(nav,"⚙","Настройки",false,v->startActivity(new Intent(this,SettingsActivity.class)));
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(72)));

        setContentView(root);
        menu.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        gear.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        mic.setOnClickListener(v->{if(active)stopAudio();else startAudio();});
    }

    private void addNav(LinearLayout nav,String icon,String label,boolean selected,View.OnClickListener click){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(0,dp(4),0,dp(3));if(selected)box.setBackground(round(0xFF20163A,18));
        TextView i=text(icon,18,selected?0xFF9D63FF:0xFFE5E2EC,Typeface.NORMAL);i.setGravity(Gravity.CENTER);TextView l=text(label,10,selected?0xFFB987FF:0xFFE5E2EC,selected?Typeface.BOLD:Typeface.NORMAL);l.setGravity(Gravity.CENTER);box.addView(i);box.addView(l);if(click!=null)box.setOnClickListener(click);nav.addView(box,new LinearLayout.LayoutParams(0,-1,1));
    }

    private void onTurnState(TurnManager.State s){
        if(!active)return;
        switch(s){
            case LISTENING: martin.setState(MartinSpriteView.State.LISTENING);state.setText("Слушаю…");subline.setText("Я слышу компанию");break;
            case THINKING: martin.setState(MartinSpriteView.State.THINKING);state.setText("Думаю…");subline.setText("Секунду, формулирую ответ");break;
            case SPEAKING: martin.setState(MartinSpriteView.State.TALKING);state.setText("Говорю…");subline.setText("Мартин отвечает");break;
            case COOLDOWN: state.setText("Слушаю…");break;
        }
    }

    private void startAudio(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPerms();return;}
        active=true;turns.forceListen();audio.start();martin.setState(MartinSpriteView.State.LISTENING);state.setText("Слушаю…");subline.setText("Скажи: «Мартин…»");mic.setText("●  СЛУШАЮ");
    }
    private void stopAudio(){active=false;audio.stop();if(neural!=null)neural.stop();turns.forceListen();martin.setState(MartinSpriteView.State.IDLE);state.setText("Готов к вечеринке");subline.setText("Скажи: «Мартин, привет»");mic.setText("🎙  НАЧАТЬ");}

    private void handleTranscript(String t){
        if(t==null||t.isBlank()){turns.forceListen();return;}
        heard.setText("Вы: "+t);
        String low=t.toLowerCase(Locale.ROOT);
        if(low.contains("мартин стоп")||low.equals("стоп")){stopAudio();return;}
        int x=low.indexOf("мартин");
        if(x<0){turns.forceListen();return;}
        String q=t.substring(Math.min(t.length(),x+6)).replaceFirst("^[,.:;!?\\s-]+","");
        if(q.isBlank())q="Поздоровайся с компанией естественно и коротко.";
        ask(q);
    }

    private void ask(String q){
        turns.onUserFinal();martin.setState(MartinSpriteView.State.THINKING);state.setText("Думаю…");subline.setText("Секунду, формулирую ответ");
        grok.reply(q,new GrokClient.Callback(){
            public void onResult(String raw){runOnUiThread(()->speak(cleanSpeech(raw)));}
            public void onError(String e){runOnUiThread(()->{state.setText("Нет связи с AI");subline.setText("Проверь Groq / GSK в настройках");turns.forceListen();});}
        });
    }

    private void speak(String text){
        if(text.isBlank()){turns.forceListen();return;}
        reply.setText(text);turns.onAiWillSpeak();martin.setState(MartinSpriteView.State.TALKING);state.setText("Говорю…");subline.setText("Мартин отвечает");
        if(neuralReady&&neural.isReady())neural.speak(text,"playful",.65f);
        else{state.setText("Голос не установлен");subline.setText("Скачай модель в настройках");turns.onAiSpeechDone();}
    }

    private String cleanSpeech(String raw){if(raw==null)return "";String s=raw.replaceAll("\\[\\[[^\\]]*\\]\\]","");s=s.replaceAll("[*#_`~>]","");s=s.replaceAll("\\[(.*?)\\]\\((.*?)\\)","$1");return s.replaceAll("\\s+"," ").trim();}
    private void refreshStatus(){String key=getSharedPreferences("martin",0).getString("ai_key","");long ok=getSharedPreferences("martin",0).getLong("groq_last_ok",0);boolean ai=key.startsWith("gsk_")&&ok>0;aiDot.setText(ai?"●  AI":"●  AI?");aiDot.setTextColor(ai?0xFF58E6A9:0xFFFFB35C);boolean v=getSharedPreferences("martin",0).getBoolean("voice_model_ready",false);setVoiceDot(v);}
    private void setVoiceDot(boolean ok){voiceDot.setText(ok?"●  ГОЛОС":"●  ГОЛОС?");voiceDot.setTextColor(ok?0xFF58E6A9:0xFFFFB35C);}
    private void requestPerms(){ArrayList<String> p=new ArrayList<>();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.RECORD_AUDIO);if(android.os.Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)p.add(Manifest.permission.BLUETOOTH_CONNECT);if(!p.isEmpty())requestPermissions(p.toArray(new String[0]),REQ);}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ&&active)startAudio();}
    @Override protected void onResume(){super.onResume();refreshStatus();if(neural!=null&&!neuralReady&&getSharedPreferences("martin",0).getBoolean("voice_model_ready",false))neural.prepare();}
    @Override protected void onDestroy(){if(audio!=null)audio.stop();if(stt!=null)stt.close();if(neural!=null)neural.close();super.onDestroy();}

    private TextView text(String s,int sp,int color,int style){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTypeface(Typeface.create(Typeface.DEFAULT,style));return v;}
    private TextView pill(String s,int bg,int fg){TextView v=text(s,10,fg,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(bg,16));return v;}
    private Button iconButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(20);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(0xFF15111F,19));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable gradient(int a,int b,int c,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{a,b,c});g.setCornerRadius(dp(radius));return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
