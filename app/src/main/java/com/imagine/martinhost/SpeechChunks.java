package com.imagine.martinhost;
import java.util.*;
/** Bounded chunks, without dropping a long sentence or cutting a UTF-16 surrogate pair. */
public final class SpeechChunks {
 public static List<String> split(String text){
  List<String> result=new ArrayList<>();
  for(String sentence:text.trim().split("(?<=[.!?…])\\s+")){
   String rest=sentence.trim();
   while(rest.length()>160){
    int cut=rest.lastIndexOf(' ',160);if(cut<40)cut=160;
    if(Character.isHighSurrogate(rest.charAt(cut-1)))cut--;
    result.add(rest.substring(0,cut).trim());rest=rest.substring(cut).trim();
   }
   if(!rest.isEmpty())result.add(rest);
  }
  return result;
 }
}
