package com.imagine.martinhost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class MusicActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);build();}
 private void build(){getWindow().setStatusBarColor(0xFF050611);getWindow().setNavigationBarColor(0xFF050611);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF050611);root.setPadding(dp(16),dp(12),dp(16),dp(8));TextView title=t("DJ Мартин 👑",24,Color.WHITE,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));
  FrameLayout hero=new FrameLayout(this);hero.setBackground(grad(0xFF110D25,0xFF071528,24));MartinSpriteView m=new MartinSpriteView(this);m.setState(MartinSpriteView.State.DJ);hero.addView(m,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,0,1);root.addView(hero,hp);
  LinearLayout now=new LinearLayout(this);now.setOrientation(LinearLayout.VERTICAL);now.setPadding(dp(14),dp(10),dp(14),dp(10));now.setBackground(round(0xFF111522,18));now.addView(t("СЕЙЧАС ИГРАЕТ",11,0xFF8F95A8,false));now.addView(t("Руки Вверх! — Крошка моя",17,Color.WHITE,true));now.addView(t("Ностальгия • 90-е / 00-е",12,0xFFB45CFF,false));root.addView(now,new LinearLayout.LayoutParams(-1,dp(84)));
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.addView(btn("⏮"),new LinearLayout.LayoutParams(0,dp(58),1));controls.addView(btn("⏸"),new LinearLayout.LayoutParams(0,dp(58),1));controls.addView(btn("⏭"),new LinearLayout.LayoutParams(0,dp(58),1));root.addView(controls);
  HorizontalScrollView hs=new HorizontalScrollView(this);LinearLayout chips=new LinearLayout(this);String[] p={"90-е","00-е","2026","Лето","Клуб","Медляки"};for(String x:p){TextView v=t(x,12,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(round(0xFF15182A,16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(86),dp(36));lp.setMargins(0,dp(8),dp(8),dp(8));chips.addView(v,lp);}hs.addView(chips);root.addView(hs,new LinearLayout.LayoutParams(-1,dp(54)));root.addView(nav(2),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);}
 private TextView btn(String s){TextView v=t(s,24,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(round(0xFF241847,20));return v;}
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setBackgroundColor(0xFF090B15);String[] t={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<t.length;i++){final int x=i;TextView v=t(t[i],11,i==s?0xFFB45CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);v.setOnClickListener(z->{if(x!=s)startActivity(new Intent(this,c[x]));});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}private GradientDrawable grad(int a,int b,int r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(r));return g;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
