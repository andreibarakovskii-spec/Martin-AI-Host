package com.imagine.martinhost;
import android.app.SearchManager;import android.content.*;import android.net.Uri;import android.provider.MediaStore;import java.util.Locale;
/** Uses the signed-in music app; Martin never sees the Yandex password or OAuth token. */
final class MusicRequestRouter {
 static String extract(String raw){String s=raw==null?"":raw.trim(),l=s.toLowerCase(Locale.ROOT);if(!(l.contains("включи")||l.contains("поставь")||l.contains("сыграй")||l.contains("запусти")))return "";return s.replaceFirst("(?iu)^.*?(?:включи|поставь|сыграй|запусти)(?:\\s+(?:песню|трек|музыку))?\\s*","").replaceAll("[.!?]+$","").trim();}
 static boolean play(Context c,String q){if(q.isBlank())return false;Intent i=new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage("ru.yandex.music").putExtra(MediaStore.EXTRA_MEDIA_FOCUS,MediaStore.Audio.Media.ENTRY_CONTENT_TYPE).putExtra(SearchManager.QUERY,q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{c.startActivity(i);DiagnosticRecorder.get(c).event("music_request","yandex_media_search;query="+q);return true;}catch(Exception ignored){}try{c.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://music.yandex.ru/search?text="+Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));DiagnosticRecorder.get(c).event("music_request","yandex_deeplink;query="+q);return true;}catch(Exception ignored){return false;}}
 private MusicRequestRouter(){}
}
