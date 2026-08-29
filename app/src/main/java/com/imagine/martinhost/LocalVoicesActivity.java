package com.imagine.martinhost;
import android.app.Activity;import android.content.*;import android.os.Bundle;import android.text.*;import android.widget.*;import java.util.Locale;
/** All ten official local styles; preview never changes the saved host voice. */
public final class LocalVoicesActivity extends Activity {
 private MartinNeuralSpeaker player;private SharedPreferences prefs;private TextView status,chosen,speedLabel;private EditText phrase;private SeekBar speed;
 @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("martin",0);LinearLayout root=PartyScreen.root(this,"Бесплатные голоса");
  PartyScreen.text(this,root,"5 мужских и 5 женских голосов Supertonic. Без подписок, ключей и платы за синтез. Новая INT8-модель: загрузка около 129 МБ на все голоса; интернет нужен только для её загрузки. Само распознавание речи и ответы AI пока используют ваш текущий API.",15);
  PartyScreen.text(this,root,"Исправлена подготовка букв ё и й; 5 шагов синтеза. Короткая реплика готовится целиком, без ожидания следующего предложения. Сравните произношение и интонацию на телефоне. Прежний голос — Женский 1 / F1.",15);
  chosen=PartyScreen.text(this,root,"",18);refreshChosen();
  speedLabel=PartyScreen.text(this,root,"",15);speed=new SeekBar(this);speed.setMax(30);speed.setProgress(Math.round((prefs.getFloat("local_voice_speed",1f)-.85f)*100));root.addView(speed);refreshSpeed();speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){refreshSpeed();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
  PartyScreen.text(this,root,"Фраза для сравнения (можно изменить)",15);phrase=new EditText(this);phrase.setText("Привет, Катя! С днём рождения! Друзья, как настроение? Кто готов первым поздравить именинницу? Сегодня будем смеяться, играть и танцевать.");phrase.setTextColor(0xFFF3F4F6);phrase.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);phrase.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});phrase.setMinLines(3);root.addView(phrase);
  status=PartyScreen.text(this,root,"Нажмите «Прослушать» у любого голоса",16);
  player=new MartinNeuralSpeaker(this,new MartinSpeaker.Listener(){public void onPreparing(String s){ui(s);}public void onReady(){}public void onStart(){ui("▶ Слушайте пробу голоса");}public void onLevel(float x){}public void onSpectrum(float[] x){}public void onDone(){ui("Проба завершена. Можно сравнить другой голос или сохранить этот.");}public void onError(String s){ui(s);}});
  PartyScreen.button(this,root,"■ Остановить пробу",()->{player.stop();ui("Проба остановлена");});
  for(String id:LocalVoiceProfiles.IDS){
   PartyScreen.text(this,root,LocalVoiceProfiles.label(id),20);
   PartyScreen.button(this,root,"▶ Прослушать "+id,()->{String text=phrase.getText().toString().trim();if(text.isEmpty()){ui("Введите пробную фразу");return;}ui("Готовлю "+LocalVoiceProfiles.label(id)+"…");player.previewLocal(text,id,rate());});
   PartyScreen.button(this,root,"✓ Оставить "+id+" для Мартина",()->{player.stop();prefs.edit().putString("voice_provider","local").putString("local_voice",id).putFloat("local_voice_speed",rate()).apply();refreshChosen();ui("Сохранено. На главном экране Мартин будет говорить выбранным голосом.");});
  }
  PartyScreen.button(this,root,"Диагностика: лог + аудио",()->startActivity(new Intent(this,DiagnosticsActivity.class)));
  PartyScreen.button(this,root,"Готово",this::finish);
 }
 private float rate(){return .85f+speed.getProgress()/100f;}
 private void refreshSpeed(){speedLabel.setText(String.format(Locale.forLanguageTag("ru"),"Темп синтеза: %.2f×",rate()));}
 private void refreshChosen(){chosen.setText("Для Мартина: "+LocalVoiceProfiles.label(LocalVoiceProfiles.valid(prefs.getString("local_voice","M1")))+String.format(Locale.forLanguageTag("ru")," · %.2f×",prefs.getFloat("local_voice_speed",1f)));}
 private void ui(String text){runOnUiThread(()->{if(!isDestroyed()&&status!=null)status.setText(text);});}
 @Override protected void onPause(){super.onPause();if(player!=null)player.releaseModel();}
 @Override protected void onDestroy(){if(player!=null)player.close();super.onDestroy();}
}
