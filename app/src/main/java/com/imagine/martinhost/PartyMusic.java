package com.imagine.martinhost;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;

/** User-selected local audio; persists URI grants, never downloads copyrighted tracks. */
public final class PartyMusic {
 public static final class Track { public String uri,title,artist=""; public int year; }
 public interface Listener {void onState(String status);}
 private static PartyMusic instance;
 public static synchronized PartyMusic get(Context c){if(instance==null)instance=new PartyMusic(c.getApplicationContext());return instance;}
 private final Context context;private MediaPlayer player;private final Handler handler=new Handler(Looper.getMainLooper());
 private final List<Track> tracks=new ArrayList<>(); private int index;private boolean ducked;private Runnable clipDone;private Listener listener;private String status="Музыка не запущена";
 private PartyMusic(Context c){context=c;try{JSONArray a=new JSONArray(c.getSharedPreferences("martin",0).getString("playlist","[]"));for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);Track t=new Track();t.uri=o.getString("uri");t.title=o.optString("title","Трек");t.artist=o.optString("artist","");t.year=o.optInt("year",0);tracks.add(t);}}catch(Exception ignored){}}
 public List<Track> tracks(){return new ArrayList<>(tracks);}
 public String status(){return status;}
 public void setListener(Listener l){listener=l;if(l!=null)l.onState(status);}
 private void emit(String s){status=s;if(listener!=null)listener.onState(s);}
 public void add(String uri,String filename){for(Track x:tracks)if(x.uri.equals(uri))return;Track t=new Track();t.uri=uri;t.title=filename.replaceFirst("\\.[^.]+$","");String[] parts=t.title.split("[—–]| - ",2);if(parts.length==2){t.artist=parts[0].trim();t.title=parts[1].trim();}java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b(19[5-9][0-9]|20[0-2][0-9])\\b").matcher(filename);if(m.find())t.year=Integer.parseInt(m.group());tracks.add(t);save();}
 public void edit(int i,String title,String artist,int year){Track t=tracks.get(i);t.title=title.trim();t.artist=artist.trim();t.year=year;save();}
 private void save(){JSONArray a=new JSONArray();try{for(Track t:tracks)a.put(new JSONObject().put("uri",t.uri).put("title",t.title).put("artist",t.artist).put("year",t.year));context.getSharedPreferences("martin",0).edit().putString("playlist",a.toString()).apply();}catch(Exception ignored){}}
 public void clear(){stop();tracks.clear();save();}
 public void duck(boolean value){ducked=value;if(player!=null){float v=value?.12f:1f;try{player.setVolume(v,v);}catch(Exception ignored){}}}
 public boolean isPlaying(){try{return player!=null&&player.isPlaying();}catch(Exception e){return false;}}
 public void play(int i){if(tracks.isEmpty()){emit("Добавьте аудиофайлы");return;}index=(i+tracks.size())%tracks.size();Track t=tracks.get(index);open(t.uri,t.artist+" — "+t.title,0,null);}
 public void next(){play(index+1);}
 public void previous(){play(index-1);}
 public void toggle(){if(player==null){play(index);return;}if(isPlaying()){player.pause();emit("Пауза");}else{player.start();emit("Воспроизведение");}}
 public void clip(String uri,Runnable done){open(uri,"Фрагмент для конкурса",6000,done);}
 private void open(String uri,String label,int duration,Runnable done){
  stop();clipDone=done;final MediaPlayer p=new MediaPlayer();player=p;
  try{p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());p.setDataSource(context,Uri.parse(uri));
   p.setOnPreparedListener(mp->{if(player!=mp)return;mp.setVolume(ducked?.12f:1f,ducked?.12f:1f);mp.start();emit(label);if(duration>0)handler.postDelayed(this::finishClip,duration);});
   p.setOnCompletionListener(mp->{if(duration>0)finishClip();else next();});
   p.setOnErrorListener((mp,w,e)->{emit("Не удалось открыть трек. Выберите файл заново.");finishClip();return true;});p.prepareAsync();
  }catch(Exception e){emit("Нет доступа к файлу. Выберите его заново.");finishClip();}
 }
 private void finishClip(){Runnable d=clipDone;clipDone=null;release();if(d!=null)d.run();}
 private void release(){handler.removeCallbacksAndMessages(null);if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}}
 public void stopClip(){if(clipDone!=null)stop();}
 public void stop(){clipDone=null;release();emit("Музыка остановлена");}
}
