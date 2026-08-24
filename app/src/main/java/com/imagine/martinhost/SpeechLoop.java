package com.imagine.martinhost;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;

public final class SpeechLoop implements RecognitionListener {
    public interface Listener { void onFinalText(String text); void onStatus(String status); void onError(String error); }
    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean running;
    private boolean listening;

    public SpeechLoop(Context context, Listener listener) { this.context=context; this.listener=listener; }

    public void start() {
        if (running) return;
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            listener.onError("На телефоне нет офлайн-распознавания Android"); return;
        }
        running=true;
        recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context);
        recognizer.setRecognitionListener(this);
        listen(100);
    }

    private Intent intent() {
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 650L);
        return i;
    }

    private void listen(long delay) {
        if (!running || recognizer==null || listening) return;
        handler.postDelayed(() -> {
            if (!running || recognizer==null || listening) return;
            try { recognizer.startListening(intent()); listening=true; listener.onStatus("Слушаю…"); }
            catch(Exception e) { listening=false; listen(300); }
        }, delay);
    }

    public void stop() {
        running=false; listening=false; handler.removeCallbacksAndMessages(null);
        if (recognizer!=null) { try{recognizer.cancel();}catch(Exception ignored){} try{recognizer.destroy();}catch(Exception ignored){} recognizer=null; }
    }

    @Override public void onResults(Bundle results) {
        listening=false;
        ArrayList<String> list=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list!=null && !list.isEmpty()) listener.onFinalText(list.get(0));
        listen(250);
    }
    @Override public void onError(int error) { listening=false; if(running) listen(300); }
    @Override public void onReadyForSpeech(Bundle params) { listener.onStatus("Говорите"); }
    @Override public void onBeginningOfSpeech() { listener.onStatus("Распознаю…"); }
    @Override public void onEndOfSpeech() { listener.onStatus("Понимаю фразу…"); }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}
}
