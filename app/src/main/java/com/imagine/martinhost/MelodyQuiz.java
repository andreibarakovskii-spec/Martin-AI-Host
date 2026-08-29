package com.imagine.martinhost;

import java.util.*;

/** Curated party-safe melody rounds. Only metadata/search terms are stored; audio comes from Yandex Music. */
final class MelodyQuiz {
 static final class Track {
  final String artist,title,answers; final int tier;
  Track(int tier,String artist,String title,String answers){this.tier=tier;this.artist=artist;this.title=title;this.answers=answers;}
  String query(){return artist+" "+title;}
  String answerPattern(){return answers;}
  String label(){return artist+" — "+title;}
 }

 private static Track t(int tier,String artist,String title,String answers){return new Track(tier,artist,title,answers);}

 private static final List<Track> ALL=Collections.unmodifiableList(Arrays.asList(
  t(1,"Руки Вверх","Крошка моя","крошка моя|руки вверх|сергей жуков"),
  t(1,"МакSим","Знаешь ли ты","знаешь ли ты|максим|максимова"),
  t(1,"Звери","Районы-кварталы","районы кварталы|районы-кварталы|звери|рома зверь"),
  t(1,"t.A.T.u.","Нас не догонят","нас не догонят|тату|tatu"),
  t(1,"Иванушки International","Тучи","тучи|иванушки|иванушки international"),
  t(1,"Демо","Солнышко","солнышко|демо|demo"),

  t(2,"Hi-Fi","Седьмой лепесток","седьмой лепесток|7 лепесток|хай фай|hi fi|hifi"),
  t(2,"Мумий Тролль","Владивосток 2000","владивосток 2000|владивосток две тысячи|мумий тролль"),
  t(2,"Земфира","Искала","искала|земфира"),
  t(2,"Би-2","Полковнику никто не пишет","полковнику никто не пишет|би 2|би2"),
  t(2,"Бумбокс","Вахтёрам","вахтерам|вахтёрам|бумбокс"),
  t(2,"Ленинград","Экспонат","экспонат|лабутены|ленинград|шнуров"),

  t(3,"The Weeknd","Blinding Lights","blinding lights|блайндинг лайтс|weeknd|уикенд"),
  t(3,"Dua Lipa","Levitating","levitating|левитейтинг|dua lipa|дуа липа"),
  t(3,"Imagine Dragons","Believer","believer|биливер|imagine dragons|имэджин дрэгонс"),
  t(3,"Måneskin","Beggin'","beggin|беггин|maneskin|манескин"),
  t(3,"Lady Gaga Bruno Mars","Die With A Smile","die with a smile|дай виз э смайл|lady gaga|леди гага|bruno mars|бруно марс"),
  t(3,"Sabrina Carpenter","Espresso","espresso|эспрессо|sabrina carpenter|сабрина карпентер")
 ));

 static List<Track> partyRounds(){return partyRounds(new Random());}
 static List<Track> partyRounds(Random random){
  ArrayList<Track> out=new ArrayList<>(18);
  for(int tier=1;tier<=3;tier++){
   ArrayList<Track> block=new ArrayList<>();
   for(Track t:ALL)if(t.tier==tier)block.add(t);
   Collections.shuffle(block,random);
   out.addAll(block);
  }
  return out;
 }
 static List<Track> all(){return ALL;}
 private MelodyQuiz(){}
}
