package com.imagine.martinhost;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Direct Yandex Music playback after OAuth. WebView is used only for sign-in. */
public final class YandexMusicClient {
    public static final class TrackInfo {
        public final String id,title,artist;
        public final int durationMs;
        TrackInfo(String id,String title,String artist,int durationMs){this.id=id;this.title=title;this.artist=artist;this.durationMs=durationMs;}
        public String label(){return artist.isBlank()?title:artist+" — "+title;}
    }
    public interface Callback {
        void onStarted(TrackInfo track);
        void onFinished(TrackInfo track);
        void onError(String message);
    }

    private static final String PREFS="martin";
    private static final String TOKEN_KEY="yandex_oauth_token";
    private static final String API="https://api.music.yandex.net";
    private static final String SIGN_SALT="XGRlBW9FXlekgbPrRHuSiA";
    private static YandexMusicClient instance;

    public static synchronized YandexMusicClient get(Context c){
        if(instance==null)instance=new YandexMusicClient(c.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private TrackInfo current;
    private Runnable fragmentStop,prepareTimeout;
    private float volume=1f;
    private int generation;

    private YandexMusicClient(Context context){this.context=context.getApplicationContext();}
    public boolean hasToken(){return !token().isBlank();}
    public String token(){return context.getSharedPreferences(PREFS,0).getString(TOKEN_KEY,"");}
    public void setToken(String token){context.getSharedPreferences(PREFS,0).edit().putString(TOKEN_KEY,token==null?"":token.trim()).apply();}
    public void clearToken(){stop();context.getSharedPreferences(PREFS,0).edit().remove(TOKEN_KEY).apply();}
    public boolean isPlaying(){try{return player!=null&&player.isPlaying();}catch(Exception e){return false;}}
    public TrackInfo current(){return current;}
    public void setVolume(float value){volume=Math.max(0f,Math.min(1f,value));MediaPlayer p=player;if(p!=null)try{p.setVolume(volume,volume);}catch(Exception ignored){}}

    public void validateToken(Callback cb){
        executor.execute(()->{
            try{
                JSONObject root=getJson(API+"/account/status",true);
                JSONObject result=root.optJSONObject("result");
                JSONObject account=result==null?null:result.optJSONObject("account");
                if(account==null)throw new IllegalStateException("Яндекс не подтвердил аккаунт");
                main.post(()->cb.onFinished(null));
            }catch(Exception e){main.post(()->cb.onError(message(e)));}
        });
    }

    public void play(String query,Callback cb){playInternal(query,0,cb);}
    public void playFragment(String query,int seconds,Callback cb){playInternal(query,Math.max(3,Math.min(9,seconds)),cb);}

    private void playInternal(String query,int fragmentSeconds,Callback cb){
        String q=query==null?"":query.trim();
        if(q.isBlank()){cb.onError("Пустой запрос к Яндекс Музыке");return;}
        if(!hasToken()){cb.onError("Сначала подключите Яндекс Музыку в разделе «Музыка»");return;}
        final int tokenGeneration=++generation;
        DiagnosticRecorder.get(context).event("yandex_direct","resolve_start;query="+q+";fragment="+fragmentSeconds);
        executor.execute(()->{
            try{
                TrackInfo track=searchFirst(q);
                String stream=streamUrl(track.id);
                if(tokenGeneration!=generation)return;
                main.post(()->prepareAndPlay(stream,track,fragmentSeconds,cb,tokenGeneration));
            }catch(Exception e){
                String msg=message(e);
                DiagnosticRecorder.get(context).event("yandex_direct","resolve_error;"+msg);
                if(msg.contains("HTTP 401")||msg.contains("HTTP 403"))clearToken();
                if(tokenGeneration==generation)main.post(()->cb.onError(msg));
            }
        });
    }

    private TrackInfo searchFirst(String query)throws Exception{
        String url=API+"/search?text="+URLEncoder.encode(query,StandardCharsets.UTF_8)+"&type=track&page=0&nocorrect=false";
        JSONObject root=getJson(url,true);
        JSONObject result=root.optJSONObject("result");
        JSONObject tracks=result==null?null:result.optJSONObject("tracks");
        JSONArray arr=tracks==null?null:tracks.optJSONArray("results");
        if(arr==null||arr.length()==0)throw new IllegalStateException("Не нашёл трек «"+query+"»");
        JSONObject t=arr.getJSONObject(0);
        String artist="";
        JSONArray artists=t.optJSONArray("artists");
        if(artists!=null&&artists.length()>0)artist=artists.getJSONObject(0).optString("name","");
        return new TrackInfo(t.optString("id"),t.optString("title",query),artist,t.optInt("durationMs",0));
    }

    private String streamUrl(String trackId)throws Exception{
        JSONObject root=getJson(API+"/tracks/"+URLEncoder.encode(trackId,StandardCharsets.UTF_8)+"/download-info",true);
        JSONArray variants=root.optJSONArray("result");
        if(variants==null||variants.length()==0)throw new IllegalStateException("Яндекс не вернул поток для трека");
        JSONObject best=null;
        for(int i=0;i<variants.length();i++){
            JSONObject v=variants.getJSONObject(i);
            if(!"mp3".equalsIgnoreCase(v.optString("codec")))continue;
            if(v.optBoolean("preview",false))continue;
            if(best==null||v.optInt("bitrateInKbps",0)>best.optInt("bitrateInKbps",0))best=v;
        }
        if(best==null){
            for(int i=0;i<variants.length();i++){
                JSONObject v=variants.getJSONObject(i);
                if("mp3".equalsIgnoreCase(v.optString("codec"))){best=v;break;}
            }
        }
        if(best==null)throw new IllegalStateException("Нет совместимого MP3-потока");
        String infoUrl=best.optString("downloadInfoUrl","");
        if(infoUrl.isBlank())throw new IllegalStateException("Яндекс не вернул downloadInfoUrl");
        String[] info=parseDownloadInfo(getBytes(infoUrl,false));
        String host=info[0],path=info[1],ts=info[2],s=info[3];
        String payload=SIGN_SALT+(path.startsWith("/")?path.substring(1):path)+s;
        String sign=md5(payload);
        return "https://"+host+"/get-mp3/"+sign+"/"+ts+path;
    }

    /** Android 15 on some OEMs does not implement Xerces feature flags used by DocumentBuilderFactory.
     *  The Yandex response is a tiny fixed XML envelope, so parse only the four known text tags and
     *  reject DTD/entity declarations instead of invoking a general XML parser. */
    static String[] parseDownloadInfo(byte[] data)throws Exception{
        String xml=new String(data==null?new byte[0]:data,StandardCharsets.UTF_8);
        String upper=xml.toUpperCase(Locale.ROOT);
        if(upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw new IllegalStateException("Небезопасный ответ потока Яндекс Музыки");
        String host=xmlTag(xml,"host"),path=xmlTag(xml,"path"),ts=xmlTag(xml,"ts"),s=xmlTag(xml,"s");
        if(host.isBlank()||path.isBlank()||ts.isBlank()||s.isBlank())throw new IllegalStateException("Не удалось разобрать поток Яндекс Музыки");
        return new String[]{host,path,ts,s};
    }
    private static String xmlTag(String xml,String tag){
        Matcher m=Pattern.compile("<"+tag+">(.*?)</"+tag+">",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(xml==null?"":xml);
        return m.find()?xmlUnescape(m.group(1).trim()):"";
    }
    private static String xmlUnescape(String s){
        return s.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'");
    }

    private void prepareAndPlay(String stream,TrackInfo track,int fragmentSeconds,Callback cb,int tokenGeneration){
        stopPlayerOnly(false);
        current=track;
        try{
            MediaPlayer p=new MediaPlayer();
            player=p;
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setDataSource(stream);
            p.setVolume(volume,volume);
            prepareTimeout=()->{
                if(player==p&&tokenGeneration==generation){
                    DiagnosticRecorder.get(context).event("yandex_direct","prepare_timeout;track="+track.label());
                    stopPlayerOnly(false);
                    cb.onError("Яндекс долго не отдаёт аудио. Переподключите аккаунт или попробуйте ещё раз.");
                }
            };
            main.postDelayed(prepareTimeout,18000);
            p.setOnPreparedListener(mp->{
                if(player!=mp||tokenGeneration!=generation)return;
                if(prepareTimeout!=null){main.removeCallbacks(prepareTimeout);prepareTimeout=null;}
                int start=0;
                if(fragmentSeconds>0&&track.durationMs>45000){
                    int max=Math.max(0,track.durationMs-fragmentSeconds*1000-12000);
                    if(max>12000)start=12000+Math.abs(track.id.hashCode())%(max-12000+1);
                }
                final int seek=start;
                Runnable begin=()->{
                    if(player!=mp||tokenGeneration!=generation)return;
                    try{
                        mp.start();
                        mp.setVolume(volume,volume);
                        DiagnosticRecorder.get(context).event("yandex_direct","playing;track="+track.label()+";seek="+seek);
                        cb.onStarted(track);
                        if(fragmentSeconds>0){
                            fragmentStop=()->{
                                if(player==mp&&tokenGeneration==generation){
                                    try{mp.pause();}catch(Exception ignored){}
                                    DiagnosticRecorder.get(context).event("yandex_direct","fragment_done;track="+track.label());
                                    cb.onFinished(track);
                                }
                            };
                            main.postDelayed(fragmentStop,fragmentSeconds*1000L);
                        }
                    }catch(Exception e){cb.onError(message(e));}
                };
                if(start>0){mp.setOnSeekCompleteListener(x->begin.run());mp.seekTo(start,MediaPlayer.SEEK_CLOSEST_SYNC);}else begin.run();
            });
            p.setOnCompletionListener(mp->{if(player==mp&&tokenGeneration==generation){DiagnosticRecorder.get(context).event("yandex_direct","completed;track="+track.label());cb.onFinished(track);}});
            p.setOnErrorListener((mp,what,extra)->{if(prepareTimeout!=null){main.removeCallbacks(prepareTimeout);prepareTimeout=null;}DiagnosticRecorder.get(context).event("yandex_direct","media_error="+what+"/"+extra);cb.onError("Ошибка воспроизведения Яндекс Музыки: "+what+"/"+extra);return true;});
            p.prepareAsync();
        }catch(Exception e){cb.onError(message(e));}
    }

    public void pause(){if(player!=null)try{player.pause();}catch(Exception ignored){}}
    public void resume(){if(player!=null)try{player.start();}catch(Exception ignored){}}
    public void stop(){generation++;main.post(()->stopPlayerOnly(true));}
    private void stopPlayerOnly(boolean clearCurrent){
        if(fragmentStop!=null){main.removeCallbacks(fragmentStop);fragmentStop=null;}
        if(prepareTimeout!=null){main.removeCallbacks(prepareTimeout);prepareTimeout=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
        if(clearCurrent)current=null;
    }

    private JSONObject getJson(String url,boolean auth)throws Exception{return new JSONObject(new String(getBytes(url,auth),StandardCharsets.UTF_8));}
    private byte[] getBytes(String url,boolean auth)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        try{
            c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("Accept","application/json, text/xml, */*");c.setRequestProperty("Accept-Language","ru-RU,ru;q=0.9");c.setRequestProperty("User-Agent","SergeyAIHost/0.10.3 Android");
            if(auth)c.setRequestProperty("Authorization","OAuth "+token());
            int code=c.getResponseCode();
            InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
            byte[] bytes=in==null?new byte[0]:in.readAllBytes();
            if(code<200||code>=300)throw new IllegalStateException("Яндекс Музыка HTTP "+code+": "+new String(bytes,StandardCharsets.UTF_8));
            return bytes;
        }finally{c.disconnect();}
    }
    static String md5(String s)throws Exception{
        byte[] d=MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:d)out.append(String.format(Locale.ROOT,"%02x",b&0xff));return out.toString();
    }
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
}
