package com.imagine.martinhost;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

public final class OfflineSpeaker implements TextToSpeech.OnInitListener {
    public interface Listener { void onReady(); void onDone(); void onError(String message); }
    private final TextToSpeech tts;
    private final Listener listener;
    private boolean ready;

    public OfflineSpeaker(Context context, Listener listener) {
        this.listener = listener;
        this.tts = new TextToSpeech(context, this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) { listener.onDone(); }
            @Override public void onError(String utteranceId) { listener.onError("Ошибка озвучки"); }
        });
    }

    @Override public void onInit(int status) {
        ready = status == TextToSpeech.SUCCESS;
        if (!ready) { listener.onError("TTS недоступен"); return; }
        Locale ru = new Locale("ru", "RU");
        var best = tts.getVoices().stream()
                .filter(v -> v.getLocale().getLanguage().equals(ru.getLanguage()))
                .max(Comparator.comparingInt(v -> v.getQuality()))
                .orElse(null);
        if (best != null) tts.setVoice(best); else tts.setLanguage(ru);
        tts.setSpeechRate(1.03f);
        listener.onReady();
    }

    public void speak(String text) {
        if (!ready || text == null || text.isBlank()) return;
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString());
    }

    public void stop() { if (ready) tts.stop(); }
    public void close() { tts.stop(); tts.shutdown(); }
}
