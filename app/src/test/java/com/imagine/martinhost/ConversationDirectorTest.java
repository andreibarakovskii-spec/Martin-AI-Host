package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationDirectorTest {
    @Test public void directAddressRespondsAndStripsName() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("IMA, что мы сегодня делаем?", 1000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertEquals("что мы сегодня делаем?", r.text);
        assertEquals(AttentionManager.Attention.DIRECT, r.attention);
    }

    @Test public void russianImaAddressWorks() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("Има, расскажи про память", 1000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertEquals("расскажи про память", r.text);
    }

    @Test public void ambientStatementIsIgnoredBeforeConversation() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.IGNORE, d.decide("На улице машина проехала", 1000).kind);
    }

    @Test public void continuationWorksWithoutWakeWord() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.RESPOND, d.decide("IMA, расскажи про память", 1000).kind);
        ConversationDirector.Decision r = d.decide("А чем эпизодическая отличается от обычной?", 5000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertEquals(AttentionManager.Attention.LIKELY, r.attention);
    }

    @Test public void continuationExpires() {
        ConversationDirector d = new ConversationDirector();
        d.decide("IMA, расскажи про память", 1000);
        assertEquals(ConversationDirector.Kind.IGNORE, d.decide("Сегодня довольно тепло", 60_000).kind);
    }

    @Test public void naturalInterruptStops() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.STOP, d.decide("IMA, погоди", 1000).kind);
    }

    @Test public void continueHasDedicatedRoute() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.CONTINUE, d.decide("продолжай", 1000).kind);
    }

    @Test public void correctionIsMarkedAsRepair() {
        ConversationDirector d = new ConversationDirector();
        d.decide("IMA, расскажи про эпизодическую память", 1000);
        ConversationDirector.Decision r=d.decide("Нет, не речь, а память",2000);
        assertEquals(ConversationDirector.Kind.RESPOND,r.kind);
        assertEquals("repair",r.reason);
        assertTrue(r.text.contains("Исправление пользователя"));
    }

    @Test public void musicIsLocalToolIntent() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.MUSIC, d.decide("IMA, включи музыку", 1000).kind);
    }

    @Test public void visionIsLocalToolIntent() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.VISION, d.decide("IMA, посмотри что у меня в руках", 1000).kind);
    }
}
