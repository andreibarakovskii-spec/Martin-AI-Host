package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationDirectorTest {
    @Test public void directAddressRespondsAndStripsName() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("Сергей, что мы сегодня делаем?", 1000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertEquals("что мы сегодня делаем?", r.text);
        assertEquals(AttentionManager.Attention.DIRECT, r.attention);
    }

    @Test public void ambientStatementIsIgnoredBeforeConversation() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("На улице машина проехала", 1000);
        assertEquals(ConversationDirector.Kind.IGNORE, r.kind);
    }

    @Test public void continuationWorksWithoutWakeWord() {
        ConversationDirector d = new ConversationDirector();
        assertEquals(ConversationDirector.Kind.RESPOND, d.decide("Сергей, расскажи про память", 1000).kind);
        ConversationDirector.Decision r = d.decide("А чем эпизодическая отличается от обычной?", 5000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertEquals(AttentionManager.Attention.LIKELY, r.attention);
    }

    @Test public void continuationExpires() {
        ConversationDirector d = new ConversationDirector();
        d.decide("Сергей, расскажи про память", 1000);
        ConversationDirector.Decision r = d.decide("Сегодня довольно тепло", 60_000);
        assertEquals(ConversationDirector.Kind.IGNORE, r.kind);
    }

    @Test public void naturalInterruptStops() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("Сергей, погоди", 1000);
        assertEquals(ConversationDirector.Kind.STOP, r.kind);
    }

    @Test public void musicIsLocalToolIntent() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("Сергей, включи музыку", 1000);
        assertEquals(ConversationDirector.Kind.MUSIC, r.kind);
    }

    @Test public void visionIsLocalToolIntent() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("Сергей, посмотри что у меня в руках", 1000);
        assertEquals(ConversationDirector.Kind.VISION, r.kind);
    }
}
