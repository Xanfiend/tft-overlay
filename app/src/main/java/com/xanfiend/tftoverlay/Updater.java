package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional in-app self-update. The app is otherwise fully offline; this is the
 * one feature that reaches the network, and only when the user opens the app or
 * taps "Check for updates". It queries the GitHub "latest" release, compares the
 * version to the installed one, and (on confirmation) downloads the signed APK
 * and hands it to the system installer. Because every release is signed with the
 * same key, the update installs over the top with no uninstall.
 *
 * Privacy: the only host contacted is GitHub (api.github.com + the release CDN).
 * No analytics, no identifiers — just a release-metadata GET and the APK GET.
 */
public final class Updater {

    private static final String API_LATEST =
            "https://api.github.com/repos/Xanfiend/tft-overlay/releases/latest";
    private static final String APK_URL =
            "https://github.com/Xanfiend/tft-overlay/releases/latest/download/tft-scryer.apk";
    // version embedded in the per-release asset name, e.g. tft-scryer-v1.66.apk
    private static final Pattern VER_IN_ASSET =
            Pattern.compile("tft-scryer-v([0-9]+(?:\\.[0-9]+)+)\\.apk");

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Updater(){}

    public interface CheckCallback {
        void onResult(boolean updateAvailable, String latestVersion);
        void onError(String message);
    }

    /** Background version check. Safe to call on app launch. */
    public static void checkAsync(final Activity act, final CheckCallback cb){
        new Thread(new Runnable(){ public void run(){
            try{
                String json = httpGet(API_LATEST);
                String latest = parseLatestVersion(json);
                if(latest == null){ post(new Runnable(){ public void run(){ cb.onError("could not read latest version"); }}); return; }
                final String installed = installedVersion(act);
                final boolean newer = isNewer(latest, installed);
                final String fLatest = latest;
                post(new Runnable(){ public void run(){ cb.onResult(newer, fLatest); }});
            }catch(Exception e){
                final String msg = e.getClass().getSimpleName()+(e.getMessage()!=null?": "+e.getMessage():"");
                post(new Runnable(){ public void run(){ cb.onError(msg); }});
            }
        }}).start();
    }

    /** Ask the user, then download + install. */
    public static void promptAndInstall(final Activity act, final String latestVersion){
        new AlertDialog.Builder(act)
            .setTitle("Update available")
            .setMessage("TFT Scryer v"+latestVersion+" is available (you have v"
                    +installedVersion(act)+").\n\nDownload and install now?")
            .setPositiveButton("Update", new android.content.DialogInterface.OnClickListener(){
                public void onClick(android.content.DialogInterface d, int w){ download(act, latestVersion); }
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private static void download(final Activity act, final String latestVersion){
        final ProgressDialog pd = new ProgressDialog(act);
        pd.setTitle("Downloading v"+latestVersion);
        pd.setMessage("Fetching the latest APK…");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setIndeterminate(false);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();
        new Thread(new Runnable(){ public void run(){
            HttpURLConnection conn = null;
            try{
                File dir = new File(act.getExternalFilesDir(null), "updates");
                if(!dir.exists()) dir.mkdirs();
                final File out = new File(dir, "tft-scryer-update.apk");
                if(out.exists()) out.delete();

                URL url = new URL(APK_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "TFT-Scryer-Updater");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.connect();
                int code = conn.getResponseCode();
                if(code != HttpURLConnection.HTTP_OK)
                    throw new Exception("server returned "+code);
                final int total = conn.getContentLength();
                InputStream in = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(out);
                byte[] buf = new byte[16384];
                int read, sum = 0;
                while((read = in.read(buf)) != -1){
                    fos.write(buf, 0, read);
                    sum += read;
                    if(total > 0){
                        final int pct = (int)(sum * 100L / total);
                        post(new Runnable(){ public void run(){ pd.setProgress(pct); }});
                    }
                }
                fos.flush(); fos.close(); in.close();
                post(new Runnable(){ public void run(){
                    try{ pd.dismiss(); }catch(Exception ignored){}
                    install(act, out);
                }});
            }catch(final Exception e){
                post(new Runnable(){ public void run(){
                    try{ pd.dismiss(); }catch(Exception ignored){}
                    new AlertDialog.Builder(act)
                        .setTitle("Update failed")
                        .setMessage("Could not download the update:\n"+e.getMessage()
                                +"\n\nYou can still grab it from the GitHub releases page.")
                        .setPositiveButton("Open releases", new android.content.DialogInterface.OnClickListener(){
                            public void onClick(android.content.DialogInterface d, int w){
                                try{ act.startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/Xanfiend/tft-overlay/releases/latest"))); }catch(Exception ignored){}
                            }
                        })
                        .setNegativeButton("Close", null)
                        .show();
                }});
            }finally{
                if(conn != null) conn.disconnect();
            }
        }}).start();
    }

    private static void install(Activity act, File apk){
        // API 26+ gates sideload installs behind a per-app "install unknown apps"
        // permission; send the user to grant it once if they haven't.
        if(Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(act)
                .setTitle("One-time permission")
                .setMessage("Android needs permission to install updates for TFT Scryer. "
                        +"Enable \"Allow from this source\", then tap Update again.")
                .setPositiveButton("Open settings", new android.content.DialogInterface.OnClickListener(){
                    public void onClick(android.content.DialogInterface d, int w){
                        try{
                            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:"+act.getPackageName()));
                            act.startActivity(i);
                        }catch(Exception e){
                            try{ act.startActivity(new Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)); }catch(Exception ignored){}
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
            return;
        }
        try{
            Uri uri = FileProvider.getUriForFile(act, act.getPackageName()+".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        }catch(Exception e){
            // last resort: open the releases page in a browser
            try{ act.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/Xanfiend/tft-overlay/releases/latest"))); }catch(Exception ignored){}
        }
    }

    // ---- helpers ----

    static String installedVersion(Activity act){
        try{
            return act.getPackageManager().getPackageInfo(act.getPackageName(), 0).versionName;
        }catch(Exception e){ return "0"; }
    }

    private static String parseLatestVersion(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        // prefer the version baked into the per-release asset filename
        if(root.has("assets")){
            JSONArray assets = root.getJSONArray("assets");
            for(int i=0;i<assets.length();i++){
                String name = assets.getJSONObject(i).optString("name","");
                Matcher m = VER_IN_ASSET.matcher(name);
                if(m.find()) return m.group(1);
            }
        }
        // fall back to the release name, e.g. "TFT Scryer v1.66"
        String name = root.optString("name","");
        Matcher m = Pattern.compile("v([0-9]+(?:\\.[0-9]+)+)").matcher(name);
        if(m.find()) return m.group(1);
        return null;
    }

    /** true when a > b, comparing dotted numeric versions like 1.66 vs 1.7. */
    static boolean isNewer(String a, String b){
        try{
            String[] pa = a.split("\\."), pb = b.split("\\.");
            int n = Math.max(pa.length, pb.length);
            for(int i=0;i<n;i++){
                int x = i<pa.length ? Integer.parseInt(pa[i].trim()) : 0;
                int y = i<pb.length ? Integer.parseInt(pb[i].trim()) : 0;
                if(x != y) return x > y;
            }
        }catch(Exception e){ return false; }
        return false;
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = null;
        try{
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "TFT-Scryer-Updater");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            if(code != HttpURLConnection.HTTP_OK) throw new Exception("HTTP "+code);
            InputStream in = conn.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int r;
            while((r = in.read(buf)) != -1) bos.write(buf, 0, r);
            in.close();
            return bos.toString("UTF-8");
        }finally{
            if(conn != null) conn.disconnect();
        }
    }

    private static void post(Runnable r){ MAIN.post(r); }
}
