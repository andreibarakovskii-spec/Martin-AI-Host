package com.imagine.martinhost;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GuestStore {
    public static final class Guest {
        public String name="";
        public String callName="";
        public String relation="";
        public String facts="";
        public String boundaries="";
        public int score=0;
        public int participated=0;
    }

    private static final Set<String> BOGUS_CAMERA_NAMES=Set.of(
            "зовут","поп","не","готов","готова","всего","лишь","здесь","один","одна","меня","я","это","ответил","ответила","привет","да","нет","потом","сейчас","музыка","включай","включи"
    );

    private final Context context;
    public GuestStore(Context context){ this.context=context.getApplicationContext(); migrateBogusCameraNames(); }

    public List<Guest> load(){
        ArrayList<Guest> out=new ArrayList<>();
        try{
            String raw=context.getSharedPreferences("martin",0).getString("guests","[]");
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i); Guest g=new Guest();
                g.name=o.optString("name",""); g.callName=o.optString("callName",g.name);
                g.relation=o.optString("relation",""); g.facts=o.optString("facts","");
                g.boundaries=o.optString("boundaries",""); g.score=o.optInt("score",0); g.participated=o.optInt("participated",0);
                if(!g.name.isBlank()) out.add(g);
            }
        }catch(Exception ignored){}
        return out;
    }

    public void save(List<Guest> guests){
        try{
            JSONArray a=new JSONArray();
            for(Guest g:guests){
                JSONObject o=new JSONObject(); o.put("name",g.name); o.put("callName",g.callName); o.put("relation",g.relation);
                o.put("facts",g.facts); o.put("boundaries",g.boundaries); o.put("score",g.score); o.put("participated",g.participated); a.put(o);
            }
            context.getSharedPreferences("martin",0).edit().putString("guests",a.toString()).apply();
        }catch(Exception ignored){}
    }

    private void migrateBogusCameraNames(){
        var p=context.getSharedPreferences("martin",0);
        if(p.getBoolean("guest_identity_fix_101",false))return;
        List<Guest> all=loadWithoutMigration();
        boolean changed=all.removeIf(g->BOGUS_CAMERA_NAMES.contains(normal(g.name))||BOGUS_CAMERA_NAMES.contains(normal(g.callName)));
        if(changed)save(all);
        p.edit().putBoolean("guest_identity_fix_101",true).apply();
    }

    private List<Guest> loadWithoutMigration(){
        ArrayList<Guest> out=new ArrayList<>();
        try{
            String raw=context.getSharedPreferences("martin",0).getString("guests","[]");
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i); Guest g=new Guest();
                g.name=o.optString("name","");g.callName=o.optString("callName",g.name);g.relation=o.optString("relation","");g.facts=o.optString("facts","");g.boundaries=o.optString("boundaries","");g.score=o.optInt("score",0);g.participated=o.optInt("participated",0);
                if(!g.name.isBlank())out.add(g);
            }
        }catch(Exception ignored){}
        return out;
    }

    public String promptContext(){
        List<Guest> guests=load();
        if(guests.isEmpty()) return "Список гостей пока не заполнен. Не выдумывай личные факты.";
        StringBuilder s=new StringBuilder("Подготовленный список гостей. Используй только эти факты и соблюдай границы:\n");
        for(Guest g:guests){
            s.append("- ").append(g.name);
            if(!g.callName.isBlank()) s.append(" (обращаться: ").append(g.callName).append(")");
            if(!g.relation.isBlank()) s.append("; связь с Катей: ").append(g.relation);
            if(!g.facts.isBlank()) s.append("; разрешённые факты: ").append(g.facts);
            if(!g.boundaries.isBlank()) s.append("; НЕ шутить/не затрагивать: ").append(g.boundaries);
            s.append("; баллы: ").append(g.score).append("; участий: ").append(g.participated).append(".\n");
        }
        return s.toString();
    }

    public boolean addScore(String spokenName,int delta){
        if(spokenName==null)return false; String q=normal(spokenName); List<Guest> guests=load();
        for(Guest g:guests){
            if(normal(g.name).equals(q)||normal(g.callName).equals(q)){
                g.score+=delta; g.participated++; save(guests); return true;
            }
        }
        return false;
    }

    static boolean isPlausibleNewName(String name){
        if(name==null)return false;
        String s=name.trim();if(s.length()<2||s.length()>32||s.contains(" "))return false;
        String n=normal(s);if(BOGUS_CAMERA_NAMES.contains(n))return false;
        char first=s.charAt(0);
        // Whisper normally capitalises proper names. Requiring this prevents “я готов/я всего/я не” from becoming people.
        if(!Character.isUpperCase(first))return false;
        return s.matches("[\\p{L}Ёё-]{2,32}");
    }

    public String canonicalName(String spokenName){
        if(spokenName==null)return "";String q=normal(spokenName);
        for(Guest g:load())if(normal(g.name).equals(q)||normal(g.callName).equals(q))return g.callName.isBlank()?g.name:g.callName;
        return isPlausibleNewName(spokenName)?spokenName.trim():"";
    }

    public void ensureGuest(String name){
        if(!isPlausibleNewName(name))return;
        String q=normal(name);List<Guest> all=load();for(Guest g:all)if(normal(g.name).equals(q)||normal(g.callName).equals(q))return;
        Guest g=new Guest();g.name=name.trim();g.callName=g.name;all.add(g);save(all);
    }

    private static String normal(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L} -]","").replaceAll("\\s+"," ").trim();}
}
