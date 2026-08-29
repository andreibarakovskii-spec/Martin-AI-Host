package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;

public class PartyMusicRouteTest {
 @Test public void yandexQuizUrisAreExplicitlyRecognized(){assertTrue(PartyMusic.isYandexClipUri("yandex:Руки Вверх Крошка моя"));assertFalse(PartyMusic.isYandexClipUri("content://music/1"));assertFalse(PartyMusic.isYandexClipUri("yandex:"));assertFalse(PartyMusic.isYandexClipUri(null));}
}
