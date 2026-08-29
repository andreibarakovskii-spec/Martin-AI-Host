package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class GroqTranscriberTest {
 @Test public void knownSubtitleHallucinationIsAlwaysDropped(){assertTrue(GroqTranscriber.shouldDropTranscript("Редактор субтитров А.Семкин Корректор А.Егорова",false));}
 @Test public void promptEchoNamesAreDroppedOnlyDuringMusic(){assertTrue(GroqTranscriber.shouldDropTranscript("Имена, Игорь, Катя, Сергей, Андрей",true));assertFalse(GroqTranscriber.shouldDropTranscript("Имена, Игорь, Катя, Сергей, Андрей",false));}
 @Test public void normalSpeechSurvivesMusic(){assertFalse(GroqTranscriber.shouldDropTranscript("Сергей, выключи музыку",true));assertFalse(GroqTranscriber.shouldDropTranscript("Меня зовут Андрей",true));}
}
