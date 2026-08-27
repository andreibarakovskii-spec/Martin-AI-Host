package com.imagine.martinhost;
import android.app.*;import android.os.Bundle;import android.widget.*;import java.util.*;
public final class RankingActivity extends Activity {
 private GuestStore store;
 public void onCreate(Bundle b){super.onCreate(b);store=new GuestStore(this);render();}
 private void render(){LinearLayout root=PartyScreen.root(this,"Счёт гостей и команд");List<GuestStore.Guest> guests=store.load();guests.sort((a,b)->Integer.compare(b.score,a.score));if(guests.isEmpty())PartyScreen.text(this,root,"Пока нет участников. После правильного ответа назовите имя — ведущий создаст запись.",16);int n=0;for(GuestStore.Guest g:guests){PartyScreen.text(this,root,(++n)+". "+g.name+" — "+g.score+" баллов",19);PartyScreen.button(this,root,"Исправить счёт: "+g.name,()->{EditText e=new EditText(this);e.setInputType(2);e.setText(""+g.score);new AlertDialog.Builder(this).setTitle("Баллы: "+g.name).setView(e).setPositiveButton("Сохранить",(d,w)->{try{int score=Integer.parseInt(e.getText().toString());List<GuestStore.Guest> all=store.load();for(GuestStore.Guest x:all)if(x.name.equals(g.name)){x.score=Math.max(0,score);break;}store.save(all);render();}catch(Exception ignored){}}).setNegativeButton("Отмена",null).show();});}
 PartyScreen.button(this,root,"Обнулить баллы",()->new AlertDialog.Builder(this).setMessage("Обнулить баллы всех гостей? Имена и заметки сохранятся.").setPositiveButton("Обнулить",(d,w)->{List<GuestStore.Guest> all=store.load();for(GuestStore.Guest g:all){g.score=0;g.participated=0;}store.save(all);render();}).setNegativeButton("Отмена",null).show());}
}
