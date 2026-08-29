package com.imagine.martinhost;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Sends meeting-style WAV chunks to Groq Whisper without blocking the UI/audio thread. */
public final class GroqTranscriber {
    public interface Callback { void onText(String text); void onError(String error); }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static volatile String lastAcceptedText="";

    private final java.util.concurrent.atomic.AtomicInteger generation=new java.util.concurrent.atomic.AtomicInteger();
    private volatile HttpURLConnection connection;
    public void cancel(){generation.incrementAndGet();HttpURLConnection c=connection;if(c!=null)c.disconnect();}

    public GroqTranscriber(Context context) { this.context = context.getApplicationContext(); }
    static String lastAcceptedText(){return lastAcceptedText;}

    static boolean shouldDropTranscript(String text,boolean musicPlaying){
        String l=text==null?"":text.toLowerCase(Locale.ROOT).replace('ё','е').trim();
        if(l.isBlank())return false;
        if(l.contains("редактор субтитров")||l.contains("корректор а.")||l.contains("корректор а "))return true;
        if(l.startsWith("субтитры сделал")||l.startsWith("субтитры создал")||l.startsWith("продолжение следует")||l.startsWith("спасибо за просмотр"))return true;
        // Under music Whisper can echo the vocabulary prompt itself instead of speech.
        if(musicPlaying&&(l.startsWith("имена,")||l.startsWith("имена:")||l.equals("имена")||l.startsWith("возможные имена")))return true;
        return false;
    }

    public void transcribe(byte[] wav, Callback callback) {
        if (wav == null || wav.length <= 44) {
            callback.onError("Пустой аудиофрагмент");
            return;
        }
        DiagnosticRecorder.get(context).audio("stt",wav,16000,true);
        DiagnosticRecorder.get(context).event("stt_queued","");
        final int token=generation.get();
        executor.execute(() -> {
            if(token!=generation.get())return;
            DiagnosticRecorder.get(context).event("stt_request_start","");
            HttpURLConnection c = null;
            try {
                var prefs = context.getSharedPreferences("martin", 0);
                String aiKey = prefs.getString("ai_key", prefs.getString("xai_key", ""));
                String key = prefs.getString("stt_key", "");
                // Backward compatibility: one Groq key can serve both AI and Whisper.
                if ((key == null || key.isBlank()) && aiKey != null && aiKey.startsWith("gsk_")) key = aiKey;
                if (key == null || key.isBlank() || !key.startsWith("gsk_"))
                    throw new IllegalStateException("Для распознавания речи укажите Groq STT key gsk_… в настройках");

                String model = prefs.getString("stt_model", "whisper-large-v3-turbo");
                DiagnosticRecorder.get(context).event("stt_model",model+";language=ru");
                String boundary = "----MartinBoundary" + System.nanoTime();
                c = (HttpURLConnection)new URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection();
                connection=c;
                if(token!=generation.get())return;
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(12000);
                c.setReadTimeout(45000);
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = c.getOutputStream()) {
                    writeField(out, boundary, "model", model);
                    writeField(out, boundary, "language", "ru");
                    writeField(out, boundary, "temperature", "0");
                    writeField(out, boundary, "response_format", "verbose_json");
                    writeField(out, boundary, "prompt", recognitionContext());
                    out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(wav);
                    out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                DiagnosticRecorder.get(context).event("stt_upload_end","");
                int code = c.getResponseCode();
                DiagnosticRecorder.get(context).event("stt_http",String.valueOf(code));
                InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String raw = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (code < 200 || code >= 300) throw new IllegalStateException("Whisper API " + code + ": " + raw);
                if(token!=generation.get())return;
                JSONObject result = new JSONObject(raw);
                // Keep confidence/timing for diagnosis, not as an automatic name rewrite.
                org.json.JSONArray segments=result.optJSONArray("segments");
                if(segments!=null)for(int i=0;i<segments.length();i++){
                    JSONObject seg=segments.optJSONObject(i);if(seg==null)continue;
                    DiagnosticRecorder.get(context).event("stt_segment","start="+seg.optDouble("start")+";end="+seg.optDouble("end")+";avg_logprob="+seg.optDouble("avg_logprob")+";no_speech_prob="+seg.optDouble("no_speech_prob")+";compression_ratio="+seg.optDouble("compression_ratio")+";text="+seg.optString("text"));
                }
                String text = result.optString("text", "").trim();
                if (text.isBlank()) {DiagnosticRecorder.get(context).event("stt_empty","");callback.onError("Речь не распознана");}
                else if(shouldDropTranscript(text,YandexMusicClient.get(context).isPlaying())){
                    DiagnosticRecorder.get(context).event("stt_filtered","playback_hallucination;"+text);
                    callback.onText("");
                }else{
                    lastAcceptedText=text;
                    DiagnosticRecorder.get(context).event("stt_result",text);callback.onText(text);
                }
            } catch (Exception e) {
                DiagnosticRecorder.get(context).event("stt_error",e.getClass().getSimpleName());
                if(token==generation.get())callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                if (c != null) c.disconnect();
                if(connection==c)connection=null;
            }
        });
    }

    private String recognitionContext() {
        // Vocabulary only; no private guest facts or fabricated speaker identity.
        StringBuilder names=new StringBuilder("Андрей, Катя");
        for(GuestStore.Guest guest:new GuestStore(context).load()){
            String name=guest.name.replaceAll("[^\\p{L} -]", "").trim();
            if(name.equalsIgnoreCase("Сергей"))continue;
            if(!name.isEmpty() && names.length()+name.length()<160)names.append(", ").append(name);
        }
        return "Русская речь на домашнем празднике. Ведущего зовут Сергей. В речи могут встречаться гости "+names+". Темы: день рождения, музыка, колонка, зарядка, игры, конкурсы.";
    }

    private static void writeField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public void close() { cancel();executor.shutdownNow(); }
}
