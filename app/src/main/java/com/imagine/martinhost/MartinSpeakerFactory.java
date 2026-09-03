package com.imagine.martinhost;
import android.content.Context;

/** Production voice is owned by imagination, never the device vendor TTS. */
public final class MartinSpeakerFactory {
 private MartinSpeakerFactory(){}
 public static MartinSpeaker create(Context c,MartinSpeaker.Listener l){
  return new MartinNeuralSpeaker(c,l);
 }
}
