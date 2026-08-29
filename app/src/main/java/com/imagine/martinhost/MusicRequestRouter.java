package com.imagine.martinhost;
import android.content.*;import java.util.Locale;
/** Opens a persistent, sandboxed Yandex Music web session inside this application. */
final class MusicRequestRouter {
 static String extract(String raw){String s=raw==null?"":raw.trim(),l=s.toLowerCase(Locale.ROOT);if(!(l.contains("включи")||l.contains("поставь")||l.contains("сыграй")||l.contains("запусти")))return "";return s.replaceFirst("(?iu)^.*?(?:включи|поставь|сыграй|запусти)(?:\\s+(?:песню|трек|музыку))?\\s*","").replaceAll("[.!?]+$","").trim();}
 static boolean play(Context c,String q){if(q.isBlank())return false;boolean ok=YandexMusicPlayback.play(q);DiagnosticRecorder.get(c).event("music_request",(ok?"yandex_hidden_player":"yandex_player_unavailable")+";query="+q);return ok;}
 private MusicRequestRouter(){}
}
