package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.util.*;
public class FastVoiceTest {
 @Test public void nativeCallbackHasExactJniSignatureAndHonorsCancellation()throws Exception{
  assertEquals(Integer.class,FastVoiceEngine.NativeCallback.class.getMethod("invoke",float[].class).getReturnType());
  java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean(false);
  FastVoiceEngine.NativeCallback callback=new FastVoiceEngine.NativeCallback(cancelled::get);
  assertEquals(Integer.valueOf(1),callback.invoke(new float[0]));cancelled.set(true);
  assertEquals(Integer.valueOf(0),callback.invoke(new float[0]));
 }
 @Test public void russianLettersReachModelInReferenceNormalization(){
  assertEquals("Андреи\u0306, е\u0308лка и и\u0306огурт.",FastVoiceEngine.normalizeText("Андрей, ёлка и йогурт."));
  String text=FastVoiceEngine.normalizeText("С днём рождения! Йога, ёж.");
  assertEquals(text,FastVoiceEngine.normalizeText(text));
 }
 @Test public void shortReplyKeepsNaturalSentenceChunks(){
  String text="Привет, Андрей! Как настроение?";
  List<String> parts=SpeechChunks.split(text);
  assertEquals(Arrays.asList("Привет, Андрей!","Как настроение?"),parts);
  assertEquals(text,String.join(" ",parts));
 }
 @Test public void firstLongChunkIsLatencyBoundedWithoutDroppingText(){
  String text="Эпизодическая память хранит события нашей жизни, связанные с конкретным временем, местом и личным контекстом.";
  List<String> parts=SpeechChunks.split(text);
  assertTrue(parts.size()>1);
  assertTrue(parts.get(0).length()<=56);
  assertEquals(text,String.join(" ",parts));
 }
 @Test public void voiceIdsFollowUpstreamSortedStyles(){
  for(int i=1;i<=5;i++){assertEquals(i-1,FastVoiceEngine.speakerId("F"+i));assertEquals(i+4,FastVoiceEngine.speakerId("M"+i));}
  assertEquals(5,FastVoiceEngine.speakerId("invalid"));
 }
 @Test public void chunksPreserveAllWords(){
  String text="Привет! "+String.join(" ",Collections.nCopies(90,"именинница"))+". С праздником!";
  List<String> parts=SpeechChunks.split(text);assertEquals(text,String.join(" ",parts));assertEquals("Привет!",parts.get(0));for(String p:parts)assertTrue(p.length()<=110);
  assertTrue(SpeechChunks.split("  ").isEmpty());
 }
 @Test public void rejectArchiveTraversal()throws Exception{
  File root=new File(System.getProperty("java.io.tmpdir"),"voice-stage");
  assertEquals(new File(root,"model/voice.bin").getCanonicalFile(),FastVoiceModel.safeTarget(root,"model/voice.bin").getCanonicalFile());
  try{FastVoiceModel.safeTarget(root,"../escape");fail("Traversal accepted");}catch(IOException expected){}
 }
}
