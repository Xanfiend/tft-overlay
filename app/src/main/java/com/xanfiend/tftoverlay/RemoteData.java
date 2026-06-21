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
        }catch(Exception e){
            Log.w(TAG, "remote cache load failed, using bundled: " + e.getMessage());
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
    private static boolean validate(JSONObject o){
        if(!o.has("setName")) return false;
        JSONArray champs = o.optJSONArray("champs");
        JSONArray size = o.optJSONArray("size");
        if(champs == null || champs.length() != 6) return false;
        if(size == null || size.length() != 6) return false;
        for(int c=1;c<=5;c++){
            JSONArray tier = champs.optJSONArray(c);
            if(tier == null || tier.length() == 0) return false;
        }
        return true;
    }

    /** Overlay validated JSON onto SetData. Returns true if applied. */
    private static boolean apply(JSONObject o) throws Exception {
        if(!validate(o)) return false;
        SetData.SET_NAME = o.getString("setName");
        SetData.PATCH    = o.optString("patch", SetData.PATCH);

        JSONArray size = o.getJSONArray("size");
        int[] sz = new int[size.length()];
        for(int i=0;i<sz.length;i++) sz[i] = size.getInt(i);
        SetData.SIZE = sz;

        JSONArray champs = o.getJSONArray("champs");
        String[][] cc = new String[champs.length()][];
        for(int c=0;c<champs.length();c++){
            JSONArray tier = champs.getJSONArray(c);
            String[] arr = new String[tier.length()];
            for(int i=0;i<arr.length;i++) arr[i] = tier.getString(i);
            cc[c] = arr;
        }
        SetData.CHAMPS = cc;

        JSONArray gods = o.optJSONArray("gods");
        if(gods != null){
            String[] g = new String[gods.length()];
            for(int i=0;i<g.length;i++) g[i] = gods.getString(i);
            SetData.GODS = g;
        }
        Pool.invalidateData();   // rebuild the cost cache against the new list
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
