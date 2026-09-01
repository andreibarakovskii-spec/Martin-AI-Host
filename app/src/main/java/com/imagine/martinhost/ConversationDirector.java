package com.imagine.martinhost;

/**
 * First deterministic routing layer for the personal companion.
 * It decides whether a transcript should be ignored, handled locally, or sent to the LLM.
 * The LLM remains responsible for language generation, not basic turn ownership.
 */
public final class ConversationDirector {
    public enum Kind { IGNORE, RESPOND, STOP, MUSIC, VISION }

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
    private boolean conversationActive;
    private long lastDirectedAtMs;
    private long continuationWindowMs = 45_000L;

    public void setContinuationWindowMs(long value) {
        continuationWindowMs = Math.max(5_000L, Math.min(180_000L, value));
    }

    public void reset() {
        conversationActive = false;
        lastDirectedAtMs = 0L;
    }

    public Decision decide(String transcript, long nowMs) {
        String raw = transcript == null ? "" : transcript.trim();
        String n = AttentionManager.normalize(raw);
        boolean inWindow = conversationActive && nowMs - lastDirectedAtMs <= continuationWindowMs;
        AttentionManager.Attention a = attention.classify(raw, inWindow);

        if (raw.isBlank()) return new Decision(Kind.IGNORE, "", "blank", a);

        if (isStop(n)) {
            conversationActive = false;
            lastDirectedAtMs = nowMs;
            return new Decision(Kind.STOP, "", "stop_or_interrupt", AttentionManager.Attention.DIRECT);
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
            return new Decision(Kind.IGNORE, "", "ambient_or_not_addressed", a);
        }

        String q = stripAssistantName(raw);
        if (q.isBlank()) q = "Я здесь. Что хочешь обсудить?";
        markActive(nowMs);
        return new Decision(Kind.RESPOND, q, a == AttentionManager.Attention.DIRECT ? "direct" : "continuation", a);
    }

    private void markActive(long nowMs) {
        conversationActive = true;
        lastDirectedAtMs = nowMs;
    }

    static boolean isStop(String n) {
        return n.matches("^(сергей |мартин )?(стоп|подожди|погоди|стой|замолчи|хватит)( пожалуйста)?$")
                || n.matches("^(не надо|отмена)$");
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
        return raw.replaceFirst("(?iu)^\\s*(?:сергей|мартин|ассистент)[,.:;!?\\s—-]*", "").trim();
    }
}
