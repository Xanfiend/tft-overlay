package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int VOID   = 0xFF050305;
    private static final int CARD   = 0xFF100A0F;
    private static final int BLOOD  = 0xFF8B1A1A;
    private static final int BLOODL = 0xFFC1121F;
    private static final int EDGE   = 0xFF3A1028;
    private static final int BONE   = 0xFFE0D5C0;
    private static final int GOLD   = 0xFFC9A227;
    private static final int ASH    = 0xFF7A6B60;
    private static final int DIM    = 0xFF4A3038;
    private static final int GREEN  = 0xFF5FA046;
    private static final int PURP   = 0xFF6B1A6B;
    private static final int PURPL  = 0xFF9B2A9B;

    // v2.0 release codename — used in the SETUP teaser. One-line swap to rename.
    private static final String V2_CODENAME = "TFT REAPER";

    // changelog: major features that don't start with "NEW" but should be highlighted
    private static final java.util.Set<String> FEATURE_VERS = new java.util.HashSet<>(
        java.util.Arrays.asList("v1.0", "v1.1", "v1.3", "v1.4", "v1.5", "v1.59"));

    private int activeTab = 0;
    private LinearLayout contentArea;
    private final TextView[] tabViews = new TextView[2];

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);

        // overlay any synced set data before the UI reads SetData (set name, etc.)
        RemoteData.loadCachedOrBundled(this);

        // root frame: pattern layer behind scroll
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(VOID);

        // occult background pattern — DRAWN and tiled across the whole canvas so it
        // always fills the screen (the old fixed block of glyph-text couldn't reach the
        // edges of a large tablet, leaving the pattern covering only part of the screen)
        View pattern = new View(this){
            @Override protected void onDraw(android.graphics.Canvas c){
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(1.5f);
                p.setColor(0x1AC1121F);   // faint blood
                float step = 60 * getResources().getDisplayMetrics().density;
                float r = step * 0.30f;
                int w = getWidth(), hh = getHeight(), rowi = 0;
                for(float y = step * 0.5f; y < hh + r; y += step, rowi++){
                    float ox = (rowi % 2 == 0) ? 0 : step * 0.5f;   // brick offset for a star-field feel
                    for(float x = step * 0.5f + ox; x < w + r; x += step){
                        drawPentagram(c, p, x, y, r, false);   // no circle — keep the field subtle
                    }
                }
            }
        };
        frame.addView(pattern, new FrameLayout.LayoutParams(-1, -1));

        // animated ember layer: slow-drifting glowing particles behind the content
        EmberView embers = new EmberView(this);
        frame.addView(embers, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0x00000000);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 80, 48, 72);
        root.setBackgroundColor(0x00000000);

        // hero pentacle — DRAWN blood-red inverted pentagram inside a circle. The old
        // ⛧ glyph rendered as a gold color-emoji on some devices, ignoring our theme color
        View sigil = heroPentacle();
        root.addView(sigil);
        pulseGlow(sigil);

        // ring of inverted pentagrams under the sigil — DRAWN (not font glyphs) so it
        // renders identically on every device in our theme color (no missing-glyph boxes)
        View sigilRing = pentRow(PURPL, 5, 8f);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(-1, sigilRing.getLayoutParams().height);
        ringLp.setMargins(0, -2, 0, 0);
        sigilRing.setLayoutParams(ringLp);
        root.addView(sigilRing);

        TextView title = new TextView(this);
        title.setText("TFT SCRYER");
        title.setTextColor(BLOODL); title.setTextSize(30); title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD); title.setLetterSpacing(0.14f);
        title.setShadowLayer(22,0,0,BLOODL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1,-2);
        titleLp.setMargins(0, 8, 0, 0);
        title.setLayoutParams(titleLp);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Set 17  ·  champion pool tracker");
        sub.setTextColor(GOLD); sub.setTextSize(13); sub.setGravity(Gravity.CENTER);
        sub.setShadowLayer(10,0,0,GOLD);
        LinearLayout.LayoutParams subl = new LinearLayout.LayoutParams(-1,-2);
        subl.setMargins(0, 5, 0, 2);
        sub.setLayoutParams(subl);
        root.addView(sub);

        TextView ver = new TextView(this);
        ver.setText("v1.99.46");
        ver.setTextColor(DIM); ver.setTextSize(10); ver.setGravity(Gravity.CENTER);
        root.addView(ver);

        root.addView(divider(28, 20));

        // tab row
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsl = new LinearLayout.LayoutParams(-1,-2);
        tabsl.setMargins(0, 0, 0, 20);
        tabs.setLayoutParams(tabsl);
        String[] tabLabels = {"SETUP", "CHANGELOG"};
        for(int t = 0; t < tabLabels.length; t++){
            final int ti = t;
            TextView tab = new TextView(this); tab.setText(tabLabels[t]);
            tab.setGravity(Gravity.CENTER); tab.setTextSize(12); tab.setLetterSpacing(0.08f);
            tab.setPadding(0, 20, 0, 20);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,-2,1f);
            tl.setMargins(t==0?0:8, 0, 0, 0);
            tab.setLayoutParams(tl);
            tab.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ activeTab=ti; rebuildContent(); }});
            pressFeedback(tab);
            tabViews[t] = tab;
            tabs.addView(tab);
        }
        root.addView(tabs);

        // content area rebuilt on each tab switch
        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentArea);

        rebuildContent();

        scroll.addView(root);
        frame.addView(scroll);
        setContentView(frame);

        // one-time privacy + permissions disclosure on very first launch
        Pool prefs = new Pool(this);
        if(!prefs.getPrivacySeen()) showPrivacyNotice(true);

        // refresh the cached set data in the background for next launch (silent)
        RemoteData.syncAsync(this, null);

        // quiet auto-check on launch: only surfaces a dialog if a newer release
        // exists; silent on "up to date" or any network error
        if(!autoCheckedUpdate){
            autoCheckedUpdate = true;
            Updater.checkAsync(this, new Updater.CheckCallback(){
                public void onResult(boolean available, String latest){
                    if(available) Updater.promptAndInstall(MainActivity.this, latest);
                }
                public void onError(String msg){ /* stay silent on launch */ }
            });
        }
    }

    private boolean autoCheckedUpdate = false;

    @Override protected void onResume(){
        super.onResume();
        if(contentArea != null) rebuildContent();
    }

    private void rebuildContent(){
        for(int i = 0; i < tabViews.length; i++){
            boolean on = i == activeTab;
            tabViews[i].setTextColor(on ? BONE : ASH);
            tabViews[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            tabViews[i].setBackground(shape(on ? BLOOD : CARD, on ? BLOODL : EDGE, 12, on ? 2 : 1));
            tabViews[i].setShadowLayer(on ? 10 : 0, 0, 0, BLOODL);
        }
        contentArea.removeAllViews();
        if(activeTab == 0) buildSetup();
        else buildChangelog();

        // soft fade-in for the freshly built tab content
        contentArea.setAlpha(0f);
        contentArea.animate().alpha(1f).setDuration(220)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    // slow ambient glow pulse on the hero sigil
    private void pulseGlow(final View v){
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0.55f, 1f);
        va.setDuration(1800);
        va.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        va.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        va.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener(){
            public void onAnimationUpdate(android.animation.ValueAnimator a){
                v.setAlpha((Float)a.getAnimatedValue());
            }
        });
        va.start();
    }

    // brief press-down feedback for tappable views, without consuming the click
    private void pressFeedback(final View v){
        v.setOnTouchListener(new View.OnTouchListener(){
            public boolean onTouch(View view, android.view.MotionEvent e){
                int a = e.getAction();
                if(a == android.view.MotionEvent.ACTION_DOWN){
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                } else if(a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL){
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                }
                return false;
            }
        });
    }

    private void buildSetup(){
        // ---- v2.0 teaser (hype, no spoilers) ----
        LinearLayout teaser = new LinearLayout(this);
        teaser.setOrientation(LinearLayout.VERTICAL);
        teaser.setBackground(shape(0xFF1A0A0A, BLOODL, 12, 2));
        teaser.setPadding(20, 18, 20, 18);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1,-2);
        tlp.setMargins(0, 0, 0, 14); teaser.setLayoutParams(tlp);
        TextView tt = new TextView(this);
        tt.setText("⛧  " + V2_CODENAME + "  ·  v2.0 INCOMING");
        tt.setTextColor(GOLD); tt.setTextSize(15); tt.setTypeface(null, Typeface.BOLD);
        tt.setShadowLayer(16,0,0,GOLD);
        tt.setLetterSpacing(0.06f); teaser.addView(tt);
        TextView tb = new TextView(this);
        tb.setText("A massive update is being forged — the biggest yet. New powers, soon. "
                 + "Keep the app updated so it lands the moment it's ready.");
        tb.setTextColor(BONE); tb.setTextSize(12); tb.setLineSpacing(4,1f);
        LinearLayout.LayoutParams teaserBl = new LinearLayout.LayoutParams(-1,-2);
        teaserBl.setMargins(0, 6, 0, 0); tb.setLayoutParams(teaserBl);
        teaser.addView(tb);
        contentArea.addView(teaser);

        boolean granted = canDraw();
        // Three states: connected (works), stuck (settings say ON but Android never
        // rebound the service — common right after an app update; needs an OFF/ON
        // toggle), or plain disabled.
        boolean accConnected = TFTAccessibilityService.instance != null;
        boolean accInSettings = isAccessibilityEnabled();
        boolean accStuck = accInSettings && !accConnected;
        boolean accEnabled = accConnected;

        // ---- permission status cards ----
        permCard(granted,
            "Overlay permission",
            granted ? "Draw over other apps is on" : "Required to show the sigil over TFT",
            granted ? null : new View.OnClickListener(){ public void onClick(View v){
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            }},
            granted ? null : "Grant permission");

        permCard(accEnabled,
            "Accessibility service  (Auto Scan / Board Scan)",
            accEnabled
                ? "Silent scan enabled — no app switch needed"
                : accStuck
                ? "Stuck: the switch shows ON but the service is not running. This happens after app updates. Open Accessibility settings and toggle TFT Scryer OFF, then ON again."
                : "Android resets this after every update. Takes about 30 seconds to restore.",
            accEnabled ? null : accStuck ? new View.OnClickListener(){ public void onClick(View v){
                openAccessibility();
                toast("Toggle TFT Scryer OFF, then ON again");
            }} : new View.OnClickListener(){ public void onClick(View v){
                if(Build.VERSION.SDK_INT >= 33){
                    try{
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:"+getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
                        toast("Allow restricted settings, then go to Accessibility");
                    }catch(Exception e){ openAccessibility(); }
                } else { openAccessibility(); }
            }},
            accEnabled ? null
                : accStuck ? "Fix: toggle OFF then ON"
                : (Build.VERSION.SDK_INT >= 33 ? "Step 1: App settings" : "Enable Accessibility"));

        if(!accEnabled && !accStuck && Build.VERSION.SDK_INT >= 33){
            contentArea.addView(btn("Step 2: Accessibility settings", new View.OnClickListener(){
                public void onClick(View v){ openAccessibility(); }
            }));
        }

        // ---- passive integrity heads-up (informational, never blocks) ----
        // Only shown when something looks off, so a normal device stays uncluttered.
        if(!DeviceIntegrity.looksClean()){
            boolean rooted = DeviceIntegrity.isRooted();
            String what = rooted && DeviceIntegrity.isEmulator() ? "Rooted device / emulator"
                        : rooted ? "Rooted device" : "Emulator";
            noticeCard("⚠  " + what + " detected",
                "Heads up only — nothing is blocked and nothing is reported (the app never sends this anywhere). "
                + "On a rooted or emulated device a sideloaded build is easier to tamper with, so only install TFT Scryer "
                + "from the project's own GitHub releases. The app works exactly the same.");
        }

        contentArea.addView(divider(8, 12));

        // main action
        contentArea.addView(btnPrimary("⛧  Start Overlay", new View.OnClickListener(){
            public void onClick(View v){
                if(!canDraw()){ toast("Grant overlay permission first"); return; }
                Intent svc=new Intent(MainActivity.this, OverlayService.class);
                if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(svc);
                else startService(svc);
                toast("Overlay started — switch to TFT.");
                moveTaskToBack(true);
            }
        }));

        contentArea.addView(btnDestructive("×  Stop overlay + reset pool", new View.OnClickListener(){
            public void onClick(View v){
                stopService(new Intent(MainActivity.this, OverlayService.class));
                new Pool(MainActivity.this).reset();
                toast("Stopped and pool cleared");
            }
        }));

        // ---- updates ----
        final TextView updBtn = btn("⟳  Check for updates", null);
        updBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            updBtn.setText("⟳  Checking…");
            Updater.checkAsync(MainActivity.this, new Updater.CheckCallback(){
                public void onResult(boolean available, String latest){
                    if(available){ updBtn.setText("⬇  Update to v"+latest);
                        Updater.promptAndInstall(MainActivity.this, latest);
                    } else { updBtn.setText("✓  Up to date (v"+Updater.installedVersion(MainActivity.this)+")"); }
                }
                public void onError(String msg){ updBtn.setText("⟳  Check for updates");
                    toast("Update check failed: "+msg); }
            });
        }});
        contentArea.addView(updBtn);

        contentArea.addView(btn("🔒  Privacy & data", new View.OnClickListener(){
            public void onClick(View v){ showPrivacyNotice(false); }
        }));

        contentArea.addView(divider(14, 16));

        // tips section header
        TextView tipsHdr = new TextView(this);
        tipsHdr.setText("⛧  HOW TO USE");
        tipsHdr.setTextColor(GOLD); tipsHdr.setTextSize(11); tipsHdr.setShadowLayer(8,0,0,GOLD);
        tipsHdr.setTypeface(null, Typeface.BOLD); tipsHdr.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams thl = new LinearLayout.LayoutParams(-1,-2);
        thl.setMargins(0, 0, 0, 10);
        tipsHdr.setLayoutParams(thl);
        contentArea.addView(tipsHdr);

        String[][] tips={
            {"Tap the sigil","Opens the scout grid. Long-press opens the board tab. Hold 1.5s to trigger a scan."},
            {"Mark a champion","Tap a name to mark one copy seen. Tap the count to remove one."},
            {"Contest badge","The ◉ badge shows how many other players are running that unit."},
            {"Board scan","Tap My Board in the grid tab, then tap each unit. Auto-reads the name. Needs Accessibility service (enable in the Settings tab of the overlay)."},
            {"Drag to close","Drag the sigil onto the ✕ that appears at the bottom of the screen."},
        };
        for(String[] tip : tips){
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(CARD, EDGE, 12, 1));
            card.setPadding(18, 14, 18, 14);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1,-2);
            cl.setMargins(0, 0, 0, 8);
            card.setLayoutParams(cl);
            TextView tipTitle = new TextView(this); tipTitle.setText(tip[0]);
            tipTitle.setTextColor(BONE); tipTitle.setTextSize(13); tipTitle.setTypeface(null, Typeface.BOLD);
            TextView tipBody = new TextView(this); tipBody.setText(tip[1]);
            tipBody.setTextColor(ASH); tipBody.setTextSize(11); tipBody.setLineSpacing(4,1f);
            LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(-1,-2);
            tbl.setMargins(0, 3, 0, 0);
            tipBody.setLayoutParams(tbl);
            card.addView(tipTitle); card.addView(tipBody);
            contentArea.addView(card);
        }

        contentArea.addView(divider(20, 6));
        TextView footer = new TextView(this);
        footer.setText("@xanfiend");
        footer.setTextColor(DIM); footer.setTextSize(11); footer.setGravity(Gravity.CENTER);
        contentArea.addView(footer);
    }

    private void permCard(boolean ok, String title, String body, View.OnClickListener btnAction, String btnLabel){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(CARD, ok ? GREEN : BLOOD, 12, ok ? 2 : 1));
        card.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1,-2);
        cl.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cl);
        TextView lbl = new TextView(this);
        lbl.setText((ok ? "✓  " : "✗  ") + title);
        lbl.setTextColor(ok ? GREEN : BLOODL); lbl.setTextSize(13);
        lbl.setTypeface(null, Typeface.BOLD); card.addView(lbl);
        TextView sub = new TextView(this); sub.setText(body);
        sub.setTextColor(ASH); sub.setTextSize(11);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1,-2);
        sl.setMargins(0, 4, 0, 0); sub.setLayoutParams(sl);
        card.addView(sub);
        if(btnAction != null && btnLabel != null){
            TextView actionBtn = new TextView(this); actionBtn.setText(btnLabel);
            actionBtn.setTextColor(BONE); actionBtn.setTextSize(12); actionBtn.setGravity(Gravity.CENTER);
            actionBtn.setPadding(0, 14, 0, 14);
            actionBtn.setBackground(shape(BLOOD, BLOODL, 8, 2));
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1,-2);
            al.setMargins(0, 10, 0, 0); actionBtn.setLayoutParams(al);
            actionBtn.setOnClickListener(btnAction);
            pressFeedback(actionBtn);
            card.addView(actionBtn);
        }
        contentArea.addView(card);
    }

    // First-launch privacy & permissions disclosure. firstRun=true marks it seen on
    // dismiss (and the button is "I understand"); firstRun=false is the re-open from
    // SETUP (button is "Close"). Built as a themed dialog so it reads on our dark UI.
    private void showPrivacyNotice(final boolean firstRun){
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(VOID);
        box.setPadding(56, 48, 56, 24);

        TextView h = new TextView(this);
        h.setText("PRIVACY & PERMISSIONS");
        h.setTextColor(BLOODL); h.setTextSize(17); h.setTypeface(null, Typeface.BOLD);
        h.setLetterSpacing(0.08f);
        box.addView(h);

        TextView body = new TextView(this);
        body.setText(
            "TFT Scryer runs on your device. It has no accounts, no analytics, no ads, "
          + "and no third-party services. Your pool, gold and settings are stored only on this phone.\n\n"
          + "WHAT IT USES\n"
          + "•  Draw over other apps — to show the sigil and panel on top of TFT.\n"
          + "•  Accessibility service — to take silent screenshots and tap for you during scans "
          + "and auto-buy. Screenshots are read on-device for champion/gold OCR and never leave the phone.\n"
          + "•  Internet — only to reach GitHub: to check for app updates and to fetch the current "
          + "set's champion data. Nothing else is ever sent or received. The app works fully offline; "
          + "the network is optional.\n\n"
          + "WHAT IT NEVER DOES\n"
          + "•  No telemetry, tracking, or crash reporting.\n"
          + "•  No reading other apps or your personal data.\n"
          + "•  No sending screenshots, game state, or device info anywhere.\n\n"
          + "Install only from the project's GitHub releases. You can re-read this any time from the "
          + "SETUP tab.");
        body.setTextColor(BONE); body.setTextSize(12); body.setLineSpacing(5,1f);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1,-2);
        bl.setMargins(0, 16, 0, 0); body.setLayoutParams(bl);
        box.addView(body);

        sv.addView(box);

        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
            .setView(sv)
            .setCancelable(!firstRun)
            .create();

        TextView ok = new TextView(this);
        ok.setText(firstRun ? "I UNDERSTAND" : "CLOSE");
        ok.setGravity(Gravity.CENTER); ok.setTextColor(BONE); ok.setTextSize(14);
        ok.setTypeface(null, Typeface.BOLD); ok.setPadding(0, 22, 0, 22);
        ok.setBackground(shape(BLOOD, BLOODL, 12, 2));
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1,-2);
        ol.setMargins(0, 20, 0, 0); ok.setLayoutParams(ol);
        ok.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(firstRun) new Pool(MainActivity.this).setPrivacySeen(true);
            dlg.dismiss();
        }});
        pressFeedback(ok);
        box.addView(ok);

        dlg.show();
    }

    // amber informational card (no action button) — used for the integrity heads-up
    private void noticeCard(String title, String body){
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(CARD, GOLD, 12, 1));
        card.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1,-2);
        cl.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cl);
        TextView lbl = new TextView(this);
        lbl.setText(title);
        lbl.setTextColor(GOLD); lbl.setTextSize(13);
        lbl.setTypeface(null, Typeface.BOLD); card.addView(lbl);
        TextView sub = new TextView(this); sub.setText(body);
        sub.setTextColor(ASH); sub.setTextSize(11); sub.setLineSpacing(4,1f);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1,-2);
        sl.setMargins(0, 4, 0, 0); sub.setLayoutParams(sl);
        card.addView(sub);
        contentArea.addView(card);
    }

    private void openAccessibility(){
        try{
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
        }catch(Exception e){}
    }

    private boolean isAccessibilityEnabled(){
        if(Build.VERSION.SDK_INT < 31) return false;
        String flat = android.provider.Settings.Secure.getString(
            getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return flat != null && flat.toLowerCase().contains(getPackageName().toLowerCase());
    }

    private void buildChangelog(){
        String[][] cl={
            {"v1.99.46  ·  2026-06-26","Tap the gold/stage/HP glance bar to jump straight to the GOLD tab. Tap the augment-round banner to open GUIDE augment ratings. Copy your board or enemy scan results to clipboard. 45s panel auto-close option added."},
            {"v1.99.45  ·  2026-06-26","Planner scan now correctly pairs star levels from the health-bar pass with champion names from the snapshot, logs any names that had no matching position, and adds them to the pool even when health-bar data is partial."},
            {"v1.99.44  ·  2026-06-26","Stability fix in the scan groundwork."},
            {"v1.99.43  ·  2026-06-26","Board scan now recognizes a unit even when one letter of its name is misread, so fewer units get missed when reading them by popup."},
            {"v1.99.42  ·  2026-06-26","Internal scan improvements to keep the pipeline solid ahead of upcoming features."},
            {"v1.99.41  ·  2026-06-26","Cleaner AUGMENTS header when a tier filter is active — no more repeated tier label."},
            {"v1.99.40  ·  2026-06-26","POOL tab now shows a gold banner on augment rounds (2-1 / 3-2 / 4-2) so you never miss an offer. BUILDS tab cost-tier headers now show how many meta builds exist for each tier at a glance."},
            {"v1.99.39  ·  2026-06-26","POOL tracking header now has a copy button — tap it to copy your tracked champion list to the clipboard. Section headers throughout the overlay also now show live counts at a glance."},
            {"v1.99.38  ·  2026-06-26","Section headers throughout the overlay now show live counts: AUGMENTS shows total and per-tier counts, REVEALED YOUR BOARD and REVEALED ENEMY show how many units were found, and CRAFTABLE in the items builder shows how many items your components can make."},
            {"v1.99.36  ·  2026-06-26","The launch screen now matches the overlay's luminous look — glowing title and subtitle, glowing primary buttons and tabs, and a glowing v2 teaser and section headers."},
            {"v1.99.35  ·  2026-06-26","Visual refresh across the overlay: a luminous occult look with a glowing title and sigil, an ornamental header flourish, glowing section headers, brighter active-tab highlights, and softer panel corners."},
            {"v1.99.34  ·  2026-06-26","POOL tab tracking header now shows how many champions you are currently tracking at a glance."},
            {"v1.99.33  ·  2026-06-25","Fixes a build problem that was preventing recent updates from installing. All recent POOL, COACH, GOLD, OPENER, and AUGMENTS improvements are now available."},
            {"v1.99.32  ·  2026-06-25","Smoother, faster panel rendering when the POOL and COACH tabs have many tracked units."},
            {"v1.99.31  ·  2026-06-25","GUIDE tab OPENER section now highlights the early-game phase that matches your current stage, with a clear marker, so the relevant advice stands out as the game progresses. OPENER item-slam section now shows your pinned carry's exact item plan in a banner, tying the evergreen slam priorities to the carry you have committed to. AUGMENTS tab now marks augments you have already taken with a checkmark and green highlight throughout the tier list, so they are easy to spot at a glance."},
            {"v1.99.30  ·  2026-06-25","POOL tab tracking chips now show a green buy prompt when you are exactly one copy away from 2-starring a champion and you have enough gold to buy it right now. AUGMENTS tab now lets you long-press any augment card to immediately record it as one of your taken augments, without needing a scan. COACH tab now shows a compact item reference for every scanned board champion that has a known build, so you can see the full board item plan in one place instead of only the primary carry."},
            {"v1.99.29  ·  2026-06-25","GOLD tab streak line now always shows contextual advice: what to do to start a streak, when the bonus activates, and how large it is once active, so the streak counter is never just a number. POOL tab shows a roll-check line under your pinned carry when you have gold to spend, giving you a hit percentage at your current level before you decide to roll. ITEMS tab has a new MY COMPONENTS section: tap which components you are holding, and see every item you can craft from them at a glance."},
            {"v1.99.28  ·  2026-06-25","POOL tab tracking now shows a 3-star achieved badge when you have collected all 9 copies of a champion, making it clear at a glance which units have hit their ceiling. GOLD tab shows the current level-up cost under the gold counter, so you always know what it costs to buy the next level without guessing. COACH tab now warns you directly inside the recommended comp card when your recommended carry is being contested by two or more opponents, so you can decide whether to 3-star faster or pivot."},
            {"v1.99.27  ·  2026-06-25","POOL tab now alerts when a tracked champion is 1 or 2 copies away from 3-starring, shown as a separate banner so it is visually distinct from the 2-star proximity alert. GOLD tab now shows how much gold you can safely spend without dropping below your current interest bracket, or how much is above the 50g cap, so you always know what is free to use. AUGMENTS tab now lets you pin up to 3 augments side-by-side at the top of the list for quick comparison; tap a card to pin it, tap the X to clear."},
            {"v1.99.26  ·  2026-06-25","BUILDS and AUGMENTS tabs updated to patch 17.6: Bard, TwistedFate, LeBlanc, and Viktor all received meaningful buffs and are now tracked as meta carries with recommended item builds. Augment tier list refreshed with 17.6 changes including reworked Blood Offering, Best Friends, and Loot Singularity. Carry tier and comp priority notes updated throughout."},
            {"v1.99.25  ·  2026-06-25","POOL tab tracking section now shows 3-star progress (how many more copies you need for 3-star) once a champion is already 2-starred, making upgrade priority clear at a glance. COACH tab shows a compact list of meta carries you are still missing for your recommended comp, so you know exactly what to shop for. GOLD tab stage card highlights when item components should be slammed because PvE is one or two rounds away."},
            {"v1.99.24  ·  2026-06-25","POOL tab tracking section has a sort toggle (contest pressure vs scarcest first) so reroll players can instantly see which units are hardest to find. GOLD tab WON button now previews the income you will collect before you tap, keeping both round-result buttons self-explanatory. BOARD tab synergy section highlights traits where adding one more unit activates the next breakpoint, making board-improvement decisions obvious at a glance."},
            {"v1.99.23  ·  2026-06-24","POOL tab tracking section shows a 2-star proximity label when you are 1 or 2 copies away from 2-starring a champion and those copies exist in the pool. POOL tab shows a gold alert banner when any tracked champion is close to 2-starring. GOLD tab income card now shows a projected gold estimate for 2 and 4 rounds ahead, so you can plan a level push or roll-down timing."},
            {"v1.99.22  ·  2026-06-24","POOL tab tracking section now shows how many copies remain in the pool for each tracked champion, colored red/yellow/grey by scarcity. POOL tab glance line shows a one-liner lobby read when opponents have been scouted: boards seen, top carries, AP/AD skew, and diver-heavy flag. GOLD tab shows a forward event timeline (upcoming augments ★ and carousels ◉) below the next-event line."},
            {"v1.99.21  ·  2026-06-24","GOLD tab tracks your win-loss record and winrate for the game, shown next to the WON/LOST buttons. COACH tab shows a warning card at the top when your HP is low, so stabilization is never buried under comp advice. POOL tab has a RESET POOL button that clears your tracking data without touching gold, HP, streak, or stage. Critical HP is now flagged red in the POOL tab economy line."},
            {"v1.99.20  ·  2026-06-24","GOLD tab has WON and LOST buttons that handle a full round result in one tap: gold income, streak update, stage advance, and HP loss (on LOST) all at once. POOL tab shows your current gold, stage, and HP at a glance without switching tabs. AUGMENTS tab has a tier filter so you can view only S, A, B, or C tier augments."},
            {"v1.99.19  ·  2026-06-24","GOLD tab: tapping Next Round now also advances the stage counter, so gold and stage stay in sync automatically. A one-tap LOSS button deducts the right HP for your current stage. Interest bracket indicator highlights gold when you are one or two coins from the next bracket. POOL tab shows how many copies remain in each cost tier at a glance."},
            {"v1.99.18  ·  2026-06-24","GOLD tab shows how many more losses you can take before elimination, how much gold your streak has paid out in total, and a hint on what to take from carousel based on your HP. POOL tab flags when your pinned carry is getting scarce so you know when to bail. COACH sub-tab shows the recommended roll level for slow-roll comps."},
            {"v1.99.17  ·  2026-06-23","POOL tab shows a contest alert when any tracked champion is almost gone from the shared pool. GOLD tab shows your roll budget at a glance. AUGMENTS tab highlights when you're in an augment round."},
            {"v1.99.16  ·  2026-06-23","GOLD tab gains HP tracking (tap -5/-10/-20 after each loss) and a Stage/Round display that shows what's coming up — augment rounds, carousels. POOL tab now surfaces your tracked champions at the top so you can update them without scrolling."},
            {"v1.99.15  ·  2026-06-23","POOL tab now has a cost-tier filter (tap to show only 1-cost through 5-cost). GOLD tab has a one-tap Next Round button that advances gold by your expected income."},
            {"v1.99.14  ·  2026-06-23","UI text trimmed for clarity. No feature change."},
            {"v1.99.13  ·  2026-06-23","Minor reliability fix and SETUP polish. No feature change."},
            {"v1.99.1  ·  2026-06-22","Update-check reliability fix. The in-app updater identifies the latest version more robustly; it still downloads the same single latest build. No other change."},
            {"v1.99  ·  2026-06-22","One-time privacy and permissions notice on first launch. It plainly states what each permission is for and confirms there are no accounts, no analytics and no tracking, and that nothing is ever sent anywhere. Re-readable any time from SETUP. Disclosure only; no data collection was added."},
            {"v1.98  ·  2026-06-22","Passive device-integrity heads-up on SETUP: on a rooted device or an emulator it suggests installing only from the official GitHub releases. Informational only — nothing is blocked and nothing is reported; a normal phone shows nothing."},
            {"v1.97  ·  2026-06-22","Release build hardened and slimmed down. No behavior change."},
            {"v1.96  ·  2026-06-22","COACH now includes a roll check: your real chance of hitting your recommended carry if you roll at your current gold and level, with a clear ROLL / bank / HOLD call."},
            {"v1.94 - v1.95  ·  2026-06-22","NEW POSITION tab (under GUIDE). After a scan it sorts your board into a front / back / flank placement map, tells you which corner to hide your carry in and to switch corners each round, and lists the positioning fundamentals that win close rounds. Covers the full Set 17 roster."},
            {"v1.90 - v1.93  ·  2026-06-21","NEW COACH tab (under GUIDE): after a scan it recommends a comp built around your strongest carry, that carry's best items and a one-line plan, plus a NEXT MOVE econ and tempo call from your gold, level and stage. Plus reliability and launch-screen polish."},
            {"v1.86 - v1.89  ·  2026-06-21","NEW automatic set updates: a new TFT set no longer needs a new app build. The current set's champion list and pool sizes are fetched from the project's GitHub on launch and cached on your phone, with the built-in data as an offline fallback. Plus launch-screen fixes."},
            {"v1.74 - v1.85  ·  2026-06-17","NEW BUILDS tab: tap any champion to see the items that are meta on them right now, the comp they carry and a one-line tip. Real itemizers are flagged so you know who to slam items on, and each champion shows its unit tier. Plus scanning accuracy improvements."},
            {"v1.64 - v1.73  ·  2026-06-16","NEW one-pass board read and one-tap auto-update. Reads your whole board quickly without tapping every unit, adds an optional in-game HUD that keeps your gold, income and gold-to-next-level visible, and lets the app update itself from the project's GitHub — the only feature that uses the network."},
            {"v1.60 - v1.63  ·  2026-06-12","NEW auto-buy (THE HUNT): mark up to 5 champions you're chasing and the overlay buys them from the shop the moment they appear, so you can reroll freely and never miss your unit. A large on-screen STOP button controls it. Plus the in-game HUD."},
            {"v1.58 - v1.59  ·  2026-06-11","Redesigned around automatic scanning, plus a big feature update: a roll-down forecast on ODDS (your chance to find 1, 2 or 3 copies if you roll now), one-press reading of level, XP, gold and stage, per-opponent board memory, live trait synergies, a Realm of Gods tracker, remembered augments with tiers and a one-tap new-game reset. Economy math corrected to match real TFT."},
            {"v1.44 - v1.57  ·  2026-06-11","Game data refreshed for the current TFT patch, plus a long run of scanning reliability and visual polish: scanning is faster and more accurate across devices and screen shapes, with on-screen setup helpers and clearer status."},
            {"v1.31 - v1.43  ·  2026-06-09","Automatic scanning made faster and more reliable, with many device-specific fixes and smoother overlay animations."},
            {"v1.9 - v1.30  ·  2026-06-07","Automatic board and opponent scanning introduced and steadily refined — fewer taps, better accuracy, and on-screen calibration tools."},
            {"v1.6 - v1.8  ·  2026-06-03","Opponent board scanning, wider scan coverage, tighter name matching, and the occult app theme."},
            {"v1.3 - v1.5  ·  2026-06-02","Silent on-device scanning after a one-time setup, plus board, opponent and bench scan modes."},
            {"v1.1 - v1.2  ·  2026-05-31","Settings (transparency, haptics, start tab), an economy tab (interest, streak, expected income), the item builder, augment tiers, and the dark launch screen."},
            {"v1.0  ·  2026-05-30","First release: champion pool tracking with contest badges, a draggable overlay, level memory and recent champions."},
        };
        for(String[] entry : cl){
            // highlight feature releases (gold border + badge); bugfixes stay plain
            String ver = entry[0].split(" ")[0];
            boolean feature = entry[1].toUpperCase().startsWith("NEW") || FEATURE_VERS.contains(ver);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(CARD, feature ? GOLD : EDGE, 12, feature ? 2 : 1));
            card.setPadding(18, 16, 18, 16);
            LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-1,-2);
            cl2.setMargins(0, 0, 0, 8);
            card.setLayoutParams(cl2);
            if(feature){
                TextView badge = new TextView(this); badge.setText("✦ FEATURE");
                badge.setTextColor(GOLD); badge.setTextSize(9); badge.setTypeface(null, Typeface.BOLD);
                badge.setLetterSpacing(0.14f); card.addView(badge);
            }
            TextView entryVer = new TextView(this); entryVer.setText(entry[0]);
            entryVer.setTextColor(feature ? GOLD : BONE); entryVer.setTextSize(12); entryVer.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1,-2);
            vl.setMargins(0, feature ? 3 : 0, 0, 0); entryVer.setLayoutParams(vl);
            TextView entryDesc = new TextView(this); entryDesc.setText(entry[1]);
            entryDesc.setTextColor(ASH); entryDesc.setTextSize(11); entryDesc.setLineSpacing(5,1f);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1,-2);
            dl.setMargins(0, 5, 0, 0);
            entryDesc.setLayoutParams(dl);
            card.addView(entryVer); card.addView(entryDesc);
            contentArea.addView(card);
        }

        contentArea.addView(divider(20, 6));
        TextView footer = new TextView(this);
        footer.setText("@xanfiend");
        footer.setTextColor(DIM); footer.setTextSize(11); footer.setGravity(Gravity.CENTER);
        contentArea.addView(footer);
    }

    private TextView btn(String txt, View.OnClickListener l){
        TextView b = new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BONE); b.setTextSize(15); b.setPadding(0,20,0,20);
        b.setBackground(shape(CARD, EDGE, 12, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); pressFeedback(b); return b;
    }

    private TextView btnPrimary(String txt, View.OnClickListener l){
        TextView b = new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BONE); b.setTextSize(18); b.setPadding(0,28,0,28);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(shape(BLOOD, BLOODL, 12, 2));
        b.setShadowLayer(16,0,0,BLOODL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); pressFeedback(b); return b;
    }

    private TextView btnDestructive(String txt, View.OnClickListener l){
        TextView b = new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BLOODL); b.setTextSize(14); b.setPadding(0,18,0,18);
        b.setBackground(shape(CARD, BLOOD, 12, 1));
        b.setShadowLayer(8,0,0,BLOODL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); pressFeedback(b); return b;
    }

    // subtle vertical gradient (lighter top, darker bottom) with a brightened
    // pressed state, matching the overlay's button styling
    private Drawable shape(int fill, int stroke, int radius, int strokeW){
        GradientDrawable normal=gradFill(fill,stroke,radius,strokeW);
        GradientDrawable pressed=gradFill(shade(fill,1.35f),stroke,radius,strokeW);
        StateListDrawable sl=new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sl.addState(new int[]{}, normal);
        return sl;
    }
    private GradientDrawable gradFill(int fill, int stroke, int radius, int strokeW){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{shade(fill,1.18f), shade(fill,0.85f)});
        g.setCornerRadius(radius); g.setStroke(strokeW, stroke);
        return g;
    }
    private static int shade(int c, float f){
        int a=(c>>>24)&0xFF, r=(c>>16)&0xFF, gC=(c>>8)&0xFF, b=c&0xFF;
        r=Math.min(255,Math.round(r*f)); gC=Math.min(255,Math.round(gC*f)); b=Math.min(255,Math.round(b*f));
        return (a<<24)|(r<<16)|(gC<<8)|b;
    }

    private View divider(int top, int bot){
        View d = pentRow(0x66C1121F, 7, 4.5f);   // muted blood — drawn, can't tofu
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, d.getLayoutParams().height);
        dl.setMargins(0, top, 0, bot);
        d.setLayoutParams(dl);
        return d;
    }

    // A horizontal row of `count` inverted pentagrams (each ringed, Sigil-of-Baphomet
    // style) drawn on a Canvas in `color`. Drawn vectors instead of unicode glyphs, so
    // the occult decoration looks the same on every device regardless of its fonts.
    private View pentRow(final int color, final int count, final float radiusDp){
        final float density = getResources().getDisplayMetrics().density;
        final float r = radiusDp * density;
        final int h = Math.round(r * 2 + 10 * density);
        View v = new View(this){
            @Override protected void onDraw(android.graphics.Canvas c){
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, r * 0.07f));
                p.setColor(color);
                float cy = getHeight() / 2f;
                for(int i = 0; i < count; i++){
                    drawPentagram(c, p, getWidth() * (i + 0.5f) / count, cy, r, true);
                }
            }
        };
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, h));
        return v;
    }

    // Inverted pentagram (one point down); `circle` adds the enclosing ring (pentacle).
    private static void drawPentagram(android.graphics.Canvas c, android.graphics.Paint p,
                                      float cx, float cy, float r, boolean circle){
        float[] x = new float[5], y = new float[5];
        for(int k = 0; k < 5; k++){
            double a = Math.toRadians(90) + k * 2 * Math.PI / 5;  // 90° start = bottom vertex
            x[k] = cx + r * (float)Math.cos(a);
            y[k] = cy + r * (float)Math.sin(a);
        }
        android.graphics.Path path = new android.graphics.Path();
        int[] order = {0, 2, 4, 1, 3};   // {5/2} star: skip one vertex each step
        path.moveTo(x[order[0]], y[order[0]]);
        for(int k = 1; k < 5; k++) path.lineTo(x[order[k]], y[order[k]]);
        path.close();
        c.drawPath(path, p);
        if(circle) c.drawCircle(cx, cy, r, p);
    }

    // Big blood-red pentacle for the launch-screen hero — inverted pentagram in a
    // circle, drawn so it's always our color (the ⛧ glyph renders as a color emoji
    // on some fonts), with a slow pulsing glow (GlowPentacleView).
    private View heroPentacle(){
        final float density = getResources().getDisplayMetrics().density;
        final int size = Math.round(128 * density); // a touch bigger to give the glow room
        View v = new GlowPentacleView(this, size);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        v.setLayoutParams(lp);
        return v;
    }

    private boolean canDraw(){ return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }
    private void toast(String m){ Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    // Slow-drifting glowing embers behind the launch screen content. The frame
    // loop runs only while the view is attached and its window is visible, so it
    // costs nothing once the screen is closed or the app is in the background.
    private static class EmberView extends View {
        private static final int N = 26;
        private final float[] x = new float[N], y = new float[N], r = new float[N],
                              spd = new float[N], phase = new float[N];
        private final int[] clr = new int[N];
        private final android.graphics.Paint p =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final java.util.Random rnd = new java.util.Random();
        private boolean running = false;
        private long lastMs;
        private final Runnable tick = new Runnable(){ public void run(){
            if(!running) return;
            step(); invalidate();
            postDelayed(this, 33);
        }};
        EmberView(android.content.Context c){ super(c); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh){
            if(w>0 && h>0) for(int i=0;i<N;i++) seed(i, true);
        }
        private void seed(int i, boolean anywhere){
            int w=getWidth(), h=getHeight(); if(w==0 || h==0) return;
            x[i]=rnd.nextFloat()*w;
            y[i]=anywhere ? rnd.nextFloat()*h : h+20;
            r[i]=2f+rnd.nextFloat()*5f;
            spd[i]=(0.02f+rnd.nextFloat()*0.05f)*h/1000f; // px per ms, full climb ~20-50s
            phase[i]=rnd.nextFloat()*6.283f;
            int pick=rnd.nextInt(10);
            clr[i]= pick<5 ? BLOODL : pick<8 ? GOLD : PURPL;
        }
        private void step(){
            long now=android.os.SystemClock.uptimeMillis();
            long dt=lastMs==0 ? 33 : Math.min(80, now-lastMs);
            lastMs=now;
            for(int i=0;i<N;i++){
                y[i]-=spd[i]*dt;
                x[i]+=(float)Math.sin(now/1400f+phase[i])*0.35f;
                if(y[i]<-20) seed(i, false);
            }
        }
        @Override protected void onDraw(android.graphics.Canvas c){
            long now=android.os.SystemClock.uptimeMillis();
            for(int i=0;i<N;i++){
                // twinkle: each ember breathes between 45% and 100% of its brightness
                float tw=(float)(0.45+0.275*(1+Math.sin(now/700f+phase[i]*2)));
                p.setColor((clr[i]&0x00FFFFFF)|((int)(60*tw)<<24));
                c.drawCircle(x[i], y[i], r[i]*2.6f, p); // soft outer glow
                p.setColor((clr[i]&0x00FFFFFF)|((int)(150*tw)<<24));
                c.drawCircle(x[i], y[i], r[i], p);
            }
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();
            running=true; lastMs=0; post(tick);
        }
        @Override protected void onDetachedFromWindow(){
            running=false; removeCallbacks(tick);
            super.onDetachedFromWindow();
        }
        @Override protected void onWindowVisibilityChanged(int v){
            super.onWindowVisibilityChanged(v);
            if(v==VISIBLE && !running){ running=true; lastMs=0; post(tick); }
            else if(v!=VISIBLE && running){ running=false; removeCallbacks(tick); }
        }
    }

    // Launch-screen hero pentacle with a slow pulsing blood-red glow. A blurred,
    // thicker stroke breathes behind a crisp pentagram on top. The frame loop runs
    // only while attached and visible (same lifecycle guard as EmberView), so the
    // glow costs nothing once the screen closes or the app backgrounds.
    private static class GlowPentacleView extends View {
        private final int size;
        private final android.graphics.Paint glow =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint line =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private boolean running=false;
        private final Runnable tick=new Runnable(){ public void run(){
            if(!running) return; invalidate(); postDelayed(this, 33);
        }};
        GlowPentacleView(android.content.Context c, int size){
            super(c);
            this.size=size;
            // BlurMaskFilter is unsupported on a hardware-accelerated canvas
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            glow.setStyle(android.graphics.Paint.Style.STROKE);
            glow.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            glow.setColor(BLOODL);
            line.setStyle(android.graphics.Paint.Style.STROKE);
            line.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            line.setStrokeWidth(Math.max(3f, size*0.035f));
            line.setColor(BLOODL);
        }
        @Override protected void onDraw(android.graphics.Canvas c){
            long now=android.os.SystemClock.uptimeMillis();
            float pulse=(float)(0.5+0.5*Math.sin(now/1500.0)); // 0..1, ~3s breath
            float cx=getWidth()/2f, cy=getHeight()/2f;
            float r=Math.min(getWidth(), getHeight())*0.40f;
            // halo: a blurred, thicker stroke whose blur, width and alpha all breathe
            float blur=size*(0.020f+0.035f*pulse);
            glow.setStrokeWidth(size*(0.05f+0.03f*pulse));
            glow.setAlpha((int)(70+150*pulse));
            glow.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(1f, blur),
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            drawPentagram(c, glow, cx, cy, r, true);
            // crisp pentagram on top, brightening slightly at the pulse peak
            line.setAlpha((int)(200+55*pulse));
            drawPentagram(c, line, cx, cy, r, true);
        }
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow(); running=true; post(tick);
        }
        @Override protected void onDetachedFromWindow(){
            running=false; removeCallbacks(tick); super.onDetachedFromWindow();
        }
        @Override protected void onWindowVisibilityChanged(int vis){
            super.onWindowVisibilityChanged(vis);
            if(vis==VISIBLE && !running){ running=true; post(tick); }
            else if(vis!=VISIBLE && running){ running=false; removeCallbacks(tick); }
        }
    }
}
