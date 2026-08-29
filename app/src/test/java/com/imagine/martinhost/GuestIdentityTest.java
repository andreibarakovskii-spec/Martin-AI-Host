package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class GuestIdentityTest {
 @Test public void commonSpeechWordsAreNotNewNames(){assertFalse(GuestStore.isPlausibleNewName("не"));assertFalse(GuestStore.isPlausibleNewName("готов"));assertFalse(GuestStore.isPlausibleNewName("всего"));}
 @Test public void properNameIsAccepted(){assertTrue(GuestStore.isPlausibleNewName("Андрей"));}
 @Test public void explicitIntroductionExtractsName(){assertEquals("Андрей",GuestIdentity.explicitName("Меня зовут Андрей."));assertEquals("Катя",GuestIdentity.explicitName("Я Катя, рада познакомиться"));assertEquals("Игорь",GuestIdentity.explicitName("Игорь"));}
 @Test public void listOfNamesCannotBecomeCameraIdentity(){assertEquals("",GuestIdentity.explicitName("Имена, Игорь, Катя, Сергей, Андрей."));assertEquals("",GuestIdentity.explicitName("Катя, Сергей"));}
 @Test public void ordinarySentenceIsNotIntroduction(){assertEquals("",GuestIdentity.explicitName("Я готов"));assertEquals("",GuestIdentity.explicitName("Я всего лишь здесь один"));}
}
