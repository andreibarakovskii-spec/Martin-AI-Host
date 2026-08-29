package com.imagine.martinhost;

import android.content.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict voice-command router for direct Yandex Music playback. */
final class MusicRequestRouter {
 static final String DEFAULT_PARTY_QUERY="хиты 90-х 2000-х для вечеринки";
 private static final Pattern COMMAND=Pattern.compile("(?iu)^(?:сергей[\\s,.:;!-]+)?(?:пожалуйста[\\s,.:;!-]+)?(?:включи|включай|поставь|сыграй|запусти)(?:\\s+(?:песню|трек|музыку))?(?:\\s+(.+?))?[.!?]*$");

 static String extract(String raw){
  String s=raw==null?"":raw.trim();if(s.isBlank())return "";
  String l=s.toLowerCase(Locale.ROOT).replace('ё','е');
  if(l.matches(".*\\bне\\s+(?:включил|включила|включай|включи|поставил|поставь|запустил|запусти|сыграл|сыграй)\\b.*"))return "";
  Matcher m=COMMAND.matcher(s);if(!m.matches())return "";
  String q=m.group(1)==null?"":m.group(1).replaceAll("[.!?]+$","").trim();
  if(q.isBlank())return DEFAULT_PARTY_QUERY;if(q.length()<2)return "";return q;
 }

 static boolean play(Context c,String q){
  if(q==null||q.isBlank())return false;
  YandexMusicClient client=YandexMusicClient.get(c);
  if(!client.hasToken()){
   DiagnosticRecorder.get(c).event("music_request","oauth_required;query="+q);
   try{Intent i=new Intent(c,YandexAuthActivity.class);if(!(c instanceof android.app.Activity))i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}catch(Exception ignored){}
   return false;
  }
  client.setVolume(1f);
  client.play(q,new YandexMusicClient.Callback(){
   public void onStarted(YandexMusicClient.TrackInfo t){DiagnosticRecorder.get(c).event("music_request","playing_direct;query="+q+";track="+t.label());}
   public void onFinished(YandexMusicClient.TrackInfo t){DiagnosticRecorder.get(c).event("music_request","finished_direct;query="+q);}
   public void onError(String e){DiagnosticRecorder.get(c).event("music_request","direct_error;query="+q+";error="+e);}
  });
  return true;
 }
 private MusicRequestRouter(){}
}
