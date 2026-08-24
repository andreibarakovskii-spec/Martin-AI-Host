package com.imagine.martinhost;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private GuestStore store; private LinearLayout guestList; private List<GuestStore.Guest> guests; private SharedPreferences prefs;
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("martin",0);store=new GuestStore(this);guests=new ArrayList<>(store.load());buildUi();}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFF080A10);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,48);scroll.addView(root);
        TextView h=tv("НАСТРОЙКИ",28,Color.WHITE);h.setGravity(Gravity.CENTER);root.addView(h);
        root.addView(tv("AI • гости • проектор • персонализация",14,0xFF9AA1B2));
        Button ai=btn(aiLabel());ai.setOnClickListener(v->aiDialog(ai));root.addView(ai);
        Button projector=btn(projectorLabel());projector.setOnClickListener(v->{boolean on=!prefs.getBoolean("projector",false);prefs.edit().putBoolean("projector",on).apply();projector.setText(projectorLabel());});root.addView(projector);
        root.addView(tv("ГОСТИ",13,0xFF8B5CF6));guestList=new LinearLayout(this);guestList.setOrientation(LinearLayout.VERTICAL);root.addView(guestList);renderGuests();
        Button add=btn("＋ ДОБАВИТЬ ГОСТЯ");add.setOnClickListener(v->editGuest(-1));root.addView(add);
        Button save=btn("ГОТОВО");save.setOnClickListener(v->{store.save(guests);finish();});root.addView(save);setContentView(scroll);
    }
    private String aiLabel(){String p=prefs.getString("ai_provider","auto");return "🤖 AI: "+("groq".equals(p)?"Groq / GSK":"xai".equals(p)?"xAI / Grok":"Авто по ключу");}
    private String projectorLabel(){return prefs.getBoolean("projector",false)?"📽 ПРОЕКТОР: ПОДКЛЮЧЁН":"📱 ПРОЕКТОР: ВЫКЛЮЧЕН";}

    private void aiDialog(Button target){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(18,8,18,0);
        Spinner provider=new Spinner(this);String[] ps={"Авто (GSK → Groq)","Groq / GSK","xAI / Grok"};provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ps));String cur=prefs.getString("ai_provider","auto");provider.setSelection("groq".equals(cur)?1:"xai".equals(cur)?2:0);
        EditText key=input("API key: gsk_... или xai-...",prefs.getString("ai_key",prefs.getString("xai_key","")));key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText model=input("Groq model",prefs.getString("groq_model","openai/gpt-oss-120b"));
        box.addView(provider);box.addView(key);box.addView(model);
        new AlertDialog.Builder(this).setTitle("AI-провайдер").setMessage("Если ключ начинается с gsk_, выбери Groq или оставь Авто.").setView(box).setPositiveButton("Сохранить",(d,w)->{String p=provider.getSelectedItemPosition()==1?"groq":provider.getSelectedItemPosition()==2?"xai":"auto";prefs.edit().putString("ai_provider",p).putString("ai_key",key.getText().toString().trim()).putString("groq_model",model.getText().toString().trim()).apply();target.setText(aiLabel());}).setNegativeButton("Отмена",null).show();
    }

    private void renderGuests(){guestList.removeAllViews();if(guests.isEmpty())guestList.addView(tv("Добавьте имена и факты заранее.",15,0xFF8F96A3));for(int i=0;i<guests.size();i++){final int index=i;GuestStore.Guest g=guests.get(i);Button b=btn((i+1)+". "+g.name+"   ⭐ "+g.score);b.setOnClickListener(v->editGuest(index));guestList.addView(b);}}
    private void editGuest(int index){GuestStore.Guest g=index>=0?guests.get(index):new GuestStore.Guest();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(18,8,18,0);EditText name=input("Имя",g.name),call=input("Как обращаться",g.callName),relation=input("Кто он/она Кате",g.relation),facts=input("Факты для шуток и тостов",g.facts),bounds=input("Запретные темы",g.boundaries);facts.setMinLines(3);bounds.setMinLines(2);box.addView(name);box.addView(call);box.addView(relation);box.addView(facts);box.addView(bounds);AlertDialog.Builder d=new AlertDialog.Builder(this).setTitle(index>=0?"Гость":"Новый гость").setView(box).setPositiveButton("Сохранить",(x,w)->{g.name=name.getText().toString().trim();g.callName=call.getText().toString().trim();g.relation=relation.getText().toString().trim();g.facts=facts.getText().toString().trim();g.boundaries=bounds.getText().toString().trim();if(!g.name.isBlank()){if(index<0)guests.add(g);store.save(guests);renderGuests();}}).setNegativeButton("Отмена",null);if(index>=0)d.setNeutralButton("Удалить",(x,w)->{guests.remove(index);store.save(guests);renderGuests();});d.show();}

    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextColor(Color.BLACK);e.setHintTextColor(0xFF777777);return e;}
    private TextView tv(String s,int sp,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(c);v.setPadding(8,12,8,12);return v;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;}
}
