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

    @Override
    protected void onServiceConnected() {
        instance = this;
        OverlayService.addScanLog("AccessibilityService connected — silent scan enabled");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        OverlayService.addScanLog("AccessibilityService disconnected");
        return super.onUnbind(intent);
    }

    private boolean lastWasTFT = false;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            CharSequence pkg = event.getPackageName();
            if(pkg != null){
                boolean isTFT = "com.riotgames.league.teamfighttactics".contentEquals(pkg);
                if(lastWasTFT && !isTFT) OverlayService.setOverlayVisible(false);
                lastWasTFT = isTFT;
            }
        }
    }

    @Override
    public void onInterrupt() {}
}
