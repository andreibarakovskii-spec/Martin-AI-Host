package com.imagine.martinhost;

import android.os.Handler;
import android.os.Looper;

/** Keeps capture alive while preventing Martin's own TTS from feeding STT. */
public final class TurnManager {
    public enum State { LISTENING, THINKING, SPEAKING, COOLDOWN }
    public interface Listener { void onTurnState(State state); }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private State state = State.LISTENING;
    private long releaseTailMs = 220L;

    public TurnManager(Listener listener) { this.listener = listener; }

    public synchronized State state() { return state; }
    public synchronized boolean acceptMicForStt() { return state == State.LISTENING; }
    public synchronized boolean isSelfSpeech() { return state == State.SPEAKING || state == State.COOLDOWN; }
    public void setReleaseTailMs(long value) { releaseTailMs = Math.max(80L, Math.min(800L, value)); }

    public synchronized void onUserFinal() { setState(State.THINKING); }
    public synchronized void onAiWillSpeak() { handler.removeCallbacksAndMessages(null); setState(State.SPEAKING); }

    public synchronized void onAiSpeechDone() {
        setState(State.COOLDOWN);
        handler.postDelayed(() -> {
            synchronized (TurnManager.this) { setState(State.LISTENING); }
        }, releaseTailMs);
    }

    public synchronized void forceListen() {
        handler.removeCallbacksAndMessages(null);
        setState(State.LISTENING);
    }

    private void setState(State next) {
        if (state == next) return;
        state = next;
        if (listener != null) listener.onTurnState(next);
    }
}
