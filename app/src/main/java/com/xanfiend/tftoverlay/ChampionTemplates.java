package com.xanfiend.tftoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages per-champion visual templates captured from the unit stat popup.
 *
 * Templates are cropped from the popup portrait area when My Board scan detects
 * a champion. They're stored in the app's private files directory so they survive
 * restarts but reflect the CURRENT patch (no stale CDN data).
 *
 * Matching: 8×8 grid average RGB signature (192 floats), cosine similarity.
 */
public class ChampionTemplates {

    private static final int SIG_GRID = 8;
    private static final int SCALE    = 48;
    private static final float MIN_SIM = 0.72f;

    private static final Map<String, float[]> sigs = new LinkedHashMap<>();
    private static boolean loaded = false;

    // ---- storage helpers ----

    private static File dir(Context ctx){
        return new File(ctx.getFilesDir(), "champion_templates");
    }

    private static String filename(String name){
        return "tpl_" + name.toLowerCase().replaceAll("[^a-z0-9]", "") + ".png";
    }

    // ---- load all saved templates from disk ----

    public static synchronized void load(Context ctx){
        if(loaded) return;
        loaded = true;
        File d = dir(ctx);
        if(!d.exists()) return;
        File[] files = d.listFiles();
        if(files == null) return;
        for(File f : files){
            String fn = f.getName();
            if(!fn.startsWith("tpl_") || !fn.endsWith(".png")) continue;
            String key = fn.substring(4, fn.length()-4); // strip tpl_ and .png
            // find SetData name that lowercases to this key
            String champName = findChampName(key);
            if(champName == null) continue;
            try{
                Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                if(bmp == null) continue;
                sigs.put(champName, sig(bmp));
                bmp.recycle();
            }catch(Exception e){ android.util.Log.w("TFTTemplates","load err "+f.getName()+": "+e.getMessage()); }
        }
        android.util.Log.d("TFTTemplates","loaded "+sigs.size()+" templates");
        OverlayService.addScanLog("templates: "+sigs.size()+" loaded");
    }

    private static String findChampName(String lowerKey){
        for(String[] tier : SetData.CHAMPS)
            for(String c : tier)
                if(c.toLowerCase().replaceAll("[^a-z0-9]","").equals(lowerKey)) return c;
        return null;
    }

    // ---- save a new template (call when popup scan confirms a champion) ----
    // popupBounds: bounding box of popup text blocks in the full screenshot
    // sourceBmp:   the full screenshot bitmap (not yet recycled)

    public static void saveTemplate(Context ctx, String champName, Bitmap sourceBmp, Rect popupBounds){
        try{
            // Portrait is just to the left of the text content, at the top of the popup.
            // Estimate: 100×100 region at (popMinX - 90, popMinY).
            int pw = 100, ph = 100;
            int px = Math.max(0, popupBounds.left - 90);
            int py = Math.max(0, popupBounds.top);
            int maxX = px + pw, maxY = py + ph;
            if(maxX > sourceBmp.getWidth())  { px = sourceBmp.getWidth()  - pw; if(px < 0) pw = sourceBmp.getWidth(); }
            if(maxY > sourceBmp.getHeight()) { py = sourceBmp.getHeight() - ph; if(py < 0) ph = sourceBmp.getHeight(); }
            Bitmap crop = Bitmap.createBitmap(sourceBmp, px, py, pw, ph);
            File d = dir(ctx);
            if(!d.exists()) d.mkdirs();
            File out = new File(d, filename(champName));
            FileOutputStream fos = new FileOutputStream(out);
            crop.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            sigs.put(champName, sig(crop));
            crop.recycle();
            OverlayService.addScanLog("template saved: "+champName);
        }catch(Exception e){
            OverlayService.addScanLog("ERR save template "+champName+": "+e.getMessage());
        }
    }

    // ---- match a hex crop against all saved templates ----

    public static class Match {
        public final String name;
        public final float sim;
        public Match(String n, float s){ name=n; sim=s; }
    }

    public static Match match(Bitmap hexCrop){
        if(sigs.isEmpty()) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(hexCrop, SCALE, SCALE, true);
        float[] s = sig(scaled);
        scaled.recycle();
        String best = null; float bestSim = -1;
        for(Map.Entry<String,float[]> e : sigs.entrySet()){
            float sim = cosine(s, e.getValue());
            if(sim > bestSim){ bestSim = sim; best = e.getKey(); }
        }
        if(best == null || bestSim < MIN_SIM) return null;
        return new Match(best, bestSim);
    }

    public static int templateCount(){ return sigs.size(); }

    public static void clearAll(Context ctx){
        sigs.clear();
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
        float[] out = new float[SIG_GRID * SIG_GRID * 3];
        int idx = 0;
        for(int gy=0;gy<SIG_GRID;gy++) for(int gx=0;gx<SIG_GRID;gx++){
            int x0=gx*w/SIG_GRID, x1=(gx+1)*w/SIG_GRID;
            int y0=gy*h/SIG_GRID, y1=(gy+1)*h/SIG_GRID;
            long r=0,g=0,b=0,n=0;
            for(int py=y0;py<y1;py++) for(int px=x0;px<x1;px++){
                int c=bmp.getPixel(px,py);
                r+=(c>>16)&0xFF; g+=(c>>8)&0xFF; b+=c&0xFF; n++;
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
