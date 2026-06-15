package com.xanfiend.tftoverlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Minimal accessibility service whose sole purpose is exposing
 * takeScreenshot() (API 30+) so OverlayService can capture the screen
 * without launching an Activity or showing a permission dialog.
 *
 * The user must enable it once in Settings -> Accessibility -> TFT Scryer.
 * After that, all scans are silent and instant.
 */
public class TFTAccessibilityService extends AccessibilityService {

    static volatile TFTAccessibilityService instance;

    /**
     * True when Android's settings list this service as enabled. If this returns
     * true while {@link #instance} is null, the service is STUCK: the switch in
     * Accessibility settings shows ON but Android never (re)bound the service.
     * This commonly happens right after an app update. The only fix is toggling
     * the service OFF and back ON in Accessibility settings.
     */
    static boolean enabledInSettings(android.content.Context c){
        try{
            String flat = android.provider.Settings.Secure.getString(
                c.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return flat != null && flat.toLowerCase().contains(c.getPackageName().toLowerCase());
        }catch(Exception e){ return false; }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        OverlayService.addScanLog("AccessibilityService connected — silent scan enabled");
        OverlayService.onAccessibilityChanged(); // refresh an open SETUP panel live
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        OverlayService.addScanLog("AccessibilityService disconnected");
        OverlayService.onAccessibilityChanged();
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
                CharSequence pkg = event.getPackageName();
                if(pkg == null) return;
                String p = pkg.toString();
                // ignore own overlay windows and core system — not real app switches
                if(p.equals("com.xanfiend.tftoverlay") || p.equals("com.android.systemui") || p.equals("android")) return;
                if(!p.equals("com.riotgames.league.teamfighttactics")) OverlayService.setOverlayVisible(false);
            }
        } catch(Exception e) {
            android.util.Log.e("TFTScryer", "onAccessibilityEvent err: " + e.getMessage());
        }
    }

    @Override
    public void onInterrupt() {}
}
