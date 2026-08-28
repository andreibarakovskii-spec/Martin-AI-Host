package com.imagine.martinhost;

import android.content.Context;
import android.os.*;
import android.net.Uri;
import android.media.AudioManager;
import android.media.AudioDeviceInfo;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

/** Explicit, local-only diagnostics. No keys, camera images or network headers. */
public final class DiagnosticRecorder {
 private static DiagnosticRecorder instance;
 public static synchronized DiagnosticRecorder get(Context c){if(instance==null)instance=new DiagnosticRecorder(c.getApplicationContext());return instance;}
 private final Context app;private final ExecutorService io=Executors.newSingleThreadExecutor();
 private final Handler timer=new Handler(Looper.getMainLooper());private final AtomicInteger pending=new AtomicInteger();
 private final AtomicInteger dropped=new AtomicInteger();
 private final Runnable heartbeat=new Runnable(){public void run(){if(!active())return;snapshot();timer.postDelayed(this,2000);}};
 private volatile Session session;private String error="";private static final long LIMIT=64L*1024*1024;
 static final class Session {final File dir;final long origin=SystemClock.elapsedRealtime();final String id;long bytes;int micIndex,assetIndex;Wav mic;BufferedWriter log;Session(File d){dir=d;id=d.getName();}}
 private DiagnosticRecorder(Context c){app=c;}
 public boolean active(){return session!=null;}
 public String status(){return active()?"● Диагностика записывается (максимум 5 минут)":error.isEmpty()?"Диагностика выключена":error;}
 public synchronized void start(){if(session!=null)return;error="";dropped.set(0);File dir=new File(app.getFilesDir(),"diagnostics/session-"+System.currentTimeMillis());Session s=new Session(dir);session=s;
  io.execute(()->{try{if(!dir.mkdirs())throw new IOException("Directory");s.log=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(dir,"events.jsonl")),StandardCharsets.UTF_8));
   JSONObject m=new JSONObject().put("format",1).put("created_utc",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.ROOT).format(new Date())).put("device",Build.MANUFACTURER+" "+Build.MODEL).put("android",Build.VERSION.SDK_INT).put("app","0.9.2-local-voices").put("clock","elapsedRealtime milliseconds from session start").put("microphone","AudioRecord PCM after enabled device AEC/NS, not unprocessed hardware audio").put("playback","playback_start is AudioTrack.play call; playback_progress is consumed frames, not measured Bluetooth sound onset");
   try(FileWriter w=new FileWriter(new File(dir,"metadata.json"))){w.write(m.toString(2));}
   try(FileWriter w=new FileWriter(new File(dir,"README.txt"))){w.write("Private diagnostic recording. events.jsonl uses a monotonic ms timeline. mic-N.wav is everything AudioRecord returned, including while STT is gated; separate mic segments have start/end events. stt-N.wav is the exact audio submitted to Whisper. tts-N.wav is generated PCM (may be partly played if interrupted); playback events show actual consumption. Files contain voices and transcript text. device_snapshot reports available device types (not Bluetooth names/addresses); mic_health and playback_progress report actual routed types. Gate=false means speech is recorded but not sent to STT: automatic barge-in is not enabled. mic_health is sampled every 200 ms. No camera frames, API keys or headers are included. Do not post publicly. Delete after analysis.\n");}
   write(s,0,"session_start","");}catch(Exception e){fail(s);}});
  timer.removeCallbacksAndMessages(null);timer.postDelayed(this::stop,300000);timer.post(heartbeat);
 }
 public void snapshot(){if(!active())return;try{
  AudioManager am=(AudioManager)app.getSystemService(Context.AUDIO_SERVICE);
  StringBuilder routes=new StringBuilder();for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_ALL))routes.append("type=").append(d.getType()).append(",input=").append(d.isSource()).append(";");
  android.os.PowerManager power=(android.os.PowerManager)app.getSystemService(Context.POWER_SERVICE);
  Runtime rt=Runtime.getRuntime();event("device_snapshot","audio_mode="+am.getMode()+";media_volume="+am.getStreamVolume(AudioManager.STREAM_MUSIC)+"/"+am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)+";mic_muted="+am.isMicrophoneMute()+";devices="+routes+";thermal_status="+power.getCurrentThermalStatus()+";power_save="+power.isPowerSaveMode()+";java_heap_used="+(rt.totalMemory()-rt.freeMemory())+";native_heap="+Debug.getNativeHeapAllocatedSize()+";free_storage="+app.getFilesDir().getUsableSpace()+";pending="+pending.get()+";dropped_tasks="+dropped.get());
 }catch(Exception e){event("snapshot_error",e.getClass().getSimpleName());}}
 public synchronized void stop(){Session s=session;if(s==null)return;session=null;timer.removeCallbacksAndMessages(null);long t=elapsed(s);io.execute(()->{try{closeMic(s,t);write(s,t,"session_stop","dropped_tasks="+dropped.get());if(s.log!=null)s.log.close();}catch(Exception e){error="Не удалось завершить диагностическую запись";}});}
 public synchronized void event(String type,String value){Session s=session;if(s==null)return;long t=elapsed(s);if(pending.get()>150){dropped.incrementAndGet();return;}pending.incrementAndGet();io.execute(()->{try{write(s,t,type,redact(value));}finally{pending.decrementAndGet();}});}
 public synchronized void mic(short[] frame,int count){Session s=session;if(s==null)return;long t=elapsed(s);if(pending.get()>150){dropped.incrementAndGet();return;}byte[] pcm=new byte[count*2];for(int i=0;i<count;i++){pcm[2*i]=(byte)frame[i];pcm[2*i+1]=(byte)(frame[i]>>8);}pending.incrementAndGet();io.execute(()->{try{if(s.bytes+pcm.length>LIMIT){fail(s);return;}if(s.mic==null){String name="mic-"+(++s.micIndex)+".wav";s.mic=new Wav(new File(s.dir,name),16000);write(s,t,"mic_segment_start",name+"; first read completed; frame_ms="+(count/16));}s.mic.add(pcm);s.bytes+=pcm.length;}catch(Exception e){fail(s);}finally{pending.decrementAndGet();}});}
 public synchronized void endMic(){Session s=session;if(s==null)return;long t=elapsed(s);io.execute(()->{try{closeMic(s,t);}catch(Exception e){fail(s);}});}
 public synchronized String audio(String kind,byte[] data,int rate,boolean isWav){Session s=session;if(s==null)return "";String name=kind+"-"+(++s.assetIndex)+".wav";long t=elapsed(s);if(pending.get()>150){dropped.incrementAndGet();return "";}byte[] copy=data.clone();pending.incrementAndGet();io.execute(()->{try{if(s.bytes+copy.length>LIMIT){fail(s);return;}if(isWav){try(FileOutputStream f=new FileOutputStream(new File(s.dir,name))){f.write(copy);}}else{Wav w=new Wav(new File(s.dir,name),rate);w.add(copy);w.close();}s.bytes+=copy.length;write(s,t,kind+"_audio",name+"; rate="+rate+"; bytes="+copy.length);}catch(Exception e){fail(s);}finally{pending.decrementAndGet();}});return name;}
 private void closeMic(Session s,long t)throws IOException{if(s.mic!=null){s.mic.close();s.mic=null;write(s,t,"mic_segment_end","");}}
 private void write(Session s,long t,String type,String value){try{if(s.log!=null){s.log.write(new JSONObject().put("ms",t).put("event",type).put("detail",value).toString());s.log.newLine();s.log.flush();}}catch(Exception e){error="Ошибка записи лога";}}
 private void fail(Session s){error="Диагностика остановлена: лимит 64 МБ или ошибка записи";synchronized(this){if(session==s)stop();}}
 private long elapsed(Session s){return SystemClock.elapsedRealtime()-s.origin;}
 static String redact(String s){if(s==null)return "";return s.replaceAll("(?i)(?:gsk_|xai-|sk-)[A-Za-z0-9_-]+","[KEY REDACTED]").replaceAll("(?i)Bearer\\s+[^\\s]+","Bearer [REDACTED]");}
 public void export(Uri uri,java.util.function.Consumer<String> callback){stop();io.execute(()->{try{File root=new File(app.getFilesDir(),"diagnostics");File[] dirs=root.listFiles(File::isDirectory);if(dirs==null||dirs.length==0)throw new IOException("Сначала запишите тест");Arrays.sort(dirs,Comparator.comparing(File::getName));File dir=dirs[dirs.length-1];try(OutputStream out=app.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Нет доступа к файлу");try(ZipOutputStream zip=new ZipOutputStream(out)){File[] files=dir.listFiles();if(files!=null)for(File f:files){if(!f.isFile())continue;zip.putNextEntry(new ZipEntry(f.getName()));try(InputStream in=new FileInputStream(f)){in.transferTo(zip);}zip.closeEntry();}}}callback.accept("ZIP сохранён. В нём голоса и текст — отправляйте только для диагностики.");}catch(Exception e){callback.accept("Экспорт не удался: "+e.getMessage());}});}
 public void delete(java.util.function.Consumer<String> cb){stop();io.execute(()->{File root=new File(app.getFilesDir(),"diagnostics");boolean ok=remove(root);cb.accept(ok?"Все диагностические записи удалены":"Не удалось удалить часть файлов");});}
 private boolean remove(File f){boolean ok=true;File[] a=f.listFiles();if(a!=null)for(File x:a)ok=remove(x)&&ok;return (!f.exists()||f.delete())&&ok;}
 static final class Wav {final RandomAccessFile f;long n;final int rate;Wav(File path,int r)throws IOException{rate=r;f=new RandomAccessFile(path,"rw");f.setLength(0);f.write(new byte[44]);}void add(byte[] p)throws IOException{f.write(p);n+=p.length;}void le(int x,int bytes)throws IOException{for(int i=0;i<bytes;i++)f.write(x>>(8*i));}void close()throws IOException{f.seek(0);f.writeBytes("RIFF");le((int)n+36,4);f.writeBytes("WAVEfmt ");le(16,4);le(1,2);le(1,2);le(rate,4);le(rate*2,4);le(2,2);le(16,2);f.writeBytes("data");le((int)n,4);f.close();}}
}
