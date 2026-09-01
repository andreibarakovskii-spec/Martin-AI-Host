package com.imagine.martinhost;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

/**
 * First standalone Companion Core screen.
 * No PartyDirector is used in the normal dialogue path.
 */
public final class CompanionActivity extends FragmentActivity {
    private static final int REQ_AUDIO = 41;

    private GrokClient ai;
    private GroqTranscriber stt;
    private MartinSpeaker speaker;
    private TurnManager turns;
    private ContinuousSpeechEngine audio;
    private final ConversationDirector conversation = new ConversationDirector();

    private VoiceOrbView orb;
    private TextView state, heard, reply, detail;
    private Button mic;
    private boolean active, voiceReady, destroyed, pendingStart;
    private int generation;
    private String queuedSpeech;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF05060B);
        getWindow().setNavigationBarColor(0xFF05060B);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();

        PartyAudioRouter.prepare(this);
        ai = new GrokClient(this);
        stt = new GroqTranscriber(this);
        turns = new TurnManager(s -> runOnUiThread(() -> onTurnState(s)));
        turns.setReleaseTailMs(PartyAudioRouter.recommendedReleaseTailMs(this));

        audio = new ContinuousSpeechEngine(this, turns, new ContinuousSpeechEngine.Listener() {
            @Override public void onSpeechChunk(byte[] wav) {
                int token = generation;
                long started = android.os.SystemClock.elapsedRealtime();
                DiagnosticRecorder.get(CompanionActivity.this).event("companion_stt_start", "generation=" + token);
                turns.onUserFinal();
                stt.transcribe(wav, new GroqTranscriber.Callback() {
                    @Override public void onText(String text) {
                        long latency = android.os.SystemClock.elapsedRealtime() - started;
                        runOnUiThread(() -> {
                            if (!active || destroyed || token != generation) return;
                            DiagnosticRecorder.get(CompanionActivity.this).event("companion_stt_done", "ms=" + latency + ";text=" + text);
                            handleTranscript(text);
                        });
                    }
                    @Override public void onError(String error) {
                        runOnUiThread(() -> {
                            if (!active || destroyed || token != generation) return;
                            DiagnosticRecorder.get(CompanionActivity.this).event("companion_stt_error", error);
                            detail.setText(error);
                            turns.forceListen();
                        });
                    }
                });
            }

            @Override public void onLevel(float rms, float noise, boolean speech) {
                runOnUiThread(() -> {
                    if (orb != null && turns.acceptMicForStt()) orb.setLevel(Math.max(0f, Math.min(1f, (rms + 48f) / 36f)));
                });
            }
            @Override public void onStatus(String status) { }
            @Override public void onError(String error) { runOnUiThread(() -> detail.setText(error)); }
        });

        speaker = MartinSpeakerFactory.create(this, new MartinSpeaker.Listener() {
            @Override public void onPreparing(String message) { runOnUiThread(() -> detail.setText(message)); }
            @Override public void onReady() { runOnUiThread(() -> {
                voiceReady = true;
                detail.setText(active ? "Говори естественно" : "Голос готов");
                if (queuedSpeech != null) { String q = queuedSpeech; queuedSpeech = null; speakNow(q); }
            }); }
            @Override public void onStart() { runOnUiThread(() -> { state.setText("Говорю…"); orb.setState("talking"); }); }
            @Override public void onLevel(float level) { runOnUiThread(() -> orb.setLevel(level)); }
            @Override public void onSpectrum(float[] bands) { runOnUiThread(() -> orb.setSpectrum(bands)); }
            @Override public void onDone() { runOnUiThread(() -> {
                orb.setLevel(0f);
                PartyMusic.get(CompanionActivity.this).duck(false);
                turns.onAiSpeechDone();
            }); }
            @Override public void onError(String error) { runOnUiThread(() -> {
                voiceReady = false;
                queuedSpeech = null;
                PartyMusic.get(CompanionActivity.this).duck(false);
                detail.setText(error);
                turns.forceListen();
            }); }
        });
        speaker.prepare();
        DiagnosticRecorder.get(this).event("companion_runtime", "v1;director=local;party_director=false");
    }

    private void handleTranscript(String text) {
        if (text == null || text.isBlank()) { turns.forceListen(); return; }
        if (SttHallucinationFilter.reject(text)) {
            DiagnosticRecorder.get(this).event("companion_ignored", "stt_hallucination;" + text);
            turns.forceListen();
            return;
        }

        heard.setText("Вы: " + text);
        ConversationDirector.Decision d = conversation.decide(text, System.currentTimeMillis());
        DiagnosticRecorder.get(this).event("companion_decision", "kind=" + d.kind + ";attention=" + d.attention + ";reason=" + d.reason);

        switch (d.kind) {
            case IGNORE:
                state.setText("Слушаю…");
                detail.setText("Фоновая речь — не вмешиваюсь");
                turns.forceListen();
                return;
            case STOP:
                cancelCurrent(false);
                state.setText("Слушаю…");
                detail.setText("Прервано");
                return;
            case VISION:
                // Camera/context engine comes in a later Companion Core slice.
                requestAi("Пользователь просит визуальный контекст, но камера Companion Core пока не подключена. Ответь коротко и правдиво, что визуальное восприятие сейчас ещё не активно.");
                return;
            case MUSIC:
                String query = MusicRequestRouter.extract(text);
                if (!query.isBlank()) {
                    turns.forceListen();
                    MusicRequestRouter.play(this, query);
                    detail.setText("Музыка: " + query);
                } else requestAi(d.text);
                return;
            case RESPOND:
            default:
                requestAi(d.text);
        }
    }

    private void requestAi(String prompt) {
        final int token = generation;
        final long started = android.os.SystemClock.elapsedRealtime();
        turns.onUserFinal();
        state.setText("Думаю…");
        detail.setText("Формулирую ответ");
        orb.setState("thinking");
        DiagnosticRecorder.get(this).event("companion_ai_start", "generation=" + token);
        ai.reply(prompt, new GrokClient.Callback() {
            @Override public void onResult(String text) { runOnUiThread(() -> {
                if (destroyed || token != generation) return;
                long latency = android.os.SystemClock.elapsedRealtime() - started;
                DiagnosticRecorder.get(CompanionActivity.this).event("companion_ai_done", "ms=" + latency);
                speak(cleanSpeech(text));
            }); }
            @Override public void onError(String error) { runOnUiThread(() -> {
                if (destroyed || token != generation) return;
                DiagnosticRecorder.get(CompanionActivity.this).event("companion_ai_error", error);
                detail.setText(error);
                turns.forceListen();
            }); }
        });
    }

    private void speak(String text) {
        if (text == null || text.isBlank()) { turns.forceListen(); return; }
        reply.setText(text);
        turns.onAiWillSpeak();
        if (!voiceReady || !speaker.isReady()) {
            queuedSpeech = text;
            state.setText("Готовлю голос…");
            speaker.prepare();
            return;
        }
        speakNow(text);
    }

    private void speakNow(String text) {
        turns.onAiWillSpeak();
        PartyMusic.get(this).duck(true);
        orb.setState("talking");
        speaker.speak(text, "neutral", .58f);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        String sttKey=getSharedPreferences("martin",0).getString("stt_key","");
        String aiKey=getSharedPreferences("martin",0).getString("ai_key","");
        if(!sttKey.startsWith("gsk_")&&!aiKey.startsWith("gsk_")){
            detail.setText("Добавь Groq key в настройках");
            return;
        }
        generation++;
        active = true;
        conversation.reset();
        turns.forceListen();
        audio.start();
        mic.setText("● СЛУШАЮ");
        state.setText("Слушаю…");
        detail.setText("Скажи «Сергей…», затем можно продолжать без имени");
        orb.setState("listening");
        DiagnosticRecorder.get(this).event("companion_listening_start", "generation=" + generation);
    }

    private void stopListening() {
        active = false;
        cancelCurrent(true);
        if (audio != null) audio.stop();
        state.setText("Готов");
        detail.setText("Нажми «Начать»");
        mic.setText("🎙  НАЧАТЬ");
        orb.setLevel(0f);
        orb.setState("idle");
    }

    private void cancelCurrent(boolean resetConversation) {
        generation++;
        queuedSpeech = null;
        if (ai != null) ai.cancel();
        if (stt != null) stt.cancel();
        if (speaker != null) speaker.stop();
        turns.forceListen();
        PartyMusic.get(this).duck(false);
        if (resetConversation) conversation.reset();
        DiagnosticRecorder.get(this).event("companion_cancel", "reset=" + resetConversation + ";generation=" + generation);
    }

    private void onTurnState(TurnManager.State s) {
        DiagnosticRecorder.get(this).event("companion_turn", s.name());
        switch (s) {
            case LISTENING: orb.setState(active ? "listening" : "idle"); if(active) state.setText("Слушаю…"); break;
            case THINKING: orb.setState("thinking"); state.setText("Думаю…"); break;
            case SPEAKING: orb.setState("talking"); state.setText("Говорю…"); break;
            case COOLDOWN: orb.setState("listening"); break;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && pendingStart) {
            pendingStart = false;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening();
            else detail.setText("Без микрофона голосовой режим недоступен");
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (audio != null) audio.stop();
        if (stt != null) stt.close();
        if (ai != null) ai.close();
        if (speaker != null) speaker.close();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        root.setBackground(gradient(0xFF05060B, 0xFF0D0820, 0xFF05060B, 0));

        TextView title = text("СЕРГЕЙ", 30, Color.WHITE, Typeface.BOLD);
        title.setLetterSpacing(.08f); title.setGravity(Gravity.CENTER); root.addView(title);
        TextView tag = text("PERSONAL AI • COMPANION CORE v1", 10, 0xFFAD91FF, Typeface.BOLD);
        tag.setLetterSpacing(.08f); tag.setGravity(Gravity.CENTER); root.addView(tag);

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(gradient(0xFF070812, 0xFF130D28, 0xFF070811, 30));
        orb = new VoiceOrbView(this); hero.addView(orb, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1,0,1); hp.setMargins(0,dp(14),0,dp(12)); root.addView(hero,hp);

        state=text("Готов",21,Color.WHITE,Typeface.BOLD);state.setGravity(Gravity.CENTER);root.addView(state);
        detail=text("Нажми «Начать»",12,0xFF9D98AA,Typeface.NORMAL);detail.setGravity(Gravity.CENTER);root.addView(detail);
        heard=text("",11,0xFF777181,Typeface.NORMAL);heard.setGravity(Gravity.CENTER);root.addView(heard);
        reply=text("",13,0xFFD8C9FF,Typeface.NORMAL);reply.setGravity(Gravity.CENTER);reply.setMaxLines(5);reply.setPadding(dp(8),dp(8),dp(8),dp(8));root.addView(reply);

        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER);
        Button settings=smallButton("⚙ Настройки");settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));tools.addView(settings,new LinearLayout.LayoutParams(0,dp(44),1));
        Button logs=smallButton("Диагностика");logs.setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));tools.addView(logs,new LinearLayout.LayoutParams(0,dp(44),1));
        root.addView(tools,new LinearLayout.LayoutParams(-1,dp(48)));

        mic=new Button(this);mic.setText("🎙  НАЧАТЬ");mic.setTextColor(Color.WHITE);mic.setTextSize(16);mic.setTypeface(Typeface.DEFAULT_BOLD);mic.setAllCaps(false);mic.setBackground(gradient(0xFF6A28FF,0xFF8A3DFF,0xFF5622DD,24));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(60));mp.setMargins(0,dp(8),0,0);root.addView(mic,mp);
        mic.setOnClickListener(v->{if(active)stopListening();else startListening();});
        setContentView(root);orb.setState("idle");
    }

    private String cleanSpeech(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[*#_`~>]", "").replaceAll("\\s+", " ").trim();
    }
    private TextView text(String s,int sp,int color,int style){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTypeface(Typeface.create(Typeface.DEFAULT,style));return v;}
    private Button smallButton(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(11);b.setAllCaps(false);b.setBackground(round(0xFF171322,18));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable gradient(int a,int b,int c,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{a,b,c});g.setCornerRadius(dp(radius));return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
