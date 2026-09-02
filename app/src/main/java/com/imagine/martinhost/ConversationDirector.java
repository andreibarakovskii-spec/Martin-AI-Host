package com.imagine.martinhost;

/** Deterministic routing layer plus short-lived conversational state for imagination / IMA. */
public final class ConversationDirector {
    public enum Kind { IGNORE, RESPOND, STOP, CONTINUE, MUSIC, VISION }

    public static final class Decision {
        public final Kind kind;
        public final String text;
        public final String reason;
        public final AttentionManager.Attention attention;

        Decision(Kind kind, String text, String reason, AttentionManager.Attention attention) {
            this.kind = kind;
            this.text = text == null ? "" : text;
            this.reason = reason == null ? "" : reason;
            this.attention = attention;
        }
    }

    private final AttentionManager attention = new AttentionManager();
    private final ConversationWorkingMemory workingMemory = new ConversationWorkingMemory();
    private boolean conversationActive;
    private long lastDirectedAtMs;
    private long continuationWindowMs = 45_000L;

    public void setContinuationWindowMs(long value) {
        continuationWindowMs = Math.max(5_000L, Math.min(180_000L, value));
    }

    public void reset() {
        conversationActive = false;
        lastDirectedAtMs = 0L;
        workingMemory.reset();
    }

    public String currentTopic(){return workingMemory.activeTopic();}
    public int topicRevision(){return workingMemory.topicRevision();}

    public Decision decide(String transcript, long nowMs) {
        String raw = transcript == null ? "" : transcript.trim();
        String n = AttentionManager.normalize(raw);
        boolean inWindow = conversationActive && nowMs - lastDirectedAtMs <= continuationWindowMs;
        AttentionManager.Attention a = attention.classify(raw, inWindow);

        if (raw.isBlank()) return new Decision(Kind.IGNORE, "", "blank", a);

        // Observe every nonblank transcript before routing. Unrelated room speech is marked AMBIENT;
        // semantically related speech can become short-lived dialogue context without forcing IMA to speak.
        workingMemory.observe(raw,a,nowMs);

        if (isStop(n)) {
            lastDirectedAtMs = nowMs;
            return new Decision(Kind.STOP, "", "stop_or_interrupt", AttentionManager.Attention.DIRECT);
        }
        if (isContinue(n)) {
            markActive(nowMs);
            return new Decision(Kind.CONTINUE, "", "continue_previous_answer", AttentionManager.Attention.DIRECT);
        }
        if (looksLikeVision(n)) {
            markActive(nowMs);
            return new Decision(Kind.VISION, stripAssistantName(raw), "vision_request", a);
        }
        if (looksLikeMusic(n)) {
            markActive(nowMs);
            return new Decision(Kind.MUSIC, raw, "music_request", a);
        }
        if (a == AttentionManager.Attention.AMBIENT) {
            String reason=workingMemory.shouldKeepAmbientInDialogue(raw)?"ambient_kept_as_dialog_context":"ambient_or_not_addressed";
            return new Decision(Kind.IGNORE, "", reason, a);
        }

        String q = stripAssistantName(raw);
        if (q.isBlank()) q = "Я здесь. Что хочешь обсудить?";
        if (looksLikeRepair(n)) {
            q = "Исправление пользователя к предыдущей реплике: " + q + ". Прими исправление и продолжи исходную тему без повторения ошибки.";
        }
        String live=workingMemory.promptContext(nowMs);
        if(!live.isBlank())q=q+live;
        markActive(nowMs);
        return new Decision(Kind.RESPOND, q, looksLikeRepair(n) ? "repair" : (a == AttentionManager.Attention.DIRECT ? "direct" : "continuation"), a);
    }

    private void markActive(long nowMs) {
        conversationActive = true;
        lastDirectedAtMs = nowMs;
    }

    static boolean isStop(String n) {
        return n.matches("^(има |ima |ассистент )?(стоп|подожди|погоди|стой|замолчи|хватит)( пожалуйста)?$")
                || n.matches("^(не надо|отмена)$");
    }

    static boolean isContinue(String n) {
        return n.matches("^(има |ima )?(продолжи|продолжай|договори)( пожалуйста)?$");
    }

    static boolean looksLikeRepair(String n) {
        return n.matches(".*\\b(не так|я имел в виду|я имела в виду|я про другое|точнее|нет речь не|не речь а|мне нужна|мне нужен)\\b.*");
    }

    static boolean looksLikeMusic(String n) {
        return n.matches(".*\\b(музык|песн|трек|плейлист|яндекс музык)\\p{L}*\\b.*")
                && n.matches(".*\\b(включ|выключ|постав|запуст|останов|переключ)\\p{L}*\\b.*");
    }

    static boolean looksLikeVision(String n) {
        return n.contains("что видишь") || n.contains("посмотри") || n.contains("видишь меня");
    }

    static String stripAssistantName(String raw) {
        if (raw == null) return "";
        return raw.replaceFirst("(?iu)^\\s*(?:има|ima|ассистент)[,.:;!?\\s—-]*", "").trim();
    }
}
