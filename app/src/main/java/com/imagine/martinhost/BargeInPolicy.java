package com.imagine.martinhost;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Validates STT produced by the strict interrupt monitor while TTS is speaking. */
public final class BargeInPolicy {
    public static final class Result {
        public final boolean accepted;
        public final String reason;
        Result(boolean accepted, String reason){this.accepted=accepted;this.reason=reason;}
    }

    public Result evaluate(String transcript, String currentAssistantSpeech) {
        String n = norm(transcript);
        if (n.isBlank()) return new Result(false,"blank");

        boolean stop = n.matches(".*\\b(стоп|погоди|подожди|стой|замолчи|хватит|перебью)\\b.*");
        boolean repair = n.matches(".*\\b(нет не так|я про другое|я имел в виду|я имела в виду|не речь а|мне нужна|мне нужен)\\b.*");
        boolean name = hasWord(n,"има") || hasWord(n,"ima") || hasWord(n,"ассистент");

        // Strong local-control words win before echo comparison: a short "стоп" may also occur in TTS.
        if (stop) return new Result(true,"stop_word");
        if (looksLikeEcho(n, norm(currentAssistantSpeech))) return new Result(false,"similar_to_tts");
        if (name && tokenCount(n)>=2) return new Result(true,"assistant_name_plus_speech");
        if (repair) return new Result(true,"repair_phrase");
        return new Result(false,"not_explicit_enough");
    }

    static boolean looksLikeEcho(String candidate,String speech){
        if(candidate.isBlank()||speech.isBlank())return false;
        Set<String> a=tokens(candidate),b=tokens(speech);
        if(a.isEmpty()||b.isEmpty())return false;
        int common=0;for(String s:a)if(b.contains(s))common++;
        double overlap=(double)common/(double)Math.max(1,a.size());
        return a.size()>=2 && overlap>=0.80;
    }

    private static Set<String> tokens(String s){
        Set<String> out=new HashSet<>();
        for(String x:s.split(" "))if(x.length()>=3)out.add(x);
        return out;
    }
    private static int tokenCount(String s){return s.isBlank()?0:s.split(" ").length;}
    private static boolean hasWord(String s,String w){return (" "+s+" ").contains(" "+w+" ");}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();}
}
