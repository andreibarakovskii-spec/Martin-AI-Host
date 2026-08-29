package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class MusicRequestRouterTest {
 @Test public void extractsNaturalRequest(){assertEquals("Руки Вверх Крошка моя",MusicRequestRouter.extract("Сергей, включи песню Руки Вверх Крошка моя"));}
 @Test public void ignoresNormalConversation(){assertEquals("",MusicRequestRouter.extract("Какая музыка тебе нравится?"));}
 @Test public void addsStressHints(){assertTrue(RussianTtsNormalizer.prepare("Включить музыку").contains("\u0301"));}
}
