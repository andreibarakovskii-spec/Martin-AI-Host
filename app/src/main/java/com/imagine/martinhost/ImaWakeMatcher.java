package com.imagine.martinhost;

import java.util.Locale;

/** Tolerant matcher for the short assistant name IMA when STT introduces small errors. */
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
        // Common Russian STT substitutions observed on-device for the short wake name.
        if (t.equals("нима") || t.equals("ема") || t.equals("сима") || t.equals("тима")) return true;
        // Keep fuzzy matching deliberately narrow: only 3–4 char Cyrillic tokens one edit from "има".
        if (t.length() >= 3 && t.length() <= 4 && isCyrillic(t)) return levenshtein(t, "има") <= 1;
        return false;
    }

    private static boolean isCyrillic(String s) {
        for (int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if (c<'а' || c>'я') return false;
        }
        return true;
    }

    private static int levenshtein(String a, String b) {
        int[] prev=new int[b.length()+1];
        int[] cur=new int[b.length()+1];
        for(int j=0;j<=b.length();j++) prev[j]=j;
        for(int i=1;i<=a.length();i++){
            cur[0]=i;
            for(int j=1;j<=b.length();j++){
                int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;
                cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+cost);
            }
            int[] tmp=prev;prev=cur;cur=tmp;
        }
        return prev[b.length()];
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace('ё','е')
                .replaceAll("[^\\p{L}\\p{N}]+"," ")
                .replaceAll("\\s+"," ")
                .trim();
    }
}
