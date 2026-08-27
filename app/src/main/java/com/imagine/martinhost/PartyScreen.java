package com.imagine.martinhost;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
final class PartyScreen {
 static LinearLayout root(Activity a,String title){ScrollView s=new ScrollView(a);s.setFillViewport(true);s.setBackgroundColor(0xFF080A12);LinearLayout l=new LinearLayout(a);l.setOrientation(1);int p=(int)(16*a.getResources().getDisplayMetrics().density);l.setPadding(p,p,p,p);s.addView(l);a.setContentView(s);button(a,l,"‹ Назад",a::finish);text(a,l,title,26);return l;}
 static TextView text(Activity a,LinearLayout l,String t,int size){TextView v=new TextView(a);v.setText(t);v.setTextSize(size);v.setTextColor(0xFFE7E3F4);v.setPadding(8,14,8,14);l.addView(v);return v;}
 static Button button(Activity a,LinearLayout l,String t,Runnable r){Button b=new Button(a);b.setText(t);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT_BOLD);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xFF3B2372,0xFF252344});g.setCornerRadius(24);b.setBackground(g);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,8,0,8);l.addView(b,p);b.setOnClickListener(v->r.run());return b;}
}
