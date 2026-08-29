package com.imagine.martinhost;

/** Common host speech contract. The selected provider is explicit in settings. */
public interface MartinSpeaker extends AutoCloseable {
 interface Listener {
  void onPreparing(String message);
  void onReady();
  void onStart();
  void onLevel(float level);
  void onSpectrum(float[] bands);
  void onDone();
  void onError(String message);
 }
 boolean isReady();
 void prepare();
 void speak(String text,String emotion,float energy);
 void stop();
 void releaseModel();
 void close();
}
