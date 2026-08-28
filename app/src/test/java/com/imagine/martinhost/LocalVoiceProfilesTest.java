package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.nio.file.*;import java.util.*;
public class LocalVoiceProfilesTest {
 @Test public void selectedStyleUsesSharedWeightsWithoutChangingOriginal()throws Exception{
  Path root=Files.createTempDirectory("voice-profiles");try{
   File models=root.resolve("models").toFile();new File(models,"voice_styles").mkdirs();
   for(String name:LocalVoiceProfiles.MODEL_FILES)Files.writeString(new File(models,name).toPath(),"weights "+name);
   for(String id:LocalVoiceProfiles.IDS)Files.writeString(new File(models,"voice_styles/"+id+".json").toPath(),"style "+id);
   for(String id:LocalVoiceProfiles.IDS){File p=LocalVoiceProfiles.prepare(models,root.toFile(),id);assertEquals("style "+id,Files.readString(new File(p,"voice_styles/F1.json").toPath()));assertTrue(Files.isSameFile(new File(models,"vocoder.tflite").toPath(),new File(p,"vocoder.tflite").toPath()));assertEquals(p,LocalVoiceProfiles.prepare(models,root.toFile(),id));}
   assertEquals("style F1",Files.readString(new File(models,"voice_styles/F1.json").toPath()));
   assertEquals("M1",LocalVoiceProfiles.valid("../../bad"));
  }finally{try(var paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
 }
 @Test public void missingModelFailsInsteadOfSilentlyUsingAnotherVoice()throws Exception{Path root=Files.createTempDirectory("voice-missing");try{try{LocalVoiceProfiles.prepare(root.toFile(),root.toFile(),"M3");fail("missing model should fail");}catch(IOException expected){assertTrue(expected.getMessage().contains("модели"));}}finally{try(var paths=Files.walk(root)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}}
}
