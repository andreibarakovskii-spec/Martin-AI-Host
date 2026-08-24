package com.imagine.martinhost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class RankingActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);build();}
 private void build(){getWindow().setStatusBarColor(0xFF050611);getWindow().setNavigationBarColor(0xFF050611);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF050611);root.setPadding(dp(16),dp(12),dp(16),dp(8));TextView title=t("Рейтинг гостей 👑",24,Color.WHITE,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));
  LinearLayout tabs=new LinearLayout(this);TextView today=t("Сегодня",13,Color.WHITE,true);today.setGravity(Gravity.CENTER);today.setBackground(round(0xFF6D35F2,18));TextView all=t("За всё время",13,0xFFA7ABBC,false);all.setGravity(Gravity.CENTER);all.setBackground(round(0xFF15182A,18));tabs.addView(today,new LinearLayout.LayoutParams(0,dp(40),1));tabs.addView(all,new LinearLayout.LayoutParams(0,dp(40),1));root.addView(tabs);
  ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);String[] names={"Катя","Сергей","Лена","Андрей","Дима","Оля","Игорь","Настя","Ксюша","Макс"};int[] pts={125,98,87,76,69,64,56,48,42,39};for(int i=0;i<names.length;i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),0,dp(12),0);row.setBackground(round(i<3?0xFF14172A:0xFF0E111B,14));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(58));rp.setMargins(0,dp(5),0,0);row.setLayoutParams(rp);TextView n=t(i<3?(i==0?"🥇":i==1?"🥈":"🥉"):String.valueOf(i+1),18,Color.WHITE,true);n.setGravity(Gravity.CENTER);row.addView(n,new LinearLayout.LayoutParams(dp(54),-1));TextView name=t(names[i]+(i==0?"  👑":""),16,Color.WHITE,true);row.addView(name,new LinearLayout.LayoutParams(0,-1,1));TextView score=t(pts[i]+" баллов",14,0xFFB45CFF,true);score.setGravity(Gravity.CENTER_VERTICAL);row.addView(score,new LinearLayout.LayoutParams(dp(100),-1));list.addView(row);}sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));TextView stat=t("Все гости и статистика",14,Color.WHITE,true);stat.setGravity(Gravity.CENTER);stat.setBackground(round(0xFF6325D9,20));root.addView(stat,new LinearLayout.LayoutParams(-1,dp(50)));root.addView(nav(3),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);}
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setBackgroundColor(0xFF090B15);String[] t={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<t.length;i++){final int x=i;TextView v=t(t[i],11,i==s?0xFFB45CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);v.setOnClickListener(z->{if(x!=s)startActivity(new Intent(this,c[x]));});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
