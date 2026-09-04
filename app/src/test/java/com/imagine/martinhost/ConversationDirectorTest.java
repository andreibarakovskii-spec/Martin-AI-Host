package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationDirectorTest {
    @Test public void directAddressRespondsAndStripsName() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("IMA, что мы сегодня делаем?", 1000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertTrue(r.text.startsWith("что мы сегодня делаем?"));
        assertEquals(AttentionManager.Attention.DIRECT, r.attention);
    }

    @Test public void russianImaAddressWorks() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r = d.decide("Има, расскажи про память", 1000);
        assertEquals(ConversationDirector.Kind.RESPOND, r.kind);
        assertTrue(r.text.startsWith("расскажи про память"));
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
        assertTrue(r.text.contains("ВРЕМЕННЫЙ КОНТЕКСТ"));
    }

    @Test public void continuationExpires() {
        ConversationDirector d = new ConversationDirector();
        d.decide("IMA, расскажи про память", 1000);
        assertEquals(ConversationDirector.Kind.IGNORE, d.decide("Сегодня довольно тепло", 60_000).kind);
    }

    @Test public void relatedRoomSpeechCanBeKeptWithoutForcingReply() {
        ConversationDirector d = new ConversationDirector();
        d.decide("IMA, давай обсудим эпизодическую память", 1000);
        ConversationDirector.Decision ambient=d.decide("Эпизодическая память связана с событиями", 60_000);
        assertEquals(ConversationDirector.Kind.IGNORE,ambient.kind);
        assertEquals("ambient_kept_as_dialog_context",ambient.reason);
        ConversationDirector.Decision next=d.decide("IMA, продолжим эту тему",61_000);
        assertTrue(next.text.contains("Эпизодическая память связана с событиями"));
    }

    @Test public void unrelatedRoomSpeechDoesNotPollutePromptContext() {
        ConversationDirector d = new ConversationDirector();
        d.decide("IMA, давай обсудим эпизодическую память", 1000);
        d.decide("На кухне закипел чайник", 60_000);
        ConversationDirector.Decision next=d.decide("IMA, продолжим про память",61_000);
        assertFalse(next.text.contains("закипел чайник"));
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

    @Test public void unfinishedSentenceWaitsAndStitchesNextChunk() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision first=d.decide("Има, я хотел",1000);
        assertEquals(ConversationDirector.Kind.IGNORE,first.kind);
        assertEquals("user_turn_incomplete_wait",first.reason);
        assertTrue(d.hasPendingUserTurn());

        ConversationDirector.Decision second=d.decide("спросить тебя про память",2200);
        assertEquals(ConversationDirector.Kind.RESPOND,second.kind);
        assertTrue(second.text.startsWith("я хотел спросить тебя про память"));
        assertFalse(d.hasPendingUserTurn());
    }

    @Test public void trailingConnectorWaitsForRestOfThought() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision first=d.decide("Има, объясни мне это потому что",1000);
        assertEquals("user_turn_incomplete_wait",first.reason);
        ConversationDirector.Decision second=d.decide("я не понял прошлый ответ",2000);
        assertEquals(ConversationDirector.Kind.RESPOND,second.kind);
        assertTrue(second.text.contains("потому что я не понял прошлый ответ"));
    }

    @Test public void continuationAfterEarlyAssistantStartReconstructsUserTurn() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision first=d.decide("Има, расскажи про новый голос",1000);
        assertEquals(ConversationDirector.Kind.RESPOND,first.kind);
        ConversationDirector.Decision second=d.decide("и еще про интонации",3500);
        assertEquals(ConversationDirector.Kind.RESPOND,second.kind);
        assertTrue(second.text.startsWith("расскажи про новый голос и еще про интонации"));
    }

    @Test public void completeSentenceDoesNotGetHeldOpen() {
        ConversationDirector d = new ConversationDirector();
        ConversationDirector.Decision r=d.decide("Има, расскажи про память",1000);
        assertEquals(ConversationDirector.Kind.RESPOND,r.kind);
        assertFalse(d.hasPendingUserTurn());
    }
}
