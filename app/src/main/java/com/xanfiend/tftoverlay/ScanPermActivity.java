package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

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
            // getMediaProjection() must be called in the Activity — on API 34+ the token is
            // tied to the activity session and becomes invalid if consumed in a Service.
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            MediaProjection mp = mpm.getMediaProjection(res, data);
            if (mp != null) {
                OverlayService.acceptProjection(mp);
                // Use startForegroundService so Android 14 permits startForeground with
                // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION in onStartCommand.
                Intent svc = new Intent(this, OverlayService.class).putExtra("mp_scan_now", true);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
            }
        }
        finish();
    }
}
