package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.nio.*;import java.nio.file.*;
public class DiagnosticRecorderTest {
 @Test public void wavHeaderMatchesPcm()throws Exception{File f=File.createTempFile("diag-test",".wav");try{DiagnosticRecorder.Wav w=new DiagnosticRecorder.Wav(f,16000);w.add(new byte[]{1,2,3,4});w.close();byte[] a=Files.readAllBytes(f.toPath());ByteBuffer b=ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN);assertEquals(48,a.length);assertEquals(16000,b.getInt(24));assertEquals(4,b.getInt(40));assertEquals(40,b.getInt(4));assertEquals(1,a[44]);}finally{f.delete();}}
 @Test public void secretsAreRemoved(){String s=DiagnosticRecorder.redact("gsk_123ABC xai-secret123 sk-proj-private Bearer otherCredential");assertFalse(s.contains("123ABC"));assertFalse(s.contains("secret123"));assertFalse(s.contains("private"));assertFalse(s.contains("otherCredential"));assertEquals("Привет",DiagnosticRecorder.redact("Привет"));}
}
