package com.imagine.martinhost;

/** Common host speech contract. Production voice is app-owned and backend-neutral. */
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
 /** Called on user speech start so a hot voice path is ready before the answer arrives. */
 default void preArm(){if(!isReady())prepare();}
 void speak(String text,String emotion,float energy);
 /** Begin one logical AI response. Backends may keep generation/playback state alive across appends. */
 default void beginResponse(String emotion,float energy){}
 /** Append a speakable fragment to the current response without cancelling prior fragments. */
 default void appendResponse(String text,String emotion,float energy){speak(text,emotion,energy);}
 /** Mark the logical response complete; already queued speech must finish normally. */
 default void finishResponse(){}
 /** Cancel the current logical response immediately. */
 default void cancelResponse(){stop();}
 /** Pause current playback without discarding its position. Returns true when supported. */
 default boolean pauseForBargeIn(){return false;}
 /** Resume playback previously paused by pauseForBargeIn(). */
 default boolean resumeAfterBargeIn(){return false;}
 void stop();
 void releaseModel();
 void close();
}
