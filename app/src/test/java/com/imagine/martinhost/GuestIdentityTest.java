package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class GuestIdentityTest {
 @Test public void commonSpeechWordsAreNotNewNames(){assertFalse(GuestStore.isPlausibleNewName("не"));assertFalse(GuestStore.isPlausibleNewName("готов"));assertFalse(GuestStore.isPlausibleNewName("всего"));}
 @Test public void properNameIsAccepted(){assertTrue(GuestStore.isPlausibleNewName("Андрей"));}
}
