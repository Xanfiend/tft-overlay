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

    // costOf is called from nested loops when painting the grid and odds tabs
    // (every card recomputes its tier total, and each remaining() call needs the
    // cost). A linear scan over every champion name per call turned that into
    // tens of thousands of string compares per panel open — cache the lookup.
    private static volatile Map<String,Integer> costLookup;
    public static int costOf(String name){
        Map<String,Integer> m = costLookup;
        if(m == null){
            m = new HashMap<>();
            for(int c=1;c<=5;c++) for(String n : CHAMPS[c]) m.put(n, c);
            costLookup = m;
        }
        Integer c = m.get(name);
        return c == null ? 0 : c;
    }

    private final SharedPreferences p;
    private final Map<String,Integer> seen = new HashMap<>();   // copies gone
    private final Map<String,Integer> opp  = new HashMap<>();   // opponents contesting
    private final ArrayList<String> recent = new ArrayList<>(); // most-recently tapped first
    private static final int RECENT_MAX = 6;

    public Pool(Context ctx){
        p = ctx.getSharedPreferences("tft_pool", Context.MODE_PRIVATE);
        load();
    }

    private void touchRecent(String champ){
        recent.remove(champ);
        recent.add(0, champ);
        while(recent.size()>RECENT_MAX) recent.remove(recent.size()-1);
    }
    // champs you've interacted with this game, newest first
    public List<String> recentList(){ return new ArrayList<>(recent); }

    // ---- copies seen ----
    public void add(String champ, int n){
        int v = seen.containsKey(champ) ? seen.get(champ) : 0;
        int nv = Math.max(0, v+n);
        if(nv==0) seen.remove(champ); else seen.put(champ, nv);
        if(n>0) touchRecent(champ);
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
        if(n>0) touchRecent(champ);
        save();
    }
    public int oppCount(String c){ return opp.containsKey(c) ? opp.get(c) : 0; }

    public void reset(){
        seen.clear(); opp.clear(); recent.clear(); clearJunk();
        p.edit().remove("econ_gold").remove("econ_streak").apply();
        save();
    }
    public boolean isEmpty(){ return seen.isEmpty() && opp.isEmpty(); }

    // ---- remembered level (persists between opens) ----
    public int getLevel(){ return p.getInt("level", 8); }
    public void setLevel(int lv){ p.edit().putInt("level", lv).apply(); }

    // ---- pinned carry (shown at top of grid + board) ----
    public String getPinned(){ return p.getString("pinned", ""); }
    public void setPinned(String name){ p.edit().putString("pinned", name==null?"":name).apply(); }
    public boolean isPinned(String name){ return getPinned().equals(name); }

    // ---- settings ----
    public float getAlpha()        { return p.getFloat("cfg_alpha", 1.0f); }
    public void  setAlpha(float a) { p.edit().putFloat("cfg_alpha", a).apply(); }
    public boolean getHaptic()         { return p.getBoolean("cfg_haptic", true); }
    public void    setHaptic(boolean h){ p.edit().putBoolean("cfg_haptic", h).apply(); }
    // Smart Scan: find units by their health bars in the screenshot and tap the
    // exact unit position, instead of tapping calibrated grid dots. Falls back to
    // the grid automatically when detection is inconclusive. On by default.
    public boolean getSmartScan()         { return p.getBoolean("cfg_smartscan", true); }
    public void    setSmartScan(boolean s){ p.edit().putBoolean("cfg_smartscan", s).apply(); }
    // Instant Visual ID: units whose board sprite was learned from an earlier
    // popup-confirmed scan are recognized straight from the first screenshot,
    // skipping their tap entirely. Strict match thresholds; popup OCR stays the
    // source of truth and is what teaches the sprites. On by default.
    public boolean getVisualId()         { return p.getBoolean("cfg_visualid", true); }
    public void    setVisualId(boolean s){ p.edit().putBoolean("cfg_visualid", s).apply(); }
    // 0 = smart (board if pool non-empty, else grid), 1 = always grid
    public int  getStartTab()      { return p.getInt("cfg_start", 0); }
    public void setStartTab(int t) { p.edit().putInt("cfg_start", t).apply(); }

    // ---- economy tracker ----
    public int getGold()       { return p.getInt("econ_gold", 0); }
    public void setGold(int g) { p.edit().putInt("econ_gold", Math.max(0, g)).apply(); }

    // positive = win streak count, negative = loss streak count, 0 = neutral
    public int getStreak()        { return p.getInt("econ_streak", 0); }
    public void setStreak(int s)  { p.edit().putInt("econ_streak", s).apply(); }

    // Pure math helpers — static for easy unit testing and use without a Pool instance.
    public static int interest(int gold)       { return Math.min(5, gold / 10); }
    public static int toNextBracket(int gold)  { return (gold / 10 + 1) * 10 - gold; }
    public static int streakBonus(int streak)  {
        int abs = Math.abs(streak);
        if (abs >= 6) return 3;
        if (abs >= 4) return 2;
        if (abs >= 2) return 1;
        return 0;
    }
    public static int expectedIncome(int gold, int streak) {
        return 5 + interest(gold) + streakBonus(streak);
    }

    // ---- scan calibration: probe grid percentages ----
    // top = BACK-row hex-center Y, bot = FRONT-row hex-center Y (the probe grid lays
    // 4 perspective rows between them). Landscape defaults from TFT Mobile screenshots.
    public int getBoardTopPct()        { return p.getInt("cal_top",   39); }
    public void setBoardTopPct(int v)  { p.edit().putInt("cal_top",   Math.max(5,  Math.min(60,v))).apply(); }
    public int getBoardBotPct()        { return p.getInt("cal_bot",   66); }
    public void setBoardBotPct(int v)  { p.edit().putInt("cal_bot",   Math.max(20, Math.min(90,v))).apply(); }
    public int getBoardLeftPct()       { return p.getInt("cal_left",   8); }
    public void setBoardLeftPct(int v) { p.edit().putInt("cal_left",  Math.max(0,  Math.min(50,v))).apply(); }
    public int getBoardRightPct()      { return p.getInt("cal_right", 88); }
    public void setBoardRightPct(int v){ p.edit().putInt("cal_right", Math.max(50, Math.min(100,v))).apply(); }
    public int getBenchYPct()          { return p.getInt("cal_bench",   80); }
    public void setBenchYPct(int v)    { p.edit().putInt("cal_bench",   Math.max(50, Math.min(95,v))).apply(); }
    // Horizontal shift of the whole bench row as a percentage of screen width.
    // Negative = left, positive = right. Default -4 shifts the bench 4% left
    // to align with the actual TFT Mobile bench slot centers.
    public int getBenchXOffsetPct()         { return p.getInt("cal_bench_x",  -4); }
    public void setBenchXOffsetPct(int v)   { p.edit().putInt("cal_bench_x", Math.max(-20, Math.min(20,v))).apply(); }
    // Row spacing: vertical position of board rows 2 and 3 as a percent of the
    // back-to-front span (row 1 = 0, row 4 = 100). Defaults match the previously
    // hardcoded perspective fractions 0.27 / 0.58.
    public int getRowF1Pct()        { return p.getInt("cal_rowf1", 27); }
    public void setRowF1Pct(int v)  { p.edit().putInt("cal_rowf1", Math.max(5, Math.min(95,v))).apply(); }
    public int getRowF2Pct()        { return p.getInt("cal_rowf2", 58); }
    public void setRowF2Pct(int v)  { p.edit().putInt("cal_rowf2", Math.max(5, Math.min(95,v))).apply(); }
    // Bench span: explicit left/right ends of the bench row as a percent of screen
    // width. -1 = unset, fall back to deriving the span from the board's front row.
    public int getBenchLeftPct()        { return p.getInt("cal_bench_l", -1); }
    public void setBenchLeftPct(int v)  { p.edit().putInt("cal_bench_l", v).apply(); }
    public int getBenchRightPct()       { return p.getInt("cal_bench_r", -1); }
    public void setBenchRightPct(int v) { p.edit().putInt("cal_bench_r", v).apply(); }
    public void resetCalibration()     {
        p.edit().remove("cal_top").remove("cal_bot").remove("cal_left")
                .remove("cal_right").remove("cal_bench").remove("cal_bench_x")
                .remove("cal_tl").remove("cal_tr").remove("cal_bl").remove("cal_br")
                .remove("cal_rowf1").remove("cal_rowf2")
                .remove("cal_bench_l").remove("cal_bench_r").apply();
    }
    // Per-corner X% for trapezoidal board (front row wider than back row).
    // Default falls back to the rectangular cal_left/cal_right so old calibrations still work.
    public int getBoardTopLeftPct()       { return p.getInt("cal_tl", getBoardLeftPct()); }
    public void setBoardTopLeftPct(int v) { p.edit().putInt("cal_tl", v).apply(); }
    public int getBoardTopRightPct()      { return p.getInt("cal_tr", getBoardRightPct()); }
    public void setBoardTopRightPct(int v){ p.edit().putInt("cal_tr", v).apply(); }
    public int getBoardBotLeftPct()       { return p.getInt("cal_bl", getBoardLeftPct()); }
    public void setBoardBotLeftPct(int v) { p.edit().putInt("cal_bl", v).apply(); }
    public int getBoardBotRightPct()      { return p.getInt("cal_br", getBoardRightPct()); }
    public void setBoardBotRightPct(int v){ p.edit().putInt("cal_br", v).apply(); }

    // Portrait calibration — board sits much higher on screen in portrait mode
    public int getPortraitBoardTopPct()        { return p.getInt("cal_p_top",   22); }
    public void setPortraitBoardTopPct(int v)  { p.edit().putInt("cal_p_top",   Math.max(5,  Math.min(60,v))).apply(); }
    public int getPortraitBoardBotPct()        { return p.getInt("cal_p_bot",   58); }
    public void setPortraitBoardBotPct(int v)  { p.edit().putInt("cal_p_bot",   Math.max(20, Math.min(90,v))).apply(); }
    public int getPortraitBoardLeftPct()       { return p.getInt("cal_p_left",  12); }
    public void setPortraitBoardLeftPct(int v) { p.edit().putInt("cal_p_left",  Math.max(0,  Math.min(50,v))).apply(); }
    public int getPortraitBoardRightPct()      { return p.getInt("cal_p_right", 88); }
    public void setPortraitBoardRightPct(int v){ p.edit().putInt("cal_p_right", Math.max(50, Math.min(100,v))).apply(); }
    public int getPortraitBenchYPct()          { return p.getInt("cal_p_bench", 75); }
    public void setPortraitBenchYPct(int v)    { p.edit().putInt("cal_p_bench", Math.max(50, Math.min(95,v))).apply(); }
    public void resetPortraitCalibration()     {
        p.edit().remove("cal_p_top").remove("cal_p_bot").remove("cal_p_left")
                .remove("cal_p_right").remove("cal_p_bench")
                .remove("cal_p_tl").remove("cal_p_tr").remove("cal_p_bl").remove("cal_p_br")
                .remove("cal_p_rowf1").remove("cal_p_rowf2")
                .remove("cal_p_bench_l").remove("cal_p_bench_r").apply();
    }
    public int getPortraitRowF1Pct()        { return p.getInt("cal_p_rowf1", 27); }
    public void setPortraitRowF1Pct(int v)  { p.edit().putInt("cal_p_rowf1", Math.max(5, Math.min(95,v))).apply(); }
    public int getPortraitRowF2Pct()        { return p.getInt("cal_p_rowf2", 58); }
    public void setPortraitRowF2Pct(int v)  { p.edit().putInt("cal_p_rowf2", Math.max(5, Math.min(95,v))).apply(); }
    public int getPortraitBenchLeftPct()        { return p.getInt("cal_p_bench_l", -1); }
    public void setPortraitBenchLeftPct(int v)  { p.edit().putInt("cal_p_bench_l", v).apply(); }
    public int getPortraitBenchRightPct()       { return p.getInt("cal_p_bench_r", -1); }
    public void setPortraitBenchRightPct(int v) { p.edit().putInt("cal_p_bench_r", v).apply(); }
    public int getPortraitBoardTopLeftPct()       { return p.getInt("cal_p_tl", getPortraitBoardLeftPct()); }
    public void setPortraitBoardTopLeftPct(int v) { p.edit().putInt("cal_p_tl", v).apply(); }
    public int getPortraitBoardTopRightPct()      { return p.getInt("cal_p_tr", getPortraitBoardRightPct()); }
    public void setPortraitBoardTopRightPct(int v){ p.edit().putInt("cal_p_tr", v).apply(); }
    public int getPortraitBoardBotLeftPct()       { return p.getInt("cal_p_bl", getPortraitBoardLeftPct()); }
    public void setPortraitBoardBotLeftPct(int v) { p.edit().putInt("cal_p_bl", v).apply(); }
    public int getPortraitBoardBotRightPct()      { return p.getInt("cal_p_br", getPortraitBoardRightPct()); }
    public void setPortraitBoardBotRightPct(int v){ p.edit().putInt("cal_p_br", v).apply(); }

    // ---- bench-thinning: junk units of a cost you're holding on your bench ----
    // These temporarily remove copies from the shared pool, nudging your odds up.
    // Stored per cost tier (1..5).
    public int getJunk(int cost){ return p.getInt("junk"+cost, 0); }
    public void addJunk(int cost, int n){
        int v = Math.max(0, getJunk(cost)+n);
        p.edit().putInt("junk"+cost, v).apply();
    }
    public void clearJunk(){
        SharedPreferences.Editor e = p.edit();
        for(int c=1;c<=5;c++) e.remove("junk"+c);
        e.apply();
    }

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
        seen.clear(); opp.clear(); recent.clear();
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
        // recent list stored separately as a simple comma list
        String r = p.getString("recent","");
        if(!r.isEmpty()){
            for(String name : r.split(",")){
                if(!name.isEmpty() && recent.size()<RECENT_MAX) recent.add(name);
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
        StringBuilder rb = new StringBuilder();
        for(int i=0;i<recent.size();i++){ if(i>0) rb.append(","); rb.append(recent.get(i)); }
        p.edit().putString("d", sb.toString()).putString("recent", rb.toString()).apply();
    }
}
