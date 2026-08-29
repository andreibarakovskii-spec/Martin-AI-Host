package com.imagine.martinhost;
import android.content.*;import java.util.Locale;
/** Opens a persistent, sandboxed Yandex Music web session inside this application. */
final class MusicRequestRouter {
 static String extract(String raw){String s=raw==null?"":raw.trim(),l=s.toLowerCase(Locale.ROOT);if(!(l.contains("включи")||l.contains("поставь")||l.contains("сыграй")||l.contains("запусти")))return "";return s.replaceFirst("(?iu)^.*?(?:включи|поставь|сыграй|запусти)(?:\\s+(?:песню|трек|музыку))?\\s*","").replaceAll("[.!?]+$","").trim();}
 static boolean play(Context c,String q){if(q.isBlank())return false;try{c.startActivity(new Intent(c,YandexMusicWebActivity.class).putExtra("query",q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));DiagnosticRecorder.get(c).event("music_request","yandex_in_app;query="+q);return true;}catch(Exception ignored){return false;}}
 private MusicRequestRouter(){}
}
