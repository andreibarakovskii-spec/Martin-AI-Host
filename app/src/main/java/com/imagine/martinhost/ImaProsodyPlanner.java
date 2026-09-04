package com.imagine.martinhost;

import java.util.Locale;

/**
 * Backend-neutral expressive speech plan for IMA.
 *
 * Piper/Irina currently consumes only speed/silence/gain/pause, while the richer
 * timbre/contour fields are intentionally kept in this contract for the future
 * primary IMA Voice Engine. This lets conversation behaviour start producing
 * expressive intent now without coupling it to one synthesizer.
 */
public final class ImaProsodyPlanner {
    public static final class Plan {
        public final String style;
        public final float speed;
        public final float silenceScale;
        public final float gain;
        public final int sentencePauseMs;

        // Backend-neutral expressive controls for the future IMA Voice Engine.
        public final float warmth;       // 0..1
        public final float brightness;   // 0..1, timbre brightness, not pitch-shift
        public final float breathiness;  // 0..1
        public final float firmness;     // 0..1
        public final float expressiveness; // 0..1
        public final float emphasis;     // 0..1
        public final String finalContour; // falling, rising, suspended, animated

        Plan(String style,float speed,float silenceScale,float gain,int sentencePauseMs,
             float warmth,float brightness,float breathiness,float firmness,
             float expressiveness,float emphasis,String finalContour){
            this.style=style;
            this.speed=speed;
            this.silenceScale=silenceScale;
            this.gain=gain;
            this.sentencePauseMs=sentencePauseMs;
            this.warmth=clamp01(warmth);
            this.brightness=clamp01(brightness);
            this.breathiness=clamp01(breathiness);
            this.firmness=clamp01(firmness);
            this.expressiveness=clamp01(expressiveness);
            this.emphasis=clamp01(emphasis);
            this.finalContour=finalContour==null?"falling":finalContour;
        }
    }

    private ImaProsodyPlanner(){}

    public static Plan plan(String text,String emotion,float energy){
        String raw=text==null?"":text.trim();
        String n=raw.toLowerCase(Locale.ROOT).replace('ё','е');
        String e=emotion==null?"":emotion.toLowerCase(Locale.ROOT);
        float en=clamp01(energy);

        // When the caller still says "neutral", infer a conservative emotional
        // intent from the actual phrase. This makes the current fallback voice less
        // flat while keeping the same semantic text and without fake pitch shifting.
        if(e.isBlank()||e.equals("neutral")) e=inferEmotion(n);

        String style="neutral";
        float speed=1f,silence=.12f,gain=1f;
        int pause=85;
        float warmth=.55f,brightness=.50f,breath=.10f,firmness=.45f,express=.42f,emphasis=.42f;
        String contour="falling";

        if(has(e,"empathetic","sad","tender","support","sympathetic")){
            style="empathetic";speed=.91f;silence=.19f;gain=.94f;pause=155;
            warmth=.88f;brightness=.34f;breath=.27f;firmness=.24f;express=.66f;emphasis=.34f;contour="soft-falling";
        }else if(has(e,"warm","caring","friendly")){
            style="warm";speed=.95f;silence=.15f;gain=.98f;pause=120;
            warmth=.86f;brightness=.48f;breath=.18f;firmness=.35f;express=.58f;emphasis=.42f;contour="soft-falling";
        }else if(has(e,"calm","soft","relaxed")){
            style="calm";speed=.93f;silence=.17f;gain=.96f;pause=135;
            warmth=.72f;brightness=.38f;breath=.20f;firmness=.34f;express=.42f;emphasis=.30f;contour="falling";
        }else if(has(e,"excited","enthusiastic")){
            style="excited";speed=1.08f;silence=.075f;gain=1.08f;pause=42;
            warmth=.67f;brightness=.84f;breath=.05f;firmness=.58f;express=.94f;emphasis=.82f;contour="animated";
        }else if(has(e,"playful","ironic","teasing")){
            style="playful";speed=1.055f;silence=.09f;gain=1.055f;pause=58;
            warmth=.72f;brightness=.73f;breath=.08f;firmness=.42f;express=.85f;emphasis=.72f;contour="animated";
        }else if(has(e,"happy","joy","cheerful")){
            style="happy";speed=1.035f;silence=.10f;gain=1.045f;pause=68;
            warmth=.78f;brightness=.70f;breath=.08f;firmness=.43f;express=.74f;emphasis=.62f;contour="bright-falling";
        }else if(has(e,"curious","question","wonder")){
            style="curious";speed=.99f;silence=.115f;gain=1.00f;pause=86;
            warmth=.64f;brightness=.61f;breath=.10f;firmness=.37f;express=.62f;emphasis=.55f;contour="rising";
        }else if(has(e,"serious","confident","firm")){
            style="confident";speed=.965f;silence=.11f;gain=1.02f;pause=92;
            warmth=.48f;brightness=.45f;breath=.05f;firmness=.86f;express=.58f;emphasis=.68f;contour="firm-falling";
        }

        // Punctuation and discourse shape are meaningful even when no emotion label
        // is available yet from the LLM/Conversation Director.
        if(raw.endsWith("?")) {
            contour="rising";
            express=Math.max(express,.58f);
            emphasis=Math.max(emphasis,.50f);
        } else if(raw.endsWith("!")) {
            contour="animated";
            express=Math.max(express,.72f);
            emphasis=Math.max(emphasis,.68f);
            brightness=Math.max(brightness,.62f);
        } else if(looksSuspended(n)) {
            contour="suspended";
            pause=Math.max(pause,145);
            express=Math.max(express,.52f);
        }

        // Energy changes dynamics, but never mutates speaker identity.
        gain*=.92f+en*.16f;
        express=clamp01(express*.82f+en*.28f);
        emphasis=clamp01(emphasis*.88f+en*.18f);
        gain=Math.max(.85f,Math.min(1.12f,gain));

        return new Plan(style,speed,silence,gain,pause,warmth,brightness,breath,firmness,express,emphasis,contour);
    }

    /** Conservative text-only fallback until semantic emotion comes from the model. */
    static String inferEmotion(String n){
        if(n==null||n.isBlank())return "neutral";
        if(hasAny(n,"мне жаль","понимаю тебя","это тяжело","не переживай","я рядом","сочувств")) return "empathetic";
        if(hasAny(n,"отлично","здорово","супер","прекрасно","классно","рада","рад ")) return "happy";
        if(hasAny(n,"ха-ха","шут","забавно","смешно","ну ты даешь","ну ты даёшь")) return "playful";
        if(hasAny(n,"важно","обрати внимание","нужно","нельзя","стоит","точно","уверен")) return "confident";
        if(n.endsWith("?")) return "curious";
        if(n.endsWith("!")) return "excited";
        if(hasAny(n,"спокойно","не спеши","постепенно","мягко")) return "calm";
        if(hasAny(n,"приятно","хорошо","давай","конечно","с удовольствием")) return "warm";
        return "neutral";
    }

    private static boolean looksSuspended(String n){
        if(n==null||n.isBlank())return false;
        return n.endsWith("...")||n.endsWith("…")||n.matches(".*\\b(и|но|а|потому что|если|когда|чтобы|то есть|например|про|насчет|насчёт)$");
    }

    private static boolean has(String value,String... needles){for(String n:needles)if(value.contains(n))return true;return false;}
    private static boolean hasAny(String value,String... needles){return has(value,needles);}
    private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}

    /** Small energy contour without pitch-shifting the voice. */
    public static byte[] applyGain(byte[] pcm,float gain){
        if(pcm==null||pcm.length==0||Math.abs(gain-1f)<.002f)return pcm;
        byte[] out=pcm.clone();
        for(int i=0;i+1<out.length;i+=2){
            int s=(short)(((out[i+1]&255)<<8)|(out[i]&255));
            int v=Math.round(s*gain);v=Math.max(-32768,Math.min(32767,v));
            out[i]=(byte)v;out[i+1]=(byte)(v>>8);
        }
        return out;
    }
}
