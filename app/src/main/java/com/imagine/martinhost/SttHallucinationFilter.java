package com.imagine.martinhost;

import java.util.Locale;

/** Filters a small set of well-known Whisper hallucination phrases caused by music/silence. */
final class SttHallucinationFilter {
    static boolean reject(String raw){
        if(raw==null)return true;
        String s=raw.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("\\s+"," ").trim();
        if(s.isBlank())return true;
        if(s.contains("редактор субтитров")||s.contains("корректор субтитров")||s.contains("субтитры сделал")||s.contains("субтитры подготовил"))return true;
        if(s.equals("продолжение следует")||s.equals("спасибо за просмотр")||s.equals("подписывайтесь на канал")||s.equals("приятного просмотра"))return true;
        return false;
    }
    private SttHallucinationFilter(){}
}
