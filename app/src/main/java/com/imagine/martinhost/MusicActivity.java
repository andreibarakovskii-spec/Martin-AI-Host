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
 private void build(){
  getWindow().setStatusBarColor(0xFF060711);getWindow().setNavigationBarColor(0xFF060711);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF060711);root.setPadding(dp(14),dp(9),dp(14),dp(8));
  LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("‹",32,Color.WHITE,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=t("DJ Мартин ♛",22,Color.WHITE,true);title.setGravity(Gravity.CENTER);head.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));TextView glow=t("✦",22,0xFFB45CFF,true);glow.setGravity(Gravity.CENTER);head.addView(glow,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(head);
  FrameLayout hero=new FrameLayout(this);hero.setBackground(gradStroke(new int[]{0xFF100C26,0xFF25104C,0xFF071527},26,0x334A56A0));MartinSpriteView m=new MartinSpriteView(this);m.setState(MartinSpriteView.State.DJ);hero.addView(m,new FrameLayout.LayoutParams(-1,-1));TextView live=t("●  DJ MODE",11,Color.WHITE,true);live.setGravity(Gravity.CENTER);live.setBackground(gradStroke(new int[]{0xDD1739E6,0xDD8B2CFF},18,0x66FFFFFF));FrameLayout.LayoutParams ll=new FrameLayout.LayoutParams(dp(110),dp(34));ll.gravity=Gravity.TOP|Gravity.RIGHT;ll.setMargins(0,dp(14),dp(14),0);hero.addView(live,ll);root.addView(hero,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout now=new LinearLayout(this);now.setOrientation(LinearLayout.VERTICAL);now.setPadding(dp(14),dp(10),dp(14),dp(10));now.setBackground(glass(18));now.addView(t("СЕЙЧАС ИГРАЕТ",10,0xFF858CA3,false));now.addView(t("Руки Вверх! — Крошка моя",16,Color.WHITE,true));now.addView(t("Ностальгия • 90-е / 00-е",11,0xFFB45CFF,false));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(78));np.setMargins(0,dp(8),0,dp(6));root.addView(now,np);
  LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);String[] cs={"⏮","⏸","⏭"};for(int i=0;i<3;i++){TextView b=btn(cs[i],i==1);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(54),1);if(i>0)cp.setMargins(dp(7),0,0,0);controls.addView(b,cp);}root.addView(controls);
  HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout chips=new LinearLayout(this);String[] p={"90-е","00-е","2026","Лето","Клуб","Медляки"};for(String x:p){TextView v=t(x,11,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(glass(16));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(84),dp(36));lp.setMargins(0,dp(8),dp(8),dp(8));chips.addView(v,lp);}hs.addView(chips);root.addView(hs,new LinearLayout.LayoutParams(-1,dp(52)));
  TextView create=t("Создать плейлист  ♫",13,Color.WHITE,true);create.setGravity(Gravity.CENTER);create.setBackground(gradStroke(new int[]{0xFF173DE4,0xFF7A27EF},19,0x557D85FF));LinearLayout.LayoutParams cr=new LinearLayout.LayoutParams(-1,dp(46));cr.setMargins(0,0,0,dp(7));root.addView(create,cr);
  root.addView(nav(2),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);
 }
 private TextView btn(String s,boolean main){TextView v=t(s,22,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(main?gradStroke(new int[]{0xFF1739E7,0xFF7A29F0},20,0x667D85FF):glass(20));return v;}
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setPadding(dp(3),dp(3),dp(3),dp(3));n.setBackground(glass(18));String[] tt={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<tt.length;i++){final int x=i;TextView v=t(tt[i],10,i==s?0xFFB65CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);if(i==s)v.setBackground(round(0x252E1A57,15));v.setOnClickListener(z->{if(x!=s){startActivity(new Intent(this,c[x]));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}
 private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
 private GradientDrawable glass(int r){return gradStroke(new int[]{0xE7161928,0xE70B0D17},r,0x3344517D);}
 private GradientDrawable gradStroke(int[] cs,int r,int stroke){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,cs);g.setCornerRadius(dp(r));g.setStroke(dp(1),stroke);return g;}
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
