package com.imagine.martinhost;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GrokClient {
    public interface Callback { void onResult(String text); void onError(String error); }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    public GrokClient(Context context){ this.context=context; }
    public void setApiKey(String key){ context.getSharedPreferences("martin",0).edit().putString("xai_key", key.trim()).apply(); }
    public void reply(String userText, Callback cb){
        executor.execute(() -> {
            try {
                String key=context.getSharedPreferences("martin",0).getString("xai_key","");
                if(key.isBlank()) throw new IllegalStateException("Сначала укажите xAI API key в настройках");
                URL u=new URL("https://api.x.ai/v1/responses");
                HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(15000); c.setReadTimeout(45000);
                c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json");
                String system="Ты Мартин — голосовой AI-ведущий домашнего дня рождения Кати, 35 лет, 19 взрослых гостей. Говори по-русски коротко, живо, доброжелательно, без унижения, без принуждения к алкоголю. Проводишь игры без экрана: викторины, импровизацию, AI-суд, кто скорее всего, историю по кругу, тосты. Не выдумывай личные факты о гостях.";
                JSONObject body=new JSONObject(); body.put("model","grok-4.6"); body.put("store",false);
                JSONArray input=new JSONArray();
                input.put(new JSONObject().put("role","system").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",system))));
                input.put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",userText))));
                body.put("input",input);
                try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                InputStream is=(c.getResponseCode()>=200&&c.getResponseCode()<300)?c.getInputStream():c.getErrorStream();
                String raw=new String(is.readAllBytes(),StandardCharsets.UTF_8); JSONObject r=new JSONObject(raw); StringBuilder out=new StringBuilder();
                JSONArray arr=r.optJSONArray("output"); if(arr!=null) for(int i=0;i<arr.length();i++){ JSONArray cont=arr.getJSONObject(i).optJSONArray("content"); if(cont!=null) for(int j=0;j<cont.length();j++){ JSONObject x=cont.getJSONObject(j); if("output_text".equals(x.optString("type"))) out.append(x.optString("text")); }}
                if(out.length()==0) throw new IllegalStateException(raw); cb.onResult(out.toString());
            } catch(Exception e){ cb.onError(e.getMessage()==null?e.toString():e.getMessage()); }
        });
    }
}
