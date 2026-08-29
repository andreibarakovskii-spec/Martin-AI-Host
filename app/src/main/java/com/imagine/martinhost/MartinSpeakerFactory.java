package com.imagine.martinhost;
import android.content.Context;

public final class MartinSpeakerFactory {
 private MartinSpeakerFactory(){}
 public static MartinSpeaker create(Context c,MartinSpeaker.Listener l){
  String provider=c.getSharedPreferences("martin",0).getString("voice_provider","local");
  return "system".equals(provider)?new SystemMartinSpeaker(c,l):new MartinNeuralSpeaker(c,l);
 }
}
