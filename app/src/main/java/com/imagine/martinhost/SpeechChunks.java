package com.imagine.martinhost;
import java.util.*;
/** Latency-oriented speech chunks. First audio should be generated from a short natural phrase. */
public final class SpeechChunks {
 public static List<String> split(String text){
  List<String> result=new ArrayList<>();
  String compact=text==null?"":text.trim();
  if(compact.isEmpty())return result;
  for(String sentence:compact.split("(?<=[.!?…])\\s+")){
   String rest=sentence.trim();
   int limit=result.isEmpty()?56:110;
   while(rest.length()>limit){
    int cut=bestCut(rest,limit);
    if(Character.isHighSurrogate(rest.charAt(cut-1)))cut--;
    result.add(rest.substring(0,cut).trim());
    rest=rest.substring(cut).trim();
    limit=110;
   }
   if(!rest.isEmpty())result.add(rest);
  }
  return result;
 }
 private static int bestCut(String s,int limit){
  int from=Math.min(limit,s.length()-1);
  for(int i=from;i>=Math.max(24,from-22);i--){char c=s.charAt(i);if(c==','||c==';'||c==':'||c=='—')return i+1;}
  int space=s.lastIndexOf(' ',from);return space>=24?space:from;
 }
}
