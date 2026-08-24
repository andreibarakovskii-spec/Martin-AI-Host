package com.imagine.martinhost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public final class VisualHomeActivity extends Activity {
    private MartinSpriteView martin;
    private TextView status;
    private boolean listening=false;

    @Override public void onCreate(Bundle b){ super.onCreate(b); build(); }

    private void build(){
        getWindow().setStatusBarColor(0xFF050611); getWindow().setNavigationBarColor(0xFF050611);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xFF050611); root.setPadding(dp(16),dp(12),dp(16),dp(10));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView menu=chip("☰",0xFF14182A); top.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView brand=text("♛  MARTIN",24,Color.WHITE,true); brand.setGravity(Gravity.CENTER); top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView gear=chip("⚙",0xFF14182A); top.addView(gear,new LinearLayout.LayoutParams(dp(44),dp(44))); root.addView(top);

        FrameLayout hero=new FrameLayout(this); GradientDrawable hb=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF101329,0xFF130725,0xFF08101D}); hb.setCornerRadius(dp(28)); hero.setBackground(hb);
        martin=new MartinSpriteView(this); martin.setState(MartinSpriteView.State.IDLE); hero.addView(martin,new FrameLayout.LayoutParams(-1,-1));
        TextView bubble=text("Привет! Я Мартин 👑\nГотов сделать эту вечеринку незабываемой!",16,Color.WHITE,true); bubble.setGravity(Gravity.CENTER); GradientDrawable bb=gradient(0xFF103AD8,0xFF6A25F4,22); bubble.setBackground(bb); bubble.setPadding(dp(16),dp(10),dp(16),dp(10)); FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2);bp.gravity=Gravity.TOP;bp.setMargins(dp(24),dp(18),dp(24),0);hero.addView(bubble,bp);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,0,1);hp.setMargins(0,dp(10),0,dp(8));root.addView(hero,hp);

        status=text("Слушаю тебя…",18,0xFFEAE7FF,true); status.setGravity(Gravity.CENTER); root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
        LinearLayout quick=new LinearLayout(this); quick.setGravity(Gravity.CENTER); quick.setPadding(0,0,0,dp(8));
        quick.addView(action("🎮\nИгры",()->open(GamesActivity.class)),new LinearLayout.LayoutParams(0,dp(58),1));
        quick.addView(action("♫\nМузыка",()->open(MusicActivity.class)),new LinearLayout.LayoutParams(0,dp(58),1));
        quick.addView(action("🏆\nРейтинг",()->open(RankingActivity.class)),new LinearLayout.LayoutParams(0,dp(58),1));
        quick.addView(action("📽\nПроектор",()->open(VisualSettingsActivity.class)),new LinearLayout.LayoutParams(0,dp(58),1)); root.addView(quick);

        TextView mic=text("🎙",38,Color.WHITE,true); mic.setGravity(Gravity.CENTER); mic.setBackground(gradient(0xFF3A19C7,0xFF8B2CFF,42)); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(86),dp(86)); mp.gravity=Gravity.CENTER_HORIZONTAL; root.addView(mic,mp);
        mic.setOnClickListener(v->{listening=!listening; martin.setState(listening?MartinSpriteView.State.LISTENING:MartinSpriteView.State.HAPPY); status.setText(listening?"Слушаю внимательно…":"Мартин готов"); if(listening) martin.animate().scaleX(1.025f).scaleY(1.025f).setDuration(700).withEndAction(()->martin.animate().scaleX(1f).scaleY(1f).setDuration(700).start()).start();});

        TextView hint=text("Скажи: «Мартин, поздравь Катю» · «Запусти игру» · «Включи музыку»",12,0xFF858AA4,false); hint.setGravity(Gravity.CENTER);root.addView(hint,new LinearLayout.LayoutParams(-1,dp(38)));
        root.addView(bottomNav(0),new LinearLayout.LayoutParams(-1,dp(58))); setContentView(root);
        gear.setOnClickListener(v->open(VisualSettingsActivity.class)); menu.setOnClickListener(v->open(GamesActivity.class));
    }

    private LinearLayout bottomNav(int selected){LinearLayout n=new LinearLayout(this);n.setGravity(Gravity.CENTER);n.setBackgroundColor(0xFF090B15);String[] t={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<t.length;i++){final int x=i;TextView v=text(t[i],11,i==selected?0xFFB45CFF:0xFF8A8EA2,i==selected);v.setGravity(Gravity.CENTER);v.setOnClickListener(z->{if(x!=selected)open(c[x]);});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
    private TextView action(String s,Runnable r){TextView v=text(s,12,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(round(0xFF15182A,16));v.setOnClickListener(x->r.run());return v;}
    private TextView chip(String s,int col){TextView v=text(s,20,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(round(col,22));return v;}
    private TextView text(String s,int sp,int col,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(col);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(6),dp(4),dp(6),dp(4));return v;}
    private GradientDrawable round(int col,int r){GradientDrawable g=new GradientDrawable();g.setColor(col);g.setCornerRadius(dp(r));return g;}
    private GradientDrawable gradient(int a,int b,int r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(r));return g;}
    private void open(Class<?> c){startActivity(new Intent(this,c));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
