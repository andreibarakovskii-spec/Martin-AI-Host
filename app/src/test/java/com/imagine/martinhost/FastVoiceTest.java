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
 @Test public void streamingPcmConversionClampsAndKeepsLittleEndian(){
  byte[] pcm=FastVoiceEngine.toPcm16(new float[]{-2f,-1f,0f,1f,2f});
  assertEquals(10,pcm.length);assertEquals((byte)0x01,pcm[0]);assertEquals((byte)0x80,pcm[1]);
  assertEquals((byte)0x00,pcm[4]);assertEquals((byte)0x00,pcm[5]);assertEquals((byte)0xFF,pcm[6]);assertEquals((byte)0x7F,pcm[7]);
  assertEquals(pcm[6],pcm[8]);assertEquals(pcm[7],pcm[9]);
 }
 @Test public void russianNormalizationPreservesYoAndShortI(){
  String input="Андрей, ёлка и йогурт.";
  assertEquals(input,FastVoiceEngine.normalizeText(input));
  assertEquals(input,FastVoiceEngine.normalizeText(FastVoiceEngine.normalizeText(input)));
 }
 @Test public void piperUsesSingleConsistentVoice(){
  assertEquals(0,FastVoiceEngine.speakerId("M1"));assertEquals(0,FastVoiceEngine.speakerId("F5"));assertEquals(0,FastVoiceEngine.speakerId("anything"));
 }
 @Test public void shortReplyKeepsNaturalSentenceChunks(){
  String text="Привет, Андрей! Как настроение?";List<String> parts=SpeechChunks.split(text);
  assertEquals(Arrays.asList("Привет, Андрей!","Как настроение?"),parts);assertEquals(text,String.join(" ",parts));
 }
 @Test public void firstLongChunkIsLatencyBoundedWithoutDroppingText(){
  String text="Эпизодическая память хранит события нашей жизни, связанные с конкретным временем, местом и личным контекстом.";List<String> parts=SpeechChunks.split(text);
  assertTrue(parts.size()>1);assertTrue(parts.get(0).length()<=56);assertEquals(text,String.join(" ",parts));
 }
 @Test public void chunksPreserveAllWords(){
  String text="Привет! "+String.join(" ",Collections.nCopies(90,"именинница"))+". С праздником!";
  List<String> parts=SpeechChunks.split(text);assertEquals(text,String.join(" ",parts));assertEquals("Привет!",parts.get(0));for(String p:parts)assertTrue(p.length()<=110);assertTrue(SpeechChunks.split("  ").isEmpty());
 }
 @Test public void rejectArchiveTraversal()throws Exception{
  File root=new File(System.getProperty("java.io.tmpdir"),"voice-stage");
  assertEquals(new File(root,"model/voice.bin").getCanonicalFile(),FastVoiceModel.safeTarget(root,"model/voice.bin").getCanonicalFile());
  try{FastVoiceModel.safeTarget(root,"../escape");fail("Traversal accepted");}catch(IOException expected){}
 }
}
