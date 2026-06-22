package com.xanfiend.tftoverlay;

import android.os.Build;
import java.io.File;

/*
 * Passive device-integrity heuristics — a trust signal, NOT a gate.
 *
 * The app never blocks, never phones home, never reports any of this. It only
 * surfaces a quiet "heads up" card in SETUP when the device looks rooted or
 * like an emulator, because both raise the odds that a sideloaded build has
 * been tampered with (and, down the line, that a license key could be patched
 * out). Everything here is local, cheap, and best-effort: false positives are
 * possible and harmless since nothing depends on the result.
 */
public final class DeviceIntegrity {

    /* Common su binary locations + a couple of root-manager footprints. */
    private static final String[] SU_PATHS = {
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/app/Superuser.apk", "/system/xbin/daemonsu",
        "/data/local/bin/su", "/data/local/xbin/su",
        "/system/bin/magisk", "/sbin/magisk", "/cache/magisk.log"
    };

    public static boolean isRooted(){
        // test-keys in the build tags means the ROM wasn't signed with release keys
        String tags = Build.TAGS;
        if(tags != null && tags.contains("test-keys")) return true;
        for(String path : SU_PATHS){
            try { if(new File(path).exists()) return true; } catch(Exception ignored){}
        }
        return false;
    }

    public static boolean isEmulator(){
        String fp = nz(Build.FINGERPRINT), model = nz(Build.MODEL),
               mfr = nz(Build.MANUFACTURER), brand = nz(Build.BRAND),
               dev = nz(Build.DEVICE), prod = nz(Build.PRODUCT),
               hw = nz(Build.HARDWARE);
        return fp.startsWith("generic") || fp.startsWith("unknown")
            || fp.contains("emulator") || fp.contains("vbox") || fp.contains("genymotion")
            || model.contains("google_sdk") || model.contains("emulator")
            || model.contains("Android SDK built for")
            || mfr.contains("Genymotion")
            || (brand.startsWith("generic") && dev.startsWith("generic"))
            || prod.equals("google_sdk") || prod.contains("sdk_gphone")
            || hw.contains("goldfish") || hw.contains("ranchu") || hw.contains("vbox");
    }

    /* True when nothing looks off — the common, quiet case. */
    public static boolean looksClean(){ return !isRooted() && !isEmulator(); }

    private static String nz(String s){ return s == null ? "" : s; }

    private DeviceIntegrity(){}
}
