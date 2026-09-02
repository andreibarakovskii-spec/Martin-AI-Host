package com.imagine.martinhost;

import java.util.*;

/**
 * Short-lived room/conversation memory. It keeps only a compact window needed to
 * behave like a participant in the current discussion. This is deliberately not
 * durable personal memory.
 */
public final class ConversationWorkingMemory {
    public enum Role { USER_DIRECT, DIALOG_CONTEXT, AMBIENT }

    public static final class Entry {
        public final long atMs;
        public final Role role;
        public final String speaker;
        public final String text;
        public final String topic;
        Entry(long atMs, Role role, String speaker, String text, String topic){
            this.atMs=atMs;this.role=role;this.speaker=speaker;this.text=text;this.topic=topic;
        }
    }

    private final ArrayDeque<Entry> entries=new ArrayDeque<>();
    private final LinkedHashSet<String> activeTerms=new LinkedHashSet<>();
    private String activeTopic="";
    private long lastRelevantAtMs;
    private int topicRevision;

    public synchronized void reset(){entries.clear();activeTerms.clear();activeTopic="";lastRelevantAtMs=0;topicRevision=0;}

    public synchronized void observe(String raw, AttentionManager.Attention attention, long nowMs){
        String text=raw==null?"":raw.trim();if(text.isBlank())return;
        Set<String> terms=terms(text);
        boolean direct=attention==AttentionManager.Attention.DIRECT;
        boolean related=direct || relatedToActive(terms) || discourseContinuation(text);
        Role role=direct?Role.USER_DIRECT:(related?Role.DIALOG_CONTEXT:Role.AMBIENT);

        if(related){
            double overlap=overlap(terms,activeTerms);
            boolean substantial=terms.size()>=2;
            boolean topicShift=!activeTerms.isEmpty() && substantial && overlap<0.18 && !discourseContinuation(text);
            if(topicShift)topicRevision++;
            if(direct||topicShift||activeTerms.isEmpty()){
                activeTerms.clear();for(String t:terms){activeTerms.add(t);if(activeTerms.size()>=8)break;}
                activeTopic=compactTopic(activeTerms);
            }else{
                for(String t:terms){activeTerms.add(t);if(activeTerms.size()>10){Iterator<String> it=activeTerms.iterator();it.next();it.remove();}}
                activeTopic=compactTopic(activeTerms);
            }
            lastRelevantAtMs=nowMs;
        }
        entries.addLast(new Entry(nowMs,role,"unknown",text,related?activeTopic:""));
        while(entries.size()>14)entries.removeFirst();
        while(!entries.isEmpty()&&nowMs-entries.peekFirst().atMs>180_000L)entries.removeFirst();
    }

    public synchronized boolean shouldKeepAmbientInDialogue(String raw){
        String text=raw==null?"":raw.trim();if(text.isBlank()||activeTerms.isEmpty())return false;
        Set<String> t=terms(text);
        return relatedToActive(t)||discourseContinuation(text);
    }

    public synchronized String promptContext(long nowMs){
        if(entries.isEmpty())return "";
        StringBuilder b=new StringBuilder();
        b.append("\n\nВРЕМЕННЫЙ КОНТЕКСТ ТЕКУЩЕГО РАЗГОВОРА (не долговременная память):\n");
        if(!activeTopic.isBlank())b.append("Активная тема: ").append(activeTopic).append(". Версия темы: ").append(topicRevision).append(".\n");
        b.append("Недавние реплики:\n");
        int count=0;
        for(Entry e:entries){
            if(nowMs-e.atMs>120_000L)continue;
            if(e.role==Role.AMBIENT)continue;
            b.append("- ").append(e.role==Role.USER_DIRECT?"пользователь":"контекст беседы").append(": ").append(e.text).append("\n");
            if(++count>=8)break;
        }
        b.append("Используй этот блок только для понимания текущей беседы. Не выдавай его за подтверждённую долговременную память и не придумывай личности говорящих.");
        return b.toString();
    }

    public synchronized String activeTopic(){return activeTopic;}
    public synchronized int topicRevision(){return topicRevision;}
    public synchronized List<Entry> snapshot(){return new ArrayList<>(entries);}

    private boolean relatedToActive(Set<String> t){return !activeTerms.isEmpty()&&overlap(t,activeTerms)>=0.22;}
    private static double overlap(Set<String> a,Set<String> b){if(a.isEmpty()||b.isEmpty())return 0;int n=0;for(String s:a)if(b.contains(s))n++;return (double)n/Math.max(1,Math.min(a.size(),b.size()));}
    private static boolean discourseContinuation(String text){
        String n=AttentionManager.normalize(text);
        return n.matches("^(а |и |но |ну |так |тогда |кстати |поэтому |зато |еще |ещё |он |она |они |это |там |тут |здесь |потом |дальше ).*")
                || n.matches(".*\\b(про это|об этом|по этой теме|к этому|продолжая|как я говорил|как я говорила)\\b.*");
    }
    private static Set<String> terms(String text){
        String n=AttentionManager.normalize(text);LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String w:n.split(" ")){
            if(w.length()<4||STOP.contains(w))continue;out.add(w);
        }
        return out;
    }
    private static String compactTopic(Set<String> terms){StringBuilder b=new StringBuilder();int i=0;for(String s:terms){if(i++>0)b.append(", ");b.append(s);if(i>=5)break;}return b.toString();}
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
            "когда","тогда","этого","этой","этот","только","просто","очень","тоже","чтобы","потом","сейчас","было","была","были","будет","можно","нужно","хочу","хотел","хотела","говорить","сказать","сказал","сказала","меня","тебя","тебе","мне","него","нее","него","который","которая","которые"));
}
