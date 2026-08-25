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
 private void build(){
  getWindow().setStatusBarColor(0xFF060711);getWindow().setNavigationBarColor(0xFF060711);
  LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(0xFF060711);page.setPadding(dp(14),dp(9),dp(14),dp(8));
  LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("‹",32,Color.WHITE,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=t("Настройки ♛",22,Color.WHITE,true);title.setGravity(Gravity.CENTER);head.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));TextView person=t("◉",22,0xFFB45CFF,true);person.setGravity(Gravity.CENTER);head.addView(person,new LinearLayout.LayoutParams(dp(44),dp(44)));page.addView(head);
  ScrollView sv=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);sv.addView(root);
  root.addView(section("AI И ГОЛОС","Провайдер AI","GROQ / GSK  •  подключён","Модель","Llama / Mixtral","Голос Мартина","Будет заменён после визуального теста"));
  root.addView(section("ВЕЧЕРИНКА","Дата","29 августа","Гостей","19","Стиль","Весёлый ведущий · тосты · конкурсы · DJ"));
  root.addView(section("ГОСТИ И ПЕРСОНАЛИЗАЦИЯ","Профили","Имена, роли и факты","Безопасность","Запретные темы","Рейтинг","Баллы и история участия"));
  TextView edit=t("Редактировать гостей  ›",13,Color.WHITE,true);edit.setGravity(Gravity.CENTER);edit.setBackground(gradStroke(new int[]{0xFF203FE6,0xFF7926EF},19,0x557D85FF));edit.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(48));ep.setMargins(0,dp(2),0,dp(10));root.addView(edit,ep);
  LinearLayout projector=section("ПРОЕКТОР / TV","Режим","Подготовлен","Подключение","Android TV / HDMI / Cast","Игры","Фото, видео, рейтинг, таймеры");root.addView(projector);
  LinearLayout swCard=new LinearLayout(this);swCard.setPadding(dp(14),dp(8),dp(14),dp(8));swCard.setGravity(Gravity.CENTER_VERTICAL);swCard.setBackground(glass(18));TextView tx=t("Проектор подключён",14,Color.WHITE,true);swCard.addView(tx,new LinearLayout.LayoutParams(0,dp(48),1));Switch sw=new Switch(this);sw.setChecked(getSharedPreferences("martin",0).getBoolean("projector",false));sw.setOnCheckedChangeListener((b,on)->getSharedPreferences("martin",0).edit().putBoolean("projector",on).apply());swCard.addView(sw,new LinearLayout.LayoutParams(dp(70),dp(48)));LinearLayout.LayoutParams swp=new LinearLayout.LayoutParams(-1,dp(58));swp.setMargins(0,0,0,dp(10));root.addView(swCard,swp);
  page.addView(sv,new LinearLayout.LayoutParams(-1,0,1));page.addView(nav(4),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(page);
 }
 private LinearLayout section(String h,String a,String av,String b,String bv,String c,String cv){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(12),dp(14),dp(12));box.setBackground(glass(20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));box.setLayoutParams(lp);box.addView(t(h,11,0xFF8F75C9,true));row(box,a,av);row(box,b,bv);row(box,c,cv);return box;}
 private void row(LinearLayout box,String k,String v){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);TextView key=t(k,13,0xFFB8BCCB,false);TextView val=t(v,13,Color.WHITE,true);val.setGravity(Gravity.RIGHT);r.addView(key,new LinearLayout.LayoutParams(0,dp(38),1));r.addView(val,new LinearLayout.LayoutParams(0,dp(38),1));box.addView(r);}
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setPadding(dp(3),dp(3),dp(3),dp(3));n.setBackground(glass(18));String[] tt={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<tt.length;i++){final int x=i;TextView v=t(tt[i],10,i==s?0xFFB65CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);if(i==s)v.setBackground(round(0x252E1A57,15));v.setOnClickListener(z->{if(x!=s){startActivity(new Intent(this,c[x]));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}
 private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
 private GradientDrawable glass(int r){return gradStroke(new int[]{0xE7161928,0xE70B0D17},r,0x3344517D);}
 private GradientDrawable gradStroke(int[] cs,int r,int stroke){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,cs);g.setCornerRadius(dp(r));g.setStroke(dp(1),stroke);return g;}
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
