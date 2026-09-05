package com.imagine.martinhost;
import java.util.*;
/**
 * Prosody-aware speech chunking.
 *
 * Important: chunks are transport/synthesis units, not audible pauses. We prefer
 * complete clauses and punctuation boundaries and avoid the old arbitrary ~56
 * character split which could cut a natural phrase after only a few words.
 */
public final class SpeechChunks {
 public static List<String> split(String text){
  List<String> result=new ArrayList<>();
  String compact=text==null?"":text.replaceAll("\\s+"," ").trim();
  if(compact.isEmpty())return result;

  for(String sentence:compact.split("(?<=[.!?…])\\s+")){
   String rest=sentence.trim();
   while(rest.length()>180){
    int cut=naturalCut(rest,150);
    if(cut<=0||cut>=rest.length())break;
    result.add(rest.substring(0,cut).trim());
    rest=rest.substring(cut).trim();
   }
   if(!rest.isEmpty())result.add(rest);
  }
  return result;
 }

 /** Prefer a real clause boundary. Only very long clauses may fall back to a word boundary. */
 private static int naturalCut(String s,int target){
  int max=Math.min(s.length()-1,target+28);
  int min=Math.min(max,Math.max(70,target-55));
  for(int i=max;i>=min;i--){
   char c=s.charAt(i);
   if(c==','||c==';'||c==':'||c=='—'||c=='–')return i+1;
  }
  // Common Russian discourse boundaries are better than cutting an arbitrary phrase.
  String lower=s.toLowerCase(Locale.ROOT);
  String[] markers={" потому что "," поэтому "," но "," а "," и тогда "," то есть "," хотя "," если "," когда "};
  int best=-1;
  for(String marker:markers){
   int p=lower.lastIndexOf(marker,max);
   if(p>=min-1)best=Math.max(best,p+1);
  }
  if(best>0)return best;
  int space=s.lastIndexOf(' ',max);
  return space>=min?space:-1;
 }
}
