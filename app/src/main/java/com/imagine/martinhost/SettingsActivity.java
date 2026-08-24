package com.imagine.martinhost;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private GuestStore store;
    private LinearLayout guestList;
    private List<GuestStore.Guest> guests;

    @Override public void onCreate(Bundle b){ super.onCreate(b); store=new GuestStore(this); guests=new ArrayList<>(store.load()); buildUi(); }

    private void buildUi(){
        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(0xFF0F1014);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(22,24,22,40); scroll.addView(root);
        TextView h=tv("⚙ НАСТРОЙКИ МАРТИНА",28,Color.WHITE); h.setGravity(Gravity.CENTER); root.addView(h);
        root.addView(tv("Подготовьте Мартина к вечеринке: гости, факты, границы, API и режим проектора.",15,0xFFB9BECA));

        Button api=btn("🔑 xAI API key"); api.setOnClickListener(v->apiDialog()); root.addView(api);
        Button projector=btn(projectorLabel()); projector.setOnClickListener(v->{ boolean on=!getPreferences(0).getBoolean("projector",false); getPreferences(0).edit().putBoolean("projector",on).apply(); projector.setText(projectorLabel()); }); root.addView(projector);

        root.addView(tv("ГОСТИ",14,0xFFFFD166));
        guestList=new LinearLayout(this); guestList.setOrientation(LinearLayout.VERTICAL); root.addView(guestList); renderGuests();
        Button add=btn("＋ ДОБАВИТЬ ГОСТЯ"); add.setOnClickListener(v->editGuest(-1)); root.addView(add);
        Button save=btn("💾 СОХРАНИТЬ И ВЕРНУТЬСЯ"); save.setOnClickListener(v->{store.save(guests); finish();}); root.addView(save);
        setContentView(scroll);
    }

    private String projectorLabel(){ return getPreferences(0).getBoolean("projector",false)?"📽 ПРОЕКТОР: ПОДКЛЮЧЁН":"📱 ПРОЕКТОР: ВЫКЛЮЧЕН"; }

    private void renderGuests(){
        guestList.removeAllViews();
        if(guests.isEmpty()) guestList.addView(tv("Пока никого нет. Добавьте всех гостей заранее.",15,0xFF8F96A3));
        for(int i=0;i<guests.size();i++){
            final int index=i; GuestStore.Guest g=guests.get(i);
            Button b=btn((i+1)+". "+g.name+(g.callName.isBlank()?"":" • "+g.callName)+"   ⭐ "+g.score);
            b.setOnClickListener(v->editGuest(index)); guestList.addView(b);
        }
    }

    private void editGuest(int index){
        GuestStore.Guest g=index>=0?guests.get(index):new GuestStore.Guest();
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(18,8,18,0);
        EditText name=input("Имя",g.name); EditText call=input("Как Мартину обращаться",g.callName); EditText relation=input("Кто он/она Кате",g.relation);
        EditText facts=input("Факты и истории, которыми можно пользоваться",g.facts); facts.setMinLines(3);
        EditText boundaries=input("Запретные темы / о чём не шутить",g.boundaries); boundaries.setMinLines(2);
        box.addView(name);box.addView(call);box.addView(relation);box.addView(facts);box.addView(boundaries);
        AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle(index>=0?"Редактировать гостя":"Новый гость").setView(box)
                .setPositiveButton("Сохранить",(x,w)->{g.name=name.getText().toString().trim();g.callName=call.getText().toString().trim();g.relation=relation.getText().toString().trim();g.facts=facts.getText().toString().trim();g.boundaries=boundaries.getText().toString().trim();if(!g.name.isBlank()){if(index<0)guests.add(g);store.save(guests);renderGuests();}})
                .setNegativeButton("Отмена",null);
        if(index>=0)d.setNeutralButton("Удалить",(x,w)->{guests.remove(index);store.save(guests);renderGuests();});
        d.show();
    }

    private void apiDialog(){
        EditText in=new EditText(this); in.setHint("xai-..."); in.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("xAI API key — тест").setMessage("Для тестовой сборки хранится локально на телефоне.").setView(in)
                .setPositiveButton("Сохранить",(d,w)->getSharedPreferences("martin",0).edit().putString("xai_key",in.getText().toString().trim()).apply()).setNegativeButton("Отмена",null).show();
    }

    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(Color.BLACK);e.setHintTextColor(0xFF777777);return e;}
    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(8,10,8,10);return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
}
