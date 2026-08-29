package com.imagine.martinhost;

import android.content.Context;
import java.util.*;

/** Explicit round/confirmation/scoring states; free chat cannot award points. */
public final class PartyDirector {
 public enum Mode { FREE, OFFER, RULES, WAIT_ANSWER, WAIT_NAME, RESULT }
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
 private String offeredGame="";
 private String currentMelodyQuery="";
 private String currentMelodyAnswer="";
 private final List<PartyMusic.Track> musicRounds=new ArrayList<>();
 private final List<MelodyQuiz.Track> melodyRounds=new ArrayList<>();

 public PartyDirector(Context c){context=c.getApplicationContext();guests=new GuestStore(c);scoreSink=name->{if(!guests.addScore(name,1)){List<GuestStore.Guest> all=guests.load();GuestStore.Guest g=new GuestStore.Guest();g.name=name;g.callName=name;g.score=1;g.participated=1;all.add(g);guests.save(all);}};}
 PartyDirector(ScoreSink sink){context=null;guests=null;scoreSink=sink;}
 public Mode mode(){return mode;}
 public String summary(){return game==null?"Свободный диалог":game.title+" • раунд "+Math.max(0,index+1)+" • верных: "+score;}
 public String takeMusicUri(){String s=musicUri;musicUri=null;return s;}
 public void setSuggestedGuest(String name){suggestedGuest=name==null?"":name.trim();}
 private Action local(String s){return Action.local(s,"game","","curious");}
 private boolean isMelody(){return game!=null&&game.id.equals("melody");}
 private Action melodyAdvance(String prefix){Action n=next();return local(prefix+" "+n.speech);}

 public Action startChgk(){return startGame("chgk");}
 public Action offerGame(String id){offeredGame=id;mode=Mode.OFFER;PartyGames.Game g=PartyGames.get(id);return local("Друзья, небольшая игровая пауза! Предлагаю «"+g.title+"». Играем?");}

 public Action startGame(String id){
  game=PartyGames.get(id);index=-1;score=0;round=null;musicUri=null;musicRounds.clear();melodyRounds.clear();currentMelodyQuery="";currentMelodyAnswer="";
  if(id.equals("melody")){
   melodyRounds.addAll(MelodyQuiz.partyRounds());
  }else if(id.equals("time_machine")){
   for(PartyMusic.Track t:PartyMusic.get(context).tracks())if(t.year>0)musicRounds.add(t);
   if(musicRounds.isEmpty()){mode=Mode.FREE;return local("Сначала добавьте аудиофайлы в разделе «Музыка» и укажите год в имени файла или через кнопку «Данные».");}
   Collections.shuffle(musicRounds);
  }
  mode=Mode.RULES;
  if(id.equals("melody"))return local("Игра «Угадай мелодию». Будет 18 раундов: шесть простых, шесть средних и шесть посложнее. Я включаю примерно шесть секунд из Яндекс Музыки. Назовите песню или исполнителя — это один балл. Можно сказать «ещё раз», чтобы повторить тот же фрагмент. После правильного ответа я сам перейду дальше. Пример: если узнали «Крошка моя», можно назвать песню или «Руки Вверх». Правила понятны? Начинаем?");
  return local("Игра «"+game.title+"». "+game.rules+" Пример: "+game.example+" Правила понятны? Начинаем?");
 }

 public Action cancel(){mode=Mode.FREE;game=null;musicUri=null;currentMelodyQuery="";currentMelodyAnswer="";return local("Конкурс остановлен. Возвращаемся к разговору.");}

 public Action next(){
  if(game==null)return local("Сначала выберите игру.");
  index++;
  int count=isMelody()?melodyRounds.size():(musicRounds.isEmpty()?game.rounds.size():Math.min(6,musicRounds.size()));
  if(index>=count){String s="Игра закончена! Правильных ответов: "+score+". Баллы гостей сохранены. Можно выбрать следующий конкурс.";mode=Mode.FREE;game=null;musicUri=null;currentMelodyQuery="";currentMelodyAnswer="";return local(s);}
  mode=Mode.WAIT_ANSWER;
  if(isMelody()){
   MelodyQuiz.Track t=melodyRounds.get(index);
   currentMelodyQuery=t.query();currentMelodyAnswer=t.label();
   musicUri="yandex:"+currentMelodyQuery;
   round=new PartyGames.Round("Слушаем фрагмент. Назовите песню или исполнителя.",t.answerPattern(),false);
  }else if(!musicRounds.isEmpty()){
   PartyMusic.Track t=musicRounds.get(index);musicUri=t.uri;
   String a=t.year+"|"+decade(t.year);
   round=new PartyGames.Round("Слушаем фрагмент. После музыки назовите год или десятилетие.",a,false);
  }else round=game.rounds.get(index);
  return local("Раунд "+(index+1)+". "+round.question);
 }

 private String decade(int y){if(y>=1990&&y<2000)return "девяностые|90";if(y>=2000&&y<2010)return "нулевые|двухтысячные|2000";if(y>=2010&&y<2020)return "десятые|2010";if(y>=2020&&y<2030)return "двадцатые|2020";return "";}

 public Action award(){
  if(mode!=Mode.WAIT_ANSWER)return local("Баллы можно начислить после задания.");
  score++;
  if(!suggestedGuest.isBlank()){
   scoreSink.add(suggestedGuest);mode=Mode.RESULT;String name=suggestedGuest;suggestedGuest="";
   if(isMelody())return melodyAdvance("Верно, "+name+". Один балл записан. Дальше.");
   return local("Верно, "+name+"! Один балл записан. Скажите «дальше».");
  }
  mode=Mode.WAIT_NAME;return local("Засчитано! Я не уверен, кто ответил. Назовите имя гостя или команды для балла. Либо скажите «без имени».");
 }

 public Action reveal(){
  if(round==null)return local("Сначала начните раунд.");
  String answer=isMelody()?currentMelodyAnswer:(round.judged?(round.answer.isBlank()?"Этот конкурс оценивает организатор.":round.answer):round.answer.split("\\|")[0]);
  mode=Mode.RESULT;
  if(isMelody())return melodyAdvance("Ответ: "+answer+". Балл не начисляю. Следующий.");
  return local((round.judged&&round.answer.isBlank()?answer:"Ответ: "+answer+".")+" Скажите «дальше».");
 }

 public Action onUserText(String raw){
  String t=raw==null?"":raw.trim(),l=PartyGames.normal(t);
  if(l.equals("закончить игру")||l.equals("стоп игра")||l.equals("отмена конкурса"))return cancel();

  if(mode==Mode.FREE){
   if(l.contains("музыкальн")&&(l.contains("виктор")||l.contains("игр"))||l.contains("угадай песню")||l.contains("угадай музыку")||l.contains("угадай мелодию"))return startGame("melody");
   if(l.contains("машин")&&l.contains("времен")&&l.contains("музык"))return startGame("time_machine");
   for(PartyGames.Game g:PartyGames.all())if(l.contains(PartyGames.normal(g.title.split(" — ")[0])))return startGame(g.id);
   if(l.contains("чгк")||l.equals("начни игру"))return startChgk();
   if(l.contains("тост"))return Action.ai("Скажи короткий тёплый тост для Кати и гостей без принуждения к алкоголю.","toast","","warm");
   return Action.ai(t,"talking","","neutral");
  }

  if(mode==Mode.OFFER){
   if(PartyGames.matches(l,"да|играем|давай|поехали|согласны")){String id=offeredGame;offeredGame="";return startGame(id);}
   if(PartyGames.matches(l,"нет|не сейчас|потом|отмена")){offeredGame="";mode=Mode.FREE;return local("Хорошо, продолжаем музыку. Предложу игру позже.");}
   return local("Сыграем? Скажите «да» или «не сейчас».");
  }

  if(l.equals("правила")||l.equals("повтори правила")){
   if(isMelody())return local("Восемнадцать раундов. Слушаем шесть секунд. Назовите песню или исполнителя. Один балл. «Ещё раз» повторяет фрагмент. Когда готовы, скажите «начинаем».");
   return local(game.rules+" Когда готовы, скажите «начинаем».");
  }

  if(mode==Mode.WAIT_ANSWER&&isMelody()&&PartyGames.matches(l,"еще раз|ещё раз|повтори|повтори фрагмент|повтори песню")){
   musicUri="yandex:"+currentMelodyQuery;
   return local("Повторяю тот же фрагмент.");
  }

  if(l.equals("дальше")||l.equals("следующий")||l.equals("пропустить"))return next();
  if(l.equals("покажи ответ")||l.equals("не знаем")||l.equals("сдаемся")||l.equals("сдаёмся"))return reveal();

  if(mode==Mode.RULES){
   if(PartyGames.matches(l,"да|начинаем|готовы|поехали"))return next();
   return local("Начнём после слова «начинаем». Правила можно повторить, конкурс — отменить.");
  }

  if(mode==Mode.WAIT_NAME){
   boolean melody=isMelody();
   if(l.equals("без имени")){
    mode=Mode.RESULT;
    if(melody)return melodyAdvance("Оставляю балл в счёте раунда без имени. Дальше.");
    return local("Оставляю балл в счёте раунда без записи гостю. Скажите «дальше».");
   }
   String name=t.replaceFirst("(?iu)^(это|я|ответил|ответила)\\s+","").replaceAll("[.!?,]$","").trim();
   if(name.length()<2||name.length()>45)return local("Назовите короткое имя или название команды.");
   scoreSink.add(name);mode=Mode.RESULT;
   if(melody)return melodyAdvance(name+", один балл записан. Следующий раунд.");
   return local(name+", один балл записан. Скажите «дальше».");
  }

  if(mode==Mode.RESULT)return local("Раунд завершён. Скажите «дальше» или «закончить игру».");

  if(mode==Mode.WAIT_ANSWER){
   if(round.judged)return local("Ответ оценивает организатор: нажмите «Засчитать» или скажите «дальше». Я не буду оценивать человека по камере.");
   boolean musicGame=game!=null&&(game.id.equals("melody")||game.id.equals("time_machine"));
   if(!l.startsWith("ответ ")&&!musicGame)return local("Можете обсудить. Окончательный вариант начните со слова «ответ».");
   String candidate=l.startsWith("ответ ")?l.substring(6):l;
   if(PartyGames.matches(candidate,round.answer))return award();
   if(isMelody())return local("Не совпало. Ещё версия, «ещё раз» для повтора или «покажи ответ».");
   if(musicGame)return local("Не совпало. Попробуйте ещё раз или скажите «покажи ответ».");
   return local("Пока не совпало. Можно ещё раз, «покажи ответ» или «дальше».");
  }
  return local("Скажите «дальше».");
 }
}
