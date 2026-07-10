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

    // Set data is owned by SetData (which RemoteData may overwrite at startup
    // from the cached remote JSON). Read it live there rather than capturing a
    // copy here, so a synced new set is reflected everywhere.

    // costOf is called from nested loops when painting the grid and odds tabs
    // (every card recomputes its tier total, and each remaining() call needs the
    // cost). A linear scan over every champion name per call turned that into
    // tens of thousands of string compares per panel open — cache the lookup.
    private static volatile Map<String,Integer> costLookup;
    public static int costOf(String name){
        Map<String,Integer> m = costLookup;
        if(m == null){
            m = new HashMap<>();
            for(int c=1;c<=5;c++) for(String n : SetData.CHAMPS[c]) m.put(n, c);
            costLookup = m;
        }
        Integer c = m.get(name);
        return c == null ? 0 : c;
    }

    // Called by RemoteData after it swaps SetData's arrays at startup, so the
    // cost cache is rebuilt against the new champion list on next use.
    public static void invalidateData(){ costLookup = null; }

    private final SharedPreferences p;
    private final Map<String,Integer> seen = new HashMap<>();   // copies gone
    private final Map<String,Integer> opp  = new HashMap<>();   // opponents contesting
    private final ArrayList<String> recent = new ArrayList<>(); // most-recently tapped first
    private static final int RECENT_MAX = 6;

    public Pool(Context ctx){
        p = ctx.getSharedPreferences("tft_pool", Context.MODE_PRIVATE);
        load();
    }

    // first-launch privacy notice: true once the user has acknowledged it
    public boolean getPrivacySeen(){ return p.getBoolean("cfg_privacy_seen", false); }
    public void setPrivacySeen(boolean v){ p.edit().putBoolean("cfg_privacy_seen", v).apply(); }

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
        return Math.max(0, SetData.SIZE[co]-seenCount(c));
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
        SharedPreferences.Editor e = p.edit()
                .remove("econ_gold").remove("econ_streak").remove("econ_hp")
                .remove("econ_wins").remove("econ_losses")
                .remove("xp_cur").remove("xp_need")
                .remove("stage_round").remove("my_augs").remove("hunt_list")
                .remove("god_1").remove("god_2")
                .remove("god_picks_1").remove("god_picks_2")
                .remove("opp_slot_cursor");
        for(int s=1;s<=7;s++) e.remove("oppboard"+s);
        e.apply();
        save();
    }
    // Clear pool tracking (seen/opp/recent) only — keeps gold, HP, streak, and stage.
    public void resetPool(){
        seen.clear(); opp.clear(); recent.clear();
        save();
    }
    // ---- per-game W/L record (incremented by WON/LOST buttons, cleared by RESET ALL) ----
    public int getWins()         { return p.getInt("econ_wins", 0); }
    public void setWins(int w)   { p.edit().putInt("econ_wins", Math.max(0,w)).apply(); }
    public int getLosses()       { return p.getInt("econ_losses", 0); }
    public void setLosses(int l) { p.edit().putInt("econ_losses", Math.max(0,l)).apply(); }

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
    // Smart Scan vertical nudge: how far below the health bar (extra % of screen
    // height) the tap lands. Lets a user whose dots sit uniformly too high/low slide
    // every marker onto the unit body. 0 = the built-in 4% drop. Clamped -8..+8.
    public int  getSmartNudgeY()       { return p.getInt("cfg_smartnudge", 0); }
    public void setSmartNudgeY(int n)  { p.edit().putInt("cfg_smartnudge", Math.max(-8, Math.min(8, n))).apply(); }
    // Instant Visual ID: units whose board sprite was learned from an earlier
    // popup-confirmed scan are recognized straight from the first screenshot,
    // skipping their tap entirely. Strict match thresholds; popup OCR stays the
    // source of truth and is what teaches the sprites. On by default.
    public boolean getVisualId()         { return p.getBoolean("cfg_visualid", true); }
    public void    setVisualId(boolean s){ p.edit().putBoolean("cfg_visualid", s).apply(); }
    // 0 = smart (board if pool non-empty, else grid), 1 = always grid, 2 = last tab used
    public int  getStartTab()      { return p.getInt("cfg_start", 0); }
    public void setStartTab(int t) { p.edit().putInt("cfg_start", t).apply(); }
    // last tab the user viewed (a mode index); used when getStartTab()==2 so the
    // overlay reopens where you left off instead of jumping to smart/grid
    public int  getLastTab()       { return p.getInt("cfg_lasttab", 0); }
    public void setLastTab(int m)  { p.edit().putInt("cfg_lasttab", m).apply(); }
    // panel auto-dismiss: seconds of inactivity before the panel closes itself.
    // 0 = never, otherwise the planning phase length (15s quick / 30s default).
    public int  getPanelTimeout()       { return p.getInt("cfg_paneltimeout", 30); }
    public void setPanelTimeout(int s)  { p.edit().putInt("cfg_paneltimeout", s).apply(); }
    // compact tab row: show only the glyph icon, no text label below it —
    // saves ~25px panel height and keeps the tab row unobtrusive
    public boolean getCompactTabs()         { return p.getBoolean("cfg_compacttabs", false); }
    public void    setCompactTabs(boolean v){ p.edit().putBoolean("cfg_compacttabs", v).apply(); }
    // panel width as a percentage of screen width (96 = default full-bleed)
    public int  getPanelWidthPct()      { return p.getInt("cfg_panelwidth", 96); }
    public void setPanelWidthPct(int w) { p.edit().putInt("cfg_panelwidth", w).apply(); }
    // panel height as a percentage of screen height (86 = default tall)
    public int  getPanelHeightPct()      { return p.getInt("cfg_panelheight", 86); }
    public void setPanelHeightPct(int v) { p.edit().putInt("cfg_panelheight", v).apply(); }
    // panel anchor: 0 = middle, 1 = top, 2 = bottom — with a half-height panel
    // this keeps the shop or the board visible while the panel is open
    public int  getPanelAnchor()      { return p.getInt("cfg_panelanchor", 0); }
    public void setPanelAnchor(int a) { p.edit().putInt("cfg_panelanchor", a).apply(); }
    // large text: bump core panel text by +2sp for easier reading mid-game
    public boolean getLargeText()         { return p.getBoolean("cfg_largetext", false); }
    public void    setLargeText(boolean v){ p.edit().putBoolean("cfg_largetext", v).apply(); }
    // auto-scan on open: a quick sigil tap fires a gold/level scan instead of
    // just opening the panel — one tap to read the screen, no SCRY tap needed.
    // Off by default (it changes what a tap does); needs accessibility capture.
    public boolean getAutoScanOnOpen()         { return p.getBoolean("cfg_autoscanopen", false); }
    public void    setAutoScanOnOpen(boolean v){ p.edit().putBoolean("cfg_autoscanopen", v).apply(); }
    // smart landing: after a scan, open the tab that shows the result (GOLD)
    // instead of SETUP. On by default — it's strictly the more useful landing.
    public boolean getSmartLanding()         { return p.getBoolean("cfg_smartland", true); }
    public void    setSmartLanding(boolean v){ p.edit().putBoolean("cfg_smartland", v).apply(); }
    // floating sigil size, as a percent (80 small / 100 normal / 125 large).
    // Smaller obstructs less of the board; larger is easier to hit on a tablet.
    public int  getSigilScalePct()      { return p.getInt("cfg_sigilscale", 100); }
    public void setSigilScalePct(int s) { p.edit().putInt("cfg_sigilscale", s).apply(); }
    // accent theme index into OverlayService.THEMES (0=blood default). Recolors
    // the primary accent (buttons, highlights, the sigil); base + gold stay put.
    public int  getAccentTheme()      { return p.getInt("cfg_accent", 0); }
    public void setAccentTheme(int t) { p.edit().putInt("cfg_accent", t).apply(); }

    // ---- planner scan calibration ----
    // Screen-percent positions of the Team Planner controls, recorded by the
    // tap-through calibration in SETUP. Keys: btn (planner button), snap
    // (Snapshot button), s1 / sn (first and last snapshot slot centers), close
    // (whatever dismisses the planner). -1 = not calibrated.
    public int  getPln(String k)        { return p.getInt("pln_"+k, -1); }
    public void setPln(String k, int v) { p.edit().putInt("pln_"+k, Math.max(0, Math.min(100, v))).apply(); }
    public boolean plannerCalibrated(){
        return getPln("btn_x")>=0 && getPln("snap_x")>=0 && getPln("s1_x")>=0
            && getPln("sn_x")>=0 && getPln("close_x")>=0;
    }
    public void clearPlannerCal(){
        android.content.SharedPreferences.Editor e = p.edit();
        for(String k : new String[]{"btn","snap","s1","sn","close"}){
            e.remove("pln_"+k+"_x"); e.remove("pln_"+k+"_y");
        }
        e.apply();
    }

    // ---- in-game HUD overlay (two mini numbers: income above gold, gold-to-level above XP) ----
    public boolean getHudEnabled()         { return p.getBoolean("cfg_hud", true); }
    public void    setHudEnabled(boolean h){ p.edit().putBoolean("cfg_hud", h).apply(); }
    public int  getHudPos(String key, int def){ return p.getInt(key, def); }
    public void setHudPos(String key, int v)  { p.edit().putInt(key, v).apply(); }
    // Auto gold/XP: keep the HUD gold + level/XP live by silently reading them off
    // the screen (gold bottom-right, level/XP top-left) every few seconds. Off by
    // default; needs the accessibility service. Yields while a hunt/scan is running.
    public boolean getGoldWatch()          { return p.getBoolean("cfg_goldwatch", false); }
    public void    setGoldWatch(boolean s) { p.edit().putBoolean("cfg_goldwatch", s).apply(); }
    // Where THE HUNT looks for the shop cards. 0 = auto (top in landscape, bottom
    // in portrait), 1 = force top, 2 = force bottom — a no-overlay calibration knob
    // for devices that draw the shop somewhere the orientation default misses.
    public int  getShopPos()      { return p.getInt("cfg_shoppos", 0); }
    public void setShopPos(int v) { p.edit().putInt("cfg_shoppos", v).apply(); }

    // ---- economy tracker ----
    public int getGold()       { return p.getInt("econ_gold", 0); }
    public void setGold(int g) { p.edit().putInt("econ_gold", Math.max(0, g)).apply(); }

    // positive = win streak count, negative = loss streak count, 0 = neutral
    public int getStreak()        { return p.getInt("econ_streak", 0); }
    public void setStreak(int s)  { p.edit().putInt("econ_streak", s).apply(); }

    // Pure math helpers — static for easy unit testing and use without a Pool instance.
    public static int interest(int gold)       { return Math.min(5, gold / 10); }
    public static int toNextBracket(int gold)  { return (gold / 10 + 1) * 10 - gold; }
    // Streak gold is asymmetric in live TFT:
    //   win streaks:  +1 at 3-4 wins, +2 at 5, +3 at 6+
    //   loss streaks: +1 at 2-3 losses, +2 at 4, +3 at 5+
    public static int streakBonus(int streak)  {
        if (streak > 0) {
            if (streak >= 6) return 3;
            if (streak >= 5) return 2;
            if (streak >= 3) return 1;
            return 0;
        }
        int abs = -streak;
        if (abs >= 5) return 3;
        if (abs >= 4) return 2;
        if (abs >= 2) return 1;
        return 0;
    }
    // Expected income next round, assuming the current streak holds.
    // Winning a PvP round also pays +1g, so a held win streak includes it.
    public static int expectedIncome(int gold, int streak) {
        return 5 + interest(gold) + streakBonus(streak) + (streak > 0 ? 1 : 0);
    }
    // Total bonus gold earned across the whole streak (cumulative, not just this round).
    public static int totalStreakGold(int streak){
        int abs=Math.abs(streak); int total=0;
        for(int r=1;r<=abs;r++) total+=streakBonus(streak>0?r:-r);
        return total;
    }

    // ---- dev mode (hidden): unlocks testing tools like Scan From Image.
    // Off by default; toggled by tapping the version label 7x. Not for end users.
    public boolean isDevMode()        { return p.getBoolean("cfg_devmode", false); }
    public void setDevMode(boolean d) { p.edit().putBoolean("cfg_devmode", d).apply(); }

    // ---- XP / leveling ----
    public static int xpToNext(int level){
        if (level < 1 || level >= SetData.XP_TO_NEXT.length) return 0;
        return SetData.XP_TO_NEXT[level];
    }
    // Gold to buy the rest of the XP to the next level (4g = 4 XP, sold in 4s).
    // xpInto = XP already accumulated into the current level (0 if unknown).
    public static int goldToNextLevel(int level, int xpInto){
        int need = xpToNext(level);
        if (need <= 0) return 0;
        int rem = Math.max(0, need - Math.max(0, xpInto));
        return ((rem + 3) / 4) * 4;
    }
    // last scanned XP progress: "cur/need" read from the level button, -1 = unknown
    public int getXpCur() { return p.getInt("xp_cur", -1); }
    public int getXpNeed(){ return p.getInt("xp_need", -1); }
    public void setXp(int cur, int need){ p.edit().putInt("xp_cur", cur).putInt("xp_need", need).apply(); }

    // ---- HP tracker ----
    public int getHp()       { return p.getInt("econ_hp", 100); }
    public void setHp(int h) { p.edit().putInt("econ_hp", Math.max(0, Math.min(100, h))).apply(); }

    // ---- stage/round, as last scanned ("3-2", "" = unknown) ----
    public String getStageRound(){ return p.getString("stage_round", ""); }
    public void setStageRound(String s){ p.edit().putString("stage_round", s==null?"":s).apply(); }
    public int getStageNum(){
        String sr=getStageRound(); if(sr.isEmpty()) return 0;
        try{ return Integer.parseInt(sr.split("-")[0]); }catch(Exception e){ return 0; }
    }
    public int getRoundNum(){
        String sr=getStageRound(); if(sr.isEmpty()) return 0;
        String[] parts=sr.split("-"); if(parts.length<2) return 0;
        try{ return Integer.parseInt(parts[1]); }catch(Exception e){ return 0; }
    }
    public void setStageRoundNums(int stage, int round){ setStageRound(stage+"-"+round); }

    // ---- my augments, as scanned ----
    public List<String> getMyAugments(){
        List<String> l = new ArrayList<>();
        for(String a : p.getString("my_augs","").split(";")) if(!a.isEmpty() && !l.contains(a)) l.add(a);
        return l;
    }
    public void addMyAugment(String name){
        List<String> l = getMyAugments();
        if(l.contains(name) || l.size()>=4) return;
        l.add(name);
        StringBuilder sb=new StringBuilder();
        for(String a:l){ if(sb.length()>0) sb.append(";"); sb.append(a); }
        p.edit().putString("my_augs", sb.toString()).apply();
    }

    // ---- hunt list: champions THE HUNT auto-buys from the shop ----
    public List<String> getHunt(){
        List<String> l = new ArrayList<>();
        for(String n : p.getString("hunt_list","").split(";")) if(!n.isEmpty() && !l.contains(n)) l.add(n);
        return l;
    }
    public boolean isHunted(String name){ return getHunt().contains(name); }
    // returns true if the toggle succeeded (adds are capped at 5 marks)
    public boolean toggleHunt(String name){
        List<String> l = getHunt();
        if(l.contains(name)) l.remove(name);
        else { if(l.size()>=5) return false; l.add(name); }
        StringBuilder sb=new StringBuilder();
        for(String n:l){ if(sb.length()>0) sb.append(";"); sb.append(n); }
        p.edit().putString("hunt_list", sb.toString()).apply();
        return true;
    }
    // wipe every auto-buy mark in one go
    public void clearHunt(){ p.edit().remove("hunt_list").apply(); }

    // ---- learned champ→traits map ----
    // The overlay learns each champion's traits from the unit popup the first
    // time it's scried (the popup lists them under the name). Persists across
    // games — traits don't change mid-set.
    public List<String> traitsOf(String champ){
        List<String> l = new ArrayList<>();
        for(String t : p.getString("traits_"+champ,"").split(",")) if(!t.isEmpty()) l.add(t);
        return l;
    }
    public void learnTraits(String champ, List<String> traits){
        if(traits==null || traits.isEmpty()) return;
        StringBuilder sb=new StringBuilder();
        for(String t:traits){ if(sb.length()>0) sb.append(","); sb.append(t); }
        p.edit().putString("traits_"+champ, sb.toString()).apply();
    }

    // ---- per-opponent boards (slots 1-7) ----
    // format per slot: "name|stars;name|stars;..."
    public Map<String,Integer> getOppBoard(int slot){
        Map<String,Integer> m = new HashMap<>();
        for(String part : p.getString("oppboard"+slot,"").split(";")){
            if(part.isEmpty()) continue;
            String[] kv = part.split("\\|");
            if(kv.length>=1 && !kv[0].isEmpty()){
                int st=1;
                if(kv.length>=2){ try{ st=Integer.parseInt(kv[1]); }catch(Exception e){} }
                m.put(kv[0], st);
            }
        }
        return m;
    }
    public void setOppBoard(int slot, Map<String,Integer> board){
        StringBuilder sb=new StringBuilder();
        for(Map.Entry<String,Integer> e : board.entrySet()){
            sb.append(e.getKey()).append("|").append(e.getValue()).append(";");
        }
        p.edit().putString("oppboard"+slot, sb.toString()).apply();
    }
    public void clearOppBoard(int slot){ p.edit().remove("oppboard"+slot).apply(); }

    // ---- richer per-unit enemy data (Phase 2: champion + stars + items) ----
    // The oppboard string extends to "name|stars|item1,item2,item3;..." — the
    // third field is optional, so old "name|stars" entries still parse and the
    // Map<String,Integer> accessors above keep working (they ignore items).
    // Item names never contain | ; or , so the join is unambiguous.
    public static final class OppUnit {
        public final String name; public final int stars;
        public final java.util.List<String> items;
        public OppUnit(String name, int stars, java.util.List<String> items){
            this.name = name; this.stars = Math.max(1, stars);
            this.items = items == null ? new java.util.ArrayList<String>() : items;
        }
    }
    public java.util.List<OppUnit> getOppUnits(int slot){
        java.util.List<OppUnit> out = new java.util.ArrayList<>();
        for(String part : p.getString("oppboard"+slot,"").split(";")){
            if(part.isEmpty()) continue;
            String[] kv = part.split("\\|");
            if(kv.length < 1 || kv[0].isEmpty()) continue;
            int st = 1; if(kv.length >= 2){ try{ st = Integer.parseInt(kv[1]); }catch(Exception e){} }
            java.util.List<String> items = new java.util.ArrayList<>();
            if(kv.length >= 3 && !kv[2].isEmpty())
                for(String it : kv[2].split(",")) if(!it.isEmpty()) items.add(it);
            out.add(new OppUnit(kv[0], st, items));
        }
        return out;
    }
    public void setOppUnits(int slot, java.util.List<OppUnit> units){
        StringBuilder sb = new StringBuilder();
        for(OppUnit u : units){
            sb.append(u.name).append("|").append(u.stars);
            if(!u.items.isEmpty()){
                sb.append("|");
                for(int i=0;i<u.items.size();i++){ if(i>0) sb.append(","); sb.append(u.items.get(i)); }
            }
            sb.append(";");
        }
        p.edit().putString("oppboard"+slot, sb.toString()).apply();
    }

    // every non-empty remembered enemy board as rich units (with items), for the
    // item-aware OppScout read (Phase 2); empty items[] until the scan fills them
    public java.util.List<java.util.List<OppUnit>> getAllOppUnits(){
        java.util.List<java.util.List<OppUnit>> out = new java.util.ArrayList<>();
        for(int s=1;s<=7;s++){ java.util.List<OppUnit> u = getOppUnits(s); if(!u.isEmpty()) out.add(u); }
        return out;
    }
    // every non-empty remembered enemy board, for OppScout lobby analysis
    public java.util.List<Map<String,Integer>> getAllOppBoards(){
        java.util.List<Map<String,Integer>> out = new java.util.ArrayList<>();
        for(int s=1;s<=7;s++){ Map<String,Integer> b=getOppBoard(s); if(!b.isEmpty()) out.add(b); }
        return out;
    }
    // cycling slot assignment so repeated enemy scries file under OPP 1..7
    public int nextOppSlot(){
        int s = p.getInt("opp_slot_cursor", 0) % 7 + 1;
        p.edit().putInt("opp_slot_cursor", s).apply();
        return s;
    }
    // reset the cursor so the next one-pass lobby scan fills slots from OPP 1
    public void resetOppCursor(){ p.edit().putInt("opp_slot_cursor", 0).apply(); }

    // ---- enemy-portrait tap positions (one-pass opponent scan, REAPER) ----
    // Raw screen px for the up-to-7 player portraits along the top HUD; the
    // automation taps each to switch the viewed board, then runs the popup scan.
    // Recorded once via calibration, like the cal_* board keys.
    public void setOppPortrait(int i, int x, int y){
        p.edit().putString("cal_opp" + i, x + "," + y).apply();
    }
    public int[] getOppPortrait(int i){
        String s = p.getString("cal_opp" + i, "");
        if(s.isEmpty()) return null;
        String[] xy = s.split(",");
        try { return new int[]{ Integer.parseInt(xy[0]), Integer.parseInt(xy[1]) }; }
        catch(Exception e){ return null; }
    }
    public int oppPortraitCount(){
        int n = 0; for(int i = 1; i <= 7; i++) if(getOppPortrait(i) != null) n++; return n;
    }
    public boolean hasOppPortraitCal(){ return getOppPortrait(1) != null; }
    public void clearOppPortraits(){
        android.content.SharedPreferences.Editor e = p.edit();
        for(int i = 1; i <= 7; i++) e.remove("cal_opp" + i);
        e.apply();
    }

    // ---- god/boon tracker (set 17 Realm mechanic) ----
    // godA/godB = the two gods in this game ("" = unset); picks = offerings taken
    public String getGod(int which){ return p.getString("god_"+which, ""); }
    public void setGod(int which, String name){ p.edit().putString("god_"+which, name==null?"":name).apply(); }
    public int getGodPicks(int which){ return p.getInt("god_picks_"+which, 0); }
    public void setGodPicks(int which, int n){ p.edit().putInt("god_picks_"+which, Math.max(0,Math.min(3,n))).apply(); }

    // ---- scan calibration: probe grid percentages ----
    // top = BACK-row hex-center Y, bot = FRONT-row hex-center Y (the probe grid lays
    // 4 perspective rows between them). Landscape defaults from TFT Mobile screenshots.
    public int getBoardTopPct()        { return p.getInt("cal_top",   44); }
    public void setBoardTopPct(int v)  { p.edit().putInt("cal_top",   Math.max(5,  Math.min(60,v))).apply(); }
    public int getBoardBotPct()        { return p.getInt("cal_bot",   72); }
    public void setBoardBotPct(int v)  { p.edit().putInt("cal_bot",   Math.max(20, Math.min(90,v))).apply(); }
    public int getBoardLeftPct()       { return p.getInt("cal_left",   8); }
    public void setBoardLeftPct(int v) { p.edit().putInt("cal_left",  Math.max(0,  Math.min(50,v))).apply(); }
    public int getBoardRightPct()      { return p.getInt("cal_right", 88); }
    public void setBoardRightPct(int v){ p.edit().putInt("cal_right", Math.max(50, Math.min(100,v))).apply(); }
    public int getBenchYPct()          { return p.getInt("cal_bench",   89); }
    public void setBenchYPct(int v)    { p.edit().putInt("cal_bench",   Math.max(50, Math.min(95,v))).apply(); }
    // Horizontal shift of the whole bench row as a percentage of screen width.
    // Negative = left, positive = right. Default -4 shifts the bench 4% left
    // to align with the actual TFT Mobile bench slot centers.
    public int getBenchXOffsetPct()         { return p.getInt("cal_bench_x",  -4); }
    public void setBenchXOffsetPct(int v)   { p.edit().putInt("cal_bench_x", Math.max(-20, Math.min(20,v))).apply(); }
    // Row spacing: vertical position of board rows 2 and 3 as a percent of the
    // back-to-front span (row 1 = 0, row 4 = 100). Defaults match the previously
    // hardcoded perspective fractions 0.27 / 0.58.
    // -1 = auto: derive the middle-row spacing projectively from the calibrated
    // row spans (HexGrid.autoRowFractions). Users who dragged the ADJUST GRID
    // spacing handles keep their explicit saved values.
    public int getRowF1Pct()        { return p.getInt("cal_rowf1", -1); }
    public void setRowF1Pct(int v)  { p.edit().putInt("cal_rowf1", Math.max(5, Math.min(95,v))).apply(); }
    public int getRowF2Pct()        { return p.getInt("cal_rowf2", -1); }
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
    // drop only the landscape corner grid (keeps top/bot rows) — used when the
    // full-precision aspect model should take over from a saved integer grid
    public void clearLandscapeGridCal(){
        p.edit().remove("cal_tl").remove("cal_tr").remove("cal_bl").remove("cal_br")
                .remove("cal_left").remove("cal_right").apply();
    }
    public int getBoardTopLeftPct()       { return p.getInt("cal_tl", getBoardLeftPct()); }
    public void setBoardTopLeftPct(int v) { p.edit().putInt("cal_tl", v).apply(); }
    public int getBoardTopRightPct()      { return p.getInt("cal_tr", getBoardRightPct()); }
    public void setBoardTopRightPct(int v){ p.edit().putInt("cal_tr", v).apply(); }
    public int getBoardBotLeftPct()       { return p.getInt("cal_bl", getBoardLeftPct()); }
    public void setBoardBotLeftPct(int v) { p.edit().putInt("cal_bl", v).apply(); }
    public int getBoardBotRightPct()      { return p.getInt("cal_br", getBoardRightPct()); }
    public void setBoardBotRightPct(int v){ p.edit().putInt("cal_br", v).apply(); }
    // True once the user has manually set any landscape board edge/row. Until then the
    // grid derives its horizontal span from the screen aspect (board is height-fit and
    // centered, so a wide screen shows a narrower board) instead of a fixed percentage,
    // so the fallback grid lands on the board on any device with zero calibration.
    public boolean hasLandscapeGridCal(){
        return p.contains("cal_tl")||p.contains("cal_tr")||p.contains("cal_bl")||p.contains("cal_br")
            ||p.contains("cal_left")||p.contains("cal_right");
    }

    // Portrait calibration — board sits much higher on screen in portrait mode
    public int getPortraitBoardTopPct()        { return p.getInt("cal_p_top",   17); }
    public void setPortraitBoardTopPct(int v)  { p.edit().putInt("cal_p_top",   Math.max(5,  Math.min(60,v))).apply(); }
    public int getPortraitBoardBotPct()        { return p.getInt("cal_p_bot",   55); }
    public void setPortraitBoardBotPct(int v)  { p.edit().putInt("cal_p_bot",   Math.max(20, Math.min(90,v))).apply(); }
    public int getPortraitBoardLeftPct()       { return p.getInt("cal_p_left",   9); }
    public void setPortraitBoardLeftPct(int v) { p.edit().putInt("cal_p_left",  Math.max(0,  Math.min(50,v))).apply(); }
    public int getPortraitBoardRightPct()      { return p.getInt("cal_p_right", 91); }
    public void setPortraitBoardRightPct(int v){ p.edit().putInt("cal_p_right", Math.max(50, Math.min(100,v))).apply(); }
    public int getPortraitBenchYPct()          { return p.getInt("cal_p_bench", 73); }
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
    // absolute setter — used by the bench scan to auto-fill counts
    public void setJunk(int cost, int n){
        p.edit().putInt("junk"+cost, Math.max(0,n)).apply();
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
