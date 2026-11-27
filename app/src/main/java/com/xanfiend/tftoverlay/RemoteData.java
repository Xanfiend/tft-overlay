package com.xanfiend.tftoverlay;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Remote set-data sync. The app ships with a bundled fallback set in SetData;
 * this pulls the current set's data from the repo so a new TFT set works without
 * a new APK — just edit data/setdata.json and push.
 *
 * Two phases, deliberately split so the network never mutates data mid-session
 * (which would desync the champ-name caches in Pool / ScreenScanner):
 *
 *   loadCachedOrBundled(ctx)  — synchronous, disk-only. Call FIRST at startup,
 *                               before anything reads SetData. Overlays the last
 *                               successfully synced JSON; if none, keeps bundled.
 *   syncAsync(ctx, cb)        — background fetch from GitHub, writes the cache for
 *                               the NEXT launch. Does not touch the live SetData.
 *
 * Privacy: the only host contacted is raw.githubusercontent.com — same
 * GitHub-only promise as the updater. No identifiers, just a JSON GET.
 */
public final class RemoteData {

    private static final String TAG = "TFTScryer";
    private static final String URL =
            "https://raw.githubusercontent.com/Xanfiend/tft-overlay/main/data/setdata.json";
    private static final String CACHE = "setdata-cache.json";

    private RemoteData(){}

    /** Disk-only overlay of the cached set data onto SetData. Call at startup. */
    public static void loadCachedOrBundled(Context ctx){
        try{
            File f = new File(ctx.getFilesDir(), CACHE);
            if(!f.exists()) return;            // no sync yet — keep bundled defaults
            JSONObject o = new JSONObject(readFile(f));
            if(apply(o)) Log.d(TAG, "remote set data loaded from cache: "
                    + SetData.SET_NAME + (SetData.PATCH.isEmpty()?"":(" patch "+SetData.PATCH)));
            else f.delete();
        }catch(Exception e){
            Log.w(TAG, "remote cache load failed, using bundled: " + e.getMessage());
            new File(ctx.getFilesDir(), CACHE).delete();   // don't repeat the failure every launch
        }
    }

    public interface SyncCallback {
        /** changed = the fetched JSON differs from what was cached. */
        void onDone(boolean changed, String patch);
    }

    /** Background fetch → validate → write cache for next launch. Safe on launch. */
    public static void syncAsync(final Context ctx, final SyncCallback cb){
        new Thread(new Runnable(){ public void run(){
            try{
                String json = httpGet(URL);
                JSONObject o = new JSONObject(json);       // parse + validate before trusting
                if(!validate(o)) throw new Exception("invalid schema");
                File f = new File(ctx.getFilesDir(), CACHE);
                String prev = f.exists() ? readFile(f) : "";
                writeFile(f, json);
                boolean changed = !json.equals(prev);
                Log.d(TAG, "remote sync ok (changed=" + changed + ") set=" + o.optString("setName"));
                if(cb != null) cb.onDone(changed, o.optString("patch",""));
            }catch(Exception e){
                Log.w(TAG, "remote sync failed: " + e.getMessage());
                if(cb != null) cb.onDone(false, null);
            }
        }}).start();
    }

    // ---- internals ----

    // A payload is only trusted if it has the full champ table (index 0 + 5 tiers,
    // each non-empty) and a pool-size row — so a truncated/garbled fetch can never
    // wipe out the bundled set.
    static boolean validate(JSONObject o){
        if(!o.has("setName")) return false;
        JSONArray champs = o.optJSONArray("champs");
        JSONArray size = o.optJSONArray("size");
        if(champs == null || champs.length() < 6 || champs.length() > 8) return false;
        if(size == null || size.length() != champs.length()) return false;
        for(int c=1;c<=5;c++){
            JSONArray tier = champs.optJSONArray(c);
            if(tier == null || tier.length() == 0) return false;
            if(size.optInt(c, 0) <= 0) return false;   // pool math divides by this
        }
        return true;
    }

    /** Overlay validated JSON onto SetData. Returns true if applied. Everything is
     *  parsed first and SetData is assigned last, so a bad value throws before any
     *  field changes and can never leave the set half-updated. */
    private static boolean apply(JSONObject o) throws Exception {
        if(!validate(o)) return false;
        String setName = o.getString("setName");
        String patch = o.optString("patch", SetData.PATCH);
        int[] sz = ints(o.getJSONArray("size"));
        String[][] cc = tiers(o.getJSONArray("champs"));
        JSONArray gods = o.optJSONArray("gods");
        String[] g = gods != null ? strings(gods) : null;

        // Optional EVENT roster — a live event queue with a different champion
        // pool. Malformed/absent event data clears the fields; it can never
        // corrupt the primary set (validated separately).
        JSONObject ev = o.optJSONObject("event");
        boolean hasEvent = ev != null && validRosterShape(ev);
        String evName = null; int[] evSize = null; String[][] evChamps = null; String[] evGods = null;
        if(hasEvent){
            evName = ev.optString("name", "Event");
            JSONArray esize = ev.optJSONArray("size");
            if(esize != null && esize.length() >= 6 && esize.length() <= 8){
                int[] es = ints(esize);
                if(positive(es)) evSize = es;   // else keep the standard pool sizes in event mode
            }
            evChamps = tiers(ev.getJSONArray("champs"));
            JSONArray eg = ev.optJSONArray("gods");
            if(eg != null) evGods = strings(eg);
        }

        // the SetData statics outlive the service: restore the standard roster
        // first so a restart while in EVENT mode can't leave the two out of sync
        SetData.applyEventRoster(false);
        SetData.SET_NAME = setName;
        SetData.PATCH    = patch;
        SetData.SIZE     = sz;
        SetData.CHAMPS   = cc;
        if(g != null) SetData.GODS = g;
        SetData.EVENT_NAME = evName; SetData.EVENT_SIZE = evSize;
        SetData.EVENT_CHAMPS = evChamps; SetData.EVENT_GODS = evGods;

        Pool.invalidateData();   // rebuild every roster-derived cache against the new list
        ScreenScanner.invalidateRosterCaches();
        return true;
    }

    /** Pool sizes for tiers 1-5 must all be > 0 (pool math divides by them). */
    static boolean positive(int[] size){
        for(int c=1;c<=5;c++) if(c>=size.length || size[c] <= 0) return false;
        return true;
    }

    private static int[] ints(JSONArray a) throws Exception {
        int[] r = new int[a.length()];
        for(int i=0;i<r.length;i++) r[i] = a.getInt(i);
        return r;
    }

    private static String[] strings(JSONArray a) throws Exception {
        String[] r = new String[a.length()];
        for(int i=0;i<r.length;i++) r[i] = a.getString(i);
        return r;
    }

    private static String[][] tiers(JSONArray a) throws Exception {
        String[][] r = new String[a.length()][];
        for(int i=0;i<r.length;i++) r[i] = strings(a.getJSONArray(i));
        return r;
    }

    // Same shape check the primary payload gets (costs 1–5 must be non-empty; 6+ optional).
    static boolean validRosterShape(JSONObject o){
        JSONArray champs = o.optJSONArray("champs");
        if(champs == null || champs.length() < 6 || champs.length() > 8) return false;
        for(int c=1;c<=5;c++){
            JSONArray tier = champs.optJSONArray(c);
            if(tier == null || tier.length() == 0) return false;
        }
        return true;
    }

    private static String readFile(File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int r;
        while((r = in.read(buf)) != -1) bos.write(buf, 0, r);
        in.close();
        return bos.toString("UTF-8");
    }

    private static void writeFile(File f, String s) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(s.getBytes("UTF-8"));
        fos.flush(); fos.close();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try{
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "TFT-Scryer");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if(code != HttpURLConnection.HTTP_OK) throw new Exception("HTTP " + code);
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int r;
            while((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            in.close();
            return bos.toString("UTF-8");
        }finally{
            if(conn != null) conn.disconnect();
        }
    }
}
