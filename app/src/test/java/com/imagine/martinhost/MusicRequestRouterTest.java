package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class MusicRequestRouterTest {
 @Test public void negativeQuestionIsNotACommand(){assertEquals("",MusicRequestRouter.extract("Так ты не включил?"));}
 @Test public void genericMusicCommandGetsPartyFallback(){assertEquals(MusicRequestRouter.DEFAULT_PARTY_QUERY,MusicRequestRouter.extract("Включи музыку"));}
 @Test public void namedTrackIsPreserved(){assertEquals("Руки Вверх Крошка моя",MusicRequestRouter.extract("Сергей, поставь Руки Вверх Крошка моя"));}
 @Test public void commaAfterVerbDoesNotBreakCommand(){assertEquals("Шадэ By Индия Xcho МОТ",MusicRequestRouter.extract("Включи, она танцует под шады."));}
 @Test public void whisperDropsFirstSyllableOfVkluchi(){assertEquals(MusicRequestRouter.DEFAULT_PARTY_QUERY,MusicRequestRouter.extract("Ключи музыку."));}
}
