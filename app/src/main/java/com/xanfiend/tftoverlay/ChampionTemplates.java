package com.xanfiend.tftoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages per-champion visual templates captured from the unit stat popup,
 * plus board-sprite templates captured from the board itself.
 *
 * Popup templates are cropped from the popup portrait area when a board scan
 * detects a champion. Board-sprite templates are crops of the champion standing
 * on the board, captured at the exact probe position the moment its popup is
 * confirmed — so they show how that champion looks on THIS device at the current
 * patch.
 *
 * Both are stored in the app's private files directory so they survive restarts
 * but reflect the current patch (no stale CDN data).
 *
 * Matching: 8×8 grid average RGB signature (192 floats), cosine similarity.
 * Board-sprite matching mean-centers the signature per channel first (removes
 * the shared ground tint so the sprite pattern dominates) and requires both a
 * high score AND a clear margin over the second-best champion before accepting.
 */
public class ChampionTemplates {

    private static final int SIG_GRID = 8;
    private static final int SCALE    = 48;
    private static final float MIN_SIM = 0.72f;
    // Board-sprite matching is allowed to skip the popup tap entirely, so it must
    // never guess: high absolute similarity, plus a clear gap to the runner-up.
    private static final float BOARD_MIN_SIM    = 0.93f;
    private static final float BOARD_MIN_MARGIN = 0.03f;
    // With a single template there is no runner-up to compare against, so the
    // absolute bar is raised instead.
    private static final float BOARD_SOLO_SIM   = 0.96f;

    private static final Map<String, float[]> boardSigs = new LinkedHashMap<>(); // own units (facing away)
    private static boolean loaded = false;

    // ---- storage helpers ----

    private static File dir(Context ctx){
        return new File(ctx.getFilesDir(), "champion_templates");
    }

    private static String filename(String name){
        return "tpl_" + name.toLowerCase().replaceAll("[^a-z0-9]", "") + ".png";
    }

    // ---- load all saved templates from disk ----

    // Forget loaded sprites so the next load() re-resolves names against the
    // CURRENT roster — called when the event-mode switch swaps the pool.
    public static synchronized void reset(){
        boardSigs.clear(); loaded = false;
    }

    public static synchronized void load(Context ctx){
        if(loaded) return;
        loaded = true;
        File d = dir(ctx);
        if(!d.exists()) return;
        File[] files = d.listFiles();
        if(files == null) return;
        for(File f : files){
            String fn = f.getName();
            if(!fn.endsWith(".png")) continue;
            if(fn.startsWith("btplo_") || fn.startsWith("tpl_")){ f.delete(); continue; } // obsolete enemy-sprite / popup-portrait files from older versions
            if(!fn.startsWith("btpl_")) continue;
            String key = fn.substring("btpl_".length(), fn.length()-4);
            // optional per-star variant suffix ("aatrox_s2"): a 1-star and a
            // 2-star model look different — both are kept, keyed name+bucket
            String starSuf = "";
            int sIdx = key.lastIndexOf("_s");
            if(sIdx > 0 && sIdx == key.length()-3 && Character.isDigit(key.charAt(key.length()-1))){
                starSuf = key.substring(sIdx);
                key = key.substring(0, sIdx);
            }
            // find SetData name that lowercases to this key
            String champName = findChampName(key);
            if(champName == null) continue;
            try{
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if(bmp == null) continue;
                float[] s = sig(bmp);
                meanCenter(s);
                boardSigs.put(champName + starSuf, s);
                bmp.recycle();
            }catch(Exception e){ android.util.Log.w("TFTTemplates","load err "+f.getName()+": "+e.getMessage()); }
        }
        android.util.Log.d("TFTTemplates","loaded "+boardSigs.size()+" board templates");
        OverlayService.addScanLog("templates: "+boardSigs.size()+" sprites");
    }

    private static String findChampName(String lowerKey){
        for(String[] tier : SetData.CHAMPS)
            for(String c : tier)
                if(c.toLowerCase().replaceAll("[^a-z0-9]","").equals(lowerKey)) return c;
        return null;
    }

    // ---- board-sprite templates: how the champion looks STANDING ON THE BOARD ----
    // Captured at the confirmed probe position the moment the popup names the unit.
    // cx,cy is the tap point (unit body); size is the square crop edge in px.

    public static synchronized void saveBoardTemplate(Context ctx, String champName, Bitmap sourceBmp,
                                                      int cx, int cy, int size, int stars){
        int x0 = cx - size/2, y0 = cy - size/2;
        if(x0 < 0 || y0 < 0 || x0+size > sourceBmp.getWidth() || y0+size > sourceBmp.getHeight()) return;
        try{
            Bitmap crop = Bitmap.createBitmap(sourceBmp, x0, y0, size, size);
            saveBoardTemplateBitmap(ctx, champName, crop, stars);
            crop.recycle();
        }catch(Exception e){
            OverlayService.addScanLog("ERR save sprite "+champName+": "+e.getMessage());
        }
    }

    // Same as saveBoardTemplate but the caller already holds the sprite crop —
    // used by the planner scan, which learns every named unit's look from the
    // board shot it took before opening the planner.
    public static synchronized void saveBoardTemplateBitmap(Context ctx, String champName, Bitmap crop){
        saveBoardTemplateBitmap(ctx, champName, crop, 0);
    }
    // stars picks the variant bucket: a 1-star and a 2-star+ model differ in
    // size/pose, so each star tier keeps its own learned sprite instead of the
    // last sighting overwriting the other. 0 = unknown tier (legacy bucket).
    public static synchronized void saveBoardTemplateBitmap(Context ctx, String champName, Bitmap crop, int stars){
        try{
            Bitmap scaled = Bitmap.createScaledBitmap(crop, SCALE, SCALE, true);
            File d = dir(ctx);
            if(!d.exists()) d.mkdirs();
            String starSuf = stars > 0 ? "_s" + Math.min(3, stars) : "";
            String fn = "btpl_"
                    + champName.toLowerCase().replaceAll("[^a-z0-9]", "") + starSuf + ".png";
            File out = new File(d, fn);
            FileOutputStream fos = new FileOutputStream(out);
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            float[] s = sig(scaled);
            meanCenter(s);
            boardSigs.put(champName + starSuf, s);
            if(scaled != crop) scaled.recycle();   // same-size input returns the caller's bitmap
            OverlayService.addScanLog("sprite learned: "+champName+(stars>0?" "+Math.min(3,stars)+"\u2605":""));
        }catch(Exception e){
            OverlayService.addScanLog("ERR save sprite "+champName+": "+e.getMessage());
        }
    }

    // map key -> champion name (strips the per-star variant suffix)
    private static String baseName(String key){
        int i = key.lastIndexOf("_s");
        if(i > 0 && i == key.length()-3 && Character.isDigit(key.charAt(key.length()-1)))
            return key.substring(0, i);
        return key;
    }

    public static class BoardMatch {
        public final String name;
        public final float sim;
        public final float margin; // gap to the second-best champion
        public BoardMatch(String n, float s, float m){ name=n; sim=s; margin=m; }
    }

    // Match a board crop against learned sprites. Returns null unless the best
    // match is both strong AND clearly ahead of every other champion — a wrong
    // visual ID is worse than a 1-second tap, so this must never guess.
    public static synchronized BoardMatch matchBoardSprite(Bitmap crop){
        Map<String, float[]> pool = boardSigs;
        if(pool.isEmpty()) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(crop, SCALE, SCALE, true);
        float[] s = sig(scaled);
        if(scaled != crop) scaled.recycle();   // same-size input returns the caller's bitmap
        meanCenter(s);
        String best = null; float bestSim = -2f, secondSim = -2f;
        java.util.HashSet<String> champs = new java.util.HashSet<>();
        for(Map.Entry<String,float[]> e : pool.entrySet()){
            String base = baseName(e.getKey());
            champs.add(base);
            float sim = cosine(s, e.getValue());
            if(sim > bestSim){
                // a different variant of the SAME champion is not a rival —
                // only track the runner-up across different champions
                if(best != null && !best.equals(base)) secondSim = bestSim;
                bestSim = sim; best = base;
            } else if(best != null && !best.equals(base) && sim > secondSim){
                secondSim = sim;
            }
        }
        if(best == null) return null;
        if(champs.size() == 1){
            if(bestSim < BOARD_SOLO_SIM) return null;
            return new BoardMatch(best, bestSim, 1f);
        }
        float margin = bestSim - secondSim;
        if(bestSim < BOARD_MIN_SIM || margin < BOARD_MIN_MARGIN) return null;
        return new BoardMatch(best, bestSim, margin);
    }

    public static synchronized int boardTemplateCount(){ return boardSigs.size(); }

    // Subtract the per-channel mean across all grid cells. Crops of different
    // champions share most of their pixels (board ground), which inflates raw
    // cosine similarity toward 1 for everything. Removing the average color
    // leaves the spatial pattern — the sprite — to drive the score.
    static void meanCenter(float[] s){
        float mr=0, mg=0, mb=0;
        int cells = s.length / 3;
        for(int i=0;i<s.length;i+=3){ mr+=s[i]; mg+=s[i+1]; mb+=s[i+2]; }
        mr/=cells; mg/=cells; mb/=cells;
        for(int i=0;i<s.length;i+=3){ s[i]-=mr; s[i+1]-=mg; s[i+2]-=mb; }
    }


    public static synchronized void clearAll(Context ctx){
        boardSigs.clear();
        File d = dir(ctx);
        if(d.exists()){
            File[] files = d.listFiles();
            if(files != null) for(File f : files) f.delete();
        }
        OverlayService.addScanLog("templates cleared");
    }

    // ---- signature: 8×8 grid of mean RGB (192 floats) ----

    static float[] sig(Bitmap bmp){
        int w = bmp.getWidth(), h = bmp.getHeight();
        // One batched read instead of SIG_GRID*SIG_GRID*cell getPixel() calls — getPixel
        // has heavy per-call overhead, getPixels copies the whole buffer once.
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        float[] out = new float[SIG_GRID * SIG_GRID * 3];
        int idx = 0;
        for(int gy=0;gy<SIG_GRID;gy++) for(int gx=0;gx<SIG_GRID;gx++){
            int x0=gx*w/SIG_GRID, x1=(gx+1)*w/SIG_GRID;
            int y0=gy*h/SIG_GRID, y1=(gy+1)*h/SIG_GRID;
            long r=0,g=0,b=0,n=0;
            for(int py=y0;py<y1;py++){
                int rowBase=py*w;
                for(int px=x0;px<x1;px++){
                    int c=pixels[rowBase+px];
                    r+=(c>>16)&0xFF; g+=(c>>8)&0xFF; b+=c&0xFF; n++;
                }
            }
            if(n>0){ out[idx]=(float)r/n/255f; out[idx+1]=(float)g/n/255f; out[idx+2]=(float)b/n/255f; }
            idx+=3;
        }
        return out;
    }

    private static float cosine(float[] a, float[] b){
        float dot=0,na=0,nb=0;
        for(int i=0;i<a.length;i++){ dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i]; }
        if(na==0||nb==0) return 0;
        return dot/(float)Math.sqrt(na*nb);
    }
}
