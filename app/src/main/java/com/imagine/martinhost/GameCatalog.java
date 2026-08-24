package com.imagine.martinhost;

import android.content.Context;
import java.util.*;

public final class GameCatalog {
    public static final class Game {
        public final String id,title,category,hostPrompt;
        public final boolean projector;
        public Game(String id,String title,String category,boolean projector,String hostPrompt){this.id=id;this.title=title;this.category=category;this.projector=projector;this.hostPrompt=hostPrompt;}
    }

    private static Game g(String id,String title,String category,boolean projector,String core){
        String intro="Перед игрой ОБЯЗАТЕЛЬНО: 1) назови игру; 2) за 20-30 секунд объясни правила; 3) объясни баллы и окончание раунда; 4) дай один демонстрационный пример с правильным ответом, который потом не используется; 5) спроси «Правила понятны? Начинаем?»; 6) не начинай настоящее задание до подтверждения. ";
        return new Game(id,title,category,projector,intro+core);
    }

    public static List<Game> all(){
        ArrayList<Game> x=new ArrayList<>();
        x.add(g("melody","Угадай мелодию","Музыка",false,"Проигрывай короткие фрагменты 3-7 секунд из подготовленного музыкального банка: хиты 90-х, 2000-х, саундтреки и актуальные хиты 2026. После правильного ответа спроси имя ответившего и начисли балл."));
        x.add(g("chgk","Что? Где? Когда?","Викторина",false,"Задавай логические и культурные вопросы, а не школьные тесты. Дай команде время обсудить. После принятого правильного ответа спроси имя и начисли балл."));
        x.add(g("quotes","Кто это сказал?","Цитаты",false,"Произноси заранее проверенные цитаты известных людей, актёров, писателей, музыкантов или персонажей кино. Игроки угадывают автора. Не приписывай человеку непроверенную цитату."));
        x.add(g("naughty_rhyme","Испорченность","Юмор",false,"Читай короткие авторские двусмысленные рифмованные загадки: слушателю кажется, что окончание будет пошлым, но правильное слово всегда невинное и логичное. Не используй оскорбления или графическое сексуальное содержание."));
        x.add(g("two_stars","Две звезды — одно лицо","Знаменитости",true,"На проекторе показывается заранее подготовленное смешанное лицо двух известных людей. Один угаданный человек — 1 балл, оба — 3 балла."));
        x.add(g("transform","Что AI сделал со звездой?","Знаменитости",true,"На проекторе показывай подготовленные визуальные трансформации знаменитостей: возраст, другая эпоха, смешение двух звёзд, необычная профессия или сценический образ. Нужно узнать исходного человека и при необходимости тип трансформации."));
        x.add(g("movie_ai","AI сломал фильм","Кино",true,"Показывай подготовленную AI-пародию на узнаваемый фильм или жанр без буквального копирования кадра. Игроки угадывают фильм/произведение."));
        x.add(g("expert","Объяснение эксперта","Импровизация",true,"Покажи абсурдную AI-картинку с известным человеком или исторической фигурой и выбери игрока, который 30 секунд изображает эксперта и объясняет происходящее."));
        x.add(g("ai_court","AI-суд","Импровизация",false,"Назначь обвиняемого, прокурора и адвоката. Обвинение должно быть полностью шуточным и безопасным. Дай сторонам короткое время и вынеси забавный приговор."));
        x.add(g("story","История по кругу","Импровизация",false,"Начни абсурдную историю, по очереди вызывай гостей продолжить её одной-двумя фразами, а в финале кратко и смешно перескажи результат."));
        x.add(g("toast","Тост Мартина","Мартин",false,"Скажи короткий персональный тост для Кати или компании, используя только подготовленные факты о гостях и имениннице."));
        return Collections.unmodifiableList(x);
    }

    public static List<Game> available(Context c){
        boolean projector=c.getSharedPreferences("martin",0).getBoolean("projector",false);
        ArrayList<Game> out=new ArrayList<>();
        for(Game g:all()) if(!g.projector||projector) out.add(g);
        return out;
    }

    public static Game random(Context c,Set<String> recentlyUsed){
        ArrayList<Game> list=new ArrayList<>(available(c));
        if(recentlyUsed!=null) list.removeIf(g->recentlyUsed.contains(g.id));
        if(list.isEmpty()) list=new ArrayList<>(available(c));
        return list.get(new Random().nextInt(list.size()));
    }
}
