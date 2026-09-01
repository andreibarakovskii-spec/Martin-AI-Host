package com.imagine.martinhost;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

/** Continuous capture with adaptive VAD, pre-roll, AEC/NS and a strict TTS interrupt monitor. */
public final class ContinuousSpeechEngine {
    public interface Listener {
        void onSpeechChunk(byte[] wav16kMono);
        default void onInterruptChunk(byte[] wav16kMono) { }
        void onLevel(float rmsDb, float noiseFloorDb, boolean speech);
        void onStatus(String status);
        void onError(String error);
    }

    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_MS = 20;
    private static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;
    private static final int PRE_ROLL_MS = 700;
    private static final int PRE_ROLL_FRAMES = PRE_ROLL_MS / FRAME_MS;
    private static final int MIN_SPEECH_MS = 160;
    private static final int END_SILENCE_MS = 750;
    private static final int MAX_UTTERANCE_MS = 14000;

    private static final int INTERRUPT_PRE_ROLL_MS = 180;
    private static final int INTERRUPT_PRE_ROLL_FRAMES = INTERRUPT_PRE_ROLL_MS / FRAME_MS;
    private static final int INTERRUPT_MIN_SPEECH_MS = 240;
    private static final int INTERRUPT_END_SILENCE_MS = 260;
    private static final int INTERRUPT_MAX_MS = 2400;
    private static final int INTERRUPT_COOLDOWN_MS = 1200;

    private final Context context;
    private final Listener listener;
    private final TurnManager turnManager;
    private volatile boolean running;
    private AudioRecord record;
    private AcousticEchoCanceler aec;
    private NoiseSuppressor ns;
    private Thread thread;

    public ContinuousSpeechEngine(Context context, TurnManager turnManager, Listener listener) {
        this.context = context.getApplicationContext();
        this.turnManager = turnManager;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Нужен доступ к микрофону");
            return;
        }
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferBytes = Math.max(min * 2, FRAME_SAMPLES * 2 * 8);
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("AudioRecord not initialized");
            enableAudioFx(record.getAudioSessionId());
            running = true;
            record.startRecording();
            DiagnosticRecorder.get(context).event("capture_start","rate=16000;frame_ms=20;pre_roll_ms=700;end_silence_ms=750;min_speech_ms=160;max_utterance_ms=14000;interrupt_monitor=true;interrupt_min_ms=240;interrupt_max_ms=2400;aec="+(aec!=null&&aec.getEnabled())+";ns="+(ns!=null&&ns.getEnabled())+";source=VOICE_COMMUNICATION;buffer_bytes="+bufferBytes);
            thread = new Thread(this::captureLoop, "CompanionContinuousAudio");
            thread.start();
            listener.onStatus("Непрерывное прослушивание включено");
        } catch (Exception e) {
            running = false;
            releaseRecord();
            DiagnosticRecorder.get(context).event("capture_error",e.getClass().getSimpleName());
            listener.onError("AudioRecord: " + e.getMessage());
        }
    }

    private void enableAudioFx(int sessionId) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId);
                if (aec != null) aec.setEnabled(true);
            }
        } catch (Exception ignored) { aec = null; }
        try {
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId);
                if (ns != null) ns.setEnabled(true);
            }
        } catch (Exception ignored) { ns = null; }
    }

    public synchronized void stop() {
        running = false;
        if (record != null) { try { record.stop(); } catch (Exception ignored) {} }
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
        releaseRecord();
        DiagnosticRecorder.get(context).endMic();
    }

    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] frame = new short[FRAME_SAMPLES];
        ArrayDeque<short[]> preRoll = new ArrayDeque<>();
        ByteArrayOutputStream utterance = null;
        int speechMs = 0, silenceMs = 0, utteranceMs = 0;

        ArrayDeque<short[]> interruptPreRoll = new ArrayDeque<>();
        ByteArrayOutputStream interrupt = null;
        int interruptSpeechMs=0,interruptSilenceMs=0,interruptMs=0;
        long interruptCooldownUntil=0;

        float noiseDb = -48f;
        boolean inSpeech = false, inInterrupt=false;
        long lastHealth=0,lastRead=android.os.SystemClock.elapsedRealtime();
        boolean previousBackground=false;long musicCalibrationUntil=0;
        TurnManager.State previousTurn=turnManager.state();

        while (running && record != null) {
            int read;
            try { read = record.read(frame, 0, frame.length, AudioRecord.READ_BLOCKING); }
            catch (Exception e) { DiagnosticRecorder.get(context).event("capture_read_error",e.getClass().getSimpleName());listener.onError("Чтение микрофона: " + e.getMessage()); break; }
            if (read < 0) { DiagnosticRecorder.get(context).event("capture_read_error",String.valueOf(read));listener.onError("Микрофон остановился: " + read); break; }
            if (read == 0) continue;

            short[] copy = new short[read];
            System.arraycopy(frame, 0, copy, 0, read);
            DiagnosticRecorder.get(context).mic(copy,read);
            float rmsDb = rmsDb(copy);
            long now=android.os.SystemClock.elapsedRealtime();
            TurnManager.State turn=turnManager.state();

            if(turn!=previousTurn){
                previousTurn=turn;
                if(turn!=TurnManager.State.SPEAKING){interrupt=null;interruptPreRoll.clear();inInterrupt=false;interruptSpeechMs=interruptSilenceMs=interruptMs=0;}
            }

            boolean background=PartyMusic.get(context).isBackgroundPlaying();
            if(background!=previousBackground){previousBackground=background;musicCalibrationUntil=background?now+1600:0;inSpeech=false;utterance=null;preRoll.clear();speechMs=silenceMs=utteranceMs=0;DiagnosticRecorder.get(context).event("vad_music_mode","background="+background);}
            if (!inSpeech && turnManager.acceptMicForStt()) {
                float capped = Math.min(rmsDb, noiseDb + (background?5f:8f));
                float alpha=background&&now<musicCalibrationUntil?.16f:.015f;
                noiseDb = noiseDb * (1f-alpha) + capped * alpha;
            }
            float threshold = Math.max(-43f, noiseDb + (background?12f:9f));
            boolean voiced = turnManager.acceptMicForStt() && now>=musicCalibrationUntil && rmsDb > threshold;

            // Strict secondary monitor while TTS is playing. AEC is expected to remove most self speech;
            // remaining candidates are still validated by BargeInPolicy after STT.
            if(turn==TurnManager.State.SPEAKING){
                float interruptThreshold=Math.max(-28f,noiseDb+18f);
                boolean interruptVoiced=now>=interruptCooldownUntil && rmsDb>interruptThreshold;
                if(!inInterrupt){
                    interruptPreRoll.addLast(copy);
                    while(interruptPreRoll.size()>INTERRUPT_PRE_ROLL_FRAMES)interruptPreRoll.removeFirst();
                    if(interruptVoiced){
                        inInterrupt=true;interruptSpeechMs=FRAME_MS;interruptSilenceMs=0;interruptMs=interruptPreRoll.size()*FRAME_MS;
                        interrupt=new ByteArrayOutputStream(12000);
                        for(short[] p:interruptPreRoll)writePcm(interrupt,p);interruptPreRoll.clear();
                        DiagnosticRecorder.get(context).event("barge_candidate","rms_db="+rmsDb+";threshold_db="+interruptThreshold+";pre_roll_ms="+INTERRUPT_PRE_ROLL_MS);
                    }
                }else{
                    writePcm(interrupt,copy);interruptMs+=FRAME_MS;
                    if(interruptVoiced){interruptSpeechMs+=FRAME_MS;interruptSilenceMs=0;}else interruptSilenceMs+=FRAME_MS;
                    boolean complete=interruptSilenceMs>=INTERRUPT_END_SILENCE_MS&&interruptSpeechMs>=INTERRUPT_MIN_SPEECH_MS;
                    boolean tooLong=interruptMs>=INTERRUPT_MAX_MS;
                    if(complete||tooLong){
                        byte[] pcm=interrupt.toByteArray();
                        DiagnosticRecorder.get(context).event("barge_audio_ready","reason="+(tooLong?"max_length":"silence")+";speech_ms="+interruptSpeechMs+";bytes="+pcm.length);
                        listener.onInterruptChunk(wavFromPcm(pcm));
                        interruptCooldownUntil=now+INTERRUPT_COOLDOWN_MS;
                        inInterrupt=false;interrupt=null;interruptPreRoll.clear();interruptSpeechMs=interruptSilenceMs=interruptMs=0;
                    }
                }
            }else{interrupt=null;interruptPreRoll.clear();inInterrupt=false;interruptSpeechMs=interruptSilenceMs=interruptMs=0;}

            listener.onLevel(rmsDb, noiseDb, voiced);
            if(now-lastRead>100)DiagnosticRecorder.get(context).event("capture_read_gap","ms="+(now-lastRead));lastRead=now;
            if(now-lastHealth>=200&&DiagnosticRecorder.get(context).active()){
                int clipped=0,peak=0;for(short sample:copy){int v=Math.abs((int)sample);peak=Math.max(peak,v);if(v>=32760)clipped++;}
                android.media.AudioDeviceInfo route=record==null?null:record.getRoutedDevice();
                DiagnosticRecorder.get(context).event("mic_health","rms_db="+rmsDb+";noise_db="+noiseDb+";threshold_db="+threshold+";gate="+turnManager.acceptMicForStt()+";state="+turn+";voiced="+voiced+";interrupt_monitor="+(turn==TurnManager.State.SPEAKING)+";peak="+peak+";clipped_samples="+clipped+";read_samples="+read+";route_type="+(route==null?-1:route.getType()));lastHealth=now;
            }

            if (!inSpeech) {
                if (turnManager.acceptMicForStt()) {
                    preRoll.addLast(copy);
                    while (preRoll.size() > PRE_ROLL_FRAMES) preRoll.removeFirst();
                } else {
                    preRoll.clear();
                }
                if (voiced) {
                    inSpeech = true;
                    speechMs = FRAME_MS; silenceMs = 0;
                    utteranceMs = preRoll.size() * FRAME_MS;
                    utterance = new ByteArrayOutputStream(32000);
                    for (short[] p : preRoll) writePcm(utterance, p);
                    preRoll.clear();
                    DiagnosticRecorder.get(context).event("vad_speech_start","threshold_db="+threshold+";noise_db="+noiseDb);
                    listener.onStatus("Слышу речь…");
                }
                continue;
            }

            if (!turnManager.acceptMicForStt()) {
                DiagnosticRecorder.get(context).event("vad_discard","gate closed");
                inSpeech = false; utterance = null; preRoll.clear();
                speechMs = silenceMs = utteranceMs = 0;
                continue;
            }

            writePcm(utterance, copy);
            utteranceMs += FRAME_MS;
            if (voiced) { speechMs += FRAME_MS; silenceMs = 0; }
            else silenceMs += FRAME_MS;

            if (silenceMs >= END_SILENCE_MS && speechMs < MIN_SPEECH_MS) { DiagnosticRecorder.get(context).event("vad_discard","too_short;speech_ms="+speechMs);inSpeech=false; utterance=null; preRoll.clear(); speechMs=silenceMs=utteranceMs=0; continue; }
            boolean complete = silenceMs >= END_SILENCE_MS && speechMs >= MIN_SPEECH_MS;
            boolean tooLong = utteranceMs >= MAX_UTTERANCE_MS;
            if (complete || tooLong) {
                byte[] pcm = utterance.toByteArray();
                inSpeech = false; utterance = null; preRoll.clear();
                speechMs = silenceMs = utteranceMs = 0;
                if (turnManager.acceptMicForStt()) {
                    DiagnosticRecorder.get(context).event("vad_utterance_end","reason="+(tooLong?"max_length":"silence")+";pcm_bytes="+pcm.length);
                    listener.onStatus("Распознаю…");
                    listener.onSpeechChunk(wavFromPcm(pcm));
                }
            }
        }
    }

    private void releaseRecord() {
        if (aec != null) { try { aec.release(); } catch (Exception ignored) {} aec = null; }
        if (ns != null) { try { ns.release(); } catch (Exception ignored) {} ns = null; }
        if (record != null) { try { record.release(); } catch (Exception ignored) {} record = null; }
    }

    private static float rmsDb(short[] data) {
        if (data.length == 0) return -90f;
        double sum = 0.0;
        for (short s : data) { double v = s / 32768.0; sum += v * v; }
        double rms = Math.sqrt(sum / data.length);
        return (float)(20.0 * Math.log10(Math.max(1e-6, rms)));
    }

    private static void writePcm(ByteArrayOutputStream out, short[] samples) {
        for (short s : samples) { out.write(s & 0xff); out.write((s >>> 8) & 0xff); }
    }

    private static byte[] wavFromPcm(byte[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R','I','F','F'}).putInt(36 + pcm.length).put(new byte[]{'W','A','V','E'});
        b.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)1);
        b.putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort((short)2).putShort((short)16);
        b.put(new byte[]{'d','a','t','a'}).putInt(pcm.length).put(pcm);
        return b.array();
    }
}
