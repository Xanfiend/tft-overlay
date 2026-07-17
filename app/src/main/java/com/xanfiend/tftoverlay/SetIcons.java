package com.xanfiend.tftoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bundled 2D champion icons for the current set, used by Planner Scan to name
 * the flat tiles the Team Planner's Snapshot shows for every fielded unit.
 *
 * Icons live in assets/seticons/<Champ>.png (alternates as <Champ>_2.png etc.),
 * fetched from CommunityDragon by the fetch-icons workflow at dev time. Unlike
 * the board's 3D sprites, planner tiles are the same flat art every game, so a
 * mean-centered cosine match against these icons is close to exact.
 *
 * Matching follows the same rules as ChampionTemplates.matchBoardSprite: high
 * absolute similarity plus a clear margin over the runner-up champion, because
 * a wrong name silently corrupts the pool counts.
 */
public final class SetIcons {

    private static final int SCALE = 48;
    private static final float MIN_SIM    = 0.88f;
    private static final float MIN_MARGIN = 0.04f;

    // parallel lists: a champion can have several icon variants (tile + square)
    private static final List<String>  names = new ArrayList<>();
    private static final List<float[]> sigs  = new ArrayList<>();
    private static boolean loaded = false;
    private static long lastAttemptMs = 0;

    public static synchronized void load(Context ctx){
        // don't latch a FAILED load: if nothing loaded last time (transient I/O,
        // roster not applied yet), try again on the next call instead of leaving
        // the planner scan permanently disabled for the session. Throttled so a
        // persistent failure doesn't re-run and re-log on every panel rebuild.
        if(loaded && !sigs.isEmpty()) return;
        long now = android.os.SystemClock.uptimeMillis();
        if(loaded && now - lastAttemptMs < 30000) return;
        lastAttemptMs = now;
        names.clear(); sigs.clear();
        loaded = true;
        // failure-stage counters — surfaced in the scan log when nothing loads,
        // so a device report can pinpoint WHERE the pipeline died
        int listed=0, nameMiss=0, decodeFail=0, sigFail=0;
        try{
            String[] files = ctx.getAssets().list("seticons");
            if(files == null){ OverlayService.addScanLog("set icons: assets dir missing"); return; }
            for(String fn : files){
                if(!fn.endsWith(".png")) continue;
                listed++;
                String key = fn.substring(0, fn.length()-4);
                int us = key.indexOf('_');
                if(us > 0) key = key.substring(0, us); // strip alternate suffix
                String champ = findChampName(key);
                if(champ == null){ nameMiss++; continue; }
                try{
                    InputStream is = ctx.getAssets().open("seticons/"+fn);
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    if(bmp == null){ decodeFail++; continue; }
                    // createScaledBitmap returns the SAME bitmap when the size
                    // already matches (the bundled icons are exactly 48x48) —
                    // recycling "bmp" then killed "scaled" too and sig() threw on
                    // every file. This was the device-only sigFail=122 mystery
                    // (Robolectric bitmaps don't enforce recycle()).
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, SCALE, SCALE, true);
                    if(scaled != bmp) bmp.recycle();
                    float[] s = ChampionTemplates.sig(scaled);
                    scaled.recycle();
                    ChampionTemplates.meanCenter(s);
                    names.add(champ);
                    sigs.add(s);
                }catch(Exception e){
                    sigFail++;
                    android.util.Log.w("TFTSetIcons", "load err "+fn+": "+e.getMessage());
                }
            }
        }catch(Exception e){
            OverlayService.addScanLog("set icons: list err "+e.getMessage());
        }
        OverlayService.addScanLog("set icons: "+champCount()+" champions ("+sigs.size()+" images)"
            +(sigs.isEmpty() ? "  [listed="+listed+" nameMiss="+nameMiss
                +" decodeFail="+decodeFail+" sigFail="+sigFail+"]" : ""));
    }

    private static String findChampName(String key){
        String lk = key.toLowerCase().replaceAll("[^a-z0-9]", "");
        for(String[] tier : SetData.CHAMPS)
            for(String c : tier)
                if(c.toLowerCase().replaceAll("[^a-z0-9]","").equals(lk)) return c;
        return null;
    }

    public static synchronized int champCount(){
        java.util.HashSet<String> u = new java.util.HashSet<>(names);
        return u.size();
    }

    public static class IconMatch {
        public final String name;
        public final float sim;
        public final float margin; // gap to the best OTHER champion
        public IconMatch(String n, float s, float m){ name=n; sim=s; margin=m; }
    }

    // Match a planner tile crop. Returns the confident winner, or null. Variants
    // of the same champion don't count as a runner-up — the margin is measured
    // against the best-scoring DIFFERENT champion.
    public static synchronized IconMatch match(Bitmap tile){
        if(sigs.isEmpty()) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(tile, SCALE, SCALE, true);
        float[] s = ChampionTemplates.sig(scaled);
        if(scaled != tile) scaled.recycle();   // same-size input returns the caller's bitmap
        ChampionTemplates.meanCenter(s);
        String best = null; float bestSim = -2f;
        String second = null; float secondSim = -2f;
        for(int i=0;i<sigs.size();i++){
            float sim = cosine(s, sigs.get(i));
            String n = names.get(i);
            if(sim > bestSim){
                if(best != null && !best.equals(n)){ second = best; secondSim = bestSim; }
                best = n; bestSim = sim;
            } else if(!n.equals(best) && sim > secondSim){
                second = n; secondSim = sim;
            }
        }
        if(best == null) return null;
        float margin = second == null ? 1f : bestSim - secondSim;
        if(bestSim < MIN_SIM || margin < MIN_MARGIN)
            return null;
        return new IconMatch(best, bestSim, margin);
    }

    // best raw candidate regardless of thresholds — for the scan log, so failed
    // matches show how close they came and the thresholds can be tuned
    public static synchronized String debugBest(Bitmap tile){
        if(sigs.isEmpty()) return "no icons";
        Bitmap scaled = Bitmap.createScaledBitmap(tile, SCALE, SCALE, true);
        float[] s = ChampionTemplates.sig(scaled);
        if(scaled != tile) scaled.recycle();
        ChampionTemplates.meanCenter(s);
        String best = null; float bestSim = -2f;
        for(int i=0;i<sigs.size();i++){
            float sim = cosine(s, sigs.get(i));
            if(sim > bestSim){ bestSim = sim; best = names.get(i); }
        }
        return best+" "+(int)(bestSim*100)+"%";
    }

    private static float cosine(float[] a, float[] b){
        float dot=0,na=0,nb=0;
        for(int i=0;i<a.length;i++){ dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i]; }
        if(na==0||nb==0) return 0;
        return dot/(float)Math.sqrt(na*nb);
    }

    private SetIcons(){}
}
