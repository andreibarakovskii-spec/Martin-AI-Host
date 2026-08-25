package com.imagine.martinhost;

import android.content.Context;
import java.util.Locale;

/** Minimal event state machine. One production game first: What? Where? When? */
public final class PartyDirector {
    public enum Mode { FREE, CHGK_RULES, CHGK_WAIT_READY, CHGK_QUESTION, CHGK_WAIT_ANSWER, CHGK_WAIT_NAME, CHGK_RESULT }
    public static final class Action {
        public final String speech;
        public final String state;
        public final String gesture;
        public final String emotion;
        public final boolean askAi;
        Action(String speech, String state, String gesture, String emotion, boolean askAi) {
            this.speech=speech; this.state=state; this.gesture=gesture; this.emotion=emotion; this.askAi=askAi;
        }
        public static Action local(String s,String st,String g,String e){ return new Action(s,st,g,e,false); }
        public static Action ai(String prompt,String st,String g,String e){ return new Action(prompt,st,g,e,true); }
    }

    private final GuestStore guests;
    private Mode mode = Mode.FREE;
    private String expectedAnswer = "обещание";
    private int pendingPoints = 1;

    public PartyDirector(Context context) { guests = new GuestStore(context); }
    public Mode mode(){ return mode; }

    public Action startChgk() {
        mode = Mode.CHGK_RULES;
        return Action.local(
            "Играем в «Что? Где? Когда?». Я задаю вопрос, вы можете коротко обсудить ответ и назвать один окончательный вариант. За правильный ответ — один балл. Пример: что можно разбить, даже не прикасаясь? Ответ — обещание. Правила понятны? Начинаем?",
            "game", "explain_two_hands", "curious");
    }

    public Action onUserText(String raw) {
        String text = raw == null ? "" : raw.trim();
        String low = text.toLowerCase(Locale.ROOT);
        if (mode == Mode.FREE) {
            if (low.contains("что где когда") || low.contains("чгк") || low.contains("начни игру")) return startChgk();
            if (low.contains("тост")) return Action.ai("Скажи короткий теплый тост для Кати и гостей, без принуждения к алкоголю.","toast","raise_glass","warm");
            return Action.ai(text,"talking","talk_neutral","neutral");
        }
        if (mode == Mode.CHGK_RULES || mode == Mode.CHGK_WAIT_READY) {
            if (isYes(low)) {
                mode = Mode.CHGK_QUESTION;
                expectedAnswer = "тень";
                mode = Mode.CHGK_WAIT_ANSWER;
                return Action.local("Вопрос. Что становится больше, если от него отнимать?", "game", "question_pose", "focused");
            }
            mode = Mode.CHGK_WAIT_READY;
            return Action.local("Если что-то непонятно — спрашивайте. Когда будете готовы, скажите «начинаем».","listening","explain_one_hand","neutral");
        }
        if (mode == Mode.CHGK_WAIT_ANSWER) {
            if (isCorrect(low, expectedAnswer)) {
                pendingPoints = 1;
                mode = Mode.CHGK_WAIT_NAME;
                return Action.local("Верно! Кто угадал?", "happy", "point_forward", "happy");
            }
            return Action.local("Пока не то. Подумайте ещё.", "thinking", "head_shake", "playful");
        }
        if (mode == Mode.CHGK_WAIT_NAME) {
            String name = cleanupName(text);
            if (!name.isBlank()) {
                guests.addScoreByName(name, pendingPoints);
                mode = Mode.CHGK_RESULT;
                return Action.local(name + ", плюс один балл. Отличное начало!", "happy", "celebrate", "happy");
            }
            return Action.local("Не расслышал имя. Кто ответил?", "listening", "point_forward", "curious");
        }
        if (mode == Mode.CHGK_RESULT) {
            mode = Mode.FREE;
            return Action.local("Первый раунд закончен. Дальше можем поговорить, сказать тост или позже сыграть ещё.","idle","open_hands","neutral");
        }
        return Action.ai(text,"talking","talk_neutral","neutral");
    }

    private static boolean isYes(String s){ return s.contains("да") || s.contains("начина") || s.contains("поехали") || s.contains("готов"); }
    private static boolean isCorrect(String s,String answer){ return s.contains(answer); }
    private static String cleanupName(String s){ return s.replaceAll("(?i)это|я|ответил|ответила|угадал|угадала", "").trim(); }
}
