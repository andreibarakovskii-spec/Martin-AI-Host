package com.imagine.martinhost;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Voice-first party host. The previous 3D Martin renderer is intentionally archived:
 * the active UI is a lightweight circular audio visualizer driven by microphone/TTS levels.
 */
public final class PremiumMainActivity extends FragmentActivity {
    private static final int REQ = 17;

    private MartinSpeaker neural;
    private GrokClient grok;
    private GroqTranscriber stt;
    private TurnManager turns;
    private ContinuousSpeechEngine audio;
    private MartinFaceTracker faceTracker;
    private PartyDirector director;

    private VoiceOrbView voiceOrb;
    private TextView state, heard, reply, aiDot, voiceDot, cameraDot, subline;
    private Button mic;
    private boolean active, neuralReady, destroyed, cameraEnabled, pendingAudioStart;
    private volatile boolean faceVisible;
    private volatile int currentFaceId=-1,currentFaceCount=0;
    private final Map<Integer,String> faceNames=new HashMap<>();
    private final GuestAppearanceMemory appearanceMemory=new GuestAppearanceMemory();
    private volatile float[] currentAppearance;
    private volatile long lastPersonCallbackAt;
    private volatile int session;
    private String pendingClip;
    private String pendingMusicRequest;
    private String visualQuestion, pendingCameraQuestion;
    private int visualSession;
    private TextView cameraHelp;
    private String queuedSpeech;
    private String activeVoiceProvider;
    private String queuedEmotion = "neutral";
    private float queuedEnergy = .55f;
    private final android.os.Handler partyTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private long lastHumanAt;
    private final Runnable autoGameCheck=new Runnable(){public void run(){maybeOfferGame();if(!destroyed)partyTimer.postDelayed(this,60000);}};

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF05060B);
        getWindow().setNavigationBarColor(0xFF05060B);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cameraEnabled=getSharedPreferences("martin",0).getBoolean("camera_enabled",false);
        buildUi();

        PartyAudioRouter.prepare(this);
        grok = new GrokClient(this);
        stt = new GroqTranscriber(this);
        director = new PartyDirector(this);
        turns = new TurnManager(s -> runOnUiThread(() -> onTurnState(s)));
        turns.setReleaseTailMs(PartyAudioRouter.recommendedReleaseTailMs(this));

        audio = new ContinuousSpeechEngine(this, turns, new ContinuousSpeechEngine.Listener() {
            @Override public void onSpeechChunk(byte[] wav) {
                final int token=session;
                turns.onUserFinal();
                stt.transcribe(wav, new GroqTranscriber.Callback() {
                    @Override public void onText(String text) { runOnUiThread(() -> {if(active && token==session)handleTranscript(text);}); }
                    @Override public void onError(String e) { runOnUiThread(() -> {
                        if(!active || token!=session)return;
                        state.setText("Не расслышал");
                        subline.setText(e);
                        setVoiceLevel(0f);
                        turns.forceListen();
                    }); }
                });
            }

            @Override public void onLevel(float rms, float noise, boolean speech) {
                runOnUiThread(() -> {
                    if(active){String label=DiagnosticRecorder.get(PremiumMainActivity.this).active()?"● СЛУШАЮ • ЗАПИСЬ ЛОГА":"● СЛУШАЮ";if(!mic.getText().toString().equals(label))mic.setText(label);}
                    if (turns.acceptMicForStt()) {
                        float level = Math.max(0f, Math.min(1f, (rms + 48f) / 36f));
                        setVoiceLevel(level);
                    }
                });
            }
            @Override public void onStatus(String s) { }
            @Override public void onError(String e) { runOnUiThread(() -> subline.setText("Проверь микрофон")); }
        });

        activeVoiceProvider=getSharedPreferences("martin",0).getString("voice_provider","local");
        neural = MartinSpeakerFactory.create(this, new MartinSpeaker.Listener() {
            @Override public void onPreparing(String m) { runOnUiThread(() -> { setVoiceDot(false); subline.setText(m); }); }
            @Override public void onReady() { runOnUiThread(() -> {
                if(destroyed)return;
                neuralReady = true;
                getSharedPreferences("martin", 0).edit().putBoolean("voice_model_ready", true).apply();
                setVoiceDot(true);
                if (queuedSpeech != null) {
                    String q = queuedSpeech;
                    String e = queuedEmotion;
                    float en = queuedEnergy;
                    queuedSpeech = null;
                    speakNow(q, e, en);
                } else if (!active) {
                    subline.setText("Нажми «Начать» и говори естественно");
                }
            }); }
            @Override public void onStart() { runOnUiThread(() -> {
                setVisualState("talking");
                state.setText("Говорю…");
            }); }
            @Override public void onLevel(float level) { runOnUiThread(() -> setVoiceLevel(level)); }
            @Override public void onSpectrum(float[] bands) { runOnUiThread(() -> voiceOrb.setSpectrum(bands)); }
            @Override public void onDone() { runOnUiThread(() -> {
                setVoiceLevel(0f);
                setVisualState(active ? "listening" : "idle");
                PartyMusic.get(PremiumMainActivity.this).duck(false);
                if(pendingMusicRequest!=null){String q=pendingMusicRequest;pendingMusicRequest=null;MusicRequestRouter.play(PremiumMainActivity.this,q);turns.onAiSpeechDone();}
                else if(pendingClip!=null){String u=pendingClip;pendingClip=null;final int token=session;
                    turns.onAiWillSpeak();state.setText("Слушаем фрагмент…");
                    PartyMusic.get(PremiumMainActivity.this).clip(u,()->{if(token==session&&!destroyed)turns.onAiSpeechDone();});
                }else turns.onAiSpeechDone();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> {
                neuralReady = false;
                setVoiceDot(false);
                setVoiceLevel(0f);
                PartyMusic.get(PremiumMainActivity.this).duck(false);
                queuedSpeech=null;pendingClip=null;
                state.setText("Голос недоступен");
                subline.setText(message);
                turns.onAiSpeechDone();
            }); }
        });

        faceTracker = new MartinFaceTracker(this, new MartinFaceTracker.Listener() {
            @Override public void onLook(float x, float y, boolean visible) {
                faceVisible=visible;
            }
            @Override public void onPerson(int trackingId,int count,boolean visible,float[] appearance){
                long now=android.os.SystemClock.elapsedRealtime();
                int previousId=currentFaceId,previousCount=currentFaceCount;
                String previousKnown=previousId>=0?faceNames.get(previousId):null;
                long gap=lastPersonCallbackAt==0?Long.MAX_VALUE:now-lastPersonCallbackAt;
                currentFaceId=trackingId;currentFaceCount=count;currentAppearance=appearance;lastPersonCallbackAt=now;
                if(trackingId>=0&&!faceNames.containsKey(trackingId)){
                    String restored=appearanceMemory.match(appearance);
                    if(restored.isBlank()&&count==1&&previousCount==1&&previousKnown!=null&&!previousKnown.isBlank()&&gap<1200){
                        restored=previousKnown;
                        DiagnosticRecorder.get(PremiumMainActivity.this).event("camera_guest_continuity","old_tracking_id="+previousId+";new_tracking_id="+trackingId+";name="+restored+";gap_ms="+gap);
                    }
                    if(!restored.isBlank())faceNames.put(trackingId,restored);
                }
                String known=trackingId>=0?faceNames.get(trackingId):null;
                if(known!=null&&!known.isBlank())appearanceMemory.remember(known,appearance);
                runOnUiThread(()->{if(!cameraEnabled)return;cameraHelp.setText(!visible?"Камера: человек вне кадра":count>1?"Камера: несколько людей — спрошу имя":known==null||known.isBlank()?"Камера: одежда и силуэт вижу • имя пока неизвестно":"Камера: "+known);});
            }
            @Override public void onStatus(boolean ok, String message) { runOnUiThread(() -> {setCameraDot(ok,message);if(!ok&&visualQuestion!=null){visualQuestion=null;subline.setText(message);turns.forceListen();}}); }
            @Override public void onFrame(byte[] jpeg){runOnUiThread(()->{
                if(visualQuestion==null||visualSession!=session||destroyed)return;
                final String q=visualQuestion;visualQuestion=null;final int token=session;state.setText("Смотрю на кадр…");
                grok.replyWithImage(q,jpeg,new GrokClient.Callback(){public void onResult(String text){runOnUiThread(()->{if(token==session&&!destroyed)speak(cleanSpeech(text),"curious",.6f);});}
                    public void onError(String e){runOnUiThread(()->{if(token==session&&!destroyed){subline.setText(e);turns.forceListen();}});}});
            });}
        });

        neural.prepare();
        refreshStatus();
        startFaceTrackerIfAllowed();
        handleIntent(getIntent());
        long now=System.currentTimeMillis();if(getSharedPreferences("martin",0).getLong("last_auto_game_offer",0)==0)getSharedPreferences("martin",0).edit().putLong("last_auto_game_offer",now).apply();lastHumanAt=now;partyTimer.postDelayed(autoGameCheck,60000);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(7), dp(14), dp(10));
        root.setBackground(gradient(0xFF05060B, 0xFF090612, 0xFF05060B, 0));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button menu = iconButton("☰");
        top.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER);
        TextView title = text("СЕРГЕЙ", 28, Color.WHITE, Typeface.BOLD);
        title.setLetterSpacing(.06f);
        title.setGravity(Gravity.CENTER);
        TextView tag = text("AI HOST • VOICE LIVE", 10, 0xFF9B7CFF, Typeface.BOLD);
        tag.setLetterSpacing(.12f);
        tag.setGravity(Gravity.CENTER);
        titleBox.addView(title);
        titleBox.addView(tag);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, dp(56), 1));
        Button gear = iconButton("⚙");
        top.addView(gear, new LinearLayout.LayoutParams(dp(52), dp(52)));
        root.addView(top);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER);
        statusRow.setPadding(0, dp(3), 0, dp(5));
        aiDot = pill("● AI", 0xFF123E31, 0xFF58E6A9);
        voiceDot = pill("● ГОЛОС", 0xFF3A2A15, 0xFFFFB35C);
        cameraDot = pill("● КАМЕРА", 0xFF25252B, 0xFFAAA6B3);
        statusRow.addView(aiDot, new LinearLayout.LayoutParams(dp(70), dp(28)));
        statusRow.addView(new Space(this), new LinearLayout.LayoutParams(dp(7), 1));
        statusRow.addView(voiceDot, new LinearLayout.LayoutParams(dp(94), dp(28)));
        statusRow.addView(new Space(this), new LinearLayout.LayoutParams(dp(7), 1));
        statusRow.addView(cameraDot, new LinearLayout.LayoutParams(dp(105), dp(28)));
        root.addView(statusRow);

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(gradient(0xFF070812, 0xFF100B20, 0xFF070811, 28));
        voiceOrb = new VoiceOrbView(this);
        FrameLayout.LayoutParams orbParams = new FrameLayout.LayoutParams(-1, -1);
        orbParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        hero.addView(voiceOrb, orbParams);

        TextView badge = text("VOICE • LOCAL CAMERA", 9, 0xFFBCA8FF, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(0x991A1030, 14));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(150), dp(26), Gravity.TOP | Gravity.RIGHT);
        bp.setMargins(0, dp(10), dp(10), 0);
        hero.addView(badge, bp);
        root.addView(hero, new LinearLayout.LayoutParams(-1, 0, 1));

        state = text("Готов", 20, Color.WHITE, Typeface.BOLD);
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, dp(8), 0, 0);
        root.addView(state);
        subline = text("Нажми «Начать» и говори естественно", 12, 0xFF8D8997, Typeface.NORMAL);
        subline.setGravity(Gravity.CENTER);
        root.addView(subline);
        heard = text("", 10, 0xFF6F6A7A, Typeface.NORMAL);
        heard.setGravity(Gravity.CENTER);
        heard.setMaxLines(1);
        root.addView(heard);
        reply = text("", 11, 0xFFCDBAF7, Typeface.NORMAL);
        reply.setGravity(Gravity.CENTER);
        reply.setMaxLines(4);
        reply.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        reply.setPadding(dp(8), dp(3), dp(8), dp(3));
        root.addView(reply);
        cameraHelp=text("Камера выключена • нажмите индикатор для включения",10,0xFF9691A5,Typeface.NORMAL);cameraHelp.setGravity(Gravity.CENTER);root.addView(cameraHelp);
        cameraDot.setOnClickListener(v->toggleCamera());
        LinearLayout actions=new LinearLayout(this);
        for(String label:new String[]{"Текст","Дальше","Засчитать","Ответ"}){Button b=iconButton(label);b.setTextSize(10);actions.addView(b,new LinearLayout.LayoutParams(0,dp(42),1));b.setOnClickListener(v->{
            if(label.equals("Текст")){textInput();return;}
            cancelCurrent();
            director.setSuggestedGuest(currentCameraGuest());
            runDirectorAction(label.equals("Дальше")?director.next():label.equals("Засчитать")?director.award():director.reveal());
        });}root.addView(actions);
        reply.setOnLongClickListener(v->{new android.app.AlertDialog.Builder(this).setMessage(reply.getText()).setPositiveButton("Закрыть",null).show();return true;});

        mic = new Button(this);
        mic.setText("🎙  НАЧАТЬ");
        mic.setTextColor(Color.WHITE);
        mic.setTextSize(16);
        mic.setTypeface(Typeface.DEFAULT_BOLD);
        mic.setAllCaps(false);
        mic.setBackground(gradient(0xFF6A28FF, 0xFF8A3DFF, 0xFF5622DD, 24));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(58));
        mp.setMargins(0, dp(6), 0, dp(7));
        root.addView(mic, mp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(2), dp(3), dp(2), dp(3));
        nav.setBackground(round(0xFF11121A, 22));
        addNav(nav, "⌂", "Главная", true, null);
        addNav(nav, "🎮", "Игры", false, v -> startActivity(new Intent(this, GamesActivity.class)));
        addNav(nav, "♫", "Музыка", false, v -> startActivity(new Intent(this, MusicActivity.class)));
        addNav(nav, "★", "Счёт", false, v -> startActivity(new Intent(this, RankingActivity.class)));
        addNav(nav, "⚙", "Настройки", false, v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(66)));

        setContentView(root);
        YandexMusicPlayback.attach(this,root);
        menu.setOnClickListener(v -> new android.app.AlertDialog.Builder(this).setTitle("Ведущий")
            .setItems(new String[]{"Приветствие","Тост для Кати","Закончить конкурс","Очистить память диалога","Посмотри в камеру","Диагностика: лог + аудио"},(d,w)->{cancelCurrent();if(w==5){startActivity(new Intent(this,DiagnosticsActivity.class));return;}if(w==4){requestVisualReply("Что сейчас видно перед камерой? Ответь коротко и дружелюбно.");return;}if(w==2)runDirectorAction(director.cancel());else if(w==3){grok.clearHistory();turns.forceListen();reply.setText("История диалога очищена");}else handleTranscript(w==0?"Поздоровайся с Катей и гостями, предложи начать праздник":"тост для Кати");}).show());
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        mic.setOnClickListener(v -> { if (active || queuedSpeech!=null || (turns!=null && turns.state()!=TurnManager.State.LISTENING)) stopAudio(); else startAudio(); });
        setVisualState("idle");
    }

    private void setVoiceLevel(float value) {
        if (voiceOrb != null) voiceOrb.setLevel(Math.max(0f, Math.min(1f, value)));
    }

    private void setVisualState(String value) {
        if (voiceOrb != null) voiceOrb.setState(value == null ? "idle" : value);
    }

    private void setVisualEmotion(String emotion) {
        if (voiceOrb != null) voiceOrb.setEmotion(emotion == null ? "neutral" : emotion);
    }

    private void setVisualEnergy(float energy) {
        if (voiceOrb != null) voiceOrb.setEnergy(Math.max(0f, Math.min(1f, energy)));
    }

    private void handleTranscript(String t) {
        if (destroyed)return;
        if (t == null || t.isBlank()) { turns.forceListen(); return; }
        if(SttHallucinationFilter.reject(t)){
            DiagnosticRecorder.get(this).event("stt_filtered",t);
            heard.setText("Шум/музыка — пропущено");
            turns.forceListen();
            return;
        }
        heard.setText("Вы: " + t);
        lastHumanAt=System.currentTimeMillis();
        String low = t.toLowerCase(Locale.ROOT);
        if(low.contains("выключи музыку")||low.contains("останови музыку")||low.equals("музыка стоп")){YandexMusicPlayback.stop();speak("Музыка остановлена.","neutral",.45f);return;}
        if (low.contains("сергей стоп") || low.contains("мартин стоп") || low.equals("стоп")) { stopAudio(); return; }

        String musicRequest=MusicRequestRouter.extract(t);
        if(!musicRequest.isBlank()){cancelCurrent();pendingMusicRequest=musicRequest;speak("Ставлю.","playful",.65f);return;}

        if(low.contains("посмотри")||low.contains("что видишь")){requestVisualReply(t);return;}
        if(low.contains("ты видишь")||low.contains("видишь меня")){String known=currentCameraGuest();speak(cameraEnabled?(faceVisible?(known.isBlank()?"Вижу человека, но имя пока не знаю. Как вас зовут?":"Вижу. Сейчас перед камерой "+known+"."):"Сейчас лицо не попало в кадр. Но можем продолжать голосом."):"Камера выключена. Включить её можно нажатием на индикатор камеры.","neutral",.5f);return;}
        DiagnosticRecorder.get(this).event("transcript_accepted",t);
        rememberIntroduction(t);
        if (director.mode() != PartyDirector.Mode.FREE) {
            PartyDirector.Mode before=director.mode();
            director.setSuggestedGuest(currentCameraGuest());
            PartyDirector.Action action=director.onUserText(t);
            if(before==PartyDirector.Mode.WAIT_NAME&&director.mode()==PartyDirector.Mode.RESULT&&!PartyGames.normal(t).equals("без имени")){
                String name=new GuestStore(this).canonicalName(extractScoredName(t));
                if(!name.isBlank())bindCurrentFace(name);
            }
            runDirectorAction(action);
            return;
        }

        int x = low.indexOf("сергей");if(x<0)x=low.indexOf("мартин");
        String q = x >= 0
                ? t.substring(Math.min(t.length(), x + 6)).replaceFirst("^[,.:;!?\\s-]+", "")
                : t.trim();
        if (q.isBlank()) q = "Поздоровайся с компанией естественно и коротко.";
        director.setSuggestedGuest(currentCameraGuest());
        runDirectorAction(director.onUserText(q));
    }

    private String currentCameraGuest(){if(!cameraEnabled||currentFaceCount!=1||currentFaceId<0)return "";String name=faceNames.get(currentFaceId);return name==null?"":name;}
    private void bindCurrentFace(String name){
        if(!cameraEnabled||currentFaceCount!=1||currentFaceId<0||name==null||name.isBlank())return;
        GuestStore store=new GuestStore(this);store.ensureGuest(name);String canonical=store.canonicalName(name);
        if(canonical==null||canonical.isBlank())return;
        faceNames.put(currentFaceId,canonical);appearanceMemory.remember(canonical,currentAppearance);cameraHelp.setText("Камера: "+canonical);DiagnosticRecorder.get(this).event("camera_guest_bound","tracking_id="+currentFaceId+";name="+canonical+";appearance=clothing_pose_transient");
    }
    private void rememberIntroduction(String text){
        if(text==null)return;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?iu)^\\s*(?:меня\\s+зовут|я|это)[\\s,:—-]+([\\p{L}Ёё-]{2,32})\\s*[.!?]?\\s*$").matcher(text.trim());
        if(m.matches()&&GuestStore.isPlausibleNewName(m.group(1)))bindCurrentFace(m.group(1));
    }
    private String extractScoredName(String text){return text.replaceFirst("(?iu)^(это|я|ответил|ответила)\\s+","").replaceAll("[.!?,]$","").trim();}

    private void runDirectorAction(PartyDirector.Action a) {
        if (a == null || destroyed) { turns.forceListen(); return; }
        pendingClip=director.takeMusicUri();
        final int token=session;
        setVisualState(a.state);
        setVisualEmotion(a.emotion);
        setVisualEnergy(energyFor(a.emotion));

        if (a.askAi) {
            turns.onUserFinal();
            setVisualState("thinking");
            state.setText("Думаю…");
            subline.setText("Формулирую ответ");
            grok.reply(a.speech, new GrokClient.Callback() {
                @Override public void onResult(String raw) {
                    runOnUiThread(() -> {if(token==session&&!destroyed)speak(cleanSpeech(raw), a.emotion, energyFor(a.emotion));});
                }
                @Override public void onError(String e) { runOnUiThread(() -> {
                    if(token!=session||destroyed)return;
                    state.setText("Нет связи с AI");
                    subline.setText(e);
                    setVoiceLevel(0f);
                    turns.forceListen();
                }); }
            });
        } else {
            speak(a.speech, a.emotion, energyFor(a.emotion));
        }
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
        turns.onAiWillSpeak();
        reply.setText(text);
        setVisualEmotion(emotion);
        setVisualEnergy(energy);
        if (!neuralReady || !neural.isReady()) {
            queuedSpeech = text;
            queuedEmotion = emotion;
            queuedEnergy = energy;
            state.setText("Готовлю голос…");
            subline.setText("Нейроголос загружается один раз");
            neural.prepare();
            return;
        }
        speakNow(text, emotion, energy);
    }

    private void speakNow(String text, String emotion, float energy) {
        turns.onAiWillSpeak();
        setVisualState("talking");
        setVisualEmotion(emotion);
        setVisualEnergy(energy);
        state.setText("Говорю…");
        subline.setText("Синтез речи");
        PartyMusic.get(this).duck(true);
        neural.speak(text, emotion, energy);
    }

    private void onTurnState(TurnManager.State s) {
        DiagnosticRecorder.get(this).event("turn_state",s.name());
        if (!active && director.mode() == PartyDirector.Mode.FREE) return;
        switch (s) {
            case LISTENING:
                if(director.mode()==PartyDirector.Mode.FREE||director.mode()==PartyDirector.Mode.OFFER){PartyMusic.get(this).ensureBackground();PartyMusic.get(this).listeningVolume();}else PartyMusic.get(this).stopBackground();
                setVisualState("listening");
                state.setText("Слушаю…");
                subline.setText(director.mode() == PartyDirector.Mode.FREE ? "Говори — я слушаю" : "Отвечайте вслух");
                break;
            case THINKING:
                PartyMusic.get(this).duck(true);
                setVisualState("thinking");
                state.setText("Думаю…");
                break;
            case SPEAKING:
                PartyMusic.get(this).duck(true);
                setVisualState("talking");
                state.setText("Говорю…");
                break;
            case COOLDOWN:
                setVisualState("listening");
                state.setText("Слушаю…");
                break;
        }
    }

    private void startAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAudioStart=true;
            requestPerms();
            return;
        }
        if(!getSharedPreferences("martin",0).getBoolean("audio_consent",false)){
            new android.app.AlertDialog.Builder(this).setTitle("Голосовой диалог")
            .setMessage("Предупредите гостей: речь отправляется в Groq для распознавания, текст — выбранному AI. Видео остаётся на телефоне. Пока ведущий говорит, распознавание приостановлено; остановить можно кнопкой. Продолжить?")
            .setPositiveButton("Начать",(d,w)->{getSharedPreferences("martin",0).edit().putBoolean("audio_consent",true).apply();startAudio();}).setNegativeButton("Отмена",null).show();return;}
        String sttKey=getSharedPreferences("martin",0).getString("stt_key","");String aiKey=getSharedPreferences("martin",0).getString("ai_key","");
        if(!sttKey.startsWith("gsk_")&&!aiKey.startsWith("gsk_")){subline.setText("Для голоса укажите Groq key в настройках. Можно использовать кнопку «Текст».");return;}
        session++;
        active = true;
        turns.forceListen();
        audio.start();
        setVisualState("listening");
        state.setText("Слушаю…");
        subline.setText("Говори естественно, без паузы на кнопку");
        mic.setText(DiagnosticRecorder.get(this).active()?"● СЛУШАЮ • ЗАПИСЬ ЛОГА":"●  СЛУШАЮ");
    }

    private void stopAudio() {
        active = false;
        cancelCurrent();
        PartyMusic.get(this).stopClip();
        audio.stop();
        if (neural != null) neural.stop();
        turns.forceListen();
        setVoiceLevel(0f);
        setVisualState("idle");
        state.setText("Готов");
        subline.setText("Нажми «Начать» и говори естественно");
        mic.setText("🎙  НАЧАТЬ");
    }

    private void startFaceTrackerIfAllowed() {
        if (cameraEnabled && faceTracker != null && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            faceTracker.start();
        }
    }

    private void setCameraDot(boolean ok, String message) {
        cameraDot.setText(ok ? "● КАМЕРА" : "● КАМЕРА?");
        cameraDot.setTextColor(ok ? 0xFF58E6A9 : 0xFFFFB35C);
        cameraDot.setContentDescription(message);
    }

    private void refreshStatus() {
        String key = getSharedPreferences("martin", 0).getString("ai_key", "");
        long ok = getSharedPreferences("martin", 0).getLong("groq_last_ok", 0);
        boolean ai = !key.isBlank() && ok > 0;
        aiDot.setText(ai ? "● AI" : "● AI?");
        aiDot.setTextColor(ai ? 0xFF58E6A9 : 0xFFFFB35C);
        setVoiceDot(neuralReady);
        setCameraDot(cameraEnabled && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED, "Локальная привязка имени к отслеживаемому лицу");
    }

    private void setVoiceDot(boolean ok) {
        voiceDot.setText(ok ? "● ГОЛОС" : "● ГОЛОС?");
        voiceDot.setTextColor(ok ? 0xFF58E6A9 : 0xFFFFB35C);
    }

    private void requestPerms() {
        ArrayList<String> p = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.RECORD_AUDIO);
        if (cameraEnabled && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.CAMERA);
        if (android.os.Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), REQ);
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if(r==18){visualQuestion=pendingCameraQuestion;pendingCameraQuestion=null;visualSession=session;if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){startFaceTrackerIfAllowed();faceTracker.requestFrame();}else{visualQuestion=null;turns.forceListen();subline.setText("Нет доступа к камере");}return;}
        if (r == REQ) {
            startFaceTrackerIfAllowed();
            refreshStatus();
            if (pendingAudioStart){pendingAudioStart=false;if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startAudio();}
        }
    }

    @Override protected void onResume() {
        super.onResume();
        String selectedProvider=getSharedPreferences("martin",0).getString("voice_provider","local");
        if(activeVoiceProvider!=null&&!activeVoiceProvider.equals(selectedProvider)){recreate();return;}
        DiagnosticRecorder.get(this).event("activity_resume", "main");
        refreshStatus();
        if (neural != null && (!neuralReady || !neural.isReady())) neural.prepare();
        startFaceTrackerIfAllowed();
        if(director!=null&&director.mode()==PartyDirector.Mode.FREE)PartyMusic.get(this).ensureBackground();
    }

    @Override protected void onDestroy() {
        destroyed=true;YandexMusicPlayback.release();
        partyTimer.removeCallbacksAndMessages(null);
        if(grok!=null)grok.close();
        if(turns!=null)turns.forceListen();
        if (audio != null) audio.stop();
        if (stt != null) stt.close();
        if (neural != null) neural.close();
        if (faceTracker != null) faceTracker.close();
        super.onDestroy();
    }

    private void cancelCurrent(){DiagnosticRecorder.get(this).event("dialogue_cancel","session="+session);session++;visualQuestion=null;PartyMusic.get(this).stopClip();queuedSpeech=null;pendingClip=null;pendingMusicRequest=null;if(stt!=null)stt.cancel();if(grok!=null)grok.cancel();if(neural!=null)neural.stop();PartyMusic.get(this).duck(false);}
    @Override protected void onPause(){super.onPause();DiagnosticRecorder.get(this).event("activity_pause","main");if(audio!=null)stopAudio();if(faceTracker!=null)faceTracker.stop();if(neural!=null){neural.releaseModel();neuralReady=false;}}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);handleIntent(i);}
    private void handleIntent(Intent i){if(i!=null&&i.hasExtra("game_id")){String id=i.getStringExtra("game_id");i.removeExtra("game_id");cancelCurrent();runDirectorAction(director.startGame(id));}}
    private void maybeOfferGame(){
        if(destroyed||!getSharedPreferences("martin",0).getBoolean("auto_games",true)||director==null||director.mode()!=PartyDirector.Mode.FREE||turns==null||turns.state()!=TurnManager.State.LISTENING||!hasWindowFocus()||!faceVisible)return;
        long now=System.currentTimeMillis(),last=getSharedPreferences("martin",0).getLong("last_auto_game_offer",now);if(now-last<30*60*1000L||now-lastHumanAt<45000L)return;
        PartyMusic pm=PartyMusic.get(this);String[] ids;
        if(pm.tracks().isEmpty())ids=pm.hasYandexAccount()?new String[]{"melody","chgk","true_false","crocodile","birthday_blitz","who_am_i"}:new String[]{"chgk","true_false","crocodile","birthday_blitz","who_am_i"};
        else ids=new String[]{"melody","time_machine","chgk","true_false","crocodile","birthday_blitz"};
        int n=getSharedPreferences("martin",0).getInt("auto_game_index",0);getSharedPreferences("martin",0).edit().putLong("last_auto_game_offer",now).putInt("auto_game_index",n+1).apply();runDirectorAction(director.offerGame(ids[n%ids.length]));
    }
    private void textInput(){android.widget.EditText e=new android.widget.EditText(this);e.setHint("Реплика, ответ или имя гостя");new android.app.AlertDialog.Builder(this).setTitle("Сказать ведущему текстом").setView(e).setPositiveButton("Отправить",(d,w)->{cancelCurrent();handleTranscript(e.getText().toString());}).setNegativeButton("Отмена",null).show();}
    private void toggleCamera(){
        if(cameraEnabled){cameraEnabled=false;getSharedPreferences("martin",0).edit().putBoolean("camera_enabled",false).apply();faceTracker.stop();cameraHelp.setText("Камера выключена");return;}
        new android.app.AlertDialog.Builder(this).setTitle("Камера для диалога").setMessage("С согласия гостей: локально отслеживать лицо перед телефоном и на время вечеринки связывать его с названным именем. Фото и биометрический шаблон не сохраняются и не отправляются. Если лицо потеряно или людей несколько, Сергей переспросит имя.")
        .setPositiveButton("Включить",(d,w)->{cameraEnabled=true;getSharedPreferences("martin",0).edit().putBoolean("camera_enabled",true).apply();if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},REQ);else startFaceTrackerIfAllowed();}).setNegativeButton("Отмена",null).show();
    }

    private void requestVisualReply(String question){
        turns.onUserFinal();
        new android.app.AlertDialog.Builder(this).setTitle("Отправить один кадр в AI?")
        .setMessage("С согласия людей в кадре: текущая фотография с фронтальной камеры будет отправлена выбранному провайдеру Groq или xAI для ответа. Видео не записывается. После ответа обычный режим камеры остаётся локальным.")
        .setPositiveButton("Отправить кадр",(d,w)->{cancelCurrent();visualQuestion=question;visualSession=session;cameraEnabled=true;turns.onUserFinal();state.setText("Получаю кадр…");if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){pendingCameraQuestion=question;requestPermissions(new String[]{Manifest.permission.CAMERA},18);}else{startFaceTrackerIfAllowed();faceTracker.requestFrame();}})
        .setNegativeButton("Отмена",(d,w)->turns.forceListen()).setOnCancelListener(d->turns.forceListen()).show();
    }

    private String cleanSpeech(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\[\\[[^\\]]*\\]\\]", "");
        s = s.replaceAll("[*#_`~>]", "");
        s = s.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "$1");
        return s.replaceAll("\\s+", " ").trim();
    }

    private void addNav(LinearLayout nav, String icon, String label, boolean selected, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, dp(3), 0, dp(2));
        if (selected) box.setBackground(round(0xFF20163A, 18));
        TextView i = text(icon, 17, selected ? 0xFF9D63FF : 0xFFE5E2EC, Typeface.NORMAL);
        i.setGravity(Gravity.CENTER);
        TextView l = text(label, 9, selected ? 0xFFB987FF : 0xFFE5E2EC, selected ? Typeface.BOLD : Typeface.NORMAL);
        l.setGravity(Gravity.CENTER);
        box.addView(i);
        box.addView(l);
        if (click != null) box.setOnClickListener(click);
        nav.addView(box, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private TextView text(String s, int sp, int color, int style) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        return v;
    }

    private TextView pill(String s, int bg, int fg) {
        TextView v = text(s, 10, fg, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackground(round(bg, 16));
        return v;
    }

    private Button iconButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(round(0xFF15111F, 19));
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable gradient(int a, int b, int c, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{a, b, c});
        g.setCornerRadius(dp(radius));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
