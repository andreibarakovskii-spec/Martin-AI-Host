package com.imagine.martinhost;

import com.k2fsa.sherpa.onnx.*;
import java.io.File;
import java.util.Collections;
import java.util.function.BooleanSupplier;

/** Owned only by MartinNeuralSpeaker's serialized operation; never released during inference. */
public final class FastVoiceEngine implements AutoCloseable {
 private final OfflineTts tts;
 public static final int STEPS=5;
 public FastVoiceEngine(File d){
  OfflineTtsSupertonicModelConfig sc=OfflineTtsSupertonicModelConfig.builder()
   .setDurationPredictor(new File(d,"duration_predictor.int8.onnx").getAbsolutePath())
   .setTextEncoder(new File(d,"text_encoder.int8.onnx").getAbsolutePath())
   .setVectorEstimator(new File(d,"vector_estimator.int8.onnx").getAbsolutePath())
   .setVocoder(new File(d,"vocoder.int8.onnx").getAbsolutePath())
   .setTtsJson(new File(d,"tts.json").getAbsolutePath())
   .setUnicodeIndexer(new File(d,"unicode_indexer.bin").getAbsolutePath())
   .setVoiceStyle(new File(d,"voice.bin").getAbsolutePath()).build();
  OfflineTtsModelConfig mc=OfflineTtsModelConfig.builder().setSupertonic(sc).setNumThreads(2).setDebug(false).setProvider("cpu").build();
  tts=new OfflineTts(OfflineTtsConfig.builder().setModel(mc).build());
  if(tts.getNumSpeakers()!=10){tts.release();throw new IllegalStateException("Неверное число голосов модели");}
 }
 public static int speakerId(String voice){
  String v=LocalVoiceProfiles.valid(voice);
  // Upstream generate_voices_bin.py sorts style filenames: F1..F5,M1..M5.
  return (v.charAt(0)=='M'?5:0)+(v.charAt(1)-'1');
 }
 public static final class Pcm {
  public final byte[] pcm16;public final int sampleRate;
  Pcm(byte[] bytes,int rate){pcm16=bytes;sampleRate=rate;}
 }
 public Pcm synthesize(String text,String voice,float speed,BooleanSupplier cancelled){
  GenerationConfig gc=new GenerationConfig();gc.setSid(speakerId(voice));gc.setSpeed(speed);gc.setNumSteps(STEPS);gc.setSilenceScale(.16f);gc.setExtra(Collections.singletonMap("lang","ru"));
  GeneratedAudio audio=tts.generateWithConfigAndCallback(text,gc,(OfflineTtsCallback)samples->cancelled.getAsBoolean()?0:1);
  if(cancelled.getAsBoolean())return new Pcm(new byte[0],tts.getSampleRate());
  float[] samples=audio.getSamples();byte[] bytes=new byte[samples.length*2];
  for(int i=0;i<samples.length;i++){int v=(int)(Math.max(-1f,Math.min(1f,samples[i]))*32767);bytes[2*i]=(byte)v;bytes[2*i+1]=(byte)(v>>8);}
  return new Pcm(bytes,audio.getSampleRate());
 }
 @Override public void close(){tts.release();}
}
