package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public class BargeInPolicyTest {
    private final BargeInPolicy p=new BargeInPolicy();

    @Test public void explicitStopInterrupts(){assertTrue(p.evaluate("Стоп, погоди", "Сейчас я расскажу длинную историю").accepted);}
    @Test public void assistantNamePlusSpeechInterrupts(){assertTrue(p.evaluate("IMA, я про другое", "Сейчас расскажу про погоду").accepted);}
    @Test public void russianNamePlusSpeechInterrupts(){assertTrue(p.evaluate("Има, подожди", "Сейчас расскажу про погоду").accepted);}
    @Test public void observedSttVariantOfImaInterrupts(){assertTrue(p.evaluate("Нима", "Продолжаю рассказ").accepted);}
    @Test public void assistantNameAloneInterrupts(){assertTrue(p.evaluate("IMA", "Продолжаю").accepted);}
    @Test public void compactTwoWordInterjectionInterrupts(){
        BargeInPolicy.Result r=p.evaluate("Синтез речи", "Продолжаю длинный ответ про архитектуру");
        assertTrue(r.accepted);assertEquals("short_interjection",r.reason);
    }
    @Test public void naturalMultiwordSpeechInterrupts(){
        BargeInPolicy.Result r=p.evaluate("Я живу в Дзержинске", "Сейчас расскажу про эпизодическую память");
        assertTrue(r.accepted);assertEquals("human_floor_claim",r.reason);
    }
    @Test public void unfinishedThoughtInterruptsAndKeepsFloor(){
        BargeInPolicy.Result r=p.evaluate("Ты уже начинаешь…", "Я уже начала отвечать на вопрос");
        assertTrue(r.accepted);assertEquals("unfinished_user_turn",r.reason);
    }
    @Test public void continuationTailInterrupts(){
        BargeInPolicy.Result r=p.evaluate("И ещё я хотел добавить про голос", "Продолжаю свой ответ");
        assertTrue(r.accepted);assertEquals("unfinished_user_turn",r.reason);
    }
    @Test public void userSaysTheyWereNotFinished(){
        BargeInPolicy.Result r=p.evaluate("Я не договорил, а ты уже отвечаешь", "Продолжаю свой ответ");
        assertTrue(r.accepted);assertEquals("repair_phrase",r.reason);
    }
    @Test public void directedContinuationInterrupts(){assertTrue(p.evaluate("Продолжай говорить про память", "Сейчас расскажу про другое").accepted);}
    @Test public void unrelatedRoomSentenceDoesNotInterrupt(){assertFalse(p.evaluate("На улице проехала красная машина", "Продолжаю ответ").accepted);}
    @Test public void weakAmbientWordDoesNotInterrupt(){assertFalse(p.evaluate("ага", "Продолжаю ответ").accepted);}
    @Test public void likelyTtsEchoIsRejected(){
        BargeInPolicy.Result r=p.evaluate("Ситуация развивается довольно быстро", "Ситуация развивается довольно быстро, поэтому продолжим");
        assertFalse(r.accepted);assertEquals("similar_to_tts",r.reason);
    }
    @Test public void stopWinsEvenWhenTtsContainsStop(){assertTrue(p.evaluate("стоп", "Чтобы остановить таймер, скажи стоп").accepted);}
}
