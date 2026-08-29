package com.imagine.martinhost;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
public final class GamesActivity extends Activity {
 public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=PartyScreen.root(this,"Игры и конкурсы");PartyScreen.text(this,root,"Правила → подтверждение → раунды → баллы. Для обсуждения используйте «ответ …», «дальше», «покажи ответ». Кнопки работают без интернета; голосовой ввод требует Groq. Фото-конкурсы пока представлены словесными версиями.",14);android.widget.Button auto=PartyScreen.button(this,root,autoLabel(),()->{});auto.setOnClickListener(v->{boolean on=!getSharedPreferences("martin",0).getBoolean("auto_games",true);getSharedPreferences("martin",0).edit().putBoolean("auto_games",on).putLong("last_auto_game_offer",System.currentTimeMillis()).apply();auto.setText(autoLabel());});
 for(PartyGames.Game g:PartyGames.all()){PartyScreen.button(this,root,g.title,()->{Intent i=new Intent(this,PremiumMainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);i.putExtra("game_id",g.id);startActivity(i);finish();});PartyScreen.text(this,root,g.rules,13);}}
 private String autoLabel(){return getSharedPreferences("martin",0).getBoolean("auto_games",true)?"⏱ АВТОКОНКУРСЫ: ПРИМЕРНО КАЖДЫЕ 30 МИНУТ":"⏱ АВТОКОНКУРСЫ: ВЫКЛ";}
}
