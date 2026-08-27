package com.imagine.martinhost;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Sends meeting-style WAV chunks to Groq Whisper without blocking the UI/audio thread. */
public final class GroqTranscriber {
    public interface Callback { void onText(String text); void onError(String error); }
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GroqTranscriber(Context context) { this.context = context.getApplicationContext(); }

    public void transcribe(byte[] wav, Callback callback) {
        if (wav == null || wav.length <= 44) {
            callback.onError("Пустой аудиофрагмент");
            return;
        }
        DiagnosticRecorder.get(context).audio("stt",wav,16000,true);
        DiagnosticRecorder.get(context).event("stt_queued","");
        executor.execute(() -> {
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
                String boundary = "----MartinBoundary" + System.nanoTime();
                c = (HttpURLConnection)new URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection();
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setConnectTimeout(12000);
                c.setReadTimeout(45000);
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = c.getOutputStream()) {
                    writeField(out, boundary, "model", model);
                    writeField(out, boundary, "language", "ru");
                    out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(wav);
                    out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                DiagnosticRecorder.get(context).event("stt_http",String.valueOf(code));
                InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String raw = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (code < 200 || code >= 300) throw new IllegalStateException("Whisper API " + code + ": " + raw);
                String text = new JSONObject(raw).optString("text", "").trim();
                if (text.isBlank()) callback.onError("Речь не распознана");
                else {DiagnosticRecorder.get(context).event("stt_result",text);callback.onText(text);}
            } catch (Exception e) {
                DiagnosticRecorder.get(context).event("stt_error",e.getClass().getSimpleName());
                callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private static void writeField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public void close() { executor.shutdownNow(); }
}
