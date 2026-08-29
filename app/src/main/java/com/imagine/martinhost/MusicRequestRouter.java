package com.imagine.martinhost;

import android.content.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict voice-command router for the embedded Yandex Music player. */
final class MusicRequestRouter {
 static final String DEFAULT_PARTY_QUERY="хиты 90-х 2000-х для вечеринки";
 private static final Pattern COMMAND=Pattern.compile("(?iu)^(?:сергей[\\s,.:;!-]+)?(?:пожалуйста[\\s,.:;!-]+)?(?:включи|включай|поставь|сыграй|запусти)(?:\\s+(?:песню|трек|музыку))?(?:\\s+(.+?))?[.!?]*$");

 static String extract(String raw){
  String s=raw==null?"":raw.trim();
  if(s.isBlank())return "";
  String l=s.toLowerCase(Locale.ROOT).replace('ё','е');
  // Questions/negations such as “так ты не включил?” are not playback commands.
  if(l.matches(".*\\bне\\s+(?:включил|включила|включай|включи|поставил|поставь|запустил|запусти|сыграл|сыграй)\\b.*"))return "";
  Matcher m=COMMAND.matcher(s);
  if(!m.matches())return "";
  String q=m.group(1)==null?"":m.group(1).replaceAll("[.!?]+$","").trim();
  if(q.isBlank())return DEFAULT_PARTY_QUERY;
  if(q.length()<2)return "";
  return q;
 }

 static boolean play(Context c,String q){
  if(q==null||q.isBlank())return false;
  boolean ok=YandexMusicPlayback.play(q);
  DiagnosticRecorder.get(c).event("music_request",(ok?"yandex_hidden_player":"yandex_player_unavailable")+";query="+q);
  return ok;
 }
 private MusicRequestRouter(){}
}
