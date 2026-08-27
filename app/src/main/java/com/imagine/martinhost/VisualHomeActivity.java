package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

public final class VisualHomeActivity extends Activity {
    private static final int REQ_AUDIO=41;
    private AvatarHostView martin;
    private TextView status,backend,bubble,mic;
    private SpeechLoop speech;
    private OfflineSpeaker speaker;
    private GrokClient grok;
    private GuestStore guests;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean active=false;
    private boolean busy=false;
    private int awaitingScore=0;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        grok=new GrokClient(this);
        guests=new GuestStore(this);
        speaker=new OfflineSpeaker(this,new OfflineSpeaker.Listener(){
            @Override public void onReady(){ runOnUiThread(()->status.setText("Голос готов")); }
            @Override public void onDone(){ runOnUiThread(()->{
                busy=false;
                martin.setLipSync(0f);
                martin.setState(AvatarState.HAPPY);
                status.setText(active?"Снова слушаю…":"Мартин готов");
                if(active) handler.postDelayed(VisualHomeActivity.this::startSpeech,420);
            }); }
            @Override public void onError(String message){ runOnUiThread(()->{
                busy=false;
                martin.setLipSync(0f);
                status.setText(message);
                if(active) handler.postDelayed(VisualHomeActivity.this::startSpeech,450);
            }); }
        });
        build();
    }

    private void build(){
        getWindow().setStatusBarColor(0xFF060711);getWindow().setNavigationBarColor(0xFF060711);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF060711);root.setPadding(dp(14),dp(8),dp(14),dp(8));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(0,0,0,dp(6));
        TextView menu=icon("☰");top.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(46)));
        TextView brand=text("♛  M A R T I N",22,Color.WHITE,true);brand.setGravity(Gravity.CENTER);top.addView(brand,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView gear=icon("⚙");top.addView(gear,new LinearLayout.LayoutParams(dp(46),dp(46)));root.addView(top);

        FrameLayout hero=new FrameLayout(this);hero.setBackground(gradient(new int[]{0xFF0B0D19,0xFF170A30,0xFF071426},30));
        martin=new AvatarHostView(this);martin.setState(AvatarState.IDLE);hero.addView(martin,new FrameLayout.LayoutParams(-1,-1));
        bubble=text("Привет! Я Мартин 👑\nНажми микрофон и скажи: «Мартин, давай игру»",15,Color.WHITE,true);bubble.setGravity(Gravity.CENTER);bubble.setPadding(dp(14),dp(9),dp(14),dp(9));bubble.setBackground(gradientStroke(new int[]{0xEE123BE3,0xEE7C25F5},22,0x66FFFFFF));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2);bp.gravity=Gravity.TOP;bp.setMargins(dp(22),dp(16),dp(22),0);hero.addView(bubble,bp);
        backend=text("Avatar: "+martin.backendName(),10,0xFF9EA5BB,false);backend.setGravity(Gravity.RIGHT);FrameLayout.LayoutParams blp=new FrameLayout.LayoutParams(-1,-2);blp.gravity=Gravity.BOTTOM;blp.setMargins(dp(12),0,dp(12),dp(8));hero.addView(backend,blp);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,0,1);hp.setMargins(0,dp(3),0,dp(8));root.addView(hero,hp);

        status=text("Мартин готов",17,0xFFEFEAFF,true);status.setGravity(Gravity.CENTER);root.addView(status,new LinearLayout.LayoutParams(-1,dp(32)));
        LinearLayout quick=new LinearLayout(this);quick.setGravity(Gravity.CENTER);String[] qs={"🎮\nИгры","♫\nМузыка","🏆\nРейтинг","▣\nПроектор"};Class<?>[] qc={GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<qs.length;i++){final int x=i;TextView q=action(qs[i]);q.setOnClickListener(v->open(qc[x]));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(62),1);if(i>0)lp.setMargins(dp(7),0,0,0);quick.addView(q,lp);}root.addView(quick);

        FrameLayout micWrap=new FrameLayout(this);mic=text("🎙",36,Color.WHITE,true);mic.setGravity(Gravity.CENTER);mic.setBackground(gradientStroke(new int[]{0xFF173AFF,0xFF922CFF},50,0x99C995FF));FrameLayout.LayoutParams mlp=new FrameLayout.LayoutParams(dp(88),dp(88));mlp.gravity=Gravity.CENTER;micWrap.addView(mic,mlp);root.addView(micWrap,new LinearLayout.LayoutParams(-1,dp(100)));
        TextView hint=text("Микрофон телефона • ответ через динамик/Bluetooth • «Мартин, стоп»",11,0xFF8D91A6,false);hint.setGravity(Gravity.CENTER);root.addView(hint,new LinearLayout.LayoutParams(-1,dp(38)));
        root.addView(nav(0),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);

        mic.setOnClickListener(v->{ if(active) stopParty(); else startParty(); });
        gear.setOnClickListener(v->open(VisualSettingsActivity.class));menu.setOnClickListener(v->open(GamesActivity.class));
    }

    private void startParty(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}
        active=true; busy=false; mic.setText("■"); mic.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180).start(); startSpeech();
    }

    private void startSpeech(){
        if(!active||busy)return;
        if(speech!=null){speech.stop();speech=null;}
        speech=new SpeechLoop(this,new SpeechLoop.Listener(){
            @Override public void onFinalText(String text){runOnUiThread(()->handleHeard(text));}
            @Override public void onStatus(String s){runOnUiThread(()->{if(active&&!busy){status.setText(s);martin.setState(AvatarState.LISTENING);}});}
            @Override public void onError(String e){runOnUiThread(()->{status.setText(e);martin.setState(AvatarState.IDLE);});}
        });
        speech.start();
    }

    private void handleHeard(String raw){
        if(raw==null)return;
        String t=raw.trim(); if(t.isBlank())return;
        String low=t.toLowerCase(Locale.ROOT);
        bubble.setText("Вы: "+t);
        if(low.equals("стоп")||low.contains("мартин стоп")){stopParty();return;}
        if(awaitingScore>0){
            stopSpeechOnly();
            if(guests.addScore(t,awaitingScore)){int pts=awaitingScore;awaitingScore=0;say("Записал! "+t+" получает "+pts+" балл.",AvatarState.HAPPY);}
            else say("Не нашёл «"+t+"» в гостях. Назови имя ещё раз.",AvatarState.LISTENING);
            return;
        }
        int p=low.indexOf("мартин");
        if(p<0){startSpeech();return;}
        String q=t.substring(Math.min(t.length(),p+6)).replaceFirst("^[,.:;!?\\s-]+","").trim();
        if(q.isBlank())q="Поздоровайся с компанией и коротко спроси, чем помочь.";
        ask(q);
    }

    private void ask(String q){
        stopSpeechOnly();busy=true;martin.setState(AvatarState.THINKING);status.setText("Думаю…");bubble.setText("Мартин думает…");
        final AvatarState answerState=stateFor(q);
        grok.reply(q,new GrokClient.Callback(){
            @Override public void onResult(String text){runOnUiThread(()->{
                String marker="[[ASK_NAME_FOR_SCORE:1]]";if(text.contains(marker))awaitingScore=1;
                say(text.replace(marker,"").trim(),answerState);
            });}
            @Override public void onError(String error){runOnUiThread(()->{
                busy=false;
                String fallback="Связь с AI сейчас капризничает. Я на месте — можем запустить игру, тост или попробовать ещё раз.";
                bubble.setText("⚠ "+error);
                say(fallback,AvatarState.HAPPY);
            });}
        });
    }

    private void say(String text,AvatarState state){
        stopSpeechOnly();busy=true;martin.setState(state);martin.setLipSync(.72f);status.setText("Мартин говорит…");bubble.setText(text);speaker.speak(text);
    }

    private AvatarState stateFor(String q){String s=q.toLowerCase(Locale.ROOT);if(s.contains("тост"))return AvatarState.TOAST;if(s.contains("музык")||s.contains("песн")||s.contains("мелоди")||s.contains("дидже"))return AvatarState.DJ;if(s.contains("игр")||s.contains("конкурс")||s.contains("виктор")||s.contains("цитат")||s.contains("что где когда"))return AvatarState.GAME;return AvatarState.TALKING;}

    private void stopSpeechOnly(){if(speech!=null){speech.stop();speech=null;}}
    private void stopParty(){active=false;busy=false;awaitingScore=0;stopSpeechOnly();if(speaker!=null)speaker.stop();handler.removeCallbacksAndMessages(null);martin.setLipSync(0f);martin.setState(AvatarState.SLEEPING);status.setText("Пауза");bubble.setText("Мартин отдыхает. Нажми микрофон, когда продолжим.");mic.setText("🎙");mic.animate().scaleX(1f).scaleY(1f).setDuration(180).start();}

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_AUDIO&&grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)startParty();else status.setText("Нужен доступ к микрофону");}
    @Override protected void onDestroy(){stopSpeechOnly();if(speaker!=null)speaker.close();handler.removeCallbacksAndMessages(null);super.onDestroy();}

    private TextView icon(String s){TextView v=text(s,22,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(gradientStroke(new int[]{0xD9181B2D,0xD90D1020},24,0x334F5AFF));return v;}
    private TextView action(String s){TextView v=text(s,12,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(gradientStroke(new int[]{0xD91A1E34,0xD90B0E1C},18,0x335D64A0));return v;}
    private LinearLayout nav(int selected){LinearLayout n=new LinearLayout(this);n.setPadding(dp(3),dp(3),dp(3),dp(3));n.setBackground(gradientStroke(new int[]{0xF20A0C17,0xF20A0B13},19,0x223E4568));String[] t={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<t.length;i++){final int x=i;TextView v=text(t[i],10,i==selected?0xFFB65CFF:0xFF8A8EA2,i==selected);v.setGravity(Gravity.CENTER);if(i==selected)v.setBackground(round(0x252E1A57,15));v.setOnClickListener(z->{if(x!=selected)open(c[x]);});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
    private TextView text(String s,int sp,int col,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(col);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}
    private GradientDrawable round(int col,int r){GradientDrawable g=new GradientDrawable();g.setColor(col);g.setCornerRadius(dp(r));return g;}
    private GradientDrawable gradient(int[] c,int r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,c);g.setCornerRadius(dp(r));return g;}
    private GradientDrawable gradientStroke(int[] c,int r,int stroke){GradientDrawable g=gradient(c,r);g.setStroke(dp(1),stroke);return g;}
    private void open(Class<?> c){startActivity(new Intent(this,c));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
