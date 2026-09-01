package com.imagine.martinhost;

import java.util.Locale;

/** Conservative deterministic attention gate for imagination / IMA. */
public final class AttentionManager {
    public enum Attention { DIRECT, LIKELY, AMBIENT }

    public Attention classify(String text, boolean conversationActive) {
        String s = normalize(text);
        if (s.isBlank()) return Attention.AMBIENT;
        if (mentionsAssistant(s)) return Attention.DIRECT;
        if (looksLikeStopOrRepair(s)) return Attention.DIRECT;
        if (conversationActive && looksLikeContinuation(s)) return Attention.LIKELY;
        if (looksLikeQuestion(s) || looksLikeActionRequest(s)) return Attention.LIKELY;
        if (looksLikeAmbientFragment(s)) return Attention.AMBIENT;
        return conversationActive ? Attention.LIKELY : Attention.AMBIENT;
    }

    static boolean mentionsAssistant(String s) {
        return containsWord(s, "има") || containsWord(s, "ima") || containsWord(s, "ассистент");
    }

    static boolean looksLikeStopOrRepair(String s) {
        return s.matches(".*\\b(стоп|подожди|погоди|стой|нет|не так|я имел в виду|я имела в виду|я про другое|перебью|продолжи|продолжай|договори)\\b.*");
    }

    static boolean looksLikeQuestion(String s) {
        return s.endsWith("?") || s.matches("^(кто|что|где|когда|как|почему|зачем|сколько|какой|какая|какие|можешь|умеешь|помнишь|знаешь)\\b.*");
    }

    static boolean looksLikeActionRequest(String s) {
        return s.matches("^(напомни|запомни|добавь|запиши|найди|проверь|покажи|включи|выключи|поставь|создай|открой|расскажи|скажи|позвони|напиши|продолжи|продолжай)\\b.*");
    }

    static boolean looksLikeContinuation(String s) {
        return s.length() >= 2 && !s.matches("^(ага|угу|мм|эм|ну)$");
    }

    static boolean looksLikeAmbientFragment(String s) {
        if (s.length() < 4) return true;
        return s.matches("^(ага|угу|мм|эм|ну|ладно|понятно|ясно|окей|ок)$")
                || s.matches(".*\\b(телевизор|субтитр|редактор субтитров|корректор|спасибо за просмотр)\\b.*");
    }

    private static boolean containsWord(String s, String word) {
        return s.matches(".*(?:^|\\s)" + java.util.regex.Pattern.quote(word) + "(?:$|\\s).*" );
    }

    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}?]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
