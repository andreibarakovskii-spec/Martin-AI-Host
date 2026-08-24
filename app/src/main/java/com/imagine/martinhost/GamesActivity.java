package com.imagine.martinhost;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class GamesActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);build();}
 private void build(){getWindow().setStatusBarColor(0xFF050611);getWindow().setNavigationBarColor(0xFF050611);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF050611);root.setPadding(dp(16),dp(12),dp(16),dp(8));
  TextView title=t("Игры и конкурсы",24,Color.WHITE,true);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(50)));
  HorizontalScrollView hs=new HorizontalScrollView(this);LinearLayout chips=new LinearLayout(this);String[] cats={"Все","Музыка","Интеллект","Фото","Активные"};for(int i=0;i<cats.length;i++){TextView c=t(cats[i],12,Color.WHITE,i==0);c.setGravity(Gravity.CENTER);c.setBackground(round(i==0?0xFF6D35F2:0xFF121522,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(92),dp(34));p.setMargins(0,0,dp(8),0);chips.addView(c,p);}hs.addView(chips);root.addView(hs,new LinearLayout.LayoutParams(-1,dp(44)));
  ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);String[][] games={{"🎵","Угадай мелодию","Хиты 90-х, 00-х и 2026","10–15 мин"},{"👥","Две звезды в одном лице","Угадай знаменитостей","10–15 мин"},{"🧠","Что? Где? Когда?","Логика, факты и вопросы с подвохом","15–20 мин"},{"😈","Испорченные стишки","Проверка испорченности гостей","10 мин"},{"💬","Кто сказал эту цитату?","Известные люди и неожиданные ответы","10–15 мин"},{"🎶","Музыкальная машина времени","Угадай год и исполнителя","10–15 мин"}};for(String[] g:games)list.addView(card(g));sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));root.addView(nav(1),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);}
 private LinearLayout card(String[] g){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackground(round(0xFF111522,18));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(86));rp.setMargins(0,0,0,dp(9));row.setLayoutParams(rp);TextView ic=t(g[0],28,Color.WHITE,true);ic.setGravity(Gravity.CENTER);ic.setBackground(round(0xFF4A25A7,16));row.addView(ic,new LinearLayout.LayoutParams(dp(60),dp(60)));LinearLayout tx=new LinearLayout(this);tx.setOrientation(LinearLayout.VERTICAL);TextView a=t(g[1],16,Color.WHITE,true);TextView b=t(g[2],12,0xFFA2A7B8,false);TextView c=t(g[3],11,0xFF7F8498,false);tx.addView(a);tx.addView(b);tx.addView(c);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1);tp.setMargins(dp(12),0,dp(8),0);row.addView(tx,tp);TextView go=t("Старт",12,Color.WHITE,true);go.setGravity(Gravity.CENTER);go.setBackground(round(0xFF6D35F2,18));row.addView(go,new LinearLayout.LayoutParams(dp(68),dp(42)));return row;}
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setBackgroundColor(0xFF090B15);String[] t={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={VisualHomeActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,VisualSettingsActivity.class};for(int i=0;i<t.length;i++){final int x=i;TextView v=t(t[i],11,i==s?0xFFB45CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);v.setOnClickListener(z->{if(x!=s)startActivity(new Intent(this,c[x]));});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
