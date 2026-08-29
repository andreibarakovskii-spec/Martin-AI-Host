package com.imagine.martinhost;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GrokClient {
    public interface Callback { void onResult(String text); void onError(String error); }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Deque<String> history=new ArrayDeque<>();
    public GrokClient(Context context){ this.context=context; }
    public void setApiKey(String key){ context.getSharedPreferences("martin",0).edit().putString("xai_key", key.trim()).apply(); }
    public void clearHistory(){synchronized(history){history.clear();}}

    private String historyText(){
        synchronized(history){
            if(history.isEmpty())return "";
            StringBuilder b=new StringBuilder("\n\nКОНТЕКСТ ПОСЛЕДНЕГО ДИАЛОГА. Продолжай его последовательно, не начинай игру заново:\n");
            for(String s:history)b.append(s).append('\n');
            return b.toString();
        }
    }
    private void remember(String user,String assistant){
        synchronized(history){
            history.addLast("Гость: "+user);history.addLast("Мартин: "+assistant);
            while(history.size()>20)history.removeFirst();
        }
    }

    public void reply(String userText, Callback cb){
        executor.execute(() -> {
            try {
                String key=context.getSharedPreferences("martin",0).getString("xai_key","");
                if(key.isBlank()) throw new IllegalStateException("Сначала укажите xAI API key в настройках");
                URL u=new URL("https://api.x.ai/v1/responses");
                HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(15000); c.setReadTimeout(45000);
                c.setRequestProperty("Authorization","Bearer "+key); c.setRequestProperty("Content-Type","application/json");
                String guestContext=new GuestStore(context).promptContext();
                String system="Ты Мартин — голосовой AI-ведущий домашнего дня рождения Кати, 35 лет, 19 взрослых гостей. Говори по-русски коротко, живо, доброжелательно, естественными фразами, удобными для озвучки. Ты DJ и ведущий: проводи конкурсы, задавай вопросы в стиле Что? Где? Когда?, проводи Угадай мелодию, цитаты, логические игры, произноси тосты, рассказывай короткие смешные шутки и поддерживай разговор. Не унижай гостей, не заставляй пить алкоголь, не шути про здоровье, внешность, деньги и чувствительные темы. Для персонализации используй ТОЛЬКО подготовленные сведения ниже, ничего личного не выдумывай. Если у гостя указаны запретные темы — строго их соблюдай.\n\n"+
                        "ОБЯЗАТЕЛЬНОЕ ПРАВИЛО ДЛЯ ЛЮБОЙ ИГРЫ: сначала объясни правила, баллы и окончание, затем дай один демонстрационный пример с ответом, спроси, понятны ли правила, и дождись подтверждения. Настоящая игра содержит 18 раундов. Раунды 1-6 простые, 7-12 средние, 13-18 более сложные. Никогда не выдавай два задания сразу. После ответа обязательно сообщи, верно или нет, затем переходи к следующему раунду.\n\n"+
                        "ТЕХНИЧЕСКИЕ МАРКЕРЫ. Они не произносятся вслух и всегда ставятся в самом конце реплики. Если признал ответ правильным и нужно начислить N баллов (1-3), закончи слышимую фразу вопросом «Кто ответил?» и добавь [[ASK_NAME_FOR_SCORE:N]]. Для обычного включения музыки используй [[PLAY_MUSIC:поисковый запрос]], например запрос должен содержать исполнителя и/или название. Для игры Угадай мелодию используй только [[PLAY_FRAGMENT:исполнитель название|6]]: один маркер = один реальный аудиофрагмент. Перед маркером не называй ответ. Не говори, что музыка уже включилась, если не добавил соответствующий маркер.\n\n"+
                        guestContext+historyText();
                JSONObject body=new JSONObject(); body.put("model","grok-4.6"); body.put("store",false);
                JSONArray input=new JSONArray();
                input.put(new JSONObject().put("role","system").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",system))));
                input.put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","input_text").put("text",userText))));
                body.put("input",input);
                try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                InputStream is=(c.getResponseCode()>=200&&c.getResponseCode()<300)?c.getInputStream():c.getErrorStream();
                String raw=new String(is.readAllBytes(),StandardCharsets.UTF_8); JSONObject r=new JSONObject(raw); StringBuilder out=new StringBuilder();
                JSONArray arr=r.optJSONArray("output"); if(arr!=null) for(int i=0;i<arr.length();i++){ JSONArray cont=arr.getJSONObject(i).optJSONArray("content"); if(cont!=null) for(int j=0;j<cont.length();j++){ JSONObject x=cont.getJSONObject(j); if("output_text".equals(x.optString("type"))) out.append(x.optString("text")); }}
                if(out.length()==0) throw new IllegalStateException(raw);
                String answer=out.toString();remember(userText,answer);cb.onResult(answer);
            } catch(Exception e){ cb.onError(e.getMessage()==null?e.toString():e.getMessage()); }
        });
    }
}
