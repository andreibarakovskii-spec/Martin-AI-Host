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

    // Human-like endpointing state. Strongly incomplete user clauses are kept open instead of
    // immediately producing an answer. The next recognized chunk is stitched onto this text.
    private String pendingUserTurn = "";
    private long pendingUserTurnAtMs;
    private String lastDirectedRaw = "";

    public void setContinuationWindowMs(long value) {
        continuationWindowMs = Math.max(5_000L, Math.min(180_000L, value));
    }

    public void reset() {
        conversationActive = false;
        lastDirectedAtMs = 0L;
        pendingUserTurn = "";
        pendingUserTurnAtMs = 0L;
        lastDirectedRaw = "";
        workingMemory.reset();
    }

    public String currentTopic(){return workingMemory.activeTopic();}
    public int topicRevision(){return workingMemory.topicRevision();}
    public boolean hasPendingUserTurn(){return !pendingUserTurn.isBlank();}
    public String pendingUserTurn(){return pendingUserTurn;}

    public Decision decide(String transcript, long nowMs) {
        String incoming = transcript == null ? "" : transcript.trim();
        if (incoming.isBlank()) {
            return new Decision(Kind.IGNORE, "", "blank", AttentionManager.Attention.AMBIENT);
        }

        String raw = incoming;
        if (!pendingUserTurn.isBlank() && nowMs - pendingUserTurnAtMs <= 12_000L) {
            raw = joinUserChunks(pendingUserTurn, incoming);
            pendingUserTurn = "";
            pendingUserTurnAtMs = 0L;
        } else if (!lastDirectedRaw.isBlank()
                && nowMs - lastDirectedAtMs <= 5_000L
                && looksLikeContinuationStart(AttentionManager.normalize(incoming))) {
            // Safety net for the case where IMA already started answering but the user was still
            // finishing the same thought. Barge-in cancels playback, then we reconstruct the whole turn.
            raw = joinUserChunks(lastDirectedRaw, incoming);
        }

        String n = AttentionManager.normalize(raw);
        boolean inWindow = conversationActive && nowMs - lastDirectedAtMs <= continuationWindowMs;
        AttentionManager.Attention a = attention.classify(raw, inWindow);

        // Special commands must stay immediate even if their wording happens to end with a connector.
        if (isStop(n)) {
            lastDirectedAtMs = nowMs;
            lastDirectedRaw = raw;
            return new Decision(Kind.STOP, "", "stop_or_interrupt", AttentionManager.Attention.DIRECT);
        }
        if (isContinue(n)) {
            markActive(nowMs, raw);
            return new Decision(Kind.CONTINUE, "", "continue_previous_answer", AttentionManager.Attention.DIRECT);
        }

        // If the utterance is clearly addressed to IMA / part of the live dialogue but sounds
        // grammatically unfinished, do not jump in. Keep listening for the rest of the sentence.
        if (a != AttentionManager.Attention.AMBIENT && looksStronglyIncomplete(raw)) {
            pendingUserTurn = raw;
            pendingUserTurnAtMs = nowMs;
            conversationActive = true;
            lastDirectedAtMs = nowMs;
            lastDirectedRaw = raw;
            return new Decision(Kind.IGNORE, "", "user_turn_incomplete_wait", a);
        }

        // Observe only the reconstructed turn, not each split fragment separately.
        workingMemory.observe(raw,a,nowMs);

        if (looksLikeVision(n)) {
            markActive(nowMs, raw);
            return new Decision(Kind.VISION, stripAssistantName(raw), "vision_request", a);
        }
        if (looksLikeMusic(n)) {
            markActive(nowMs, raw);
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
        markActive(nowMs, raw);
        return new Decision(Kind.RESPOND, q, looksLikeRepair(n) ? "repair" : (a == AttentionManager.Attention.DIRECT ? "direct" : "continuation"), a);
    }

    private void markActive(long nowMs, String raw) {
        conversationActive = true;
        lastDirectedAtMs = nowMs;
        lastDirectedRaw = raw == null ? "" : raw.trim();
    }

    static boolean looksStronglyIncomplete(String raw) {
        if (raw == null) return false;
        String trimmed=raw.trim();
        // Preserve punctuation evidence before normalization. Whisper often emits an ellipsis or
        // dash when the speaker trails off; that should be treated as a held conversational turn.
        if (trimmed.endsWith("...") || trimmed.endsWith("…") || trimmed.endsWith("—") || trimmed.endsWith("-")) return true;

        String n = AttentionManager.normalize(stripAssistantName(raw));
        if (n.isBlank()) return false;
        // Strong grammatical tails: connectors/prepositions that normally require a continuation.
        if (n.matches(".*\\b(и|а|но|или|если|когда|пока|хотя|чтобы|потому что|так как|который|которая|которые|которое|где|куда|откуда|зачем|почему|в|на|к|с|со|из|от|до|для|без|про|о|об|по|у|через|между|перед|после)$")) return true;
        // Common unfinished Russian speech constructions.
        if (n.matches(".*\\b(я хотел|я хотела|я хочу|мне нужно|мне надо|можешь|можешь ли|скажи мне|расскажи мне|дело в том|смысл в том|проблема в том)$")) return true;
        // Real-device speech often loses punctuation. These short trailing predicates are very
        // frequently a lead-in rather than a complete thought (e.g. «Ты уже начинаешь...»).
        if (n.matches("^(ты|вы) .{0,48}\\b(начинаешь|начинаете|пытаешься|пытаетесь|хочешь|хотите|будешь|будете|говоришь|говорите|делаешь|делаете)$")) return true;
        if (n.matches("^я .{0,48}\\b(начинаю|пытаюсь|хочу|буду|говорю|делаю)$")) return true;
        return false;
    }

    static boolean looksLikeContinuationStart(String n) {
        if (n == null || n.isBlank()) return false;
        return n.matches("^(и еще|и ещё|а еще|а ещё|но|потому что|так как|то есть|просто|который|которая|которые|которое|я хотел сказать|я хотела сказать|я не договорил|я не договорила|в смысле|точнее)\\b.*");
    }

    static String joinUserChunks(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isBlank()) return b;
        if (b.isBlank()) return a;
        return a + " " + b;
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
