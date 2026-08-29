package com.imagine.martinhost;
import java.util.*;import java.util.regex.Pattern;
/** Stress hints are applied only to TTS; the readable reply remains unchanged. */
final class RussianTtsNormalizer {
 private static final Map<String,String> WORDS=new LinkedHashMap<>();static{WORDS.put("музыка","му\u0301зыка");WORDS.put("музыку","му\u0301зыку");WORDS.put("музыкальная","музыка\u0301льная");WORDS.put("музыкальный","музыка\u0301льный");WORDS.put("начнем","начнём");WORDS.put("включим","вклю\u0301чим");WORDS.put("включить","включи\u0301ть");WORDS.put("позвонит","позвони\u0301т");WORDS.put("торты","то\u0301рты");WORDS.put("торта","то\u0301рта");WORDS.put("каталог","катало\u0301г");WORDS.put("баловать","балова\u0301ть");WORDS.put("красивее","краси\u0301вее");}
 static String prepare(String s){String out=s==null?"":s;for(var e:WORDS.entrySet())out=Pattern.compile("(?iu)\\b"+Pattern.quote(e.getKey())+"\\b").matcher(out).replaceAll(e.getValue());return out.replace(" — ",". ");}
 private RussianTtsNormalizer(){}
}
