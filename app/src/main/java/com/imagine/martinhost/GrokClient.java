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
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    public GrokClient(Context context){this.context=context.getApplicationContext();}

    public void reply(String userText, Callback cb){
        executor.execute(()->{
            try{
                var prefs=context.getSharedPreferences("martin",0);
                String key=prefs.getString("ai_key",prefs.getString("xai_key",""));
                if(key.isBlank()) throw new IllegalStateException("Укажите API key в настройках");
                String provider=prefs.getString("ai_provider","auto");
                if("auto".equals(provider)) provider=key.startsWith("gsk_")?"groq":"xai";
                String system=systemPrompt();
                String text="groq".equals(provider)?callGroq(key,system,userText,prefs.getString("groq_model","openai/gpt-oss-120b")):callXai(key,system,userText,prefs.getString("xai_model","grok-4.6"));
                if(text==null||text.isBlank()) throw new IllegalStateException("AI вернул пустой ответ");
                cb.onResult(text.trim());
            }catch(Exception e){cb.onError(e.getMessage()==null?e.toString():e.getMessage());}
        });
    }

    private String systemPrompt(){
        String guestContext=new GuestStore(context).promptContext();
        return "Ты Мартин — живой голосовой AI-ведущий домашнего дня рождения Кати, 35 лет, 19 взрослых гостей. Говори по-русски коротко, естественно и с юмором. Ты тамада, DJ, собеседник и ведущий игр: Что? Где? Когда?, угадай мелодию, цитаты, логика, импровизация, тосты и короткие шутки. Не унижай людей, не заставляй пить, не затрагивай здоровье, внешность, деньги и другие чувствительные темы. Персонализируй только по подготовленным данным, ничего личного не выдумывай. Перед КАЖДОЙ игрой обязательно: назови её, объясни правила за 20–30 секунд, расскажи начисление баллов, покажи один демонстрационный пример с правильным ответом, затем спроси «Правила понятны? Начинаем?» и дождись подтверждения. Если ответ правильный и нужен балл, закончи слышимой фразой «Кто ответил?» и затем добавь [[ASK_NAME_FOR_SCORE:1]]. Маркер не объясняй.\n\n"+guestContext;
    }

    private HttpURLConnection open(String url,String key)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("POST");c.setDoOutput(true);c.setConnectTimeout(15000);c.setReadTimeout(60000);
        c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("Content-Type","application/json");return c;
    }
    private JSONObject post(HttpURLConnection c,JSONObject body)throws Exception{
        try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();InputStream is=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        String raw=is==null?"":new String(is.readAllBytes(),StandardCharsets.UTF_8);
        if(code<200||code>=300) throw new IllegalStateException("AI API "+code+": "+raw);
        return new JSONObject(raw);
    }
    private String callGroq(String key,String system,String user,String model)throws Exception{
        HttpURLConnection c=open("https://api.groq.com/openai/v1/chat/completions",key);
        JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content",system)).put(new JSONObject().put("role","user").put("content",user));
        JSONObject body=new JSONObject().put("model",model).put("messages",messages).put("temperature",0.85).put("max_completion_tokens",1000);
        JSONObject r=post(c,body);JSONArray choices=r.optJSONArray("choices");
        if(choices==null||choices.length()==0)return "";return choices.getJSONObject(0).getJSONObject("message").optString("content","");
    }
    private String callXai(String key,String system,String user,String model)throws Exception{
        HttpURLConnection c=open("https://api.x.ai/v1/responses",key);
        JSONArray input=new JSONArray();
        input.put(new JSONObject().put("role","system").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",system))));
        input.put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",user))));
        JSONObject r=post(c,new JSONObject().put("model",model).put("store",false).put("input",input));
        StringBuilder out=new StringBuilder();JSONArray arr=r.optJSONArray("output");
        if(arr!=null)for(int i=0;i<arr.length();i++){JSONArray cont=arr.getJSONObject(i).optJSONArray("content");if(cont!=null)for(int j=0;j<cont.length();j++){JSONObject x=cont.getJSONObject(j);if("output_text".equals(x.optString("type")))out.append(x.optString("text"));}}
        return out.toString();
    }
}
