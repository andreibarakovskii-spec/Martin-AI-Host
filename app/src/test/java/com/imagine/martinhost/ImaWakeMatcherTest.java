package com.imagine.martinhost;

import static org.junit.Assert.*;
import org.junit.Test;

public class ImaWakeMatcherTest {
    @Test public void acceptsObservedSttVariants() {
        assertTrue(ImaWakeMatcher.mentionsIma("Има, привет"));
        assertTrue(ImaWakeMatcher.mentionsIma("Нима привет"));
        assertTrue(ImaWakeMatcher.mentionsIma("Имма"));
        assertTrue(ImaWakeMatcher.mentionsIma("Ема"));
        assertTrue(ImaWakeMatcher.mentionsIma("Тима"));
        assertTrue(ImaWakeMatcher.mentionsIma("Сима"));
    }

    @Test public void rejectsUnrelatedWords() {
        assertFalse(ImaWakeMatcher.mentionsIma("мама привет"));
        assertFalse(ImaWakeMatcher.mentionsIma("зима пришла"));
        assertFalse(ImaWakeMatcher.mentionsIma("привет всем"));
    }
}
