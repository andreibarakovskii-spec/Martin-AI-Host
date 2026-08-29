package com.imagine.martinhost;
import org.junit.Test;import static org.junit.Assert.*;
public class GuestAppearanceMemoryTest{
 @Test public void restoresOnlyClearAppearanceMatch(){GuestAppearanceMemory m=new GuestAppearanceMemory();float[] andrei={.4f,.6f,.3f,.22f,.31f,.95f};float[] katya={.8f,.3f,.7f,.14f,.22f,.70f};m.remember("Андрей",andrei);m.remember("Катя",katya);assertEquals("Андрей",m.match(new float[]{.41f,.59f,.31f,.22f,.30f,.96f}));assertEquals("",m.match(new float[]{.6f,.45f,.5f,.18f,.26f,.82f}));}
}
