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
            // Must call getMediaProjection() here in the Activity — on API 34+ the token is
            // tied to the activity session and becomes invalid if deferred to a Service.
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            MediaProjection mp = mpm.getMediaProjection(res, data);
            if (mp != null) {
                OverlayService.acceptProjection(mp);
                startService(new Intent(this, OverlayService.class).putExtra("mp_ready", true));
            }
        }
        finish();
    }
}
