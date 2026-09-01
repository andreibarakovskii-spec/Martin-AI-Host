package com.imagine.martinhost;

import org.junit.Test;
import static org.junit.Assert.*;

public class BargeInPolicyTest {
    private final BargeInPolicy p=new BargeInPolicy();

    @Test public void explicitStopInterrupts(){assertTrue(p.evaluate("Стоп, погоди", "Сейчас я расскажу длинную историю").accepted);}
    @Test public void assistantNamePlusSpeechInterrupts(){assertTrue(p.evaluate("Сергей, я про другое", "Сейчас расскажу про погоду").accepted);}
    @Test public void weakAmbientWordDoesNotInterrupt(){assertFalse(p.evaluate("ага", "Продолжаю ответ").accepted);}
    @Test public void likelyTtsEchoIsRejected(){
        BargeInPolicy.Result r=p.evaluate("Ситуация развивается довольно быстро", "Ситуация развивается довольно быстро, поэтому продолжим");
        assertFalse(r.accepted);assertEquals("similar_to_tts",r.reason);
    }
    @Test public void nameAloneIsNotEnough(){assertFalse(p.evaluate("Сергей", "Продолжаю").accepted);}
}
