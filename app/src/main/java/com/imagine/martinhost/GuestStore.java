package com.imagine.martinhost;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

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

    private final Context context;
    public GuestStore(Context context){ this.context=context.getApplicationContext(); }

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
    public String canonicalName(String spokenName){if(spokenName==null)return "";String q=normal(spokenName);for(Guest g:load())if(normal(g.name).equals(q)||normal(g.callName).equals(q))return g.callName.isBlank()?g.name:g.callName;return spokenName.trim();}
    public void ensureGuest(String name){if(name==null||name.isBlank())return;String q=normal(name);List<Guest> all=load();for(Guest g:all)if(normal(g.name).equals(q)||normal(g.callName).equals(q))return;Guest g=new Guest();g.name=name.trim();g.callName=g.name;all.add(g);save(all);}
    private static String normal(String s){return s.toLowerCase(java.util.Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L} -]","").replaceAll("\\s+"," ").trim();}
}
