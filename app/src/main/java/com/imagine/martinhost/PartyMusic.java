package com.imagine.martinhost;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;

/** User-selected local audio plus short Yandex Music quiz fragments. */
public final class PartyMusic {
 public static final class Track { public String uri,title,artist=""; public int year; }
 public interface Listener {void onState(String status);}
 private static PartyMusic instance;
 public static synchronized PartyMusic get(Context c){if(instance==null)instance=new PartyMusic(c.getApplicationContext());return instance;}
 private final Context context;private MediaPlayer player;private final Handler handler=new Handler(Looper.getMainLooper());
 private final List<Track> tracks=new ArrayList<>(); private int index;private boolean ducked,clipMode,backgroundMode,yandexClip;private float requestedVolume=1f;private Runnable clipDone;private Listener listener;private String status="Музыка не запущена";
 private PartyMusic(Context c){context=c;try{JSONArray a=new JSONArray(c.getSharedPreferences("martin",0).getString("playlist","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Track t=new Track();t.uri=o.getString("uri");t.title=o.optString("title","Трек");t.artist=o.optString("artist","");t.year=o.optInt("year",0);tracks.add(t);}}catch(Exception ignored){}}
 public List<Track> tracks(){return new ArrayList<>(tracks);}
 public String status(){return status;}
 public void setListener(Listener l){listener=l;if(l!=null)l.onState(status);}
 private void emit(String s){status=s;if(listener!=null)listener.onState(s);}
 public void add(String uri,String filename){for(Track x:tracks)if(x.uri.equals(uri))return;Track t=new Track();t.uri=uri;t.title=filename.replaceFirst("\\.[^.]+$","");String[] parts=t.title.split("[—–]| - ",2);if(parts.length==2){t.artist=parts[0].trim();t.title=parts[1].trim();}java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b(19[5-9][0-9]|20[0-2][0-9])\\b").matcher(filename);if(m.find()){t.year=Integer.parseInt(m.group());t.title=t.title.replaceAll("[\\s(\\[]*"+t.year+"[)\\]]*"," ").replaceAll("\\s+"," ").trim();}tracks.add(t);save();}
 public void edit(int i,String title,String artist,int year){Track t=tracks.get(i);t.title=title.trim();t.artist=artist.trim();t.year=year;save();}
 private void save(){JSONArray a=new JSONArray();try{for(Track t:tracks)a.put(new JSONObject().put("uri",t.uri).put("title",t.title).put("artist",t.artist).put("year",t.year));context.getSharedPreferences("martin",0).edit().putString("playlist",a.toString()).apply();}catch(Exception ignored){}}
 public void clear(){stop();tracks.clear();save();}
 public void duck(boolean value){ducked=value;applyVolume();}
 public void listeningVolume(){requestedVolume=.28f;ducked=false;applyVolume();}
 private void applyVolume(){if(player!=null){float v=ducked?.08f:requestedVolume;try{player.setVolume(v,v);}catch(Exception ignored){}}}
 public boolean isPlaying(){try{return player!=null&&player.isPlaying();}catch(Exception e){return false;}}
 public boolean isBackgroundPlaying(){return backgroundMode&&isPlaying();}
 public void play(int i){if(tracks.isEmpty()){emit("Добавьте аудиофайлы");return;}index=(i+tracks.size())%tracks.size();requestedVolume=1f;Track t=tracks.get(index);open(t.uri,t.artist+" — "+t.title,0,null,false);}
 public void next(){play(index+1);}
 public void previous(){play(index-1);}
 public void toggle(){if(player==null){play(index);return;}if(isPlaying()){player.pause();emit("Пауза");}else{player.start();emit("Воспроизведение");}}
 public void ensureBackground(){if(tracks.isEmpty()||player!=null||!context.getSharedPreferences("martin",0).getBoolean("auto_music",true))return;requestedVolume=.28f;Track t=tracks.get(index%tracks.size());open(t.uri,"Фон: "+t.artist+" — "+t.title,0,null,true);}
 public void stopBackground(){if(backgroundMode)stop();}

 public void clip(String uri,Runnable done){
  if(uri!=null&&uri.startsWith("yandex:")){
   stop();clipDone=done;clipMode=true;yandexClip=true;backgroundMode=false;requestedVolume=1f;
   String query=uri.substring("yandex:".length()).trim();
   emit("Фрагмент из Яндекс Музыки");
   boolean started=YandexMusicPlayback.playFragment(query,6,()->{if(clipMode&&yandexClip)finishClip();});
   if(!started){emit("Яндекс Музыка не готова. Откройте раздел «Музыка» и войдите в аккаунт.");finishClip();}
   return;
  }
  requestedVolume=1f;open(uri,"Фрагмент для конкурса",6000,done,false);
 }

 private void open(String uri,String label,int duration,Runnable done,boolean background){
  stop();clipDone=done;clipMode=duration>0;backgroundMode=background;yandexClip=false;final MediaPlayer p=new MediaPlayer();player=p;
  try{p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());p.setDataSource(context,Uri.parse(uri));
   p.setOnPreparedListener(mp->{if(player!=mp)return;applyVolume();Runnable begin=()->{if(player!=mp)return;mp.start();emit(label);if(duration>0)handler.postDelayed(this::finishClip,duration);};if(duration>0&&mp.getDuration()>18000){mp.setOnSeekCompleteListener(x->begin.run());mp.seekTo(Math.min(mp.getDuration()-7000,Math.max(8000,mp.getDuration()/3)),MediaPlayer.SEEK_CLOSEST_SYNC);}else begin.run();});
   p.setOnCompletionListener(mp->{if(duration>0)finishClip();else if(backgroundMode)backgroundNext();else next();});
   p.setOnErrorListener((mp,w,e)->{emit("Не удалось открыть трек. Выберите файл заново.");finishClip();return true;});p.prepareAsync();
  }catch(Exception e){emit("Нет доступа к файлу. Выберите его заново.");finishClip();}
 }
 private void backgroundNext(){if(tracks.isEmpty()){stop();return;}index=(index+1)%tracks.size();requestedVolume=.28f;Track t=tracks.get(index);open(t.uri,"Фон: "+t.artist+" — "+t.title,0,null,true);}
 private void finishClip(){Runnable d=clipDone;clipDone=null;clipMode=false;yandexClip=false;release();if(d!=null)d.run();}
 private void release(){handler.removeCallbacksAndMessages(null);if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}}
 public void stopClip(){if(clipMode)stop();}
 public void stop(){boolean stopYandex=yandexClip;clipDone=null;clipMode=false;backgroundMode=false;yandexClip=false;if(stopYandex)YandexMusicPlayback.stop();release();emit("Музыка остановлена");}
}
