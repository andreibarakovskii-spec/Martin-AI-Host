package com.imagine.martinhost;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.godotengine.godot.Godot;
import org.godotengine.godot.GodotFragment;
import org.godotengine.godot.GodotHost;
import org.godotengine.godot.plugin.GodotPlugin;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/** Party-ready launcher: real Godot 3D + continuous audio + local camera gaze + one complete voice game. */
public final class PremiumMainActivity extends FragmentActivity implements GodotHost {
    private static final int REQ = 17;

    private MartinNeuralSpeaker neural;
    private GrokClient grok;
    private GroqTranscriber stt;
    private TurnManager turns;
    private ContinuousSpeechEngine audio;
    private MartinFaceTracker faceTracker;
    private PartyDirector director;

    private GodotFragment godotFragment;
    private MartinHostPlugin martinPlugin;
    private FrameLayout godotContainer;
    private String pendingMartinState = "idle";
    private float pendingMartinSpeech = 0f;
    private float pendingLookX = 0f, pendingLookY = 0f;
    private float pendingEnergy = .55f;
    private String pendingEmotion = "neutral";

    private TextView state, heard, reply, aiDot, voiceDot, cameraDot, subline;
    private Button mic;
    private boolean active, neuralReady;
    private String queuedSpeech;
    private String queuedEmotion = "neutral";
    private float queuedEnergy = .55f;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF05060B);
        getWindow().setNavigationBarColor(0xFF05060B);
        buildUi();
        attachGodotRenderer();

        PartyAudioRouter.prepare(this);
        grok = new GrokClient(this);
        stt = new GroqTranscriber(this);
        director = new PartyDirector(this);
        turns = new TurnManager(s -> runOnUiThread(() -> onTurnState(s)));
        turns.setReleaseTailMs(110L);

        audio = new ContinuousSpeechEngine(this, turns, new ContinuousSpeechEngine.Listener() {
            @Override public void onSpeechChunk(byte[] wav) {
                turns.onUserFinal();
                stt.transcribe(wav, new GroqTranscriber.Callback() {
                    @Override public void onText(String text) { runOnUiThread(() -> handleTranscript(text)); }
                    @Override public void onError(String e) { runOnUiThread(() -> {
                        state.setText("Не расслышал"); subline.setText("Скажи ещё раз"); turns.forceListen();
                    }); }
                });
            }
            @Override public void onLevel(float rms, float noise, boolean speech) {
                runOnUiThread(() -> { if (turns.acceptMicForStt()) setMartinSpeech(Math.max(0f, Math.min(1f, (rms + 48f) / 36f))); });
            }
            @Override public void onStatus(String s) { }
            @Override public void onError(String e) { runOnUiThread(() -> subline.setText("Проверь микрофон")); }
        });

        neural = new MartinNeuralSpeaker(this, new MartinNeuralSpeaker.Listener() {
            @Override public void onPreparing(String m) { runOnUiThread(() -> { setVoiceDot(false); subline.setText(m); }); }
            @Override public void onReady() { runOnUiThread(() -> {
                neuralReady = true;
                getSharedPreferences("martin", 0).edit().putBoolean("voice_model_ready", true).apply();
                setVoiceDot(true);
                if (queuedSpeech != null) {
                    String q = queuedSpeech; String e = queuedEmotion; float en = queuedEnergy; queuedSpeech = null;
                    speakNow(q, e, en);
                } else if (!active) subline.setText("Скажи: «Мартин, привет»");
            }); }
            @Override public void onStart() { runOnUiThread(() -> { setMartinState("talking"); state.setText("Говорю…"); }); }
            @Override public void onLevel(float level) { runOnUiThread(() -> setMartinSpeech(level)); }
            @Override public void onDone() { runOnUiThread(() -> {
                setMartinSpeech(0f); setMartinState("happy");
                if (martinPlugin != null) martinPlugin.triggerAction("happy");
                turns.onAiSpeechDone();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> {
                neuralReady = false; setVoiceDot(false); state.setText("Голос готовится"); subline.setText(message); turns.onAiSpeechDone();
            }); }
        });

        faceTracker = new MartinFaceTracker(this, new MartinFaceTracker.Listener() {
            @Override public void onLook(float x, float y, boolean faceVisible) { runOnUiThread(() -> setMartinLook(x, y)); }
            @Override public void onStatus(boolean ok, String message) { runOnUiThread(() -> setCameraDot(ok, message)); }
        });

        neural.prepare();
        requestPerms();
        refreshStatus();
        startFaceTrackerIfAllowed();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(7), dp(14), dp(10));
        root.setBackground(gradient(0xFF05060B, 0xFF090612, 0xFF05060B, 0));

        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        Button menu = iconButton("☰"); top.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titleBox = new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL); titleBox.setGravity(Gravity.CENTER);
        TextView title = text("MARTIN", 28, Color.WHITE, Typeface.BOLD); title.setLetterSpacing(.06f); title.setGravity(Gravity.CENTER);
        TextView tag = text("AI HOST • LIVE 3D", 10, 0xFF9B5CFF, Typeface.BOLD); tag.setLetterSpacing(.12f); tag.setGravity(Gravity.CENTER);
        titleBox.addView(title); titleBox.addView(tag); top.addView(titleBox, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button gear = iconButton("⚙"); top.addView(gear, new LinearLayout.LayoutParams(dp(52), dp(52))); root.addView(top);

        LinearLayout statusRow = new LinearLayout(this); statusRow.setGravity(Gravity.CENTER); statusRow.setPadding(0, dp(3), 0, dp(5));
        aiDot = pill("● AI", 0xFF123E31, 0xFF58E6A9); voiceDot = pill("● ГОЛОС", 0xFF3A2A15, 0xFFFFB35C); cameraDot = pill("● КАМЕРА", 0xFF25252B, 0xFFAAA6B3);
        statusRow.addView(aiDot, new LinearLayout.LayoutParams(dp(70), dp(28))); statusRow.addView(new Space(this), new LinearLayout.LayoutParams(dp(7), 1));
        statusRow.addView(voiceDot, new LinearLayout.LayoutParams(dp(94), dp(28))); statusRow.addView(new Space(this), new LinearLayout.LayoutParams(dp(7), 1));
        statusRow.addView(cameraDot, new LinearLayout.LayoutParams(dp(105), dp(28))); root.addView(statusRow);

        FrameLayout hero = new FrameLayout(this); hero.setBackground(gradient(0xFF070812, 0xFF130D24, 0xFF070811, 28));
        godotContainer = new FrameLayout(this); godotContainer.setId(View.generateViewId()); hero.addView(godotContainer, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = text("RIGGED • CAMERA LOOK", 9, 0xFFB68CFF, Typeface.BOLD); badge.setGravity(Gravity.CENTER); badge.setBackground(round(0x991A1030, 14));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(150), dp(26), Gravity.TOP | Gravity.RIGHT); bp.setMargins(0, dp(10), dp(10), 0); hero.addView(badge, bp);
        root.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1));

        state = text("Готов к вечеринке", 20, Color.WHITE, Typeface.BOLD); state.setGravity(Gravity.CENTER); state.setPadding(0, dp(8), 0, 0); root.addView(state);
        subline = text("Скажи: «Мартин, привет»", 12, 0xFF8D8997, Typeface.NORMAL); subline.setGravity(Gravity.CENTER); root.addView(subline);
        heard = text("", 10, 0xFF6F6A7A, Typeface.NORMAL); heard.setGravity(Gravity.CENTER); heard.setMaxLines(1); root.addView(heard);
        reply = text("", 11, 0xFFCDBAF7, Typeface.NORMAL); reply.setGravity(Gravity.CENTER); reply.setMaxLines(2); reply.setPadding(dp(8), dp(3), dp(8), dp(3)); root.addView(reply);

        mic = new Button(this); mic.setText("🎙  НАЧАТЬ"); mic.setTextColor(Color.WHITE); mic.setTextSize(16); mic.setTypeface(Typeface.DEFAULT_BOLD); mic.setAllCaps(false); mic.setBackground(gradient(0xFF6A28FF, 0xFF8A3DFF, 0xFF5622DD, 24));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(58)); mp.setMargins(0, dp(6), 0, dp(7)); root.addView(mic, mp);

        LinearLayout nav = new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(2), dp(3), dp(2), dp(3)); nav.setBackground(round(0xFF11121A, 22));
        addNav(nav, "⌂", "Главная", true, null);
        addNav(nav, "🎮", "Игра", false, v -> runDirectorAction(director.startChgk()));
        addNav(nav, "♫", "Музыка", false, v -> startActivity(new Intent(this, MusicActivity.class)));
        addNav(nav, "★", "Счёт", false, v -> startActivity(new Intent(this, RankingActivity.class)));
        addNav(nav, "⚙", "Настройки", false, v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(66)));

        setContentView(root);
        menu.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        mic.setOnClickListener(v -> { if (active) stopAudio(); else startAudio(); });
    }

    private void attachGodotRenderer() {
        Fragment existing = getSupportFragmentManager().findFragmentById(godotContainer.getId());
        if (existing instanceof GodotFragment) godotFragment = (GodotFragment) existing;
        else {
            godotFragment = new GodotFragment();
            getSupportFragmentManager().beginTransaction().replace(godotContainer.getId(), godotFragment, "martin_godot").commitNowAllowingStateLoss();
        }
    }

    @Override public Activity getActivity() { return this; }
    @Override public Godot getGodot() { return godotFragment == null ? null : godotFragment.getGodot(); }
    @Override public Set<GodotPlugin> getHostPlugins(Godot godot) {
        if (martinPlugin == null) martinPlugin = new MartinHostPlugin(godot);
        godotContainer.postDelayed(this::syncMartin, 700);
        return Set.of(martinPlugin);
    }

    private void syncMartin() {
        if (martinPlugin == null) return;
        martinPlugin.setState(pendingMartinState); martinPlugin.setSpeechLevel(pendingMartinSpeech);
        martinPlugin.setLook(pendingLookX, pendingLookY); martinPlugin.setEnergy(pendingEnergy); martinPlugin.setEmotion(pendingEmotion);
    }
    private void setMartinState(String v) { pendingMartinState = v == null ? "idle" : v; if (martinPlugin != null) martinPlugin.setState(pendingMartinState); }
    private void setMartinSpeech(float v) { pendingMartinSpeech = Math.max(0f, Math.min(1f, v)); if (martinPlugin != null) martinPlugin.setSpeechLevel(pendingMartinSpeech); }
    private void setMartinLook(float x,float y){ pendingLookX=x; pendingLookY=y; if(martinPlugin!=null)martinPlugin.setLook(x,y); }
    private void setMartinEmotion(String e){ pendingEmotion=e==null?"neutral":e; if(martinPlugin!=null)martinPlugin.setEmotion(pendingEmotion); }
    private void setMartinEnergy(float e){ pendingEnergy=Math.max(0f,Math.min(1f,e)); if(martinPlugin!=null)martinPlugin.setEnergy(pendingEnergy); }

    private void handleTranscript(String t) {
        if (t == null || t.isBlank()) { turns.forceListen(); return; }
        heard.setText("Вы: " + t);
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("мартин стоп") || low.equals("стоп")) { stopAudio(); return; }

        // During a game guests answer naturally, without repeating the wake word every time.
        if (director.mode() != PartyDirector.Mode.FREE) {
            runDirectorAction(director.onUserText(t));
            return;
        }

        int x = low.indexOf("мартин");
        if (x < 0) { turns.forceListen(); return; }
        String q = t.substring(Math.min(t.length(), x + 6)).replaceFirst("^[,.:;!?\\s-]+", "");
        if (q.isBlank()) q = "Поздоровайся с компанией естественно и коротко.";
        runDirectorAction(director.onUserText(q));
    }

    private void runDirectorAction(PartyDirector.Action a) {
        if (a == null) { turns.forceListen(); return; }
        setMartinState(a.state); setMartinEmotion(a.emotion); setMartinEnergy(energyFor(a.emotion));
        if (martinPlugin != null && a.gesture != null && !a.gesture.isBlank()) martinPlugin.triggerAction(mapGesture(a.gesture));
        if (a.askAi) {
            turns.onUserFinal(); setMartinState("thinking"); state.setText("Думаю…"); subline.setText("Формулирую ответ");
            grok.reply(a.speech, new GrokClient.Callback() {
                @Override public void onResult(String raw) { runOnUiThread(() -> speak(cleanSpeech(raw), a.emotion, energyFor(a.emotion))); }
                @Override public void onError(String e) { runOnUiThread(() -> { state.setText("Нет связи с AI"); subline.setText("Проверь Groq в настройках"); turns.forceListen(); }); }
            });
        } else speak(a.speech, a.emotion, energyFor(a.emotion));
    }

    private static String mapGesture(String g) {
        String s = g == null ? "" : g.toLowerCase(Locale.ROOT);
        if (s.contains("dance")) return "dance";
        if (s.contains("walk")) return "walk";
        if (s.contains("run")) return "run";
        if (s.contains("toast") || s.contains("glass")) return "toast";
        if (s.contains("celebrate") || s.contains("cheer") || s.contains("point") || s.contains("explain") || s.contains("question")) return "happy";
        return "idle";
    }

    private float energyFor(String emotion) {
        String e = emotion == null ? "" : emotion.toLowerCase(Locale.ROOT);
        if (e.contains("happy") || e.contains("excited") || e.contains("playful")) return .82f;
        if (e.contains("warm")) return .55f;
        if (e.contains("focused") || e.contains("curious")) return .62f;
        return .55f;
    }

    private void speak(String text, String emotion, float energy) {
        if (text == null || text.isBlank()) { turns.forceListen(); return; }
        reply.setText(text); setMartinEmotion(emotion); setMartinEnergy(energy);
        if (!neuralReady || !neural.isReady()) {
            queuedSpeech = text; queuedEmotion = emotion; queuedEnergy = energy;
            state.setText("Готовлю голос…"); subline.setText("Нейроголос загружается один раз"); neural.prepare(); return;
        }
        speakNow(text, emotion, energy);
    }

    private void speakNow(String text,String emotion,float energy) {
        turns.onAiWillSpeak(); setMartinState("talking"); setMartinEmotion(emotion); setMartinEnergy(energy);
        state.setText("Говорю…"); subline.setText("Мартин отвечает"); neural.speak(text, emotion, energy);
    }

    private void onTurnState(TurnManager.State s) {
        if (!active && director.mode() == PartyDirector.Mode.FREE) return;
        switch (s) {
            case LISTENING: setMartinState("listening"); state.setText("Слушаю…"); subline.setText(director.mode()==PartyDirector.Mode.FREE?"Скажи: «Мартин…»":"Отвечайте вслух"); break;
            case THINKING: setMartinState("thinking"); state.setText("Думаю…"); break;
            case SPEAKING: setMartinState("talking"); state.setText("Говорю…"); break;
            case COOLDOWN: state.setText("Слушаю…"); break;
        }
    }

    private void startAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPerms(); return; }
        active = true; turns.forceListen(); audio.start(); setMartinState("listening"); state.setText("Слушаю…"); subline.setText("Скажи: «Мартин…»"); mic.setText("●  СЛУШАЮ");
    }
    private void stopAudio() {
        active = false; audio.stop(); if (neural != null) neural.stop(); turns.forceListen(); setMartinSpeech(0f); setMartinState("idle"); state.setText("Готов к вечеринке"); subline.setText("Скажи: «Мартин, привет»"); mic.setText("🎙  НАЧАТЬ");
    }

    private void startFaceTrackerIfAllowed(){ if(faceTracker!=null && checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) faceTracker.start(); }
    private void setCameraDot(boolean ok,String message){ cameraDot.setText(ok?"● КАМЕРА":"● КАМЕРА?"); cameraDot.setTextColor(ok?0xFF58E6A9:0xFFFFB35C); cameraDot.setContentDescription(message); }

    private void refreshStatus() {
        String key = getSharedPreferences("martin", 0).getString("ai_key", ""); long ok = getSharedPreferences("martin", 0).getLong("groq_last_ok", 0);
        boolean ai = key.startsWith("gsk_") && ok > 0; aiDot.setText(ai ? "● AI" : "● AI?"); aiDot.setTextColor(ai ? 0xFF58E6A9 : 0xFFFFB35C);
        setVoiceDot(neuralReady || getSharedPreferences("martin", 0).getBoolean("voice_model_ready", false));
        setCameraDot(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED, "Локальное распознавание лица");
    }
    private void setVoiceDot(boolean ok) { voiceDot.setText(ok ? "● ГОЛОС" : "● ГОЛОС?"); voiceDot.setTextColor(ok ? 0xFF58E6A9 : 0xFFFFB35C); }

    private void requestPerms() {
        ArrayList<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.RECORD_AUDIO);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.CAMERA);
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ);
    }
    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) { super.onRequestPermissionsResult(r,p,g); if(r==REQ){ startFaceTrackerIfAllowed(); refreshStatus(); if(active)startAudio(); } }
    @Override protected void onResume(){ super.onResume(); refreshStatus(); if(neural!=null&&!neuralReady)neural.prepare(); startFaceTrackerIfAllowed(); godotContainer.postDelayed(this::syncMartin,500); }
    @Override protected void onDestroy(){ if(audio!=null)audio.stop(); if(stt!=null)stt.close(); if(neural!=null)neural.close(); if(faceTracker!=null)faceTracker.close(); martinPlugin=null; super.onDestroy(); }

    private String cleanSpeech(String raw) { if(raw==null)return ""; String s=raw.replaceAll("\\[\\[[^\\]]*\\]\\]",""); s=s.replaceAll("[*#_`~>]",""); s=s.replaceAll("\\[(.*?)\\]\\((.*?)\\)","$1"); return s.replaceAll("\\s+"," ").trim(); }
    private void addNav(LinearLayout nav,String icon,String label,boolean selected,View.OnClickListener click){ LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(0,dp(3),0,dp(2));if(selected)box.setBackground(round(0xFF20163A,18));TextView i=text(icon,17,selected?0xFF9D63FF:0xFFE5E2EC,Typeface.NORMAL);i.setGravity(Gravity.CENTER);TextView l=text(label,9,selected?0xFFB987FF:0xFFE5E2EC,selected?Typeface.BOLD:Typeface.NORMAL);l.setGravity(Gravity.CENTER);box.addView(i);box.addView(l);if(click!=null)box.setOnClickListener(click);nav.addView(box,new LinearLayout.LayoutParams(0,-1,1)); }
    private TextView text(String s,int sp,int color,int style){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setTypeface(Typeface.create(Typeface.DEFAULT,style));return v;}
    private TextView pill(String s,int bg,int fg){TextView v=text(s,10,fg,Typeface.BOLD);v.setGravity(Gravity.CENTER);v.setBackground(round(bg,16));return v;}
    private Button iconButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(20);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(round(0xFF15111F,19));return b;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable gradient(int a,int b,int c,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{a,b,c});g.setCornerRadius(dp(radius));return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
