package com.xanfiend.tftoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// TFT Set 17 "Space Gods"
public class Pool {
    public static final int[] SIZE = {0,30,25,18,10,9};
    public static final String[][] CHAMPS = {
        {},
        {"Aatrox","Briar","Caitlyn","Chogath","Ezreal","Leona","Lissandra",
         "Nasus","Poppy","RekSai","Talon","Teemo","TwistedFate","Veigar"},
        {"Akali","Belveth","Gnar","Gragas","Gwen","Jax","Jinx",
         "Meepsie","Milio","Mordekaiser","Pantheon","Pyke","Zoe"},
        {"Aurora","Diana","Fizz","Illaoi","Kaisa","Lulu","Maokai",
         "MissFortune","Ornn","Rhaast","Samira","Urgot","Viktor"},
        {"AurelionSol","Corki","Karma","Kindred","Leblanc","MasterYi","Nami",
         "Nunu","Rammus","Riven","TahmKench","MightyMech","Xayah"},
        {"Bard","Blitzcrank","Fiora","Graves","Jhin","Morgana","Shen","Sona","Vex","Zed"}
    };

    public static int costOf(String name){
        for(int c=1;c<=5;c++) for(int i=0;i<CHAMPS[c].length;i++)
            if(CHAMPS[c][i].equals(name)) return c;
        return 0;
    }

    private final SharedPreferences p;
    private final Map<String,Integer> seen = new HashMap<>();

    public Pool(Context ctx){
        p = ctx.getSharedPreferences("tft_pool", Context.MODE_PRIVATE);
        load();
    }
    public void add(String champ, int n){
        int v = seen.containsKey(champ) ? seen.get(champ) : 0;
        int nv = Math.max(0, v+n);
        if(nv==0) seen.remove(champ); else seen.put(champ, nv);
        save();
    }
    public int seenCount(String c){ return seen.containsKey(c) ? seen.get(c) : 0; }
    public int remaining(String c){
        int co=costOf(c); if(co==0) return 0;
        return Math.max(0, SIZE[co]-seenCount(c));
    }
    public void reset(){ seen.clear(); save(); }
    public boolean isEmpty(){ return seen.isEmpty(); }
    public List<String> seenSorted(){
        List<String> l = new ArrayList<>(seen.keySet());
        Collections.sort(l, new Comparator<String>(){
            public int compare(String a, String b){ return seen.get(b)-seen.get(a); }
        });
        return l;
    }
    private void load(){
        seen.clear();
        for(String part : p.getString("d","").split(";")){
            if(part.isEmpty()) continue;
            String[] kv = part.split("\\|");
            if(kv.length==2) try { seen.put(kv[0], Integer.parseInt(kv[1])); } catch(Exception e){}
        }
    }
    private void save(){
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String,Integer> e : seen.entrySet())
            sb.append(e.getKey()).append("|").append(e.getValue()).append(";");
        p.edit().putString("d", sb.toString()).apply();
    }

    // OCR helper: match recognized text against roster
    public static Map<String,Integer> matchText(String ocr){
        Map<String,Integer> found = new HashMap<>();
        if(ocr==null) return found;
        String low = ocr.toLowerCase().replaceAll("[^a-z\n ]","");
        for(int c=1;c<=5;c++){
            for(String name : CHAMPS[c]){
                String nl = name.toLowerCase();
                int idx=0, count=0;
                while((idx=low.indexOf(nl, idx))!=-1){ count++; idx+=nl.length(); }
                if(count>0) found.put(name, count);
            }
        }
        return found;
    }
}
