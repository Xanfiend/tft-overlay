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
import android.util.Log;

public class ScanPermActivity extends Activity {

    private static final String TAG = "TFTScryer";
    private static final int REQ_PROJECTION = 1001;
    private static final int REQ_NOTIF = 1002;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Log.d(TAG, "ScanPermActivity.onCreate  SDK=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting POST_NOTIFICATIONS");
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            startProjectionRequest();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        Log.d(TAG, "onRequestPermissionsResult req=" + req
                + " result=" + (results.length > 0 ? results[0] : "none"));
        startProjectionRequest();
    }

    private void startProjectionRequest() {
        Log.d(TAG, "startProjectionRequest");
        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm == null) {
                Log.e(TAG, "MediaProjectionManager is null");
                OverlayService.deliverScanError("mpm null");
                finish();
                return;
            }
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
        } catch (Exception e) {
            Log.e(TAG, "startProjectionRequest failed: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            OverlayService.deliverScanError(e.getClass().getSimpleName() + ": " + e.getMessage());
            finish();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        Log.d(TAG, "onActivityResult req=" + req + " res=" + res + " data=" + (data != null));
        if (req == REQ_PROJECTION && res == RESULT_OK && data != null) {
            try {
                MediaProjectionManager mpm =
                        (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                if (mpm == null) {
                    Log.e(TAG, "MediaProjectionManager null in onActivityResult");
                    OverlayService.deliverScanError("mpm null");
                    finish();
                    return;
                }
                Log.d(TAG, "Calling getMediaProjection");
                MediaProjection mp = mpm.getMediaProjection(res, data);
                if (mp != null) {
                    Log.d(TAG, "MediaProjection obtained, scheduling scan in 600ms");
                    new Handler(Looper.getMainLooper()).postDelayed(() -> runScan(mp), 600);
                    return; // do NOT finish yet
                } else {
                    Log.e(TAG, "getMediaProjection returned null");
                    OverlayService.deliverScanError("projection null");
                }
            } catch (Exception e) {
                Log.e(TAG, "onActivityResult error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                OverlayService.deliverScanError(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        } else {
            Log.d(TAG, "onActivityResult: user denied or wrong req (req=" + req + " res=" + res + ")");
        }
        finish();
    }

    private void runScan(MediaProjection mp) {
        Log.d(TAG, "runScan start");
        new Thread(() -> {
            try {
                ScreenScanner scanner = new ScreenScanner(getApplicationContext(), mp);
                scanner.scan(new ScreenScanner.ScanCallback() {
                    public void onResult(ScreenScanner.ScanResult r) {
                        Log.d(TAG, "scan onResult: " + r.status);
                        try { mp.stop(); } catch (Exception ignored) {}
                        OverlayService.deliverScanResult(r);
                        runOnUiThread(ScanPermActivity.this::finish);
                    }
                    public void onError(String msg) {
                        Log.e(TAG, "scan onError: " + msg);
                        try { mp.stop(); } catch (Exception ignored) {}
                        OverlayService.deliverScanError(msg);
                        runOnUiThread(ScanPermActivity.this::finish);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "runScan exception: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                try { mp.stop(); } catch (Exception ignored) {}
                OverlayService.deliverScanError(e.getClass().getSimpleName() + ": " + e.getMessage());
                runOnUiThread(ScanPermActivity.this::finish);
            }
        }).start();
    }
}
