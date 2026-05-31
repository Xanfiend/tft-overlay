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

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            startProjectionRequest();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        startProjectionRequest();
    }

    private void startProjectionRequest() {
        MediaProjectionManager mpm =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PROJECTION && res == RESULT_OK && data != null) {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            MediaProjection mp = mpm.getMediaProjection(res, data);
            if (mp != null) {
                // Run scan after 600 ms so the overlay panel and permission dialog are fully gone.
                // This Activity stays alive (transparent) until scan completes.
                // No startForegroundService() here — that triggers a 5-second startForeground()
                // deadline that crashes the overlay service on devices like Xiaomi MIUI.
                new Handler(Looper.getMainLooper()).postDelayed(() -> runScan(mp), 600);
                return; // do NOT finish yet
            }
        }
        finish();
    }

    private void runScan(MediaProjection mp) {
        new Thread(() -> {
            try {
                ScreenScanner scanner = new ScreenScanner(this, mp);
                scanner.scan(new ScreenScanner.ScanCallback() {
                    public void onResult(ScreenScanner.ScanResult r) {
                        try { mp.stop(); } catch (Exception ignored) {}
                        OverlayService.deliverScanResult(r);
                        runOnUiThread(ScanPermActivity.this::finish);
                    }
                    public void onError(String msg) {
                        try { mp.stop(); } catch (Exception ignored) {}
                        OverlayService.deliverScanError(msg);
                        runOnUiThread(ScanPermActivity.this::finish);
                    }
                });
            } catch (Exception e) {
                try { mp.stop(); } catch (Exception ignored) {}
                OverlayService.deliverScanError("scan failed");
                runOnUiThread(ScanPermActivity.this::finish);
            }
        }).start();
    }
}
