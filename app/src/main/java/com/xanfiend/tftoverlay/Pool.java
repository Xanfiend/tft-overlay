package com.xanfiend.tftoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Pool tracking logic. This file does NOT need editing for set updates.
 * All champion/pool data lives in SetData.java.
 */
public class Pool {

    // These point at SetData so the rest of the app can keep using
    // Pool.SIZE and Pool.CHAMPS unchanged.
    public static final int[] SIZE = SetData.SIZE;
    public static final String[][] CHAMPS = SetData.CHAMPS;
    public static final String SET_NAME = SetData.SET_NAME;

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
}
