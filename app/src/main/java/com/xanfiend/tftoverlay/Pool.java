package com.xanfiend.tftoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Pool tracking logic. Does NOT need editing for set updates.
 * All champion/pool data lives in SetData.java.
 *
 * Tracks TWO things per champ:
 *   seen      = copies of that unit gone from the shared pool (affects hit odds)
 *   opponents = how many players are contesting it (contest pressure)
 */
public class Pool {

    public static final int[] SIZE = SetData.SIZE;
    public static final String[][] CHAMPS = SetData.CHAMPS;
    public static final String SET_NAME = SetData.SET_NAME;

    public static int costOf(String name){
        for(int c=1;c<=5;c++) for(int i=0;i<CHAMPS[c].length;i++)
            if(CHAMPS[c][i].equals(name)) return c;
        return 0;
    }

    private final SharedPreferences p;
    private final Map<String,Integer> seen = new HashMap<>();   // copies gone
    private final Map<String,Integer> opp  = new HashMap<>();   // opponents contesting

    public Pool(Context ctx){
        p = ctx.getSharedPreferences("tft_pool", Context.MODE_PRIVATE);
        load();
    }

    // ---- copies seen ----
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

    // ---- opponents contesting ----
    public void addOpp(String champ, int n){
        int v = opp.containsKey(champ) ? opp.get(champ) : 0;
        int nv = Math.max(0, Math.min(7, v+n)); // cap at 7 opponents
        if(nv==0) opp.remove(champ); else opp.put(champ, nv);
        save();
    }
    public int oppCount(String c){ return opp.containsKey(c) ? opp.get(c) : 0; }

    public void reset(){ seen.clear(); opp.clear(); save(); }
    public boolean isEmpty(){ return seen.isEmpty() && opp.isEmpty(); }

    // union of any champ that has either a copy or an opponent tracked,
    // sorted by opponents first (contest pressure), then copies
    public List<String> seenSorted(){
        Set<String> keys = new HashSet<>();
        keys.addAll(seen.keySet());
        keys.addAll(opp.keySet());
        List<String> l = new ArrayList<>(keys);
        Collections.sort(l, new Comparator<String>(){
            public int compare(String a, String b){
                int byOpp = oppCount(b)-oppCount(a);
                if(byOpp!=0) return byOpp;
                return seenCount(b)-seenCount(a);
            }
        });
        return l;
    }

    private void load(){
        seen.clear(); opp.clear();
        // format: name|copies|opponents ; ...   (back-compat: name|copies)
        for(String part : p.getString("d","").split(";")){
            if(part.isEmpty()) continue;
            String[] kv = part.split("\\|");
            if(kv.length>=2){
                try { 
                    int copies = Integer.parseInt(kv[1]);
                    if(copies>0) seen.put(kv[0], copies);
                } catch(Exception e){}
            }
            if(kv.length>=3){
                try {
                    int players = Integer.parseInt(kv[2]);
                    if(players>0) opp.put(kv[0], players);
                } catch(Exception e){}
            }
        }
    }
    private void save(){
        StringBuilder sb = new StringBuilder();
        Set<String> keys = new HashSet<>();
        keys.addAll(seen.keySet()); keys.addAll(opp.keySet());
        for(String k : keys){
            sb.append(k).append("|").append(seenCount(k)).append("|").append(oppCount(k)).append(";");
        }
        p.edit().putString("d", sb.toString()).apply();
    }
}
