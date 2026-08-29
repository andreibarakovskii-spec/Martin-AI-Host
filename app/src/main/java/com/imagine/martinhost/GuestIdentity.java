package com.imagine.martinhost;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative extraction of an explicitly spoken guest name. */
final class GuestIdentity {
 private static final Pattern INTRO=Pattern.compile("(?iu)^(?:меня\\s+зовут|мо[её]\\s+имя)\\s*[:,\\-]?\\s*([А-ЯЁ][а-яё-]{1,30})\\b");
 private static final Pattern I_AM=Pattern.compile("(?iu)^(?:я|это)\\s+([А-ЯЁ][а-яё-]{1,30})(?:[.!?,]|$)");
 private static final Pattern ONE=Pattern.compile("^[А-ЯЁ][а-яё-]{1,30}[.!?]?$",Pattern.UNICODE_CASE);

 static String explicitName(String text){
  String s=text==null?"":text.trim();if(s.isBlank())return "";
  Matcher m=INTRO.matcher(s);if(m.find()&&GuestStore.isPlausibleNewName(m.group(1)))return m.group(1);
  m=I_AM.matcher(s);if(m.find()&&GuestStore.isPlausibleNewName(m.group(1)))return m.group(1);
  if(ONE.matcher(s).matches()){
   String n=s.replaceAll("[.!?]+$","");if(GuestStore.isPlausibleNewName(n))return n;
  }
  return "";
 }
 private GuestIdentity(){}
}
