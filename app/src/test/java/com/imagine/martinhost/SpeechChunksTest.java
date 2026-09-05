package com.imagine.martinhost;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SpeechChunksTest {
    @Test public void shortNaturalSentenceIsNotCutByCharacterCount(){
        String s="Я хотела рассказать тебе об этом спокойно и без странной паузы посередине фразы.";
        List<String> parts=SpeechChunks.split(s);
        assertEquals(1,parts.size());
        assertEquals(s,parts.get(0));
    }

    @Test public void punctuationCreatesNaturalUnits(){
        List<String> parts=SpeechChunks.split("Сначала скажу главное. Потом добавлю детали? Хорошо!");
        assertEquals(3,parts.size());
        assertTrue(parts.get(0).endsWith("."));
        assertTrue(parts.get(1).endsWith("?"));
        assertTrue(parts.get(2).endsWith("!"));
    }

    @Test public void veryLongSentencePrefersClauseBoundary(){
        String s="Это достаточно длинная фраза которая специально продолжается без точки чтобы проверить новый алгоритм, но здесь уже есть естественная граница смысловой части, поэтому транспортный блок можно разделить здесь и не делать это после случайного пятого слова потому что так речь звучит значительно естественнее.";
        List<String> parts=SpeechChunks.split(s);
        assertTrue(parts.size()>=2);
        assertTrue(parts.get(0).length()>=70);
    }
}
