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
        ver.setText("v1.8");
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
    }

    private void buildSetup(){
        boolean granted = canDraw();

        // permission status card
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(shape(CARD, granted ? GREEN : EDGE, 12, granted ? 2 : 1));
        statusCard.setPadding(20, 16, 20, 16);
        LinearLayout.LayoutParams scl = new LinearLayout.LayoutParams(-1,-2);
        scl.setMargins(0, 0, 0, 20);
        statusCard.setLayoutParams(scl);
        TextView statusLbl = new TextView(this);
        statusLbl.setText(granted ? "✓ Overlay permission granted" : "✗ Overlay permission not granted");
        statusLbl.setTextColor(granted ? GREEN : ASH); statusLbl.setTextSize(13);
        statusLbl.setTypeface(null, Typeface.BOLD); statusCard.addView(statusLbl);
        if(!granted){
            TextView statusHint = new TextView(this);
            statusHint.setText("Tap Grant below, then come back and tap Start.");
            statusHint.setTextColor(ASH); statusHint.setTextSize(11);
            LinearLayout.LayoutParams shl = new LinearLayout.LayoutParams(-1,-2);
            shl.setMargins(0, 4, 0, 0);
            statusHint.setLayoutParams(shl);
            statusCard.addView(statusHint);
        }
        contentArea.addView(statusCard);

        // main action
        contentArea.addView(btnPrimary("⛧  Start Overlay", new View.OnClickListener(){
            public void onClick(View v){
                if(!canDraw()){ toast("Grant overlay permission first"); return; }
                startService(new Intent(MainActivity.this, OverlayService.class));
                toast("Overlay started — switch to TFT.");
                moveTaskToBack(true);
            }
        }));

        if(!granted){
            contentArea.addView(btn("Grant overlay permission", new View.OnClickListener(){
                public void onClick(View v){
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+getPackageName())));
                }
            }));
        }

        contentArea.addView(btnDestructive("☠  Stop overlay + reset pool", new View.OnClickListener(){
            public void onClick(View v){
                stopService(new Intent(MainActivity.this, OverlayService.class));
                new Pool(MainActivity.this).reset();
                toast("Stopped and pool cleared");
            }
        }));

        contentArea.addView(divider(22, 16));

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

    private void buildChangelog(){
        String[][] cl={
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
        b.setOnClickListener(l); return b;
    }

    private TextView btnPrimary(String txt, View.OnClickListener l){
        TextView b = new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BONE); b.setTextSize(18); b.setPadding(0,28,0,28);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(shape(BLOOD, BLOODL, 12, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }

    private TextView btnDestructive(String txt, View.OnClickListener l){
        TextView b = new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BLOODL); b.setTextSize(14); b.setPadding(0,18,0,18);
        b.setBackground(shape(CARD, BLOOD, 12, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
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
