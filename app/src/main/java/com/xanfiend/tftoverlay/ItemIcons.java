package com.xanfiend.tftoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Bundled 2D full-item icons, used by the opponent scan (Phase 2) to name the
 * items an enemy unit is holding. The unit stat popup renders held items as
 * fixed flat sprites — the same art every game — so a mean-centered cosine
 * match against these icons is close to exact, exactly like SetIcons does for
 * champion tiles.
 *
 * Icons live in assets/itemicons/<Item>.png (alternates as <Item>_2.png etc.),
 * fetched from CommunityDragon at dev time. The filename (minus any _suffix) is
 * matched against the canonical completed-item names in ItemData.fullItems().
 *
 * SCAFFOLD: the matcher, thresholds, and storage plumbing are in place so the
 * only work left on a real device/emulator is (a) dropping the icon PNGs into
 * assets/itemicons and (b) tuning MIN_SIM / MIN_MARGIN and the popup item-slot
 * crop rects. With no icons bundled yet, match() returns null and the rest of
 * the app is unaffected (items[] stays empty, OppScout behaves as today).
 *
 * Matching mirrors SetIcons.match: high absolute similarity plus a clear margin
 * over the runner-up item, because a wrong item silently corrupts the threat read.
 */
public final class ItemIcons {

    private static final int SCALE = 32;        // item sprites are small; 32 is plenty
    private static final float MIN_SIM    = 0.86f;  // TUNE LIVE
    private static final float MIN_MARGIN = 0.04f;  // TUNE LIVE

    // parallel lists: an item can have several icon variants
    private static final List<String>  names = new ArrayList<>();
    private static final List<float[]> sigs  = new ArrayList<>();
    private static boolean loaded = false;

    public static synchronized void load(Context ctx){
        if(loaded) return;
        loaded = true;
        try{
            String[] files = ctx.getAssets().list("itemicons");
            if(files == null) return;
            for(String fn : files){
                if(!fn.endsWith(".png")) continue;
                String key = fn.substring(0, fn.length()-4);
                int us = key.indexOf('_');
                if(us > 0) key = key.substring(0, us); // strip alternate suffix
                String item = findItemName(key);
                if(item == null) continue;
                try{
                    InputStream is = ctx.getAssets().open("itemicons/"+fn);
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    is.close();
                    if(bmp == null) continue;
                    Bitmap scaled = Bitmap.createScaledBitmap(bmp, SCALE, SCALE, true);
                    bmp.recycle();
                    float[] s = ChampionTemplates.sig(scaled);
                    scaled.recycle();
                    ChampionTemplates.meanCenter(s);
                    names.add(item);
                    sigs.add(s);
                }catch(Exception e){
                    android.util.Log.w("TFTItemIcons", "load err "+fn+": "+e.getMessage());
                }
            }
        }catch(Exception e){
            android.util.Log.w("TFTItemIcons", "assets list err: "+e.getMessage());
        }
        OverlayService.addScanLog("item icons: "+itemCount()+" items ("+sigs.size()+" images)");
    }

    // map an icon filename key to a canonical completed-item name (loose match:
    // case- and punctuation-insensitive, so "infinity_edge" or "InfinityEdge"
    // both resolve to "Infinity Edge")
    private static String findItemName(String key){
        String lk = key.toLowerCase().replaceAll("[^a-z0-9]", "");
        for(String item : ItemData.fullItems())
            if(item.toLowerCase().replaceAll("[^a-z0-9]","").equals(lk)) return item;
        return null;
    }

    public static synchronized int itemCount(){
        return new java.util.HashSet<>(names).size();
    }
    public static synchronized boolean isReady(){ return !sigs.isEmpty(); }

    public static class ItemMatch {
        public final String name;
        public final float sim;
        public final float margin; // gap to the best OTHER item
        public ItemMatch(String n, float s, float m){ name=n; sim=s; margin=m; }
    }

    // Match a cropped item-slot icon. Returns the confident winner, or null.
    // Variants of the same item don't count as a runner-up.
    public static synchronized ItemMatch match(Bitmap icon){
        if(sigs.isEmpty()) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(icon, SCALE, SCALE, true);
        float[] s = ChampionTemplates.sig(scaled);
        scaled.recycle();
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
        if(bestSim < MIN_SIM || margin < MIN_MARGIN) return null;
        return new ItemMatch(best, bestSim, margin);
    }

    // best raw candidate regardless of thresholds — for the scan log so a failed
    // match shows how close it came and the thresholds can be tuned
    public static synchronized String debugBest(Bitmap icon){
        if(sigs.isEmpty()) return "no item icons";
        Bitmap scaled = Bitmap.createScaledBitmap(icon, SCALE, SCALE, true);
        float[] s = ChampionTemplates.sig(scaled);
        scaled.recycle();
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

    private ItemIcons(){}
}
