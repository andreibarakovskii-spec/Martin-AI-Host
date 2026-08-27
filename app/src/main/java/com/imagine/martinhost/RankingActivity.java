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
 private void build(){
  getWindow().setStatusBarColor(0xFF060711);getWindow().setNavigationBarColor(0xFF060711);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF060711);root.setPadding(dp(14),dp(9),dp(14),dp(8));
  LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=t("‹",32,Color.WHITE,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=t("Рейтинг гостей ♛",22,Color.WHITE,true);title.setGravity(Gravity.CENTER);head.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));TextView cup=t("🏆",20,0xFFFFC64A,true);cup.setGravity(Gravity.CENTER);head.addView(cup,new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(head);
  LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(dp(14),dp(12),dp(14),dp(12));summary.setBackground(gradStroke(new int[]{0xFF182058,0xFF3E176A,0xFF101325},22,0x445A6AD0));summary.addView(t("Сегодняшний чемпионат",12,0xFFAEB5D0,false));summary.addView(t("19 гостей · 6 конкурсов · 486 баллов",16,Color.WHITE,true));summary.addView(t("Мартин запоминает ответы и начисляет баллы по имени",11,0xFFC28BFF,false));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(86));sp.setMargins(0,0,0,dp(8));root.addView(summary,sp);
  LinearLayout tabs=new LinearLayout(this);TextView today=t("Сегодня",12,Color.WHITE,true);today.setGravity(Gravity.CENTER);today.setBackground(gradStroke(new int[]{0xFF2140F0,0xFF7C27EF},18,0x557D85FF));TextView all=t("За всё время",12,0xFFA7ABBC,false);all.setGravity(Gravity.CENTER);all.setBackground(glass(18));tabs.addView(today,new LinearLayout.LayoutParams(0,dp(40),1));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(40),1);ap.setMargins(dp(8),0,0,0);tabs.addView(all,ap);root.addView(tabs);
  ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);String[] names={"Катя","Сергей","Лена","Андрей","Дима","Оля","Игорь","Настя","Ксюша","Макс"};int[] pts={125,98,87,76,69,64,56,48,42,39};for(int i=0;i<names.length;i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(11),0,dp(11),0);row.setBackground(i<3?gradStroke(new int[]{0xE71B1D38,0xE7111223},15,0x334A59A0):glass(15));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(56));rp.setMargins(0,dp(6),0,0);row.setLayoutParams(rp);TextView n=t(i<3?(i==0?"🥇":i==1?"🥈":"🥉"):String.valueOf(i+1),17,Color.WHITE,true);n.setGravity(Gravity.CENTER);row.addView(n,new LinearLayout.LayoutParams(dp(48),-1));TextView avatar=t("●",22,i==0?0xFFFFC24D:i==1?0xFFB8C2DA:i==2?0xFFC98A58:0xFF6E58C8,true);avatar.setGravity(Gravity.CENTER);row.addView(avatar,new LinearLayout.LayoutParams(dp(36),-1));TextView name=t(names[i]+(i==0?"  👑":""),15,Color.WHITE,true);row.addView(name,new LinearLayout.LayoutParams(0,-1,1));TextView score=t(pts[i]+"",15,0xFFB45CFF,true);score.setGravity(Gravity.CENTER);row.addView(score,new LinearLayout.LayoutParams(dp(62),-1));list.addView(row);}sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));TextView stat=t("Все гости и статистика  ›",13,Color.WHITE,true);stat.setGravity(Gravity.CENTER);stat.setBackground(gradStroke(new int[]{0xFF213FE6,0xFF7625EA},19,0x557D85FF));LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-1,dp(46));st.setMargins(0,dp(7),0,dp(7));root.addView(stat,st);root.addView(nav(3),new LinearLayout.LayoutParams(-1,dp(58)));setContentView(root);
 }
 private LinearLayout nav(int s){LinearLayout n=new LinearLayout(this);n.setPadding(dp(3),dp(3),dp(3),dp(3));n.setBackground(glass(18));String[] tt={"⌂\nМартин","🎮\nИгры","♫\nМузыка","🏆\nРейтинг","⚙\nНастройки"};Class<?>[] c={PremiumMainActivity.class,GamesActivity.class,MusicActivity.class,RankingActivity.class,SettingsActivity.class};for(int i=0;i<tt.length;i++){final int x=i;TextView v=t(tt[i],10,i==s?0xFFB65CFF:0xFF8A8EA2,i==s);v.setGravity(Gravity.CENTER);if(i==s)v.setBackground(round(0x252E1A57,15));v.setOnClickListener(z->{if(x!=s){startActivity(new Intent(this,c[x]));overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);}});n.addView(v,new LinearLayout.LayoutParams(0,-1,1));}return n;}
 private TextView t(String s,int sp,int c,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);v.setPadding(dp(5),dp(3),dp(5),dp(3));return v;}
 private GradientDrawable round(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
 private GradientDrawable glass(int r){return gradStroke(new int[]{0xE7161928,0xE70B0D17},r,0x3344517D);}
 private GradientDrawable gradStroke(int[] cs,int r,int stroke){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,cs);g.setCornerRadius(dp(r));g.setStroke(dp(1),stroke);return g;}
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
