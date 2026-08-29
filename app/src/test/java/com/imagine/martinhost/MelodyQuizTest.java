package com.imagine.martinhost;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class MelodyQuizTest {
 @Test public void hasEighteenProgressiveUniqueRounds(){
  List<MelodyQuiz.Track> rounds=MelodyQuiz.partyRounds(new Random(7));
  assertEquals(18,rounds.size());
  Set<String> queries=new HashSet<>();
  for(int i=0;i<rounds.size();i++){
   MelodyQuiz.Track t=rounds.get(i);
   assertEquals(i<6?1:i<12?2:3,t.tier);
   assertFalse(t.query().isBlank());
   assertFalse(t.answerPattern().isBlank());
   assertTrue(queries.add(t.query()));
  }
 }

 @Test public void directorUsesYandexCueAndCanRepeatSameFragment(){
  PartyDirector d=new PartyDirector(name->{});
  d.startGame("melody");
  assertEquals(PartyDirector.Mode.RULES,d.mode());
  d.onUserText("начинаем");
  assertEquals(PartyDirector.Mode.WAIT_ANSWER,d.mode());
  String first=d.takeMusicUri();
  assertNotNull(first);
  assertTrue(first.startsWith("yandex:"));
  d.onUserText("ещё раз");
  assertEquals(first,d.takeMusicUri());
 }

 @Test public void melodyEndsAfterEighteenRoundsWithoutManualStateLeaks(){
  PartyDirector d=new PartyDirector(name->{});
  d.startGame("melody");
  d.onUserText("начинаем");
  for(int i=1;i<18;i++){
   assertEquals(PartyDirector.Mode.WAIT_ANSWER,d.mode());
   d.onUserText("дальше");
  }
  assertEquals(PartyDirector.Mode.WAIT_ANSWER,d.mode());
  d.onUserText("дальше");
  assertEquals(PartyDirector.Mode.FREE,d.mode());
 }
}
