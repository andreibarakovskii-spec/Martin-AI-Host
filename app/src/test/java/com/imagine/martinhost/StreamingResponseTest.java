package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public class StreamingResponseTest {
 @Test public void speaksFirstFinishedSentenceEarly(){
  String partial="Эпизодическая память хранит конкретные события. Она связана";
  assertEquals("Эпизодическая память хранит конкретные события.",CompanionActivity.firstSpeakablePrefix(partial));
 }
 @Test public void boundedPrefixStartsBeforePunctuationWhenModelRambles(){
  String partial="Это достаточно длинное начало ответа которое уже можно начать озвучивать не ожидая завершения всего ответа модели";
  String p=CompanionActivity.firstSpeakablePrefix(partial);
  assertFalse(p.isBlank());
  assertTrue(p.length()<=56);
 }
 @Test public void remainderDoesNotRepeatEarlySpeech(){
  String first="Эпизодическая память хранит конкретные события.";
  String full=first+" Она связана со временем и местом.";
  assertEquals("Она связана со временем и местом.",CompanionActivity.remainderAfterPrefix(full,first));
 }
}
