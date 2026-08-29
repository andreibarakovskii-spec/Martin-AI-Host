package com.imagine.martinhost;

import android.content.Context;
import java.util.*;

/** Explicit round/confirmation/scoring states; free chat cannot award points. */
public final class PartyDirector {
 public enum Mode { FREE, RULES, WAIT_ANSWER, WAIT_NAME, RESULT }
 public static final class Action {
  public final String speech,state,gesture,emotion; public final boolean askAi;
  Action(String s,String st,String g,String e,boolean ai){speech=s;state=st;gesture=g;emotion=e;askAi=ai;}
  public static Action local(String s,String st,String g,String e){return new Action(s,st,g,e,false);}
  public static Action ai(String s,String st,String g,String e){return new Action(s,st,g,e,true);}
 }
 interface ScoreSink {void add(String name);}
 private final ScoreSink scoreSink;
 private final GuestStore guests;
 private final Context context;
 private Mode mode=Mode.FREE;
 private PartyGames.Game game;
 private PartyGames.Round round;
 private int index=-1,score=0;
 private String musicUri;
 private String suggestedGuest="";
 private final List<PartyMusic.Track> musicRounds=new ArrayList<>();
 public PartyDirector(Context c){context=c.getApplicationContext();guests=new GuestStore(c);scoreSink=name->{if(!guests.addScore(name,1)){List<GuestStore.Guest> all=guests.load();GuestStore.Guest g=new GuestStore.Guest();g.name=name;g.callName=name;g.score=1;g.participated=1;all.add(g);guests.save(all);}};}
 PartyDirector(ScoreSink sink){context=null;guests=null;scoreSink=sink;}
 public Mode mode(){return mode;}
 public String summary(){return game==null?"Свободный диалог":game.title+" • раунд "+Math.max(0,index+1)+" • верных: "+score;}
 public String takeMusicUri(){String s=musicUri;musicUri=null;return s;}
 public void setSuggestedGuest(String name){suggestedGuest=name==null?"":name.trim();}
 private Action local(String s){return Action.local(s,"game","","curious");}
 public Action startChgk(){return startGame("chgk");}
 public Action startGame(String id){
  game=PartyGames.get(id);index=-1;score=0;round=null;musicUri=null;musicRounds.clear();
  if(id.equals("melody")||id.equals("time_machine")){
   for(PartyMusic.Track t:PartyMusic.get(context).tracks())if(!id.equals("time_machine")||t.year>0)musicRounds.add(t);
   if(musicRounds.isEmpty()){mode=Mode.FREE;return local("Сначала добавьте аудиофайлы в разделе «Музыка». Для машины времени укажите год в имени файла или через кнопку «Данные». Музыка не скачивается автоматически.");}
   Collections.shuffle(musicRounds);
  }
  mode=Mode.RULES;
  return local("Игра «"+game.title+"». "+game.rules+" Пример: "+game.example+" Правила понятны? Начинаем?");
 }
 public Action cancel(){mode=Mode.FREE;game=null;musicUri=null;return local("Конкурс остановлен. Возвращаемся к разговору.");}
 public Action next(){
  if(game==null)return local("Сначала выберите игру.");
  index++;int count=musicRounds.isEmpty()?game.rounds.size():Math.min(6,musicRounds.size());
  if(index>=count){String s="Игра закончена! Правильных ответов: "+score+". Баллы гостей сохранены. Можно выбрать следующий конкурс.";mode=Mode.FREE;game=null;return local(s);}
  mode=Mode.WAIT_ANSWER;
  if(!musicRounds.isEmpty()){
   PartyMusic.Track t=musicRounds.get(index);musicUri=t.uri;
   String a=game.id.equals("melody")?t.title+"|"+t.artist:t.year+"|"+decade(t.year);
   round=new PartyGames.Round("Слушаем фрагмент. После музыки скажите «ответ» и ваш вариант.",a,false);
  }else round=game.rounds.get(index);
  return local("Раунд "+(index+1)+". "+round.question);
 }
 private String decade(int y){if(y>=1990&&y<2000)return "девяностые|90";if(y>=2000&&y<2010)return "нулевые|двухтысячные|2000";if(y>=2010&&y<2020)return "десятые|2010";if(y>=2020&&y<2030)return "двадцатые|2020";return "";}
 public Action award(){
  if(mode!=Mode.WAIT_ANSWER)return local("Баллы можно начислить после задания.");
  score++;
  if(!suggestedGuest.isBlank()){scoreSink.add(suggestedGuest);mode=Mode.RESULT;String name=suggestedGuest;suggestedGuest="";return local("Верно, "+name+"! Один балл записан. Скажите «дальше».");}
  mode=Mode.WAIT_NAME;return local("Засчитано! Я не уверен, кто ответил. Назовите имя гостя или команды для балла. Либо скажите «без имени».");
 }
 public Action reveal(){
  if(round==null)return local("Сначала начните раунд.");
  mode=Mode.RESULT;return local((round.judged?(round.answer.isBlank()?"Этот конкурс оценивает организатор.":"Ответ: "+round.answer+"."):"Ответ: "+round.answer.split("\\|")[0]+".")+" Скажите «дальше».");
 }
 public Action onUserText(String raw){
  String t=raw==null?"":raw.trim(),l=PartyGames.normal(t);
  if(l.equals("закончить игру")||l.equals("стоп игра")||l.equals("отмена конкурса"))return cancel();
  if(mode==Mode.FREE){
   if(l.contains("музыкальн")&&(l.contains("виктор")||l.contains("игр"))||l.contains("угадай песню")||l.contains("угадай музыку"))return startGame("melody");
   if(l.contains("машин")&&l.contains("времен")&&l.contains("музык"))return startGame("time_machine");
   for(PartyGames.Game g:PartyGames.all())if(l.contains(PartyGames.normal(g.title.split(" — ")[0])))return startGame(g.id);
   if(l.contains("чгк")||l.equals("начни игру"))return startChgk();
   if(l.contains("тост"))return Action.ai("Скажи короткий тёплый тост для Кати и гостей без принуждения к алкоголю.","toast","","warm");
   return Action.ai(t,"talking","","neutral");
  }
  if(l.equals("правила")||l.equals("повтори правила"))return local(game.rules+" Когда готовы, скажите «начинаем».");
  if(l.equals("дальше")||l.equals("следующий")||l.equals("пропустить"))return next();
  if(l.equals("покажи ответ")||l.equals("не знаем")||l.equals("сдаемся"))return reveal();
  if(mode==Mode.RULES){
   if(PartyGames.matches(l,"да|начинаем|готовы|поехали"))return next();
   return local("Начнём после слова «начинаем». Правила можно повторить, конкурс — отменить.");
  }
  if(mode==Mode.WAIT_NAME){
   if(l.equals("без имени")){mode=Mode.RESULT;return local("Оставляю балл в счёте раунда без записи гостю. Скажите «дальше».");}
   String name=t.replaceFirst("(?iu)^(это|я|ответил|ответила)\\s+","").replaceAll("[.!?,]$","").trim();
   if(name.length()<2||name.length()>45)return local("Назовите короткое имя или название команды.");
   scoreSink.add(name);
   mode=Mode.RESULT;return local(name+", один балл записан. Скажите «дальше».");
  }
  if(mode==Mode.RESULT)return local("Раунд завершён. Скажите «дальше» или «закончить игру».");
  if(mode==Mode.WAIT_ANSWER){
   if(round.judged)return local("Ответ оценивает организатор: нажмите «Засчитать» или скажите «дальше». Я не буду оценивать человека по камере.");
   String candidate=l.startsWith("ответ ")?l.substring(6):l;
   if(PartyGames.matches(candidate,round.answer))return award();
   if(game!=null&&(game.id.equals("melody")||game.id.equals("time_machine")))return local("Не совпало. Попробуйте ещё раз или скажите «покажи ответ».");
   if(!l.startsWith("ответ "))return local("Можете обсудить. Окончательный вариант начните со слова «ответ».");
   return local("Пока не совпало. Можно ещё раз, «покажи ответ» или «дальше».");
  }
  return local("Скажите «дальше».");
 }
}
