package com.imagine.martinhost;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Validates STT produced by the interrupt monitor while TTS is speaking. */
public final class BargeInPolicy {
    public static final class Result {
        public final boolean accepted;
        public final String reason;
        Result(boolean accepted, String reason){this.accepted=accepted;this.reason=reason;}
    }

    public Result evaluate(String transcript, String currentAssistantSpeech) {
        String n = norm(transcript);
        if (n.isBlank()) return new Result(false,"blank");

        boolean stop = n.matches(".*\\b(стоп|погоди|подожди|стой|замолчи|хватит|перебью|пауза)\\b.*");
        boolean repair = n.matches(".*\\b(нет не так|я про другое|я имел в виду|я имела в виду|не речь а|мне нужна|мне нужен|я просил|я просила)\\b.*");
        boolean name = ImaWakeMatcher.mentionsIma(n) || hasWord(n,"ассистент");

        if (stop) return new Result(true,"stop_word");
        if (looksLikeEcho(n, norm(currentAssistantSpeech))) return new Result(false,"similar_to_tts");
        // During active TTS even saying only the assistant name is a clear bid for the floor.
        if (name) return new Result(true, tokenCount(n)>=2 ? "assistant_name_plus_speech" : "assistant_name_only");
        if (repair) return new Result(true,"repair_phrase");

        int tokens=tokenCount(n);
        if (tokens>=3 && n.length()>=10 && looksDirectedNatural(n)) return new Result(true,"meaningful_human_speech");
        // Real-device 0.12.1 showed useful two-word interruptions (e.g. "синтез речи")
        // being lost. Accept compact non-echo interjections, but not long ambient room sentences.
        if (tokens==2 && n.length()>=5 && n.length()<=24 && !looksAmbientShort(n)) return new Result(true,"short_interjection");
        return new Result(false,"not_explicit_enough");
    }

    static boolean looksLikeEcho(String candidate,String speech){
        if(candidate.isBlank()||speech.isBlank())return false;
        Set<String> a=tokens(candidate),b=tokens(speech);
        if(a.isEmpty()||b.isEmpty())return false;
        int common=0;for(String s:a)if(b.contains(s))common++;
        double overlap=(double)common/(double)Math.max(1,a.size());
        return a.size()>=2 && overlap>=0.72;
    }

    private static boolean looksDirectedNatural(String n){
        return n.matches("^(я|мне|меня|мой|моя|мы|нам|давай|слушай|смотри|скажи|расскажи|объясни|продолж\\p{L}*|повтори|нет|но|а)\\b.*")
                || n.matches("^(кто|что|где|когда|как|почему|зачем|сколько|какой|какая|какие|можешь|помнишь|знаешь|в каком|в какой)\\b.*");
    }

    private static boolean looksAmbientShort(String n){
        return n.matches("^(ага|угу|ну да|ну ладно|вот так|там что|да ладно|очень хорошо|все нормально)$");
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
