package com.imagine.martinhost;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;import org.junit.runner.RunWith;import static org.junit.Assert.*;
import android.os.SystemClock;import android.util.Log;import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;import java.util.concurrent.atomic.AtomicLong;
/** Real JNI/model smoke test. Emulator timing is not a handset benchmark. */
@RunWith(AndroidJUnit4.class)
public final class FastVoiceSmokeTest {
 @Test public void testNativeSynthesis()throws Exception{
  File dir=FastVoiceModel.ensure(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),()->false,s->Log.i("FastVoiceSmoke",s));
  assertTrue(new File(dir,FastVoiceModel.MODEL).isFile());assertTrue(new File(dir,"tokens.txt").isFile());assertTrue(new File(dir,"espeak-ng-data").isDirectory());
  try(FastVoiceEngine e=new FastVoiceEngine(dir)){
   long t=SystemClock.elapsedRealtime();FastVoiceEngine.Pcm a=e.synthesize("Андрей, с днём рождения!","M1",1f,()->false);
   Log.i("FastVoiceSmoke","Piper full_synthesis_ms="+(SystemClock.elapsedRealtime()-t)+" samples="+a.pcm16.length/2+" rate="+a.sampleRate);
   assertTrue(a.sampleRate>0);assertTrue(a.pcm16.length>1000);
   AtomicInteger callbacks=new AtomicInteger();AtomicLong bytes=new AtomicLong();AtomicLong first=new AtomicLong(-1);final long start=SystemClock.elapsedRealtime();
   int rate=e.synthesizeStreaming("Как настроение?","M1",1f,()->false,pcm->{first.compareAndSet(-1,SystemClock.elapsedRealtime()-start);callbacks.incrementAndGet();bytes.addAndGet(pcm.length);return true;});
   long total=SystemClock.elapsedRealtime()-start;
   Log.i("FastVoiceSmoke","Piper stream_first_callback_ms="+first.get()+" streaming_ms="+total+" callbacks="+callbacks.get()+" bytes="+bytes.get()+" rate="+rate);
   assertTrue(rate>0);assertTrue("No streaming callbacks",callbacks.get()>0);assertTrue("No streamed PCM",bytes.get()>1000);assertTrue("No first callback timing",first.get()>=0);
  }
 }
}
