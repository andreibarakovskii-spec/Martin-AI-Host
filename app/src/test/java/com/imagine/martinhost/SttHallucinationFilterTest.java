package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class SttHallucinationFilterTest {
 @Test public void rejectsSubtitleHallucinations(){assertTrue(SttHallucinationFilter.reject("Редактор субтитров Иван Иванов"));assertTrue(SttHallucinationFilter.reject("Спасибо за просмотр"));}
 @Test public void keepsRealPartySpeech(){assertFalse(SttHallucinationFilter.reject("Меня зовут Андрей"));assertFalse(SttHallucinationFilter.reject("Сергей, выключи музыку"));assertFalse(SttHallucinationFilter.reject("Не знаю, давай дальше"));}
}
