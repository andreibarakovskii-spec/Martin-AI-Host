package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class GrokClientTextTest {
 @Test public void removesRepeatedServiceTail(){assertEquals("Понял, Андрей.",GrokClient.sanitizeHostText("Понял, Андрей. Информация принята."));}
 @Test public void doesNotDamageNormalReply(){assertEquals("Ситуация развивается.",GrokClient.sanitizeHostText("Ситуация развивается."));}
}
