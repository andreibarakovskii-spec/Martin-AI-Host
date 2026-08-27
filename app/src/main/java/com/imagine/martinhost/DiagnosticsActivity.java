package com.imagine.martinhost;
import android.app.*;import android.content.Intent;import android.os.Bundle;import android.widget.*;
public final class DiagnosticsActivity extends Activity {
 private DiagnosticRecorder recorder;private TextView status;
 public void onCreate(Bundle b){super.onCreate(b);recorder=DiagnosticRecorder.get(this);LinearLayout root=PartyScreen.root(this,"Диагностика диалога");status=PartyScreen.text(this,root,recorder.status(),18);
 PartyScreen.text(this,root,"Запишите 3–5 реплик, включая одну с задержкой. Микрофон записывается и во время ответа ведущего — это позволит услышать эхо. Сохраняются распознанные слова, ответы AI, звук синтеза и времена каждого этапа. Камера и ключи не сохраняются. Максимум 5 минут / 64 МБ. Запись работает только при включённом микрофоне ведущего.",15);
 PartyScreen.button(this,root,"● Начать диагностическую запись",()->new AlertDialog.Builder(this).setTitle("Согласие на запись").setMessage("Предупредите всех рядом: их голоса и реплики попадут в диагностический ZIP. Он хранится на телефоне и никуда автоматически не отправляется. После включения вернитесь на главный экран и нажмите «Начать».").setPositiveButton("Все согласны — записывать",(d,w)->{recorder.start();finish();}).setNegativeButton("Отмена",null).show());
 PartyScreen.button(this,root,"■ Остановить запись",()->{recorder.stop();status.setText(recorder.status());});
 PartyScreen.button(this,root,"Скачать ZIP: лог + аудио",()->{recorder.stop();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/zip");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_TITLE,"Martin-dialogue-"+System.currentTimeMillis()+".zip");startActivityForResult(i,71);});
 PartyScreen.button(this,root,"Удалить записи с телефона",()->new AlertDialog.Builder(this).setMessage("Удалить все диагностические сессии? Уже экспортированные ZIP удаляются отдельно через файловый менеджер.").setPositiveButton("Удалить",(d,w)->recorder.delete(s->runOnUiThread(()->status.setText(s)))).setNegativeButton("Отмена",null).show());
 }
 protected void onActivityResult(int r,int c,Intent i){super.onActivityResult(r,c,i);if(r==71&&c==RESULT_OK&&i!=null&&i.getData()!=null){status.setText("Сохраняю ZIP…");recorder.export(i.getData(),s->runOnUiThread(()->status.setText(s)));}}
}
