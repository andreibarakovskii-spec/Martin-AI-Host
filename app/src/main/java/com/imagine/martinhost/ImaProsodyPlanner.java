package com.imagine.martinhost;

import java.util.Locale;

/** Backend-neutral expressive speech plan for IMA. Keeps personality/prosody outside Piper. */
public final class ImaProsodyPlanner {
    public static final class Plan {
        public final String style;
        public final float speed;
        public final float silenceScale;
        public final float gain;
        public final int sentencePauseMs;
        Plan(String style,float speed,float silenceScale,float gain,int sentencePauseMs){
            this.style=style;this.speed=speed;this.silenceScale=silenceScale;this.gain=gain;this.sentencePauseMs=sentencePauseMs;
        }
    }
    private ImaProsodyPlanner(){}

    public static Plan plan(String text,String emotion,float energy){
        String e=emotion==null?"":emotion.toLowerCase(Locale.ROOT);
        String style="neutral"; float speed=1f,silence=.12f,gain=1f; int pause=85;
        if(has(e,"empathetic","sad","tender","support","sympathetic")){
            style="empathetic";speed=.92f;silence=.18f;gain=.94f;pause=150;
        }else if(has(e,"warm","caring","friendly")){
            style="warm";speed=.95f;silence=.15f;gain=.98f;pause=120;
        }else if(has(e,"calm","soft","relaxed")){
            style="calm";speed=.94f;silence=.16f;gain=.96f;pause=130;
        }else if(has(e,"excited","enthusiastic")){
            style="excited";speed=1.08f;silence=.08f;gain=1.08f;pause=45;
        }else if(has(e,"playful","ironic","teasing")){
            style="playful";speed=1.06f;silence=.09f;gain=1.06f;pause=55;
        }else if(has(e,"happy","joy","cheerful")){
            style="happy";speed=1.04f;silence=.10f;gain=1.05f;pause=65;
        }else if(has(e,"curious","question","wonder")){
            style="curious";speed=1.00f;silence=.11f;gain=1.00f;pause=80;
        }else if(has(e,"serious","confident","firm")){
            style="confident";speed=.97f;silence=.11f;gain=1.02f;pause=90;
        }else if(text!=null&&text.trim().endsWith("?")){
            style="curious";speed=1.00f;silence=.11f;gain=1.00f;pause=80;
        }else if(text!=null&&text.trim().endsWith("!")){
            style="bright";speed=1.03f;silence=.10f;gain=1.04f;pause=65;
        }
        float en=Math.max(0f,Math.min(1f,energy));
        gain*=.92f+en*.16f;
        gain=Math.max(.85f,Math.min(1.12f,gain));
        return new Plan(style,speed,silence,gain,pause);
    }

    private static boolean has(String value,String... needles){for(String n:needles)if(value.contains(n))return true;return false;}

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
