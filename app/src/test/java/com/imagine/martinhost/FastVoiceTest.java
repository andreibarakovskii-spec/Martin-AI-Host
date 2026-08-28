package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.util.*;
public class FastVoiceTest {
 @Test public void voiceIdsFollowUpstreamSortedStyles(){
  for(int i=1;i<=5;i++){assertEquals(i-1,FastVoiceEngine.speakerId("F"+i));assertEquals(i+4,FastVoiceEngine.speakerId("M"+i));}
  assertEquals(5,FastVoiceEngine.speakerId("invalid"));
 }
 @Test public void chunksPreserveAllWords(){
  String text="Привет! "+String.join(" ",Collections.nCopies(90,"именинница"))+". С праздником!";
  List<String> parts=SpeechChunks.split(text);assertEquals(text,String.join(" ",parts));assertEquals("Привет!",parts.get(0));for(String p:parts)assertTrue(p.length()<=160);
  assertTrue(SpeechChunks.split("  ").isEmpty());
 }
 @Test public void rejectArchiveTraversal()throws Exception{
  File root=new File(System.getProperty("java.io.tmpdir"),"voice-stage");
  assertEquals(new File(root,"model/voice.bin").getCanonicalFile(),FastVoiceModel.safeTarget(root,"model/voice.bin").getCanonicalFile());
  try{FastVoiceModel.safeTarget(root,"../escape");fail("Traversal accepted");}catch(IOException expected){}
 }
}
