package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ImaProsodyPlannerTest {
 @Test public void empathyIsSlowerAndMorePausedThanExcited(){
  ImaProsodyPlanner.Plan empathy=ImaProsodyPlanner.plan("Я рядом.","empathetic",.5f);
  ImaProsodyPlanner.Plan excited=ImaProsodyPlanner.plan("Отлично!","excited",.8f);
  assertTrue(empathy.speed<excited.speed);
  assertTrue(empathy.sentencePauseMs>excited.sentencePauseMs);
  assertTrue(empathy.silenceScale>excited.silenceScale);
  assertTrue(empathy.warmth>excited.warmth);
  assertTrue(excited.expressiveness>empathy.expressiveness);
 }

 @Test public void neutralQuestionBecomesCurious(){
  ImaProsodyPlanner.Plan p=ImaProsodyPlanner.plan("Как ты себя чувствуешь?","neutral",.5f);
  assertEquals("curious",p.style);
  assertEquals("rising",p.finalContour);
 }

 @Test public void neutralTextCanInferEmotionWithoutChangingSpeakerIdentity(){
  ImaProsodyPlanner.Plan warm=ImaProsodyPlanner.plan("Конечно, давай попробуем вместе.","neutral",.55f);
  ImaProsodyPlanner.Plan firm=ImaProsodyPlanner.plan("Важно сначала проверить результат.","neutral",.55f);
  assertEquals("warm",warm.style);
  assertEquals("confident",firm.style);
  assertTrue(warm.warmth>firm.warmth);
  assertTrue(firm.firmness>warm.firmness);
 }

 @Test public void suspendedPhraseGetsSuspendedContour(){
  ImaProsodyPlanner.Plan p=ImaProsodyPlanner.plan("Я бы добавила еще и...","neutral",.45f);
  assertEquals("suspended",p.finalContour);
  assertTrue(p.sentencePauseMs>=145);
 }

 @Test public void pcmGainClampsInsteadOfOverflowing(){
  byte[] max={(byte)0xff,(byte)0x7f,(byte)0x00,(byte)0x80};
  byte[] out=ImaProsodyPlanner.applyGain(max,1.12f);
  int positive=(short)(((out[1]&255)<<8)|(out[0]&255));
  int negative=(short)(((out[3]&255)<<8)|(out[2]&255));
  assertEquals(32767,positive);assertEquals(-32768,negative);
 }
}
