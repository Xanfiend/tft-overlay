package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

    private int activeTab = 0;
    private LinearLayout contentArea;
    private final TextView[] tabViews = new TextView[2];

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);

        // root frame: pattern layer behind scroll
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(VOID);

        // occult background pattern
        TextView pattern = new TextView(this);
        pattern.setText(buildPattern());
        pattern.setTextColor(0x18C1121F);
        pattern.setTextSize(15);
        pattern.setLetterSpacing(0.04f);
        pattern.setLineSpacing(6, 1f);
        pattern.setPadding(8, 0, 8, 0);
        FrameLayout.LayoutParams patLp = new FrameLayout.LayoutParams(-1, -1);
        frame.addView(pattern, patLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0x00000000);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 80, 48, 72);
        root.setBackgroundColor(0x00000000);

        // hero sigil
        TextView sigil = new TextView(this);
        sigil.setText("⛧");
        sigil.setTextColor(BLOODL);
        sigil.setTextSize(80);
        sigil.setGravity(Gravity.CENTER);
        root.addView(sigil);
        pulseGlow(sigil);

        // glow ring of symbols under sigil
        TextView sigilRing = new TextView(this);
        sigilRing.setText("☽  ✡  ⛤  ♄  ✡  ☾");
        sigilRing.setTextColor(PURPL);
        sigilRing.setTextSize(13);
        sigilRing.setGravity(Gravity.CENTER);
        sigilRing.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams ringLp = new LinearLayout.LayoutParams(-1,-2);
        ringLp.setMargins(0, -4, 0, 0);
        sigilRing.setLayoutParams(ringLp);
        root.addView(sigilRing);

        TextView title = new TextView(this);
        title.setText("TFT SCRYER");
        title.setTextColor(BLOODL); title.setTextSize(30); title.setGravity(Gravity.CENTER);
        title.setTypeface(null, Typeface.BOLD); title.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1,-2);
        titleLp.setMargins(0, 8, 0, 0);
        title.setLayoutParams(titleLp);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Set 17  ·  champion pool tracker");
        sub.setTextColor(GOLD); sub.setTextSize(13); sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subl = new LinearLayout.LayoutParams(-1,-2);
        subl.setMargins(0, 5, 0, 2);
        sub.setLayoutParams(subl);
        root.addView(sub);

        TextView ver = new TextView(this);
        ver.setText("v1.45");
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
    }

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
    private void pulseGlow(final TextView v){
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
        boolean granted = canDraw();
        boolean accEnabled = isAccessibilityEnabled();

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
                : "Android resets this after every update. Takes about 30 seconds to restore.",
            accEnabled ? null : new View.OnClickListener(){ public void onClick(View v){
                if(Build.VERSION.SDK_INT >= 33){
                    try{
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:"+getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i);
                        toast("Allow restricted settings, then go to Accessibility");
                    }catch(Exception e){ openAccessibility(); }
                } else { openAccessibility(); }
            }},
            accEnabled ? null : (Build.VERSION.SDK_INT >= 33 ? "Step 1: App settings" : "Enable Accessibility"));

        if(!accEnabled && Build.VERSION.SDK_INT >= 33){
            contentArea.addView(btn("Step 2: Accessibility settings", new View.OnClickListener(){
                public void onClick(View v){ openAccessibility(); }
            }));
        }

        contentArea.addView(divider(8, 12));

        // main action
        contentArea.addView(btnPrimary("⛧  Start Overlay", new View.OnClickListener(){
            public void onClick(View v){
                if(!canDraw()){ toast("Grant overlay permission first"); return; }
                startService(new Intent(MainActivity.this, OverlayService.class));
                toast("Overlay started — switch to TFT.");
                moveTaskToBack(true);
            }
        }));

        contentArea.addView(btnDestructive("☠  Stop overlay + reset pool", new View.OnClickListener(){
            public void onClick(View v){
                stopService(new Intent(MainActivity.this, OverlayService.class));
                new Pool(MainActivity.this).reset();
                toast("Stopped and pool cleared");
            }
        }));

        contentArea.addView(divider(14, 16));

        // tips section header
        TextView tipsHdr = new TextView(this);
        tipsHdr.setText("⛤  HOW TO USE");
        tipsHdr.setTextColor(GOLD); tipsHdr.setTextSize(11);
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
            {"v1.45  ·  2026-06-10","Fix: probe dots were bunching up at the front of the board after calibration. The cause was tap order during TAP TO CALIBRATE. The guide asks for the back row first, but if you tapped the front row first instead, the stored back and front positions ended up swapped, and the perspective spacing then ran backwards, squeezing the rows together at the front. Calibration now detects a swapped tap order and corrects it automatically when saving, so either order works. It also repairs an already saved swapped calibration on the fly, so you do not need to recalibrate, your existing calibration will lay out correctly right away. The same correction applies to the opponent board grid used by Auto Opp Scan. Note: in the screenshot you shared, the banner read GRID FALLBACK with no health bars detected, which means Smart Scan could not find your unit health bars on that frame and used the calibrated grid instead. If that keeps happening, open the scan log in SETUP, it records the exact colour values of every bar candidate, and sharing that log will let the detection be tuned for your screen."},
            {"v1.44  ·  2026-06-10","Game data updated for TFT patch 17.5, which went live on June 9. The augment guide now reflects the big 17.5 augment pass: econ augments like Risky Moves, Save This Account, Upward Mobility and Slam and Plus were nerfed, while combat augments like Buried Treasures (back to 5 rounds), Climb the Ladder, Early Learnings, Heart of the Swarm, Little Buddies and Earth were buffed. Comp priorities updated to match: Vex Fast 9 is back on top after her spell damage rebuff, Stargazer Xayah dropped after the Serpent poison bugfix nerf, Space Groove Ornn was nerfed at every Groove breakpoint, and 5-Meeple Rammus lost its innate bonuses while individual Meeple champions got buffed, opening up 7-Meeple boards. Champion roster and pool sizes are unchanged, so your tracking data carries over. Note: the Pengu's Party event is live in game through patch 17.6."},
            {"v1.43  ·  2026-06-10","Visual polish pass across the app and overlay. The floating sigil now bounces in when the overlay starts, gives a quick press animation on every tap, and the panel now fades and scales in when it opens instead of snapping into view. Switching tabs in the overlay cross-fades the new content in, and tab buttons plus the close button give the same tactile press feedback. On the launch screen, the hero sigil has a slow ambient glow pulse, tab content (Setup / Changelog) fades in smoothly when switching, and every button gives a soft press animation."},
            {"v1.42  ·  2026-06-10","Auto Scan rebuilt to be as reliable and as fast as the platform allows. Health bar detection now uses colour ratios instead of fixed colour values, so it works the same whether your screen renders bright or dim, and it runs in two passes: a standard pass first, then a stricter one only if the first picks up too much. Every candidate bar must also pass shape checks (a thin floating strip with clear space above and below it), which rules out grass and trees that happen to be green. New: Instant Visual ID. Each unit read by popup teaches the app what that champion looks like standing on your board. On later scans, learned units are recognized straight from the first screenshot with no tapping at all, so the scan gets faster every game you play. Only sure matches skip the tap; anything uncertain is tapped and read as usual, and a clear unit list shows which were read by popup and which were recognized visually. If a smart position taps empty ground, the scan retries slightly lower once before counting it a miss. And if the smart positions turn out to be wrong for your screen, the scan switches to the calibrated grid mid run instead of wasting the remaining taps. The SHOW DOTS banner now also says which colour pass found your units, and the scan log shows total time and how each unit was identified. Toggle Instant Visual ID in SETUP."},
            {"v1.41  ·  2026-06-09","Speed and comfort pass across the whole app. Tap anywhere outside the panel to close it, no need to reach for the X or DONE button. The floating button now glides to the nearest screen edge after you drag it, so it never settles over the middle of the board, and it pulls itself back on screen if dropped half off the edge. A finished Auto Scan now gives a distinct double vibration so you can feel it complete without watching the screen. Under the hood: champion cost lookups during panel rendering were doing a full name-list sweep per lookup, tens of thousands of string comparisons every time the ODDS tab opened on a tracked board — now a cached single-step lookup, so tab switching is snappier on slower phones. The OCR champion matcher also stops re-normalizing all sixty champion names for every piece of text it reads, which trims time off every scan, and settings writes are batched."},
            {"v1.40  ·  2026-06-09","Fix: Smart Scan was detecting a false unit on an empty board and missing real units when champions were present. The health bar colour filter was too loose and was picking up green arena elements like grass and bamboo. The filter now requires a much brighter, more saturated green with very low red, which matches actual TFT unit health bars and rejects background colour. The enemy red bar filter got the same tightening pass. The debug log now also shows the exact RGB colour values of every bar it detects, so if detection ever drifts again you can share the log and we can tune the numbers precisely."},
            {"v1.39  ·  2026-06-09","SHOW DOTS now shows a status banner at the top telling you exactly what the scan sees. If it reads SMART SCAN with a unit count, the markers are real units found by their health bars and calibration does not matter. If it reads GRID FALLBACK, health bar detection found nothing and it fell back to the old calibrated grid (which is the one that can look crowded toward the front), so the banner tells you why. This is a diagnostic aid: take a screenshot of SHOW DOTS over your board and the banner plus marker positions show whether detection is working on your device. If you ever see GRID FALLBACK while units are clearly on your board, that tells us the health bar colours on your screen need tuning, which the banner makes easy to spot and report."},
            {"v1.38  ·  2026-06-09","Smart Scan no longer needs calibration at all. It now searches a fixed region of the screen for unit health bars (the lower-centre for your own board, the upper half for the enemy during combat) instead of relying on your calibrated probe positions. So even with calibration untouched or a little off, it finds and taps the actual units. Calibration is now only used as a fallback grid if health bar detection ever comes up empty, and the SETUP tab says so. Also: tap SHOW DOTS with Smart Scan on and it now takes a real screenshot and draws a marker on every unit it detected, so you can see exactly what the scan sees before you run it, no calibration involved. The fix for probe dots stacking toward the front row only applied to the old grid path, which Smart Scan now bypasses entirely."},
            {"v1.37  ·  2026-06-09","New: Smart Scan. Auto Scan now finds your units by looking for their health bars in the screenshot and taps the exact spot each unit is standing, instead of tapping the calibrated grid dots. This means it lands right on the units even if your calibration is a little off, and it does not waste taps on empty hexes. It works the same for Auto Opp Scan by looking for the enemy red health bars. Calibration still matters as a rough guide for where the board is, but exact dot placement is no longer important. If the health bar detection ever looks wrong, the scan automatically falls back to the old calibrated grid, and you can turn Smart Scan off entirely in the SETUP tab. The in app debug log shows how many units it detected."},
            {"v1.36  ·  2026-06-09","New: Auto Opp Scan. The Grid tab now has an Auto Opp Scan button next to Auto Scan. Tap it during combat and the app automatically taps through the opponent board zone (the mirrored side of the screen, opposite your own board), reads each champion name from the unit popup, and marks them as contested in the ◉ badge column. No manual tapping needed. The old Opp Manual button is still there if you prefer to tap each unit yourself. The scan stops itself after finishing all probes or after 35 seconds, whichever comes first."},
            {"v1.35  ·  2026-06-09","Fix: bench probe row was landing a little too far to the right. The default now shifts the bench 4% left to match where the actual TFT Mobile bench slots sit. You can fine tune it yourself using the new Bench L/R shift slider in the SETUP tab under calibration. Also, Auto Scan now skips empty bench slots by analysing the board screenshot it already takes at the start, the same way it skips empty board hexes. This means it no longer taps through the empty slots at the right end of your bench."},
            {"v1.34  ·  2026-06-09","Speed: Auto Scan no longer taps empty hexes. Because Android caps screenshots at one per second, the slow part of the scan was visiting all of the empty board space. The scan now looks at the one board screenshot it already takes at the start, works out which hexes actually have a unit on them (a champion sprite has a health bar and lots of detail, an empty hex is flat ground), and only taps those. On a normal board that is roughly the number of units you have instead of all twenty eight hexes, so the scan finishes much faster. It is deliberately cautious: if a hex is even a little ambiguous it still taps it, and if the detection looks off it falls back to tapping everything like before, so it should not miss units. If you ever see a unit get skipped, the in app debug log shows what it decided. Your bench is always fully scanned."},
            {"v1.33  ·  2026-06-09","Fix: Auto Scan was missing most units after the v1.31 speed update. Android only lets an accessibility service take one screenshot per second, and the speed update made the scan tap and shoot faster than that, so the system was quietly rejecting most of the screenshots and those units never got read. The scan now spaces its screenshots out to stay under that one-per-second limit, and if a screenshot still gets rejected it waits and retries the same hex instead of skipping it. This means the scan is reliable again. The tradeoff is that the screenshot limit sets a hard floor of about one second per unit, so a full board takes a bit longer, but it actually reads every unit now."},
            {"v1.32  ·  2026-06-08","Auto Scan now also reads your gold and level. Right before it starts tapping hexes, it takes one extra screenshot of the board and reads the gold count and level number from the corners, the same way the regular Scan Now does. Those numbers get saved straight into your gold tracker and level so you do not have to enter them by hand. They also show up at the top of the auto scan results once the scan finishes."},
            {"v1.31  ·  2026-06-07","Speed: Auto Scan now runs noticeably faster. The board scan taps every hex, waits for the unit popup, takes a screenshot, and reads the name, dozens of times in a row, so every little delay adds up. Trimmed the per-tap timing (shorter tap, shorter wait for the popup to appear, shorter gap between taps), shrank the screenshot down before reading text from it so the recognizer has fewer pixels to chew through, and cut out repeated text cleanup work that was running on every name check. The champion name reading stays just as accurate, it just gets there quicker. Same goes for the visual board matching, which now reads pixels in one batch instead of one at a time."},
            {"v1.30  ·  2026-06-07","Fix: the probe dots really were landing on the right spots after all, the dots themselves were just drawn too big. The board is shown at an angle, so the back rows sit closer together on screen than the front rows. The dots had a fixed size that did not shrink for the back rows, so next to each other they overlapped into what looked like a tangled mesh, even though their centers were correctly placed in a clean grid. Dots now shrink to fit the gap between them, so the back rows look as clean as the front rows. This was the real cause of the crisscross look reported after v1.28 and v1.29, not the placement math, which is why changing that math did not help."},
            {"v1.29  ·  2026-06-07","Fix: the v1.28 stagger correction made probe dots worse, not better, on TFT Mobile. It computed a sideways shift from real measured PC board coordinates and applied it to alternating rows, but on phones that shift came out too large and turned the dots into a dense crisscross mesh instead of clean rows. Removed the stagger correction and went back to the plain smooth interpolation between the four measured corners from the 5-step calibration, which lined up much better. The 5-step calibration guide stays (it measures the front-left corner directly instead of guessing it), only the math that places the dots between the corners changed."},
            {"v1.28  ·  2026-06-07","Fix: probe dots were still landing slightly off to the side on alternating rows. Researched real measured TFT board coordinates and found the board uses a staggered hex grid (pointy-top hexes), where every other row is shifted sideways by about half a hex width, on top of the front-to-back perspective. The old calibration also guessed the front-left corner using left-right symmetry, which does not hold on a staggered board. Calibration is now a 5-step guide that taps all four board corners directly (back-left, back-right, front-left, front-right, then bench), and the probe grid now works out the sideways stagger from those four points and applies it to each row. Re-run TAP TO CALIBRATE in SETUP after updating."},
            {"v1.27  ·  2026-06-06","Fix: probe dots still did not sit on the hexes. Two causes. First, the grid generated 5 rows but a standard board has 4, so an extra row floated off the board and every row drifted. Second, the rows were spaced evenly, but the TFT board is drawn in perspective: the back rows are compressed and the front rows are spread apart, so even spacing dropped the middle dots into the gaps between hexes. The grid now lays exactly 4 rows using perspective spacing (gaps grow toward the front) and interpolates the trapezoid edges with the same curve. Calibration now stores the front row directly, so re-run TAP TO CALIBRATE in SETUP after updating for the tightest fit."},
            {"v1.26  ·  2026-06-06","UI redesign: 5 tabs instead of 6. GRID renamed to POOL. BOARD renamed to ODDS. AUGS and ITEMS merged into a single GUIDE tab with sub-tabs (tap AUGMENTS or ITEMS at the top). SETTINGS renamed to SETUP and now shows Accessibility permission status as the first thing, with a clear card showing if it is on or off and step-by-step instructions if not. Scan buttons in the POOL tab are now side by side. ODDS tab has a clearer empty state with step-by-step instructions. GOLD tab shows interest and streak bonus scale inline. Calibration guide text corrected to say 4-step (was 3-step)."},
            {"v1.25  ·  2026-06-06","Fix: probe dots were not forming a rectangle over the board because the TFT board is trapezoidal in screen space (front row wider than back row). Calibration now asks for 4 points: tap the back-left unit, back-right unit, front-right unit, then bench. The probe grid now interpolates left and right edges per row so dots land on hexes from top to bottom. The front-left corner is inferred from board symmetry so you only need 3 board taps."},
            {"v1.24  ·  2026-06-06","Fix: tap-to-calibrate was offset to the right. The capture overlay used raw touch coordinates against full-screen metrics, but in landscape the status bar / notch inset shifted everything sideways. The capture overlay now uses the exact same full-screen window and coordinate space as the probe dots, so a tap lands precisely where the dot is drawn. Debug aid added: each tap now shows a green crosshair where it registered, plus a live readout of the tap pixel and percent values and the view size, so any future offset is visible on screen."},
            {"v1.23  ·  2026-06-06","Setup screen now shows both permission statuses with one-tap fix buttons. Overlay permission and Accessibility service each get a green or red card. Android resets the Accessibility service on every update (unavoidable) so the card shows a note and direct buttons to restore it in about 30 seconds. On Android 13+ both steps are shown side by side. Grant overlay permission button removed from the middle of the screen and moved into the status card."},
            {"v1.22  ·  2026-06-06","Fix: tap-to-calibrate was placing probe dots between board rows instead of on them. The column probe positions were calculated from hex centers instead of board edges (shifted all 7 columns inward). The row step was dividing by 4 instead of 3, placing rows 1 and 2 between actual board rows instead of on them. Both fixed: probe dots now land on hex centers after tap calibration."},
            {"v1.21  ·  2026-06-06","Tap to calibrate: instead of adjusting sliders blind, tap 3 actual units in TFT to set all 5 calibration values at once. Tap TAP TO CALIBRATE in Settings, then tap your top-left board unit, bottom-right board unit, and any bench unit. The probe dots appear immediately after so you can confirm the positions are correct. Sliders still available for fine-tuning. Rotation during calibration cancels safely."},
            {"v1.20  ·  2026-06-06","Fix: probe dots (and Auto Scan) were completely outside the board in portrait mode. The app now uses separate scan coordinates for portrait vs landscape — in portrait the board starts around 22% of screen height, not 39%. Calibrate Scan in Settings now shows (PORTRAIT) or (LANDSCAPE) and saves values separately for each orientation. Defaults: portrait top 22%, bottom 65%, left 12%, right 88%, bench 75%."},
            {"v1.19  ·  2026-06-05","Fix: Auto Scan was missing units in Tocker's Trials and on boards where units sit lower on screen. The scan now uses 5 probe rows instead of 4, extending coverage down to 72% of screen height (was 65%). The early-stop threshold for consecutive board misses is raised from 5 to 8, so one fully empty probe row no longer aborts the scan before reaching the units below it. Default Board bottom calibration value updated from 65% to 72%."},
            {"v1.18  ·  2026-06-05","Fix: default scan probe grid was too narrow. Board left edge moved from 28% to 8% of screen width so the scan covers the left side of the board (units in cols 0-2 were completely missed). Board right edge moved from 70% to 88%. Front row bottom moved from 60% to 65%. Bench moved from 72% to 80%. Use Settings -> Calibrate Scan -> SHOW DOTS to fine-tune for your device."},
            {"v1.17  ·  2026-06-05","Calibrate Scan (Settings tab): nudge board top, bottom, left, right edge, and bench row by 1% at a time until the probe dots land on the correct hexes. Tap SHOW DOTS to see all scan positions drawn over TFT in red (board) and blue (bench) — numbered in scan order, fades after 5 seconds. Values persist across restarts. RESET restores defaults. Navigation: tabs reordered to Grid, Econ, Board, Augs, Items, Settings and labelled with words instead of symbols. Tap targets are taller. My Board manual scan button removed from Grid tab (Auto Scan covers it). Opp Board stays as a single full-width button."},
            {"v1.16  ·  2026-06-05","Fix: Auto Scan was detecting Leona (and other 5-letter champion names) when they were not on the board. The partial OCR match rule that allowed a 4-letter fragment like 'leon' to match 'Leona' is now restricted to champion names that are 6 or more letters long. Short names now require a full match. Debug: when the scan sees a popup but does not find a champion, it now logs what text was actually seen, making it easier to diagnose future missed detections."},
            {"v1.15  ·  2026-06-05","Fix: Auto Scan was tapping outside the board area — hitting the Buy XP button, Refresh button, and trait panel. Board and bench probe coordinates are now accurate to confirmed TFT Mobile hex positions. Board x range narrowed to 28-70% of screen width to clear the trait panel and health bar. Bench moved from 80% to 72% screen height, away from shop buttons. All four board rows now use exact y positions instead of an estimate that placed probes in empty air."},
            {"v1.14  ·  2026-06-05","Fix: scan was tapping into the augment panel at the top of the screen. Board probe area raised to start lower on screen, away from the augment and HUD zone. Fix: when the board has no units, all 28 board probes were scanned before reaching the bench because the early-stop only fired after the first hit. Now any 5 consecutive board misses skip directly to the bench scan instead of stopping the scan entirely."},
            {"v1.13  ·  2026-06-05","Scan speed and accuracy pass. The text reader is now built once and reused instead of being rebuilt for every hex. Each screenshot is cropped to the unit popup band before reading, so there are fewer pixels to process and no shop, bench, or trait text to misread. Per-hex logging trimmed so the scan spends its time scanning. Tap gesture and the gap between taps shortened. Detection zone is unchanged so accuracy holds, while a full board scan finishes noticeably faster."},
            {"v1.12  ·  2026-06-05","Fix: Auto Scan was stopping early after tapping on an item instead of a champion. The scan now distinguishes empty hexes (no popup at all) from non-champion popups (item descriptions, ability text). Only truly empty hexes count toward the miss streak that stops the scan. Item taps are skipped without penalty. Bench probe moved lower on screen to avoid the item bench row in TFT Mobile."},
            {"v1.11  ·  2026-06-05","Fix: Auto Scan was detecting champions that were not on the board. Template-first pass removed (false positive source). Popup wait restored to 350ms."},
            {"v1.10  ·  2026-06-05","Auto Scan Board speed improvements. Popup wait reduced to 250ms, gap between probes to 50ms. Template bitmaps only created when a champion is detected. Champion list cached across OCR calls."},
            {"v1.9  ·  2026-06-05","Auto Scan Board now taps every hex automatically. Covers all 28 board positions plus 9 bench slots. Scans front row first so it finds your units sooner. Stops after 5 consecutive empty board hexes, then sweeps the bench and stops after 3 empty bench slots. Typical scan time is 8-15 seconds. Fix: accessibility service no longer shows as malfunctioning on Samsung/OnePlus/Xiaomi. Fix: Scan Now was scanning the overlay panel instead of TFT. Fix: popup OCR now ignores the trait sidebar."},
            {"v1.8  ·  2026-06-03","Auto Scan Board now uses template matching instead of OCR. When you scan your board or an opponent's board, the app saves a portrait crop of each champion it detects. After enough templates are captured, Auto Scan compares hex crops against those portraits and identifies units without you tapping each one. Template count shown on the Auto Scan button. Templates survive app restarts and can be cleared from Settings."},
            {"v1.7  ·  2026-06-02","Fix: debug scan now closes the overlay before taking the screenshot so it scans TFT instead of the app itself. After the scan completes the Settings tab reopens automatically with the log. Fix: overlay now hides automatically when you leave TFT and reappears when you come back. Requires Accessibility service."},
            {"v1.6  ·  2026-06-02","Bug fixes: board scan no longer detects ghost champions (Lissandra false positive on Redmi and similar devices). Scan zone now covers the full screen width so the popup is found whether it appears on the left or right side. Fuzzy name matching tightened to reject short OCR fragments. Overlay permission status now refreshes when you return from Android Settings. Morgana moved to 4-cost pool (was wrong since patch 17.3). Occult theme for the app UI."},
            {"v1.5  ·  2026-06-02","Opponent board scan: tap Opp Board in the grid tab, then tap each unit on the opponent's board. Auto-reads champion name and star level from the stat popup. Results shown with star counts (Jinx ★★, TwistedFate ★). 30 second window. Contest badges increment automatically."},
            {"v1.4  ·  2026-06-01","Board scan mode: tap My Board in the grid tab, then tap each unit on your board. Auto-reads the champion name from the stat popup and marks it in the pool. 25 second window, vibrates per unit detected. Bench detection: full scan now reads champion names from the bench row."},
            {"v1.3  ·  2026-06-01","Silent scan via Accessibility Service (Android 12+): no app switch, no permission dialog after setup. Shop champion detection via OCR. Portrait mode scan zones. Hold sigil 1.5s to scan. Economy tab scan shortcut."},
            {"v1.2  ·  2026-05-31","Screen scan fixed for Xiaomi/MIUI: scan runs inside the permission Activity. Transparency slider 20-100%. No-flash panel updates. Versioned APK in releases."},
            {"v1.1  ·  2026-05-31","Settings tab (transparency, haptic, start-tab, position reset, Scan Now). Economy tab (interest brackets, streak, expected income, hold-to-repeat gold). Item builder. Augment tiers S/A/B/C. Dark launch screen."},
            {"v1.0  ·  2026-05-30","Grid and board tabs with champion pool tracking and contest badges. Drag to move, drag to X to close. Level memory, recent champions, version footer."},
        };
        for(String[] entry : cl){
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(CARD, EDGE, 12, 1));
            card.setPadding(18, 16, 18, 16);
            LinearLayout.LayoutParams cl2 = new LinearLayout.LayoutParams(-1,-2);
            cl2.setMargins(0, 0, 0, 8);
            card.setLayoutParams(cl2);
            TextView entryVer = new TextView(this); entryVer.setText(entry[0]);
            entryVer.setTextColor(BONE); entryVer.setTextSize(12); entryVer.setTypeface(null, Typeface.BOLD);
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

    private String buildPattern(){
        String[] rows = {
            "⛧  ✡  ☽  ⛤  ☾  ♄  ⛧  ✡  ☽  ⛤  ☾  ♄  ⛧  ✡  ☽  ⛤  ☾  ♄",
            "☿  ⛧  ☠  ✡  ⛤  ☽  ♄  ☾  ⛧  ☿  ✡  ☠  ⛤  ☽  ⛧  ♄  ☾  ⛧",
            "☽  ⛤  ⛧  ☾  ✡  ☿  ⛧  ♄  ☠  ⛧  ☽  ✡  ⛤  ☾  ☿  ⛧  ♄  ☠",
            "✡  ☾  ♄  ⛧  ☿  ⛤  ☠  ☽  ✡  ⛧  ☾  ♄  ⛧  ☿  ⛤  ☠  ☽  ✡",
        };
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < 60; i++){
            sb.append(rows[i % rows.length]).append("\n");
        }
        return sb.toString();
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); pressFeedback(b); return b;
    }

    private TextView btnDestructive(String txt, View.OnClickListener l){
        TextView b = new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BLOODL); b.setTextSize(14); b.setPadding(0,18,0,18);
        b.setBackground(shape(CARD, BLOOD, 12, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); pressFeedback(b); return b;
    }

    private GradientDrawable shape(int fill, int stroke, int radius, int strokeW){
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius); g.setColor(fill); g.setStroke(strokeW, stroke);
        return g;
    }

    private TextView divider(int top, int bot){
        TextView d = new TextView(this);
        d.setText("⛧ · ☽ · ✡ · ☾ · ⛧ · ☽ · ✡");
        d.setTextColor(EDGE); d.setTextSize(9); d.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1,-2);
        dl.setMargins(0, top, 0, bot);
        d.setLayoutParams(dl);
        return d;
    }

    private boolean canDraw(){ return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }
    private void toast(String m){ Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }
}
