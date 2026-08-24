package com.imagine.martinhost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class VisualSettingsActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);build();}
 private void build(){getWindow().setStatusBarColor(0xFF050611);getWindow().setNavigationBarColor(0xFF050611);ScrollView sv=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF050611);root.setPadding(dp(16),dp(12),dp(16),dp(18));sv.addView(root);TextView title=t("Настройки 👑",24,Color.WHITE,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
  root.addView(section("AI и голос","GROQ / GSK подключён","Модель: mixtral / llama","Голос Мартина: временный Android TTS"));
  root.addView(section("Вечеринка","Дата: 29 августа","Гостей: 19","Стиль: весёлый и остроумный"));
  root.addView(section("Гости и персонализация","Имена, факты, роли","Запретные темы","Баллы и история участия"));
  TextView edit=t("Редактировать гостей",14,Color.WHITE,true);edit.setGravity(Gravity.CENTER);edit.setBackground(round(0xFF6D35F2,18));edit.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(48));ep.setMargins(0,dp(10),0,dp(10));root.addView(edit,ep);
  LinearLayout projector=section("Проектор / TV","Режим: подготовлен","Android TV / HDMI / Cast","Фото, видео и экранные конкурсы");root.addView(projector);
  Switch sw=new Switch(this);sw.setText("Проектор подключён");sw.setTextColor(Color.WHITE);sw.setTextSize(14);sw.setChecked(getSharedPreferences("martin",0).getBoolean("projector",false));sw.setOnCheckedChangeListener((b,on)->getSharedPreferences("martin",0).edit().putBoolean("projector",on).apply());root.addView(sw,new LinearLayout.LayoutParams(-1,dp(54)));
  LinearLayout nav=nav(4);root.addView(nav,new LinearLayout.LayoutParams(-1,dp(58)));setContentView(sv);}
 private LinearLayout section(String h,String a,String b,String c){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackground(round(0xFF111522,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));box.setLayoutParams(lp);box.addView(t(h,16,Color.WHITE,true));box.addView(t(a,13,0xFFD4D6DF,false));box.addView(t(b,13,0xFFD4D6DF,false));box.addView(t(c,13,0xFF9A6BFF,false));return box;}
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setBackgroundColor(0xFF090B15);String[] t={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<t.length;i++){final int x=i;TextView v=t(t[i],11,i==s?0xFFB45CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);v.setOnClickListener(z->{if(x!=s)startActivity(new Intent(this,c[x]));});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(4),dp(5),dp(4));return v;}private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
