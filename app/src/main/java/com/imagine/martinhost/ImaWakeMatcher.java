package com.imagine.martinhost;

import java.util.Locale;

/** Tolerant matcher for the short assistant name IMA when STT introduces small observed errors. */
public final class ImaWakeMatcher {
    private ImaWakeMatcher() {}

    public static boolean mentionsIma(String text) {
        String n = normalize(text);
        if (n.isBlank()) return false;
        for (String token : n.split(" ")) {
            if (isImaToken(token)) return true;
        }
        return false;
    }

    static boolean isImaToken(String token) {
        if (token == null || token.isBlank()) return false;
        String t = token.toLowerCase(Locale.ROOT).replace('ё','е');
        if (t.equals("ima") || t.equals("има") || t.equals("имма") || t.equals("имаа") || t.equals("иму")) return true;
        // Only observed on-device substitutions are accepted here. Do not use generic edit-distance
        // matching for a 3-letter wake name: it turns ordinary words such as «зима» into false wakes.
        return t.equals("нима") || t.equals("ема") || t.equals("сима") || t.equals("тима");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('ё','е')
                .replaceAll("[^\\p{L}\\p{N}]+"," ")
                .replaceAll("\\s+"," ")
                .trim();
    }
}
