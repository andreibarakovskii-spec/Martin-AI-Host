package com.imagine.martinhost;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
/** Real JNI/model smoke test. Emulator timing is not a handset benchmark. */
@RunWith(AndroidJUnit4.class)
public final class FastVoiceSmokeTest {
 @Test public void testNativeSynthesis()throws Exception{
  File dir=FastVoiceModel.ensure(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),()->false,s->Log.i("FastVoiceSmoke",s));
  // Check actual downloaded vocabulary, not just a Java string transformation.
  byte[] index=java.nio.file.Files.readAllBytes(new File(dir,"unicode_indexer.bin").toPath());
  java.nio.ByteBuffer ids=java.nio.ByteBuffer.wrap(index).order(java.nio.ByteOrder.LITTLE_ENDIAN);
  String normalized=FastVoiceEngine.normalizeText("Андрей, с днём рождения!");
  for(char ch:normalized.toCharArray())assertTrue("Unknown normalized character "+(int)ch,ids.getInt(ch*4)>=0);
  Log.i("FastVoiceSmoke","Russian normalized vocabulary verified; raw_yo="+ids.getInt('ё'*4)+" raw_short_i="+ids.getInt('й'*4));
  try(FastVoiceEngine e=new FastVoiceEngine(dir)){
   long t=SystemClock.elapsedRealtime();FastVoiceEngine.Pcm a=e.synthesize("Андрей, с днём рождения!","F5",1f,()->false);
   Log.i("FastVoiceSmoke","F5 synthesis_ms="+(SystemClock.elapsedRealtime()-t)+" samples="+a.pcm16.length/2);
   assertTrue(a.sampleRate>0);assertTrue(a.pcm16.length>1000);
   t=SystemClock.elapsedRealtime();FastVoiceEngine.Pcm b=e.synthesize("Как настроение?","M1",1.1f,()->false);
   Log.i("FastVoiceSmoke","M1 speed=1.1 synthesis_ms="+(SystemClock.elapsedRealtime()-t)+" samples="+b.pcm16.length/2);
   assertTrue(b.pcm16.length>1000);
  }
 }
}
