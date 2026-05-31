package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class ScanPermActivity extends Activity {

    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_NOTIF = 1002;

    private static void log(String msg) { OverlayService.addScanLog(msg); }
    private static void err(String msg) { OverlayService.addScanLog("ERR " + msg); }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        OverlayService.clearScanLog();
        log("onCreate  SDK=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            log("requesting POST_NOTIFICATIONS");
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            startProjectionRequest();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        log("onRequestPermissionsResult req=" + req
                + " result=" + (results.length > 0 ? results[0] : "none"));
        startProjectionRequest();
    }

    private void startProjectionRequest() {
        log("startProjectionRequest");
        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm == null) {
                err("MediaProjectionManager null");
                OverlayService.deliverScanError("mpm null");
                finish();
                return;
            }
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
            log("createScreenCaptureIntent launched");
        } catch (Exception e) {
            err(e.getClass().getSimpleName() + ": " + e.getMessage());
            OverlayService.deliverScanError(e.getClass().getSimpleName() + ": " + e.getMessage());
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        log("onActivityResult req=" + req + " res=" + res + " hasData=" + (data != null));
        if (req == REQ_PROJECTION && res == RESULT_OK && data != null) {
            // Start ScanService so Android 14 FGS requirement is satisfied before
            // getMediaProjection(). On MIUI, startForeground() inside ScanService will
            // throw and ScanService calls stopSelf() — we try getMediaProjection() anyway
            // since MIUI doesn't enforce the FGS requirement.
            try {
                Intent svc = new Intent(this, ScanService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
                log("ScanService start requested");
            } catch (Exception e) {
                log("ScanService start failed: " + e.getClass().getSimpleName());
            }

            // Wait 400ms for ScanService.onStartCommand to run startForeground()
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isFinishing()) return;
                log("calling getMediaProjection (ScanService.active=" + ScanService.active + ")");
                try {
                    MediaProjectionManager mpm =
                            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    if (mpm == null) {
                        err("mpm null");
                        OverlayService.deliverScanError("mpm null");
                        ScanService.stop(this);
                        finish();
                        return;
                    }
                    MediaProjection mp = mpm.getMediaProjection(res, data);
                    if (mp != null) {
                        log("MediaProjection ok, scan in 300ms");
                        new Handler(Looper.getMainLooper()).postDelayed(() -> runScan(mp), 300);
                        return;
                    } else {
                        err("getMediaProjection returned null");
                        OverlayService.deliverScanError("projection null");
                    }
                } catch (Exception e) {
                    err(e.getClass().getSimpleName() + ": " + e.getMessage());
                    OverlayService.deliverScanError(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                ScanService.stop(this);
                finish();
            }, 400);
            return;
        }
        log("denied or wrong req (res=" + res + ")");
        finish();
    }

    private void runScan(MediaProjection mp) {
        // Move TFT Scryer behind TFT automatically — no user action needed.
        // moveTaskToBack(true) sends this task to the back of the stack so TFT
        // becomes the visible app. We then wait 800 ms for the transition to finish
        // before capturing.
        log("runScan — moveTaskToBack so TFT is on screen");
        moveTaskToBack(true);
        new Thread(() -> {
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}
            log("capturing now");
            try {
                ScreenScanner scanner = new ScreenScanner(getApplicationContext(), mp);
                scanner.scan(new ScreenScanner.ScanCallback() {
                    public void onResult(ScreenScanner.ScanResult r) {
                        log("scan ok: " + r.status);
                        try { mp.stop(); } catch (Exception ignored) {}
                        ScanService.stop(getApplicationContext());
                        OverlayService.deliverScanResult(r);
                        runOnUiThread(ScanPermActivity.this::finish);
                    }
                    public void onError(String msg) {
                        err("scan error: " + msg);
                        try { mp.stop(); } catch (Exception ignored) {}
                        ScanService.stop(getApplicationContext());
                        OverlayService.deliverScanError(msg);
                        runOnUiThread(ScanPermActivity.this::finish);
                    }
                });
            } catch (Exception e) {
                err(e.getClass().getSimpleName() + ": " + e.getMessage());
                try { mp.stop(); } catch (Exception ignored) {}
                ScanService.stop(getApplicationContext());
                OverlayService.deliverScanError(e.getClass().getSimpleName() + ": " + e.getMessage());
                runOnUiThread(ScanPermActivity.this::finish);
            }
        }).start();
    }
}
