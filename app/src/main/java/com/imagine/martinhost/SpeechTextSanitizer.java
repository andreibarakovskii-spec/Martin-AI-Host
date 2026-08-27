package com.imagine.martinhost;

/** Removes markdown and machine-only tokens before Martin speaks. */
public final class SpeechTextSanitizer {
    private SpeechTextSanitizer() {}

    public static String forSpeech(String raw){
        if(raw==null)return "";
        String s=raw;
        s=s.replaceAll("\\[\\[[A-Z0-9_:\\-]+\\]\\]", " ");
        s=s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        s=s.replace("**", "").replace("__", "").replace("*", "").replace("_", "");
        s=s.replace("`", "");
        s=s.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
        s=s.replaceAll("(?m)^\\s*[-+>]\\s+", "");
        s=s.replaceAll("(?m)^\\s*\\d+[.)]\\s+", "");
        s=s.replaceAll("[\\u2605\\u2606]", "");
        s=s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
