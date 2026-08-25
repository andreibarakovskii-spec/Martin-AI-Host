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
        if (wav == null || wav.length <= 44) return;
        executor.execute(() -> {
            try {
                var prefs = context.getSharedPreferences("martin", 0);
                String key = prefs.getString("ai_key", prefs.getString("xai_key", ""));
                if (key == null || key.isBlank() || !key.startsWith("gsk_"))
                    throw new IllegalStateException("Для распознавания укажите Groq gsk_ ключ");
                String model = prefs.getString("stt_model", "whisper-large-v3-turbo");
                String boundary = "----MartinBoundary" + System.nanoTime();
                HttpURLConnection c = (HttpURLConnection)new URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection();
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
                InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                String raw = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
                if (code < 200 || code >= 300) throw new IllegalStateException("Whisper API " + code + ": " + raw);
                String text = new JSONObject(raw).optString("text", "").trim();
                if (!text.isBlank()) callback.onText(text);
            } catch (Exception e) {
                callback.onError(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    private static void writeField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n").getBytes(StandardCharsets.UTF_8));
    }

    public void close() { executor.shutdownNow(); }
}
