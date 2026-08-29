package com.imagine.martinhost;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.xml.parsers.DocumentBuilderFactory;

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

    private final Context context;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private TrackInfo current;
    private Runnable fragmentStop;

    public YandexMusicClient(Context context){this.context=context.getApplicationContext();}
    public boolean hasToken(){return !token().isBlank();}
    public String token(){return context.getSharedPreferences(PREFS,0).getString(TOKEN_KEY,"");}
    public void setToken(String token){context.getSharedPreferences(PREFS,0).edit().putString(TOKEN_KEY,token==null?"":token.trim()).apply();}
    public void clearToken(){stop();context.getSharedPreferences(PREFS,0).edit().remove(TOKEN_KEY).apply();}
    public boolean isPlaying(){return player!=null&&player.isPlaying();}
    public TrackInfo current(){return current;}

    public void validateToken(Callback cb){
        executor.execute(()->{
            try{
                JSONObject root=getJson(API+"/account/status",true);
                JSONObject account=root.optJSONObject("result")==null?null:root.optJSONObject("result").optJSONObject("account");
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
        if(!hasToken()){cb.onError("Сначала войдите в Яндекс Музыку в настройках");return;}
        executor.execute(()->{
            try{
                TrackInfo track=searchFirst(q);
                String stream=streamUrl(track.id);
                main.post(()->prepareAndPlay(stream,track,fragmentSeconds,cb));
            }catch(Exception e){main.post(()->cb.onError(message(e)));}
        });
    }

    private TrackInfo searchFirst(String query)throws Exception{
        String url=API+"/search?text="+URLEncoder.encode(query,StandardCharsets.UTF_8)+"&type=track&page=0";
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
        byte[] xml=getBytes(infoUrl,false);
        Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        String host=text(doc,"host"),path=text(doc,"path"),ts=text(doc,"ts"),s=text(doc,"s");
        if(host.isBlank()||path.isBlank()||ts.isBlank()||s.isBlank())throw new IllegalStateException("Не удалось разобрать поток Яндекс Музыки");
        String payload=SIGN_SALT+(path.startsWith("/")?path.substring(1):path)+s;
        String sign=md5(payload);
        return "https://"+host+"/get-mp3/"+sign+"/"+ts+path;
    }

    private void prepareAndPlay(String stream,TrackInfo track,int fragmentSeconds,Callback cb){
        stopPlayerOnly();
        current=track;
        try{
            MediaPlayer p=new MediaPlayer();
            player=p;
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            p.setDataSource(stream);
            p.setOnPreparedListener(mp->{
                int start=0;
                if(fragmentSeconds>0&&track.durationMs>45000){
                    int max=Math.max(0,track.durationMs-fragmentSeconds*1000-12000);
                    start=Math.min(max,12000+(Math.abs(track.id.hashCode())%Math.max(1,max-12000+1)));
                }
                if(start>0)mp.seekTo(start,MediaPlayer.SEEK_CLOSEST_SYNC);
                mp.start();
                cb.onStarted(track);
                if(fragmentSeconds>0){
                    fragmentStop=()->{
                        if(player==mp){
                            try{mp.pause();}catch(Exception ignored){}
                            cb.onFinished(track);
                        }
                    };
                    main.postDelayed(fragmentStop,fragmentSeconds*1000L);
                }
            });
            p.setOnCompletionListener(mp->{if(player==mp)cb.onFinished(track);});
            p.setOnErrorListener((mp,what,extra)->{cb.onError("Ошибка воспроизведения Яндекс Музыки: "+what+"/"+extra);return true;});
            p.prepareAsync();
        }catch(Exception e){cb.onError(message(e));}
    }

    public void pause(){if(player!=null)try{player.pause();}catch(Exception ignored){}}
    public void resume(){if(player!=null)try{player.start();}catch(Exception ignored){}}
    public void stop(){main.post(this::stopPlayerOnly);}
    private void stopPlayerOnly(){
        if(fragmentStop!=null){main.removeCallbacks(fragmentStop);fragmentStop=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
        current=null;
    }

    private JSONObject getJson(String url,boolean auth)throws Exception{return new JSONObject(new String(getBytes(url,auth),StandardCharsets.UTF_8));}
    private byte[] getBytes(String url,boolean auth)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("Accept","application/json, text/xml, */*");
        if(auth)c.setRequestProperty("Authorization","OAuth "+token());
        int code=c.getResponseCode();
        InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
        byte[] data=in==null?new byte[0]:in.readAllBytes();
        if(code<200||code>=300)throw new IllegalStateException("Яндекс Музыка HTTP "+code+": "+new String(data,StandardCharsets.UTF_8));
        return data;
    }
    private static String text(Document doc,String tag){return doc.getElementsByTagName(tag).getLength()==0?"":doc.getElementsByTagName(tag).item(0).getTextContent();}
    private static String md5(String s)throws Exception{
        byte[] d=MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:d)out.append(String.format(Locale.ROOT,"%02x",b&0xff));return out.toString();
    }
    private static String message(Exception e){return e.getMessage()==null?e.toString():e.getMessage();}
}
