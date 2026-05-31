package com.xanfiend.tftoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/*
 * Minimal foreground service started just before getMediaProjection().
 * Android 14 (API 34) requires FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION to
 * be active before calling getMediaProjection(). On Xiaomi MIUI, startForeground()
 * with that type throws — we catch it and call stopSelf() immediately, which
 * cancels the 5-second ANR timer so the overlay process is not killed.
 * ScanPermActivity then calls getMediaProjection() regardless; it succeeds on
 * MIUI without the FGS, and succeeds on stock Android 14 with it.
 */
public class ScanService extends Service {

    static final int NOTIF_ID = 9001;
    static volatile boolean active = false;

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        active = false;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm.getNotificationChannel("tft_scan") == null) {
                    nm.createNotificationChannel(new NotificationChannel(
                            "tft_scan", "Screen scan", NotificationManager.IMPORTANCE_LOW));
                }
            }
            Notification n = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "tft_scan")
                            .setContentTitle("TFT Scryer")
                            .setContentText("Reading screen...")
                            .setSmallIcon(android.R.drawable.ic_menu_camera)
                            .build()
                    : new Notification.Builder(this)
                            .setContentTitle("TFT Scryer")
                            .setContentText("Reading screen...")
                            .setSmallIcon(android.R.drawable.ic_menu_camera)
                            .build();

            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIF_ID, n);
            }
            active = true;
            OverlayService.addScanLog("ScanService FGS ok");
        } catch (Exception e) {
            OverlayService.addScanLog("ERR ScanService: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + " — stopSelf, will try without FGS");
            stopSelf(); // cancels the 5-second ANR timer
        }
        return START_NOT_STICKY;
    }

    static void stop(Context ctx) {
        active = false;
        try { ctx.stopService(new Intent(ctx, ScanService.class)); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
