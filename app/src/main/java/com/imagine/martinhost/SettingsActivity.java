package com.imagine.martinhost;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

public final class SettingsActivity extends Activity {
    private GuestStore store;
    private LinearLayout guestList;
    private List<GuestStore.Guest> guests;
    private SharedPreferences prefs;
    private Button aiButton, voiceButton;
    private TextView aiStatus, voiceStatus;
    private MartinNeuralSpeaker voiceLoader;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("martin", 0);
        store = new GuestStore(this);
        guests = new ArrayList<>(store.load());
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF080A10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 48);
        scroll.addView(root);

        TextView h = tv("НАСТРОЙКИ", 28, Color.WHITE);
        h.setGravity(Gravity.CENTER);
        root.addView(h);
        root.addView(tv("AI • распознавание • голос • гости • проектор", 14, 0xFF9AA1B2));

        root.addView(tv("ИИ И РАСПОЗНАВАНИЕ", 13, 0xFF8B5CF6));
        aiButton = btn(aiLabel());
        aiButton.setOnClickListener(v -> aiDialog(aiButton));
        root.addView(aiButton);
        aiStatus = tv(aiStatusLabel(), 14, 0xFFB8BFCC);
        root.addView(aiStatus);
        Button testAi = btn("🔌 ПРОВЕРИТЬ AI");
        testAi.setOnClickListener(v -> testAiConnection(testAi));
        root.addView(testAi);

        root.addView(tv("ГОЛОС МАРТИНА", 13, 0xFF8B5CF6));
        voiceStatus = tv("Нейроголос Supertonic хранится локально на телефоне. Системный голос не используется как основной.", 14, 0xFFB8BFCC);
        root.addView(voiceStatus);
        voiceButton = btn("⬇ СКАЧАТЬ / ПРОВЕРИТЬ МОДЕЛЬ ГОЛОСА");
        voiceButton.setOnClickListener(v -> downloadVoiceModel());
        root.addView(voiceButton);

        Button projector = btn(projectorLabel());
        projector.setOnClickListener(v -> {
            boolean on = !prefs.getBoolean("projector", false);
            prefs.edit().putBoolean("projector", on).apply();
            projector.setText(projectorLabel());
        });
        root.addView(projector);

        root.addView(tv("ГОСТИ", 13, 0xFF8B5CF6));
        guestList = new LinearLayout(this);
        guestList.setOrientation(LinearLayout.VERTICAL);
        root.addView(guestList);
        renderGuests();
        Button add = btn("＋ ДОБАВИТЬ ГОСТЯ");
        add.setOnClickListener(v -> editGuest(-1));
        root.addView(add);
        Button save = btn("ГОТОВО");
        save.setOnClickListener(v -> { store.save(guests); finish(); });
        root.addView(save);
        setContentView(scroll);
    }

    private String aiLabel() {
        String p = prefs.getString("ai_provider", "auto");
        return "🤖 AI: " + ("groq".equals(p) ? "Groq" : "xai".equals(p) ? "xAI / Grok" : "Авто по ключу");
    }

    private String aiStatusLabel() {
        String aiKey = prefs.getString("ai_key", prefs.getString("xai_key", ""));
        String sttKey = prefs.getString("stt_key", "");
        if ((sttKey == null || sttKey.isBlank()) && aiKey != null && aiKey.startsWith("gsk_")) sttKey = aiKey;
        boolean aiReady = aiKey != null && (aiKey.startsWith("gsk_") || aiKey.startsWith("xai-"));
        boolean sttReady = sttKey != null && sttKey.startsWith("gsk_");
        String brain = aiKey != null && aiKey.startsWith("xai-") ? "xAI/Grok" : aiKey != null && aiKey.startsWith("gsk_") ? "Groq" : "не настроен";
        return "AI: " + (aiReady ? brain + " ✓" : "ключ не указан") + "   •   Речь: " + (sttReady ? "Groq Whisper ✓" : "нужен gsk_ ключ");
    }

    private String projectorLabel() {
        return prefs.getBoolean("projector", false) ? "📽 ПРОЕКТОР: ПОДКЛЮЧЁН" : "📱 ПРОЕКТОР: ВЫКЛЮЧЕН";
    }

    private void testAiConnection(Button source) {
        String key = prefs.getString("ai_key", prefs.getString("xai_key", ""));
        if (key == null || key.isBlank()) {
            aiStatus.setText("❌ Сначала укажите AI key: gsk_… или xai-…");
            return;
        }
        source.setEnabled(false);
        aiStatus.setText("⏳ Проверяю AI…");
        GrokClient client = new GrokClient(this);
        client.reply("Ответь только одним словом: OK", new GrokClient.Callback() {
            public void onResult(String text) {
                runOnUiThread(() -> {
                    source.setEnabled(true);
                    prefs.edit().putLong("ai_last_ok", System.currentTimeMillis()).apply();
                    aiStatus.setText("✅ AI отвечает. " + aiStatusLabel());
                });
            }
            public void onError(String e) {
                runOnUiThread(() -> {
                    source.setEnabled(true);
                    aiStatus.setText("❌ AI: " + e);
                });
            }
        });
    }

    private void downloadVoiceModel() {
        if (voiceLoader != null) {
            voiceStatus.setText("Загрузка уже запущена…");
            return;
        }
        voiceButton.setEnabled(false);
        voiceStatus.setText("Подготавливаю загрузку нейроголоса…");
        voiceLoader = new MartinNeuralSpeaker(this, new MartinNeuralSpeaker.Listener() {
            public void onPreparing(String m) { runOnUiThread(() -> { voiceStatus.setText("⬇ " + m); voiceButton.setText("ЗАГРУЗКА МОДЕЛИ…"); }); }
            public void onReady() { runOnUiThread(() -> {
                voiceStatus.setText("✅ Нейроголос загружен и готов к работе");
                voiceButton.setText("✓ МОДЕЛЬ ГОЛОСА ГОТОВА");
                voiceButton.setEnabled(true);
                prefs.edit().putBoolean("voice_model_ready", true).apply();
                if (voiceLoader != null) { voiceLoader.close(); voiceLoader = null; }
            }); }
            public void onStart() {}
            public void onLevel(float level) {}
            public void onDone() {}
            public void onError(String message) { runOnUiThread(() -> {
                voiceStatus.setText("❌ " + message);
                voiceButton.setText("↻ ПОВТОРИТЬ ЗАГРУЗКУ");
                voiceButton.setEnabled(true);
                prefs.edit().putBoolean("voice_model_ready", false).apply();
                if (voiceLoader != null) { voiceLoader.close(); voiceLoader = null; }
            }); }
        });
        voiceLoader.prepare();
    }

    private void aiDialog(Button target) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 8, 18, 0);

        Spinner provider = new Spinner(this);
        String[] ps = {"Авто по AI-ключу", "Groq / GSK", "xAI / Grok"};
        provider.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ps));
        String cur = prefs.getString("ai_provider", "auto");
        provider.setSelection("groq".equals(cur) ? 1 : "xai".equals(cur) ? 2 : 0);

        String currentAiKey = prefs.getString("ai_key", prefs.getString("xai_key", ""));
        String currentSttKey = prefs.getString("stt_key", "");
        if ((currentSttKey == null || currentSttKey.isBlank()) && currentAiKey != null && currentAiKey.startsWith("gsk_")) currentSttKey = currentAiKey;

        EditText key = input("AI key: gsk_... или xai-...", currentAiKey);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText sttKey = input("Groq STT key gsk_... (для Whisper)", currentSttKey);
        sttKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText groqModel = input("Groq AI model", prefs.getString("groq_model", "openai/gpt-oss-120b"));
        EditText xaiModel = input("xAI model", prefs.getString("xai_model", "grok-4.6"));

        box.addView(provider);
        box.addView(key);
        box.addView(sttKey);
        box.addView(groqModel);
        box.addView(xaiModel);

        new AlertDialog.Builder(this)
                .setTitle("AI и распознавание речи")
                .setMessage("Если AI = Groq, один gsk_ ключ можно использовать и для AI, и для Whisper. Если AI = xAI/Grok, укажите отдельный Groq gsk_ ключ для распознавания речи.")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String p = provider.getSelectedItemPosition() == 1 ? "groq" : provider.getSelectedItemPosition() == 2 ? "xai" : "auto";
                    String ai = key.getText().toString().trim();
                    String stt = sttKey.getText().toString().trim();
                    if (stt.isBlank() && ai.startsWith("gsk_")) stt = ai;
                    prefs.edit()
                            .putString("ai_provider", p)
                            .putString("ai_key", ai)
                            .putString("stt_key", stt)
                            .putString("groq_model", groqModel.getText().toString().trim())
                            .putString("xai_model", xaiModel.getText().toString().trim())
                            .remove("ai_last_ok")
                            .remove("groq_last_ok")
                            .apply();
                    target.setText(aiLabel());
                    aiStatus.setText(aiStatusLabel());
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void renderGuests() {
        guestList.removeAllViews();
        if (guests.isEmpty()) guestList.addView(tv("Добавьте имена и факты заранее.", 15, 0xFF8F96A3));
        for (int i = 0; i < guests.size(); i++) {
            final int index = i;
            GuestStore.Guest g = guests.get(i);
            Button b = btn((i + 1) + ". " + g.name + "   ⭐ " + g.score);
            b.setOnClickListener(v -> editGuest(index));
            guestList.addView(b);
        }
    }

    private void editGuest(int index) {
        GuestStore.Guest g = index >= 0 ? guests.get(index) : new GuestStore.Guest();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 8, 18, 0);
        EditText name = input("Имя", g.name), call = input("Как обращаться", g.callName), relation = input("Кто он/она Кате", g.relation), facts = input("Факты для шуток и тостов", g.facts), bounds = input("Запретные темы", g.boundaries);
        facts.setMinLines(3);
        bounds.setMinLines(2);
        box.addView(name); box.addView(call); box.addView(relation); box.addView(facts); box.addView(bounds);
        AlertDialog.Builder d = new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "Гость" : "Новый гость")
                .setView(box)
                .setPositiveButton("Сохранить", (x, w) -> {
                    g.name = name.getText().toString().trim();
                    g.callName = call.getText().toString().trim();
                    g.relation = relation.getText().toString().trim();
                    g.facts = facts.getText().toString().trim();
                    g.boundaries = bounds.getText().toString().trim();
                    if (!g.name.isBlank()) {
                        if (index < 0) guests.add(g);
                        store.save(guests);
                        renderGuests();
                    }
                })
                .setNegativeButton("Отмена", null);
        if (index >= 0) d.setNeutralButton("Удалить", (x, w) -> { guests.remove(index); store.save(guests); renderGuests(); });
        d.show();
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setTextColor(Color.BLACK);
        e.setHintTextColor(0xFF777777);
        return e;
    }

    private TextView tv(String s, int sp, int c) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(c);
        v.setPadding(8, 12, 8, 12);
        return v;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    @Override protected void onDestroy() {
        if (voiceLoader != null) { voiceLoader.close(); voiceLoader = null; }
        super.onDestroy();
    }
}
