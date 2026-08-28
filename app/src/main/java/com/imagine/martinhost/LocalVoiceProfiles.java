package com.imagine.martinhost;
import java.io.*;import java.nio.file.*;import java.util.*;
/** SDK 0.0.17 only exposes its default voice. Give each engine an isolated model view:
 * unchanged shared weights + the selected official style under the SDK default name F1.
 * The canonical downloaded models are never rewritten. No extra 380 MB copies. */
public final class LocalVoiceProfiles {
 public static final List<String> IDS=Collections.unmodifiableList(Arrays.asList("M1","M2","M3","M4","M5","F1","F2","F3","F4","F5"));
 static final String[] MODEL_FILES={"duration_predictor.tflite","text_encoder.tflite","vector_estimator.tflite","vocoder.tflite","tts.json","unicode_indexer.json"};
 public static String valid(String id){return IDS.contains(id)?id:"M1";}
 public static String label(String id){return (id.startsWith("M")?"Мужской ":"Женский ")+id.substring(1)+" · "+id;}
 public static File prepare(File models,File appFiles,String requested)throws IOException{
  String id=valid(requested);File profile=new File(appFiles,"local-voices-v1/"+id);File styles=new File(profile,"voice_styles");if(!styles.isDirectory()&&!styles.mkdirs())throw new IOException("Не удалось создать профиль голоса");
  for(String name:MODEL_FILES)link(new File(models,name),new File(profile,name));
  link(new File(models,"voice_styles/"+id+".json"),new File(styles,"F1.json"));return profile;
 }
 private static void link(File source,File target)throws IOException{
  if(!source.isFile()||source.length()==0)throw new IOException("Не хватает файла голосовой модели: "+source.getName());
  Path src=source.toPath().toAbsolutePath(),dst=target.toPath();
  if(Files.exists(dst)&&Files.isSameFile(src,dst))return;
  Files.deleteIfExists(dst);
  try{Files.createLink(dst,src);}catch(IOException|UnsupportedOperationException e){try{Files.createSymbolicLink(dst,src);}catch(IOException|UnsupportedOperationException second){throw new IOException("Телефон не поддерживает профили локальных голосов",second);}}
 }
}
