package com.xanfiend.tftoverlay;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class OverlayService extends Service {
    private WindowManager wm;
    private View button;
    private View panel;
    private Pool pool;
    private int level = 8; // loaded from pool in onCreate
    private int mode = 0; // 0 = scout grid, 1 = summary
    private Vibrator vib;
    // bump this each release so the footer shows the current version
    private static final String APP_VERSION = "v1.32";
    // item builder: index of selected components (1-9), -1 = none
    private int itemA = -1, itemB = -1;
    // guide tab sub-selection: 0 = augments, 1 = items
    private int guideTab = 0;
    // probe dots overlay: shows all scan tap positions over TFT for calibration
    private View probeDotsView = null;
    private final android.os.Handler probeDotsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final String RELEASES_URL = "https://github.com/Xanfiend/tft-overlay/releases/latest";

    private static final int[][] ODDS = {
        {0,0,0,0,0},{100,0,0,0,0},{100,0,0,0,0},{75,25,0,0,0},
        {55,30,15,0,0},{45,33,20,2,0},{30,40,25,5,0},{19,30,40,10,1},
        {17,24,32,24,3},{15,18,25,30,12},{5,10,20,40,25}
    };
    private static final int VOID=0xF20B0709, BLOOD=0xFF8B1A1A, BLOODL=0xFFC1121F,
        BONE=0xFFE0D5C0, ASH=0xFF7A6B60, CARD=0xFF16100F, EDGE=0xFF3A2024,
        GOLD=0xFFC9A227, GREEN=0xFF5FA046, DIM=0xFF564044;
    private static final int[] COSTC={0,0xFF9AA4B0,0xFF4E9E5A,0xFF3B82C4,0xFFB565D8,0xFFE0A93A};

    // chip references so we can update the count badge in place without rebuilding
    private TextView[] chipViews;
    private String[] chipNames;

    // economy tab: held so refreshEcon() can update without rebuilding the panel
    private TextView econGoldTv, econInterestTv, econBracketTv;
    private TextView[] econLadderTvs;
    private TextView econStreakTv, econBonusTv, econIncomeTv, econBreakTv;

    // hold-to-repeat gold buttons
    private final android.os.Handler goldHandler = new android.os.Handler();
    private Runnable goldRepeat;

    // floating button layout params promoted to field so buildSettings() can update alpha/position
    private WindowManager.LayoutParams btnLp;
    // panel layout params promoted for flash-free in-place refresh
    private WindowManager.LayoutParams panelLp;
    // screen scanning — result delivered from ScanPermActivity via static callbacks
    private String lastScanStatus = "";
    private TextView scanStatusTv;
    private static OverlayService _instance;

    // floating button label — promoted for board scan countdown updates
    private TextView btnLabel;

    // board scan mode: accessibility screenshot polls popup zone while user taps units
    private boolean boardScanMode = false;
    private long boardScanDeadline = 0;
    private java.util.List<String> boardScanResults = new java.util.ArrayList<>();
    private final android.os.Handler boardHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable boardPollRunnable;
    private Runnable boardCountdownRunnable;

    // opponent scan mode: same polling but routes into opponent tracking + records star levels
    private boolean oppScanMode = false;
    private long oppScanDeadline = 0;
    private java.util.Map<String, Integer> oppScanResults = new java.util.LinkedHashMap<>();
    private Runnable oppPollRunnable;
    private Runnable oppCountdownRunnable;

    // debug scan: close panel, scan, reopen settings so user sees results
    private boolean debugScanPending = false;
    // last known screen orientation — updated in showPanel() and used by calGet/calSet
    private boolean isPortrait = false;

    // tap-to-calibrate: multi-step overlay where user taps live board units
    private int calStep = 0; // 0=idle, 1=back-left, 2=back-right, 3=front-left, 4=front-right, 5=bench
    private View calCaptureView = null;
    // recorded taps (screen px) drawn as crosshairs so any capture offset is visible
    private final java.util.List<float[]> calTapMarks = new java.util.ArrayList<>();
    private String calDebugLine = "";
    // temporary storage for steps 1-4 (committed to Pool once all 4 corners are measured)
    private int calTmpTopY = 0, calTmpTopLeft = 0, calTmpTopRight = 0;
    private int calTmpBotY = 0, calTmpBotLeft = 0;

    // auto-tap board scan: dispatches gestures to each hex, OCRs popup — no templates needed
    private boolean autoScanPending = false;
    private java.util.List<String> autoScanResults = new java.util.ArrayList<>();
    private int autoScanGold = -1;
    private int autoScanLevel = -1;
    private int autoTapIndex = 0;
    private int autoTapConsecutiveMisses = 0;
    private int autoTapBoardProbeCount = 0; // index where bench probes start
    private java.util.List<int[]> autoTapProbes = new java.util.ArrayList<>();
    private final android.os.Handler autoTapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // Per-probe timing. These three delays run once per probe (37+ per scan), so they
    // dominate the total scan time. Tuned for speed while leaving the popup enough time
    // to animate in before the screenshot.
    private static final int TAP_STROKE_MS   = 25;  // gesture press duration
    private static final int POPUP_WAIT_MS   = 260; // wait for the unit popup to render after the tap
    private static final int PROBE_GAP_MS    = 12;  // gap before moving to the next probe

    // in-app debug log — last 80 lines, shown in Settings
    private static final java.util.List<String> scanLog = new java.util.ArrayList<>();
    static void addScanLog(String msg){
        android.util.Log.d("TFTScryer", msg);
        synchronized(scanLog){ scanLog.add(msg); if(scanLog.size()>80) scanLog.remove(0); }
    }
    static void clearScanLog(){ synchronized(scanLog){ scanLog.clear(); } }

    // called by TFTAccessibilityService when the foreground app changes
    static void setOverlayVisible(boolean visible){
        OverlayService s=_instance;
        if(s==null) return;
        if(!visible) new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){ public void run(){
            if(s.panel!=null) s.closePanel();
        }});
    }

    static void deliverScanResult(ScreenScanner.ScanResult r){
        OverlayService s=_instance;
        if(s==null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->s.applyScanResult(r));
    }
    static void deliverScanError(String msg){
        OverlayService s=_instance;
        if(s==null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            s.lastScanStatus="✗ "+msg;
            android.widget.Toast.makeText(s,"✗ "+msg,android.widget.Toast.LENGTH_SHORT).show();
            s.mode=4; s.showPanel();
        });
    }

    @Override public void onCreate(){
        super.onCreate();
        _instance=this;
        pool = new Pool(this);
        level = pool.getLevel();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        addButton();
        new Thread(new Runnable(){ public void run(){ ChampionTemplates.load(OverlayService.this); }}).start();
    }
    @Override public int onStartCommand(Intent i, int f, int id){ return START_STICKY; }

    private int wtype(){
        return Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                         : WindowManager.LayoutParams.TYPE_PHONE;
    }
    private GradientDrawable box(int c,int r,int sc,int sw){
        GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(r);
        if(sw>0) g.setStroke(sw,sc); return g;
    }
    private void buzz(){ try{ if(pool.getHaptic() && vib!=null) vib.vibrate(18); }catch(Exception e){} }

    private void addButton(){
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.setBackground(box(0xF20B0709,40,BLOOD,3)); c.setPadding(28,18,28,18);
        // all-seeing sigil over the wordmark
        TextView g=new TextView(this); g.setText("\u29BF"); g.setTextColor(BLOODL); g.setTextSize(22); g.setGravity(Gravity.CENTER);
        btnLabel=new TextView(this); btnLabel.setText("SCRY"); btnLabel.setTextColor(GOLD); btnLabel.setTextSize(8);
        btnLabel.setGravity(Gravity.CENTER); btnLabel.setLetterSpacing(0.25f); btnLabel.setPadding(0,2,0,0);
        c.addView(g); c.addView(btnLabel); button=c;
        button.setAlpha(pool.getAlpha());

        btnLp = new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        btnLp.gravity=Gravity.TOP|Gravity.START; btnLp.x=20; btnLp.y=300;
        button.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty; long down; boolean moved;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){ ix=btnLp.x;iy=btnLp.y;tx=e.getRawX();ty=e.getRawY();down=System.currentTimeMillis();moved=false; return true; }
                else if(a==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-tx),dy=(int)(e.getRawY()-ty);
                    if(Math.abs(dx)>14||Math.abs(dy)>14){ moved=true; showCloseTarget(true); }
                    btnLp.x=ix+dx; btnLp.y=iy+dy; wm.updateViewLayout(button,btnLp);
                    if(moved) highlightClose(e.getRawX(), e.getRawY());
                    return true;
                } else if(a==MotionEvent.ACTION_UP){
                    if(moved && overClose(e.getRawX(), e.getRawY())){
                        showCloseTarget(false);
                        stopSelf();
                        return true;
                    }
                    showCloseTarget(false);
                    if(!moved){
                        if(oppScanMode){ stopOppScanMode(); return true; }
                        if(boardScanMode){ stopBoardScanMode(); return true; }
                        if(autoScanPending){ finishAutoTapScan(); return true; }
                        long held=System.currentTimeMillis()-down;
                        if(held>1500){ triggerScan(); }
                        else {
                            if(held>450) mode=0;
                            else if(pool.getStartTab()==1) mode=0;
                            else mode=pool.isEmpty()?0:1;
                            itemA=-1; itemB=-1; showPanel();
                        }
                    }
                    return true;
                }
                return false;
            }
        });
        wm.addView(button, btnLp);
    }

    // ---- drag-to-close X target ----
    private View closeView;
    private int[] closeCenter = new int[]{-1,-1};
    private void showCloseTarget(boolean show){
        if(show){
            if(closeView!=null) return;
            TextView x=new TextView(this); x.setText("\u2715"); x.setTextColor(BONE); x.setTextSize(26);
            x.setGravity(Gravity.CENTER);
            x.setBackground(box(0xE6B11A22,60,BLOODL,3));
            x.setPadding(34,30,34,30);
            closeView=x;
            WindowManager.LayoutParams clp=new WindowManager.LayoutParams(-2,-2,wtype(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, PixelFormat.TRANSLUCENT);
            clp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; clp.y=120;
            try{ wm.addView(closeView, clp); }catch(Exception ex){}
            // compute its screen center after layout
            closeView.post(new Runnable(){ public void run(){
                if(closeView==null) return;
                int[] loc=new int[2]; closeView.getLocationOnScreen(loc);
                closeCenter[0]=loc[0]+closeView.getWidth()/2;
                closeCenter[1]=loc[1]+closeView.getHeight()/2;
            }});
        } else {
            if(closeView!=null){ try{wm.removeView(closeView);}catch(Exception ex){} closeView=null; closeCenter[0]=-1; closeCenter[1]=-1; }
        }
    }
    private boolean overClose(float rx, float ry){
        if(closeCenter[0]<0) return false;
        double d=Math.hypot(rx-closeCenter[0], ry-closeCenter[1]);
        return d < 140; // generous drop radius
    }
    private void highlightClose(float rx, float ry){
        if(closeView==null) return;
        boolean over=overClose(rx,ry);
        closeView.setBackground(box(over?0xFFE0303A:0xE6B11A22, 60, BONE, over?4:3));
        closeView.setScaleX(over?1.25f:1f); closeView.setScaleY(over?1.25f:1f);
    }

    private void closePanel(){
        if(panel!=null){ try{wm.removeView(panel);}catch(Exception e){} panel=null; panelLp=null; }
        if(goldRepeat!=null){ goldHandler.removeCallbacks(goldRepeat); goldRepeat=null; }
        econGoldTv=null; econInterestTv=null; econBracketTv=null;
        econLadderTvs=null; econStreakTv=null; econBonusTv=null;
        econIncomeTv=null; econBreakTv=null; scanStatusTv=null;
    }

    @SuppressWarnings("deprecation")
    private void showPanel(){
        // detect current orientation for calibration
        android.util.DisplayMetrics oriDm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(oriDm);
        isPortrait = oriDm.heightPixels > oriDm.widthPixels;
        // null per-tab TV refs (they'll be reassigned by the build methods below)
        econGoldTv=null; econInterestTv=null; econBracketTv=null;
        econLadderTvs=null; econStreakTv=null; econBonusTv=null;
        econIncomeTv=null; econBreakTv=null; scanStatusTv=null;
        if(goldRepeat!=null){ goldHandler.removeCallbacks(goldRepeat); goldRepeat=null; }

        LinearLayout root;
        if(panel==null){
            // first open: create the window and add to WindowManager
            ScrollView scroll=new ScrollView(this);
            root=new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(box(VOID,8,BLOOD,2));
            root.setPadding(22,18,22,18);
            scroll.addView(root);
            panel=scroll;
            panelLp=new WindowManager.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels*0.96),
                (int)(getResources().getDisplayMetrics().heightPixels*0.86),
                wtype(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            panelLp.gravity=Gravity.CENTER;
            wm.addView(panel,panelLp);
            panel.setAlpha(pool.getAlpha());
        } else {
            // panel already open: reuse the window, just clear and rebuild content — no flash
            root=(LinearLayout)((ScrollView)panel).getChildAt(0);
            root.removeAllViews();
            panel.setAlpha(pool.getAlpha());
        }

        // header: title + close
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText(mode==4?"\u2699 SETUP":mode==3?"\u00a7 GOLD":mode==2?"\u229e GUIDE":mode==1?"\u2738 ODDS":"\u2738 POOL");
        title.setTextColor(BLOODL); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView close=new TextView(this); close.setText("\u2715"); close.setTextColor(BONE); close.setTextSize(18);
        close.setGravity(Gravity.CENTER); close.setBackground(box(BLOOD,6,BLOODL,2)); close.setPadding(22,14,22,14);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        head.addView(title); head.addView(close);
        root.addView(head);

        // tab row \u2014 ordered by in-game frequency of use
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,10,0,2);
        int[] tabModes={0,1,2,3,4}; // Pool | Odds | Guide | Gold | Setup
        String[] tabNames={"POOL","ODDS","GUIDE","GOLD","\u2699 SETUP"};
        for(int t=0;t<5;t++){
            final int tm=tabModes[t]; boolean on=mode==tm;
            TextView tab=new TextView(this); tab.setText(tabNames[t]); tab.setGravity(Gravity.CENTER);
            tab.setTextColor(on?BONE:ASH); tab.setTextSize(9); tab.setLetterSpacing(0.05f);
            tab.setTypeface(null, on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            tab.setBackground(box(on?BLOOD:CARD,6,on?BLOODL:EDGE,on?2:1)); tab.setPadding(0,15,0,15);
            LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1f); tl.setMargins(2,0,2,0); tab.setLayoutParams(tl);
            tab.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ mode=tm; showPanel(); } });
            tabs.addView(tab);
        }
        root.addView(tabs);

        // occult divider under the header
        TextView div=new TextView(this);
        div.setText("\u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766");
        div.setTextColor(EDGE); div.setTextSize(9); div.setGravity(Gravity.CENTER); div.setPadding(0,8,0,2);
        root.addView(div);

        // level row (4-10) -- only relevant for pool/odds tabs
        if(mode<=1){
        LinearLayout lvl=new LinearLayout(this); lvl.setPadding(0,12,0,12);
        TextView ll=new TextView(this); ll.setText("LVL"); ll.setTextColor(ASH); ll.setTextSize(10); ll.setGravity(Gravity.CENTER); ll.setPadding(0,0,8,0);
        ll.setLayoutParams(new LinearLayout.LayoutParams(-2,-1)); lvl.addView(ll);
        int[] L={4,5,6,7,8,9,10};
        for(int i=0;i<L.length;i++){
            final int lv=L[i];
            TextView b=new TextView(this); b.setText(""+lv); b.setTextSize(13); b.setPadding(0,9,0,9); b.setGravity(Gravity.CENTER);
            boolean on=lv==level;
            b.setBackground(box(on?BLOOD:CARD,5,on?BLOODL:EDGE,on?2:1));
            b.setTextColor(on?BONE:ASH); if(on) b.setTypeface(null, android.graphics.Typeface.BOLD);
            b.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ level=lv; pool.setLevel(lv); showPanel(); } });
            LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,-2,1f); bl.setMargins(2,0,2,0); b.setLayoutParams(bl);
            lvl.addView(b);
        }
        root.addView(lvl);
        }

        if(mode==4) buildSettings(root);
        else if(mode==3) buildEconomy(root);
        else if(mode==2) buildGuide(root);
        else if(mode==1) buildSummary(root);
        else buildGrid(root);

    }

    private double rerollChance(String name){
        int cost=Pool.costOf(name); if(cost==0) return 0;
        int slot=ODDS[level][cost-1]; if(slot==0) return 0;
        int rem=pool.remaining(name); if(rem<=0) return 0;
        int total=0; for(String n:Pool.CHAMPS[cost]) total+=pool.remaining(n);
        // bench-thinning: junk units you hold are out of the pool, so the
        // effective competing total shrinks (but never below your target's own copies).
        total -= pool.getJunk(cost);
        if(total < rem) total = rem;
        if(total<=0) return 0;
        double per=(slot/100.0)*((double)rem/total);
        return 1.0-Math.pow(1.0-per,5);
    }

    // ---- FAST SCOUT GRID: tap = instant +1, long-press chip = -1, live badge ----
    private void buildGrid(LinearLayout root){
        int totalChamps=0; for(int c=1;c<=5;c++) totalChamps+=Pool.CHAMPS[c].length;
        chipViews=new TextView[totalChamps];
        chipNames=new String[totalChamps];
        int idx=0;

        // \u25c7 SCAN section
        boolean accAvail=Build.VERSION.SDK_INT>=31&&TFTAccessibilityService.instance!=null;
        TextView scanSecHdr=new TextView(this); scanSecHdr.setText("\u25c7 SCAN");
        scanSecHdr.setTextColor(GOLD); scanSecHdr.setTextSize(11); scanSecHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        scanSecHdr.setLetterSpacing(0.1f); scanSecHdr.setPadding(2,4,0,6); root.addView(scanSecHdr);

        if(autoScanPending){
            int total=autoTapProbes.size(); int done=autoTapIndex;
            String prog=total>0?(done+"/"+total+" hexes"):"starting...";
            TextView asActive=new TextView(this); asActive.setText("\u29bf Auto Scan: "+prog+" \u00b7 tap sigil to stop");
            asActive.setTextColor(GOLD); asActive.setTextSize(12); asActive.setGravity(Gravity.CENTER);
            asActive.setBackground(box(BLOOD,6,BLOODL,2)); asActive.setPadding(0,12,0,12);
            LinearLayout.LayoutParams asal=new LinearLayout.LayoutParams(-1,-2); asal.setMargins(0,0,0,4); asActive.setLayoutParams(asal);
            root.addView(asActive);
        } else if(oppScanMode){
            long rem=oppScanDeadline-System.currentTimeMillis();
            int remSec=(int)((rem+999)/1000); if(remSec<0) remSec=0;
            TextView osActive=new TextView(this);
            osActive.setText("\u25C9 Opp scan: "+remSec+"s \u00b7 tap to stop");
            osActive.setTextColor(GOLD); osActive.setTextSize(12); osActive.setGravity(Gravity.CENTER);
            osActive.setBackground(box(BLOOD,6,BLOODL,2)); osActive.setPadding(0,12,0,12);
            LinearLayout.LayoutParams osal=new LinearLayout.LayoutParams(-1,-2); osal.setMargins(0,0,0,6); osActive.setLayoutParams(osal);
            osActive.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ stopOppScanMode(); }});
            root.addView(osActive);
        } else {
            // two scan buttons side by side
            LinearLayout scanBtnRow=new LinearLayout(this); scanBtnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams sbrp=new LinearLayout.LayoutParams(-1,-2); sbrp.setMargins(0,0,0,6); scanBtnRow.setLayoutParams(sbrp);

            TextView asBtn=new TextView(this); asBtn.setText(accAvail?"\u29bf Auto Scan Board":"\u29bf Auto Scan");
            asBtn.setTextColor(accAvail?BONE:ASH); asBtn.setTextSize(11); asBtn.setGravity(Gravity.CENTER);
            asBtn.setPadding(8,12,8,12);
            asBtn.setBackground(box(accAvail?CARD:0xFF0D0909,6,accAvail?EDGE:DIM,1));
            LinearLayout.LayoutParams asl=new LinearLayout.LayoutParams(0,-2,1f); asl.setMargins(0,0,3,0); asBtn.setLayoutParams(asl);
            asBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){} return; }
                startAutoTapScan();
            }});

            TextView osBtn=new TextView(this); osBtn.setText(accAvail?"\u25C9 Opp Board":"\u25C9 Opp Board");
            osBtn.setTextColor(accAvail?BONE:ASH); osBtn.setTextSize(11); osBtn.setGravity(Gravity.CENTER);
            osBtn.setPadding(8,12,8,12);
            osBtn.setBackground(box(accAvail?CARD:0xFF0D0909,6,accAvail?EDGE:DIM,1));
            LinearLayout.LayoutParams osl=new LinearLayout.LayoutParams(0,-2,1f); osl.setMargins(3,0,0,0); osBtn.setLayoutParams(osl);
            osBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){} return; }
                startOppScanMode();
            }});

            scanBtnRow.addView(asBtn); scanBtnRow.addView(osBtn);
            root.addView(scanBtnRow);
            if(!accAvail){
                TextView accHint=new TextView(this); accHint.setText("Enable Accessibility in SETUP tab to use scan features");
                accHint.setTextColor(DIM); accHint.setTextSize(10); accHint.setPadding(2,0,2,4); root.addView(accHint);
            }
        }

        // auto scan results
        if(!autoScanResults.isEmpty()){
            TextView asrHdr=new TextView(this); asrHdr.setText("◇ AUTO SCAN");
            asrHdr.setTextColor(GOLD); asrHdr.setTextSize(11); asrHdr.setTypeface(null,android.graphics.Typeface.BOLD);
            asrHdr.setLetterSpacing(0.1f); asrHdr.setPadding(2,4,0,4); root.addView(asrHdr);
            if(autoScanGold>=0||autoScanLevel>=0){
                StringBuilder glSb=new StringBuilder();
                if(autoScanLevel>=0) glSb.append("Lv ").append(autoScanLevel);
                if(autoScanGold>=0){ if(glSb.length()>0) glSb.append("  ·  "); glSb.append(autoScanGold).append("g"); }
                TextView glTv=new TextView(this); glTv.setText(glSb.toString());
                glTv.setTextColor(BONE); glTv.setTextSize(12); glTv.setTypeface(null,android.graphics.Typeface.BOLD);
                glTv.setPadding(2,0,2,4); root.addView(glTv);
            }
            StringBuilder asrSb=new StringBuilder();
            for(String s:autoScanResults){ if(asrSb.length()>0) asrSb.append(" · "); asrSb.append(s); }
            TextView asrTv=new TextView(this); asrTv.setText(asrSb.toString());
            asrTv.setTextColor(BONE); asrTv.setTextSize(12); asrTv.setPadding(2,0,2,4); root.addView(asrTv);
            TextView clearAsr=new TextView(this); clearAsr.setText("clear");
            clearAsr.setTextColor(ASH); clearAsr.setTextSize(10); clearAsr.setPadding(2,0,2,8);
            clearAsr.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ autoScanResults.clear(); showPanel(); }});
            root.addView(clearAsr);
        }

        // opponent scan results
        if(!oppScanResults.isEmpty()){
            TextView osrHdr=new TextView(this); osrHdr.setText("\u25c7 OPP SCAN");
            osrHdr.setTextColor(GOLD); osrHdr.setTextSize(11); osrHdr.setTypeface(null,android.graphics.Typeface.BOLD);
            osrHdr.setLetterSpacing(0.1f); osrHdr.setPadding(2,4,0,4); root.addView(osrHdr);
            StringBuilder osrSb=new StringBuilder();
            for(java.util.Map.Entry<String,Integer> e:oppScanResults.entrySet()){
                if(osrSb.length()>0) osrSb.append(" \u00b7 ");
                osrSb.append(e.getKey());
                int st=e.getValue(); if(st>0){ osrSb.append(" "); for(int si=0;si<st;si++) osrSb.append("\u2605"); }
            }
            TextView osrTv=new TextView(this); osrTv.setText(osrSb.toString());
            osrTv.setTextColor(BONE); osrTv.setTextSize(12); osrTv.setPadding(2,0,2,4); root.addView(osrTv);
            TextView clearOsr=new TextView(this); clearOsr.setText("clear");
            clearOsr.setTextColor(ASH); clearOsr.setTextSize(10); clearOsr.setPadding(2,0,2,8);
            clearOsr.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ oppScanResults.clear(); showPanel(); }});
            root.addView(clearOsr);
        }

        // how-to card
        LinearLayout howCard=new LinearLayout(this); howCard.setOrientation(LinearLayout.VERTICAL);
        howCard.setBackground(box(CARD,6,EDGE,1)); howCard.setPadding(12,10,12,10);
        LinearLayout.LayoutParams hcl=new LinearLayout.LayoutParams(-1,-2); hcl.setMargins(0,4,0,8); howCard.setLayoutParams(hcl);
        String[] howItems={"Tap name = +1 copy seen","Tap count badge = \u22121 copy","Tap \u25C9 badge = +1 player contesting"};
        for(String h:howItems){
            TextView hv=new TextView(this); hv.setText(h);
            hv.setTextColor(ASH); hv.setTextSize(10); hv.setPadding(0,1,0,1); howCard.addView(hv);
        }
        root.addView(howCard);

        // RECENT: the champs you've tapped this game, for instant re-tapping
        java.util.List<String> rec = pool.recentList();
        if(!rec.isEmpty()){
            TextView rlbl=new TextView(this); rlbl.setText("\u25C7 RECENT");
            rlbl.setTextColor(GOLD); rlbl.setTextSize(11); rlbl.setTypeface(null, android.graphics.Typeface.BOLD);
            rlbl.setLetterSpacing(0.1f); rlbl.setPadding(2,4,0,5); root.addView(rlbl);

            LinearLayout rrow=null;
            for(int j=0;j<rec.size();j++){
                if(j%3==0){ rrow=new LinearLayout(this); root.addView(rrow); }
                final String name=rec.get(j); final int fc=Pool.costOf(name);
                LinearLayout cell=buildChipCell(name, fc);
                rrow.addView(cell);
            }
            // fill the last row so cells keep even width
            if(rrow!=null){ int fillN=(3-(rec.size()%3))%3; for(int k=0;k<fillN;k++){ View sp=new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); rrow.addView(sp);} }

            TextView rdiv=new TextView(this);
            rdiv.setText("\u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766");
            rdiv.setTextColor(EDGE); rdiv.setTextSize(9); rdiv.setGravity(Gravity.CENTER); rdiv.setPadding(0,8,0,2);
            root.addView(rdiv);
        }

        for(int cost=1;cost<=5;cost++){
            TextView lbl=new TextView(this); lbl.setText("\u25C7 "+cost+"-COST");
            lbl.setTextColor(COSTC[cost]); lbl.setTextSize(11); lbl.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl.setLetterSpacing(0.1f);
            lbl.setPadding(2,10,0,5); root.addView(lbl);

            LinearLayout row=null; String[] arr=Pool.CHAMPS[cost];
            for(int j=0;j<arr.length;j++){
                if(j%3==0){ row=new LinearLayout(this); root.addView(row); }
                final String name=arr[j]; final int fc=cost;
                if(idx<chipNames.length){ chipNames[idx]=name; idx++; }
                LinearLayout cell=buildChipCell(name, fc);
                row.addView(cell);
            }
        }
        // big done button
        Button done=new Button(this); done.setText("DONE"); done.setAllCaps(false);
        done.setBackground(box(BLOOD,6,BLOODL,2)); done.setTextColor(BONE); done.setTextSize(15); done.setTypeface(null, android.graphics.Typeface.BOLD);
        done.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2); dl.setMargins(0,14,0,0); done.setLayoutParams(dl);
        root.addView(done);
    }

    // Builds one champ cell: [ chip: name(+1) | count(-1) ][ opp badge ]
    // Used by both the RECENT row and the cost grid.
    private LinearLayout buildChipCell(final String name, final int fc){
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cellLp=new LinearLayout.LayoutParams(0,-2,1f);
        cellLp.setMargins(3,3,3,3); cell.setLayoutParams(cellLp);

        final LinearLayout chip=new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipLp=new LinearLayout.LayoutParams(0,-2,1f);
        chip.setLayoutParams(chipLp);

        final TextView nameTv=new TextView(this);
        final TextView countTv=new TextView(this);

        nameTv.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){ pool.add(name,1); buzz(); paintChipPair(chip,nameTv,countTv,name,fc); }
        });
        countTv.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){ pool.add(name,-1); buzz(); paintChipPair(chip,nameTv,countTv,name,fc); }
        });

        chip.addView(nameTv);
        chip.addView(countTv);
        paintChipPair(chip, nameTv, countTv, name, fc);

        final TextView oppBadge=new TextView(this);
        paintOpp(oppBadge, name);
        oppBadge.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){ pool.addOpp(name,1); buzz(); paintOpp(oppBadge,name); }
        });
        oppBadge.setOnLongClickListener(new View.OnLongClickListener(){
            public boolean onLongClick(View v){ pool.addOpp(name,-1); buzz(); paintOpp(oppBadge,name); return true; }
        });
        LinearLayout.LayoutParams oppLp=new LinearLayout.LayoutParams(70,-1);
        oppLp.setMargins(4,0,0,0); oppBadge.setLayoutParams(oppLp);

        cell.addView(chip); cell.addView(oppBadge);
        return cell;
    }

    // paints the chip as: [ name area (+1) ][ count area (-1) ]
    // count area only appears when seen>0, and is a single-tap decrement (no hold)
    private void paintChipPair(LinearLayout chip, TextView nameTv, TextView countTv, String name, int cost){
        int seen=pool.seenCount(name);
        if(seen>0){
            // active: cost-colored, name on left, tappable count box on right
            chip.setBackground(box(COSTC[cost],6,0xFFFFFFFF,2));
            nameTv.setText(name);
            nameTv.setTextColor(0xFF000000);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            nameTv.setTextSize(15);
            nameTv.setGravity(Gravity.CENTER);
            nameTv.setPadding(10,22,4,22);
            LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1f); nameTv.setLayoutParams(nlp);

            // count shows the number with a tiny minus hint; tapping it = -1
            countTv.setText(seen+" \u2212");
            countTv.setTextColor(0xFF000000);
            countTv.setTypeface(null, android.graphics.Typeface.BOLD);
            countTv.setTextSize(15);
            countTv.setGravity(Gravity.CENTER);
            countTv.setPadding(8,22,10,22);
            countTv.setBackground(box(0x33000000,0,0,0)); // subtle darken to show it's a separate tap zone
            countTv.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-2,-2); countTv.setLayoutParams(clp);
        } else {
            // inactive: just the name, full width, tap to +1
            chip.setBackground(box(CARD,6,EDGE,1));
            nameTv.setText(name);
            nameTv.setTextColor(BONE);
            nameTv.setTypeface(null, android.graphics.Typeface.NORMAL);
            nameTv.setTextSize(15);
            nameTv.setGravity(Gravity.CENTER);
            nameTv.setPadding(10,22,8,22);
            LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1f); nameTv.setLayoutParams(nlp);
            countTv.setText("");
            countTv.setVisibility(View.GONE);
        }
    }

    // opponent badge: shows a circle glyph, or the count when >0
    private void paintOpp(TextView badge, String name){
        int n=pool.oppCount(name);
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(13);
        if(n>0){
            badge.setText(n+"p");
            // color ramps with pressure: 1-2 gold, 3+ blood
            int c = n>=3 ? BLOOD : GOLD;
            badge.setBackground(box(c,6,n>=3?BLOODL:GOLD,2));
            badge.setTextColor(n>=3?BONE:0xFF000000);
            badge.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            badge.setText("\u25C9");
            badge.setBackground(box(CARD,6,EDGE,1));
            badge.setTextColor(ASH);
            badge.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    // 3-star feasibility note. A 3-star needs 9 copies total.
    // rem = copies still in the pool, s = copies you've marked seen/out.
    // This is a rough guide on sparse input, framed honestly.
    private String threeStarNote(int cost, int rem, int s){
        // only really meaningful to show for units people chase to 3
        if(rem<=0) return "3-star: impossible, pool empty";
        if(rem < 9) {
            // fewer than 9 copies left in the whole pool -> nobody can 3-star from here
            return "3-star: not enough copies left (" + rem + ")";
        }
        // plenty left; only nudge for expensive units where it's a real question
        if(cost>=4) return "3-star: possible, needs 9 (" + rem + " left)";
        return null; // 1-3 cost with healthy pool: no need to clutter
    }

    // rounds a percentage to a coarse band so we never imply false precision.
    // (the estimate is only as good as what the user tapped)
    private int roundBand(int pct){
        if(pct<=0) return 0;
        if(pct>=95) return 95;
        return Math.round(pct/5f)*5; // nearest 5%
    }

    // AUGMENTS TAB: per-augment tier list + comp priorities + exclusions + mechanics.
    private void buildAugments(LinearLayout root){
        // set label
        TextView lbl=new TextView(this); lbl.setText(AugmentData.SET_LABEL);
        lbl.setTextColor(DIM); lbl.setTextSize(9); lbl.setPadding(2,0,0,8); root.addView(lbl);

        // ---- tier-grouped augment list ----
        TextView ah=new TextView(this); ah.setText("◇ AUGMENTS");
        ah.setTextColor(GOLD); ah.setTextSize(11); ah.setTypeface(null, android.graphics.Typeface.BOLD);
        ah.setLetterSpacing(0.1f); ah.setPadding(2,4,0,6); root.addView(ah);

        String[] tiers   = {"S",   "A",    "B",  "C"};
        int[]    tierClr = {GOLD, GREEN,   ASH,  DIM};
        for(int t=0;t<tiers.length;t++){
            boolean headerAdded=false;
            for(AugmentData.AugmentEntry aug : AugmentData.AUGMENTS){
                if(!aug.tier.equals(tiers[t])) continue;
                if(!headerAdded){
                    TextView th=new TextView(this); th.setText(tiers[t]+"-Tier");
                    th.setTextColor(tierClr[t]); th.setTextSize(10);
                    th.setTypeface(null, android.graphics.Typeface.BOLD);
                    th.setPadding(2,8,0,4); root.addView(th);
                    headerAdded=true;
                }
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(CARD,6,EDGE,1)); card.setPadding(10,8,10,8);
                LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2); cl.setMargins(0,0,0,4); card.setLayoutParams(cl);

                LinearLayout row=new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                // tier badge
                TextView badge=new TextView(this); badge.setText(tiers[t]);
                badge.setTextColor(0xFF000000); badge.setTextSize(10);
                badge.setTypeface(null, android.graphics.Typeface.BOLD); badge.setGravity(android.view.Gravity.CENTER);
                badge.setBackground(box(tierClr[t],4,0,0)); badge.setPadding(10,4,10,4);
                LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-2,-2); bl.setMargins(0,0,8,0); badge.setLayoutParams(bl);
                // name
                TextView nm=new TextView(this); nm.setText(aug.name);
                nm.setTextColor(BONE); nm.setTextSize(13); nm.setTypeface(null, android.graphics.Typeface.BOLD);
                nm.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                row.addView(badge); row.addView(nm); card.addView(row);
                // comps
                if(!aug.comps.isEmpty()){
                    TextView cv=new TextView(this); cv.setText("→ "+aug.comps);
                    cv.setTextColor(ASH); cv.setTextSize(11); cv.setPadding(0,2,0,0); card.addView(cv);
                }
                root.addView(card);
            }
        }

        // divider before existing reference sections
        TextView adiv=new TextView(this);
        adiv.setText("❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦");
        adiv.setTextColor(EDGE); adiv.setTextSize(9); adiv.setGravity(android.view.Gravity.CENTER); adiv.setPadding(0,10,0,4);
        root.addView(adiv);

        // comp priorities
        TextView h1=new TextView(this); h1.setText("\u25C7 COMP PRIORITIES");
        h1.setTextColor(GOLD); h1.setTextSize(11); h1.setTypeface(null, android.graphics.Typeface.BOLD);
        h1.setLetterSpacing(0.1f); h1.setPadding(2,4,0,6); root.addView(h1);
        for(String[] c : AugmentData.COMP_PRIORITIES){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(box(CARD,6,EDGE,1)); row.setPadding(12,9,12,9);
            LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2); rl.setMargins(0,0,0,5); row.setLayoutParams(rl);
            TextView nm=new TextView(this); nm.setText(c[0]); nm.setTextColor(BONE); nm.setTextSize(13); nm.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView pr=new TextView(this); pr.setText(c[1]); pr.setTextColor(ASH); pr.setTextSize(11); pr.setLineSpacing(3,1f);
            row.addView(nm); row.addView(pr); root.addView(row);
        }

        // exclusions
        TextView h2=new TextView(this); h2.setText("\u25C7 KEY EXCLUSIONS");
        h2.setTextColor(GOLD); h2.setTextSize(11); h2.setTypeface(null, android.graphics.Typeface.BOLD);
        h2.setLetterSpacing(0.1f); h2.setPadding(2,14,0,6); root.addView(h2);
        for(String ex : AugmentData.EXCLUSIONS){
            TextView e=new TextView(this); e.setText("\u2022  "+ex);
            e.setTextColor(BONE); e.setTextSize(11); e.setLineSpacing(3,1f); e.setPadding(2,0,2,5); root.addView(e);
        }

        // mechanics
        TextView h3=new TextView(this); h3.setText("\u25C7 MECHANICS");
        h3.setTextColor(GOLD); h3.setTextSize(11); h3.setTypeface(null, android.graphics.Typeface.BOLD);
        h3.setLetterSpacing(0.1f); h3.setPadding(2,14,0,6); root.addView(h3);
        for(String m : AugmentData.MECHANICS){
            TextView mv=new TextView(this); mv.setText("\u2022  "+m);
            mv.setTextColor(ASH); mv.setTextSize(11); mv.setLineSpacing(3,1f); mv.setPadding(2,0,2,5); root.addView(mv);
        }

        // fallback principle
        TextView fb=new TextView(this); fb.setText(AugmentData.FALLBACK);
        fb.setTextColor(DIM); fb.setTextSize(10); fb.setLineSpacing(3,1f); fb.setPadding(2,14,2,2); root.addView(fb);
    }

    private void buildSummary(LinearLayout root){
        if(pool.isEmpty()){
            LinearLayout emptyCard=new LinearLayout(this); emptyCard.setOrientation(LinearLayout.VERTICAL);
            emptyCard.setBackground(box(CARD,6,EDGE,1)); emptyCard.setPadding(16,14,16,14);
            LinearLayout.LayoutParams ecl=new LinearLayout.LayoutParams(-1,-2); ecl.setMargins(0,4,0,0); emptyCard.setLayoutParams(ecl);
            TextView emptyTitle=new TextView(this); emptyTitle.setText("\u29BF  Nothing tracked yet");
            emptyTitle.setTextColor(BONE); emptyTitle.setTextSize(14); emptyTitle.setTypeface(null,android.graphics.Typeface.BOLD);
            emptyTitle.setPadding(0,0,0,10); emptyCard.addView(emptyTitle);
            String[] steps={
                "1. Go to the POOL tab",
                "2. Tap each champion you are playing or chasing",
                "3. Come back here for roll odds and contest pressure"
            };
            for(String s:steps){
                TextView sv=new TextView(this); sv.setText(s);
                sv.setTextColor(ASH); sv.setTextSize(12); sv.setLineSpacing(3,1f); sv.setPadding(0,3,0,3);
                emptyCard.addView(sv);
            }
            root.addView(emptyCard);
            return;
        }
        List<String> names=pool.seenSorted();
        String pin=pool.getPinned();
        if(!pin.isEmpty() && names.contains(pin)){ names.remove(pin); names.add(0, pin); }

        // tiny hint: long-press a card to pin your carry
        TextView pinTip=new TextView(this);
        pinTip.setText("long-press a unit to \u2605 pin it as your carry");
        pinTip.setTextColor(DIM); pinTip.setTextSize(9); pinTip.setPadding(2,0,2,8); root.addView(pinTip);

        // bench-thinning: junk units held shrink the pool and nudge odds up
        TextView thinLbl=new TextView(this); thinLbl.setText("\u25C7 JUNK ON BENCH (thins the pool)");
        thinLbl.setTextColor(GOLD); thinLbl.setTextSize(10); thinLbl.setTypeface(null, android.graphics.Typeface.BOLD);
        thinLbl.setLetterSpacing(0.1f); thinLbl.setPadding(2,12,0,4); root.addView(thinLbl);
        LinearLayout thinRow=new LinearLayout(this); thinRow.setOrientation(LinearLayout.HORIZONTAL);
        for(int co=1;co<=5;co++){
            final int fcost=co; int jv=pool.getJunk(co);
            LinearLayout jb=new LinearLayout(this); jb.setOrientation(LinearLayout.VERTICAL); jb.setGravity(Gravity.CENTER);
            jb.setBackground(box(CARD,5,jv>0?GOLD:EDGE,jv>0?2:1)); jb.setPadding(0,8,0,8);
            LinearLayout.LayoutParams jl=new LinearLayout.LayoutParams(0,-2,1f); jl.setMargins(3,0,3,0); jb.setLayoutParams(jl);
            TextView ct=new TextView(this); ct.setText(fcost+"c"); ct.setTextColor(COSTC[fcost]); ct.setTextSize(11); ct.setGravity(Gravity.CENTER);
            TextView nt=new TextView(this); nt.setText(""+jv); nt.setTextColor(jv>0?GOLD:ASH); nt.setTextSize(15); nt.setTypeface(null, android.graphics.Typeface.BOLD); nt.setGravity(Gravity.CENTER);
            jb.addView(ct); jb.addView(nt);
            jb.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.addJunk(fcost,1); buzz(); showPanel(); } });
            jb.setOnLongClickListener(new View.OnLongClickListener(){ public boolean onLongClick(View v){ pool.addJunk(fcost,-1); buzz(); showPanel(); return true; } });
            thinRow.addView(jb);
        }
        root.addView(thinRow);
        TextView thinHint=new TextView(this); thinHint.setText("tap +1 junk of a cost, long-press \u22121");
        thinHint.setTextColor(DIM); thinHint.setTextSize(9); thinHint.setPadding(2,4,2,10); root.addView(thinHint);


        for(final String name:names){
            int co=Pool.costOf(name); int s=pool.seenCount(name); int rem=pool.remaining(name);
            int players=pool.oppCount(name);
            int poolSize=Pool.SIZE[co];
            double takenFrac = poolSize>0 ? (double)s/poolSize : 0;
            final boolean pinned = pool.isPinned(name);

            int accent; boolean clean=false;
            if(rem<=0){ accent=BLOODL; }
            else if(takenFrac>=0.55 || players>=3){ accent=BLOODL; }
            else if(takenFrac>=0.35 || players==2){ accent=GOLD; }
            else { accent=EDGE; clean=true; }
            if(pinned) accent=GOLD; // pinned carry always stands out

            LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL);
            int bw = (clean && !pinned) ? 1 : 2;
            card.setBackground(box(CARD,6,accent,bw)); card.setPadding(12,12,10,12);
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2); cl.setMargins(0,0,0,7); card.setLayoutParams(cl);
            // long-press a card to pin/unpin it as your carry
            card.setOnLongClickListener(new View.OnLongClickListener(){
                public boolean onLongClick(View v){ pool.setPinned(pinned?"":name); buzz(); showPanel(); return true; }
            });

            TextView dot=new TextView(this); dot.setText(""+co); dot.setTextColor(0xFF000000); dot.setTextSize(11); dot.setGravity(Gravity.CENTER);
            dot.setBackground(box(COSTC[co],4,0,0)); dot.setWidth(44); dot.setHeight(44); card.addView(dot);

            LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setPadding(12,0,0,0);
            mid.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView nm=new TextView(this); nm.setText((pinned?"\u2605 ":"")+name); nm.setTextColor(pinned?GOLD:BONE); nm.setTextSize(15); nm.setTypeface(null, android.graphics.Typeface.BOLD);
            // facts line: copies left + players contesting
            String line = rem+" / "+poolSize+" left";
            if(players>0) line += "  \u00b7  "+players+(players==1?" player":" players");
            TextView sub=new TextView(this); sub.setText(line); sub.setTextColor(ASH); sub.setTextSize(11);
            mid.addView(nm); mid.addView(sub);

            // 3-star feasibility: a 3-star needs 9 copies. show only if relevant.
            String feas = threeStarNote(co, rem, s);
            if(feas!=null){
                TextView f=new TextView(this); f.setText(feas);
                f.setTextColor(rem < (9 - 0) ? GOLD : ASH); f.setTextSize(10); mid.addView(f);
            }
            card.addView(mid);

            // right side: rough "per roll" estimate, rounded to a band (no false
            // precision, since it's computed only from what you've tapped).
            LinearLayout vbox=new LinearLayout(this); vbox.setOrientation(LinearLayout.VERTICAL); vbox.setGravity(Gravity.CENTER);
            vbox.setPadding(8,0,8,0);
            double perRoll = rem<=0 ? 0 : rerollChance(name)*100.0;
            TextView pct=new TextView(this);
            pct.setText(rem<=0 ? "--" : "~"+roundBand((int)Math.round(perRoll))+"%");
            pct.setTextColor(rem<=0?DIM:BONE); pct.setTextSize(17); pct.setTypeface(null, android.graphics.Typeface.BOLD); pct.setGravity(Gravity.CENTER);
            TextView pl=new TextView(this); pl.setText(rem<=0?"gone":"per shop"); pl.setTextColor(ASH); pl.setTextSize(9); pl.setGravity(Gravity.CENTER);
            vbox.addView(pct); vbox.addView(pl); card.addView(vbox);

            TextView minus=new TextView(this); minus.setText("\u2212"); minus.setTextColor(BLOODL); minus.setTextSize(20); minus.setGravity(Gravity.CENTER);
            minus.setBackground(box(0xFF1A0C0E,5,BLOOD,1)); minus.setWidth(50); minus.setHeight(44);
            minus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.add(name,-1); buzz(); showPanel(); } });
            card.addView(minus);
            root.addView(card);
        }
        // legend
        TextView legend=new TextView(this);
        legend.setText("% = your odds to hit on a roll at this level \u00b7 you call it");
        legend.setTextColor(DIM); legend.setTextSize(10); legend.setPadding(2,8,2,0); root.addView(legend);

        // death-return reminder: eliminated players' units go back to the pool
        TextView deathTip=new TextView(this);
        deathTip.setText("\u2620 when a player dies, their units return to the pool. tap a count down to free those copies");
        deathTip.setTextColor(GOLD); deathTip.setTextSize(10); deathTip.setPadding(2,6,2,0); root.addView(deathTip);

        Button wipe=new Button(this); wipe.setText("RESET ALL"); wipe.setAllCaps(false);
        wipe.setBackground(box(0xFF1A0C0E,6,BLOOD,2)); wipe.setTextColor(BLOODL); wipe.setTextSize(13);
        wipe.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.reset(); showPanel(); } });
        LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-1,-2); wl.setMargins(0,12,0,0); wipe.setLayoutParams(wl);
        root.addView(wipe);
        TextView credit=new TextView(this); credit.setText("@xanfiend"); credit.setTextColor(DIM); credit.setTextSize(10); credit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams crl=new LinearLayout.LayoutParams(-1,-2); crl.setMargins(0,14,0,0); credit.setLayoutParams(crl);
        root.addView(credit);

        // version + get-latest link. opens GitHub in the browser (no INTERNET
        // permission needed: the browser does the network, not this app).
        TextView ver=new TextView(this);
        ver.setText(APP_VERSION + "  \u00b7  tap for the latest on GitHub");
        ver.setTextColor(GOLD); ver.setTextSize(10); ver.setGravity(Gravity.CENTER);
        ver.setPadding(0,8,0,0);
        LinearLayout.LayoutParams vrl=new LinearLayout.LayoutParams(-1,-2); vrl.setMargins(0,4,0,0); ver.setLayoutParams(vrl);
        ver.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ openLatest(); } });
        root.addView(ver);
    }

    // ---- ECONOMY TAB ----
    private void buildEconomy(LinearLayout root){
        int gold=pool.getGold(); int streak=pool.getStreak();
        int intr=Pool.interest(gold); int toNext=Pool.toNextBracket(gold);
        int sBonus=Pool.streakBonus(streak); int income=Pool.expectedIncome(gold,streak);

        // gold header row with inline scan shortcut
        LinearLayout econHdrRow=new LinearLayout(this); econHdrRow.setOrientation(LinearLayout.HORIZONTAL);
        econHdrRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ehrp=new LinearLayout.LayoutParams(-1,-2); ehrp.setMargins(0,4,0,8); econHdrRow.setLayoutParams(ehrp);
        TextView gh=new TextView(this); gh.setText("◇ GOLD");
        gh.setTextColor(GOLD); gh.setTextSize(11); gh.setTypeface(null,android.graphics.Typeface.BOLD);
        gh.setLetterSpacing(0.1f); gh.setPadding(2,0,0,0);
        gh.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        econHdrRow.addView(gh);
        TextView econScanBtn=new TextView(this); econScanBtn.setText("scan");
        econScanBtn.setTextColor(ASH); econScanBtn.setTextSize(10);
        econScanBtn.setPadding(12,4,4,4);
        econScanBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ triggerScan(); }});
        econHdrRow.addView(econScanBtn);
        root.addView(econHdrRow);

        LinearLayout goldRow=new LinearLayout(this); goldRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView gMinus=makeAdjBtn("−", 0xFF1A0C0E, BLOODL);
        econGoldTv=new TextView(this); econGoldTv.setText(gold+"g");
        econGoldTv.setTextColor(GOLD); econGoldTv.setTextSize(28); econGoldTv.setTypeface(null, android.graphics.Typeface.BOLD);
        econGoldTv.setGravity(Gravity.CENTER); econGoldTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView gPlus=makeAdjBtn("+", 0xFF1A0C0E, BLOODL);

        // hold-to-repeat: fires immediately on DOWN, then repeats at 80ms after 350ms hold
        gPlus.setOnTouchListener(new View.OnTouchListener(){ public boolean onTouch(View v, MotionEvent e){
            int a=e.getAction();
            if(a==MotionEvent.ACTION_DOWN){
                pool.setGold(pool.getGold()+1); buzz(); refreshEcon();
                goldRepeat=new Runnable(){ public void run(){
                    pool.setGold(pool.getGold()+1); buzz(); refreshEcon();
                    goldHandler.postDelayed(this,80);
                }};
                goldHandler.postDelayed(goldRepeat,350); return true;
            } else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
                goldHandler.removeCallbacks(goldRepeat); goldRepeat=null; return true;
            }
            return false;
        }});
        gMinus.setOnTouchListener(new View.OnTouchListener(){ public boolean onTouch(View v, MotionEvent e){
            int a=e.getAction();
            if(a==MotionEvent.ACTION_DOWN){
                pool.setGold(pool.getGold()-1); buzz(); refreshEcon();
                goldRepeat=new Runnable(){ public void run(){
                    pool.setGold(pool.getGold()-1); buzz(); refreshEcon();
                    goldHandler.postDelayed(this,80);
                }};
                goldHandler.postDelayed(goldRepeat,350); return true;
            } else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){
                goldHandler.removeCallbacks(goldRepeat); goldRepeat=null; return true;
            }
            return false;
        }});
        goldRow.addView(gMinus); goldRow.addView(econGoldTv); goldRow.addView(gPlus);
        root.addView(goldRow);
        TextView goldHint=new TextView(this); goldHint.setText("tap ±1  ·  hold to repeat");
        goldHint.setTextColor(DIM); goldHint.setTextSize(9); goldHint.setPadding(2,2,2,0); root.addView(goldHint);

        // interest info
        LinearLayout iRow=new LinearLayout(this); iRow.setOrientation(LinearLayout.VERTICAL);
        iRow.setBackground(box(CARD,6,EDGE,1)); iRow.setPadding(12,10,12,10);
        LinearLayout.LayoutParams irl=new LinearLayout.LayoutParams(-1,-2); irl.setMargins(0,10,0,0); iRow.setLayoutParams(irl);
        TextView iLbl=new TextView(this); iLbl.setText("INTEREST");
        iLbl.setTextColor(ASH); iLbl.setTextSize(10); iLbl.setLetterSpacing(0.08f); iRow.addView(iLbl);
        econInterestTv=new TextView(this); econInterestTv.setText("+"+intr+"g per round");
        econInterestTv.setTextColor(BONE); econInterestTv.setTextSize(17); econInterestTv.setTypeface(null, android.graphics.Typeface.BOLD); iRow.addView(econInterestTv);
        econBracketTv=new TextView(this); econBracketTv.setText(gold>=50?"max interest (50g+)":"+"+toNext+"g to next bracket");
        econBracketTv.setTextColor(gold>=50?GOLD:ASH); econBracketTv.setTextSize(11); iRow.addView(econBracketTv);
        TextView intrExplain=new TextView(this); intrExplain.setText("1g interest per 10g saved  ·  max 5g per round");
        intrExplain.setTextColor(DIM); intrExplain.setTextSize(10); intrExplain.setPadding(0,4,0,0); iRow.addView(intrExplain);
        // interest ladder dots: 10 / 20 / 30 / 40 / 50
        LinearLayout ladder=new LinearLayout(this); ladder.setPadding(0,8,0,0);
        int[] brackets={10,20,30,40,50};
        econLadderTvs=new TextView[5];
        for(int i=0;i<5;i++){
            int b=brackets[i]; boolean reached=gold>=b; boolean cur=(gold/10)*10==b||(b==50&&gold>=50);
            TextView dot=new TextView(this); dot.setGravity(Gravity.CENTER);
            dot.setText(b+"g"); dot.setTextSize(10);
            dot.setTextColor(reached?GOLD:EDGE);
            dot.setTypeface(null, cur?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            dot.setBackground(box(reached?0xFF1A1400:CARD,4,reached?GOLD:EDGE,reached?2:1));
            dot.setPadding(6,4,6,4);
            LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1f); dl.setMargins(2,0,2,0); dot.setLayoutParams(dl);
            econLadderTvs[i]=dot; ladder.addView(dot);
        }
        iRow.addView(ladder); root.addView(iRow);

        // streak row
        TextView sh=new TextView(this); sh.setText("◇ STREAK");
        sh.setTextColor(GOLD); sh.setTextSize(11); sh.setTypeface(null, android.graphics.Typeface.BOLD);
        sh.setLetterSpacing(0.1f); sh.setPadding(2,14,0,8); root.addView(sh);

        LinearLayout streakRow=new LinearLayout(this); streakRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sL=makeAdjBtn("L", BLOOD, BONE);
        sL.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); pool.setStreak(s>0?-1:s-1); buzz(); refreshEcon();
        }});
        econStreakTv=new TextView(this);
        String sText = streak==0?"—":Math.abs(streak)+(streak>0?"W":"L");
        int sColor = streak>0?GREEN:(streak<0?BLOODL:ASH);
        econStreakTv.setText(sText); econStreakTv.setTextColor(sColor);
        econStreakTv.setTextSize(24); econStreakTv.setTypeface(null, android.graphics.Typeface.BOLD);
        econStreakTv.setGravity(Gravity.CENTER); econStreakTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView sW=makeAdjBtn("W", 0xFF0D2210, GREEN);
        sW.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); pool.setStreak(s<0?1:s+1); buzz(); refreshEcon();
        }});
        streakRow.addView(sL); streakRow.addView(econStreakTv); streakRow.addView(sW);
        root.addView(streakRow);
        econBonusTv=new TextView(this);
        econBonusTv.setTextColor(ASH); econBonusTv.setTextSize(11); econBonusTv.setPadding(2,4,2,0);
        if(sBonus>0){ econBonusTv.setText("+"+sBonus+"g streak bonus"); econBonusTv.setVisibility(View.VISIBLE); }
        else { econBonusTv.setVisibility(View.GONE); }
        root.addView(econBonusTv);
        TextView streakScale=new TextView(this); streakScale.setText("2+ streak = +1g  ·  4+ = +2g  ·  6+ = +3g");
        streakScale.setTextColor(DIM); streakScale.setTextSize(10); streakScale.setPadding(2,2,2,0); root.addView(streakScale);

        // expected income card
        LinearLayout incCard=new LinearLayout(this); incCard.setOrientation(LinearLayout.VERTICAL);
        incCard.setBackground(box(CARD,6,BLOODL,2)); incCard.setPadding(14,12,14,12);
        LinearLayout.LayoutParams icl=new LinearLayout.LayoutParams(-1,-2); icl.setMargins(0,14,0,0); incCard.setLayoutParams(icl);
        TextView icH=new TextView(this); icH.setText("EXPECTED NEXT ROUND");
        icH.setTextColor(ASH); icH.setTextSize(10); icH.setLetterSpacing(0.08f); incCard.addView(icH);
        econIncomeTv=new TextView(this); econIncomeTv.setText(income+"g");
        econIncomeTv.setTextColor(GOLD); econIncomeTv.setTextSize(28); econIncomeTv.setTypeface(null, android.graphics.Typeface.BOLD); incCard.addView(econIncomeTv);
        econBreakTv=new TextView(this); econBreakTv.setText("5 base  +  "+intr+"g interest  +  "+sBonus+"g streak");
        econBreakTv.setTextColor(ASH); econBreakTv.setTextSize(11); incCard.addView(econBreakTv);
        root.addView(incCard);

        // reset econ button (resets only gold+streak, not pool)
        Button resetEcon=new Button(this); resetEcon.setText("RESET ECON"); resetEcon.setAllCaps(false);
        resetEcon.setBackground(box(0xFF1A0C0E,6,BLOOD,2)); resetEcon.setTextColor(ASH); resetEcon.setTextSize(12);
        resetEcon.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            pool.setGold(0); pool.setStreak(0); refreshEcon();
        }});
        LinearLayout.LayoutParams rel=new LinearLayout.LayoutParams(-1,-2); rel.setMargins(0,16,0,0); resetEcon.setLayoutParams(rel);
        root.addView(resetEcon);
    }

    private void refreshEcon(){
        if(econGoldTv==null) return;
        int gold=pool.getGold(); int streak=pool.getStreak();
        int intr=Pool.interest(gold); int toNext=Pool.toNextBracket(gold);
        int sBonus=Pool.streakBonus(streak); int income=Pool.expectedIncome(gold,streak);
        econGoldTv.setText(gold+"g");
        econInterestTv.setText("+"+intr+"g per round");
        econBracketTv.setText(gold>=50?"max interest (50g+)":"+"+toNext+"g to next bracket");
        econBracketTv.setTextColor(gold>=50?GOLD:ASH);
        int[] brackets={10,20,30,40,50};
        for(int i=0;i<5;i++){
            int b=brackets[i]; boolean reached=gold>=b; boolean cur=(gold/10)*10==b||(b==50&&gold>=50);
            econLadderTvs[i].setTextColor(reached?GOLD:EDGE);
            econLadderTvs[i].setTypeface(null,cur?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            econLadderTvs[i].setBackground(box(reached?0xFF1A1400:CARD,4,reached?GOLD:EDGE,reached?2:1));
        }
        econStreakTv.setText(streak==0?"—":Math.abs(streak)+(streak>0?"W":"L"));
        econStreakTv.setTextColor(streak>0?GREEN:(streak<0?BLOODL:ASH));
        if(sBonus>0){ econBonusTv.setText("+"+sBonus+"g streak bonus"); econBonusTv.setVisibility(View.VISIBLE); }
        else { econBonusTv.setVisibility(View.GONE); }
        econIncomeTv.setText(income+"g");
        econBreakTv.setText("5 base  +  "+intr+"g interest  +  "+sBonus+"g streak");
    }

    private TextView makeAdjBtn(String label, int bg, int fg){
        TextView tv=new TextView(this); tv.setText(label);
        tv.setTextColor(fg); tv.setTextSize(20); tv.setGravity(Gravity.CENTER);
        tv.setBackground(box(bg,6,BLOOD,2)); tv.setPadding(0,18,0,18);
        tv.setLayoutParams(new LinearLayout.LayoutParams(120,-2));
        return tv;
    }

    // ---- ITEMS TAB ----
    private void buildItems(LinearLayout root){
        // instruction hint
        TextView hint=new TextView(this);
        hint.setText("Tap two components to see what they make");
        hint.setTextColor(DIM); hint.setTextSize(10); hint.setPadding(2,0,2,8); root.addView(hint);

        // 9 component chips in two rows (5 + 4)
        int[][] rows={{1,2,3,4,5},{6,7,8,9}};
        for(int[] row : rows){
            LinearLayout r=new LinearLayout(this); r.setPadding(0,0,0,4);
            for(int i : row){
                final int ci=i; boolean selA=(itemA==ci); boolean selB=(itemB==ci);
                boolean sel=selA||selB;
                TextView chip=new TextView(this); chip.setText(ItemData.COMPONENT_SHORT[ci]);
                chip.setGravity(Gravity.CENTER); chip.setTextSize(11);
                chip.setTextColor(sel?0xFF000000:BONE);
                chip.setTypeface(null, sel?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                int chipBg = selA?GOLD : (selB?GREEN : CARD);
                int chipBorder = sel?0xFFFFFFFF:EDGE;
                chip.setBackground(box(chipBg,6,chipBorder,sel?2:1));
                chip.setPadding(4,10,4,10);
                LinearLayout.LayoutParams chl=new LinearLayout.LayoutParams(0,-2,1f); chl.setMargins(3,0,3,0); chip.setLayoutParams(chl);
                chip.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    if(itemA==-1){ itemA=ci; }
                    else if(itemB==-1){ itemB=ci; }
                    else { itemA=ci; itemB=-1; }
                    showPanel();
                }});
                r.addView(chip);
            }
            // fill last row for even spacing
            if(row.length<5){ for(int k=row.length;k<5;k++){ View sp=new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); r.addView(sp); } }
            root.addView(r);
        }

        // result card
        if(itemA!=-1 && itemB!=-1){
            String result = ItemData.COMBOS[itemA][itemB];
            if(result==null) result="Unknown — verify item table";
            LinearLayout rCard=new LinearLayout(this); rCard.setOrientation(LinearLayout.VERTICAL);
            rCard.setBackground(box(CARD,6,GOLD,2)); rCard.setPadding(14,12,14,12);
            LinearLayout.LayoutParams rcl=new LinearLayout.LayoutParams(-1,-2); rcl.setMargins(0,10,0,0); rCard.setLayoutParams(rcl);
            TextView rTop=new TextView(this);
            rTop.setText(ItemData.COMPONENT_SHORT[itemA]+" + "+ItemData.COMPONENT_SHORT[itemB]);
            rTop.setTextColor(ASH); rTop.setTextSize(11); rCard.addView(rTop);
            TextView rName=new TextView(this); rName.setText(result);
            rName.setTextColor(GOLD); rName.setTextSize(18); rName.setTypeface(null, android.graphics.Typeface.BOLD); rCard.addView(rName);
            root.addView(rCard);
        } else if(itemA!=-1){
            // first component selected, waiting for second
            TextView waiting=new TextView(this);
            waiting.setText("→ "+ItemData.COMPONENTS[itemA]+" — tap a second component");
            waiting.setTextColor(GOLD); waiting.setTextSize(12); waiting.setPadding(2,10,2,0); root.addView(waiting);
        }

        // clear button
        if(itemA!=-1){
            Button clear=new Button(this); clear.setText("Clear"); clear.setAllCaps(false);
            clear.setBackground(box(CARD,6,EDGE,1)); clear.setTextColor(ASH); clear.setTextSize(12);
            clear.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; showPanel(); } });
            LinearLayout.LayoutParams brl=new LinearLayout.LayoutParams(-2,-2); brl.setMargins(0,6,0,0); clear.setLayoutParams(brl);
            root.addView(clear);
        }

        // traits section
        TextView tdiv=new TextView(this);
        tdiv.setText("❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦");
        tdiv.setTextColor(EDGE); tdiv.setTextSize(9); tdiv.setGravity(Gravity.CENTER); tdiv.setPadding(0,14,0,4);
        root.addView(tdiv);

        TextView trH=new TextView(this); trH.setText("◇ TRAITS");
        trH.setTextColor(GOLD); trH.setTextSize(11); trH.setTypeface(null, android.graphics.Typeface.BOLD);
        trH.setLetterSpacing(0.1f); trH.setPadding(2,4,0,6); root.addView(trH);

        for(String[] tr : TraitData.TRAITS){
            LinearLayout tRow=new LinearLayout(this); tRow.setGravity(Gravity.CENTER_VERTICAL);
            tRow.setBackground(box(CARD,6,EDGE,1)); tRow.setPadding(10,8,10,8);
            LinearLayout.LayoutParams trl=new LinearLayout.LayoutParams(-1,-2); trl.setMargins(0,0,0,4); tRow.setLayoutParams(trl);
            TextView tName=new TextView(this); tName.setText(tr[0]);
            tName.setTextColor(BONE); tName.setTextSize(12); tName.setTypeface(null, android.graphics.Typeface.BOLD);
            tName.setLayoutParams(new LinearLayout.LayoutParams(0,-2,2.2f));
            TextView tBps=new TextView(this); tBps.setText(tr[1]);
            tBps.setTextColor(GOLD); tBps.setTextSize(11);
            tBps.setLayoutParams(new LinearLayout.LayoutParams(0,-2,2f));
            TextView tEff=new TextView(this); tEff.setText(tr[2]);
            tEff.setTextColor(ASH); tEff.setTextSize(10);
            tEff.setLayoutParams(new LinearLayout.LayoutParams(0,-2,3f));
            tRow.addView(tName); tRow.addView(tBps); tRow.addView(tEff);
            root.addView(tRow);
        }
    }

    // ---- GUIDE TAB: sub-tabs for Augments and Items reference ----
    private void buildGuide(LinearLayout root){
        // sub-tab row
        LinearLayout gtRow=new LinearLayout(this); gtRow.setPadding(0,0,0,10);
        String[] gtNames={"AUGMENTS","ITEMS"};
        for(int i=0;i<2;i++){
            final int gi=i; boolean on=guideTab==gi;
            TextView gt=new TextView(this); gt.setText(gtNames[i]); gt.setGravity(Gravity.CENTER);
            gt.setTextColor(on?BONE:ASH); gt.setTextSize(10); gt.setLetterSpacing(0.05f);
            gt.setTypeface(null,on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            gt.setBackground(box(on?BLOOD:CARD,6,on?BLOODL:EDGE,on?2:1)); gt.setPadding(0,12,0,12);
            LinearLayout.LayoutParams gtl=new LinearLayout.LayoutParams(0,-2,1f); gtl.setMargins(2,0,2,0); gt.setLayoutParams(gtl);
            gt.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ guideTab=gi; showPanel(); }});
            gtRow.addView(gt);
        }
        root.addView(gtRow);
        if(guideTab==0) buildAugments(root);
        else buildItems(root);
    }

    private void buildSettings(LinearLayout root){
        boolean accEnabled = Build.VERSION.SDK_INT >= 31 && TFTAccessibilityService.instance != null;

        // ◇ PERMISSIONS
        TextView permHdr=new TextView(this); permHdr.setText("◇ PERMISSIONS");
        permHdr.setTextColor(GOLD); permHdr.setTextSize(11); permHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        permHdr.setLetterSpacing(0.1f); permHdr.setPadding(2,4,0,6); root.addView(permHdr);

        LinearLayout accCard=new LinearLayout(this); accCard.setOrientation(LinearLayout.VERTICAL);
        accCard.setBackground(box(CARD,6,accEnabled?GREEN:EDGE,accEnabled?2:1)); accCard.setPadding(12,10,12,10);
        LinearLayout.LayoutParams acardl=new LinearLayout.LayoutParams(-1,-2); acardl.setMargins(0,0,0,8); accCard.setLayoutParams(acardl);
        TextView accLabel=new TextView(this); accLabel.setText("Accessibility (silent scan)");
        accLabel.setTextColor(ASH); accLabel.setTextSize(10); accLabel.setLetterSpacing(0.05f); accCard.addView(accLabel);
        TextView accStatus=new TextView(this);
        accStatus.setText(accEnabled ? "Enabled — scan works silently, no app switch" : "Disabled — scan buttons will not work");
        accStatus.setTextColor(accEnabled?GREEN:BLOODL);
        accStatus.setTextSize(13); accStatus.setTypeface(null,android.graphics.Typeface.BOLD); accCard.addView(accStatus);
        if(!accEnabled){
            String steps = Build.VERSION.SDK_INT >= 33
                ? "1. App settings below → Allow restricted settings\n2. Accessibility → TFT Scryer → On"
                : "Accessibility → TFT Scryer → On";
            TextView accInstr=new TextView(this); accInstr.setText(steps);
            accInstr.setTextColor(ASH); accInstr.setTextSize(11); accInstr.setPadding(0,4,0,0); accCard.addView(accInstr);
        }
        root.addView(accCard);

        if(!accEnabled){
            LinearLayout accBtnRow=new LinearLayout(this); accBtnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams abrp=new LinearLayout.LayoutParams(-1,-2); abrp.setMargins(0,0,0,12); accBtnRow.setLayoutParams(abrp);
            if(Build.VERSION.SDK_INT >= 33){
                TextView appInfoBtn=new TextView(this); appInfoBtn.setText("App settings");
                appInfoBtn.setTextColor(BONE); appInfoBtn.setTextSize(12); appInfoBtn.setGravity(Gravity.CENTER);
                appInfoBtn.setPadding(0,10,0,10); appInfoBtn.setBackground(box(CARD,6,EDGE,1));
                LinearLayout.LayoutParams aibl=new LinearLayout.LayoutParams(0,-2,1f); aibl.setMargins(0,0,4,0); appInfoBtn.setLayoutParams(aibl);
                appInfoBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    try{ Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(android.net.Uri.parse("package:"+getPackageName()));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){}
                }});
                accBtnRow.addView(appInfoBtn);
            }
            TextView accBtn=new TextView(this); accBtn.setText("Accessibility settings");
            accBtn.setTextColor(BONE); accBtn.setTextSize(12); accBtn.setGravity(Gravity.CENTER);
            accBtn.setPadding(0,10,0,10); accBtn.setBackground(box(CARD,6,EDGE,1));
            LinearLayout.LayoutParams abl=new LinearLayout.LayoutParams(0,-2,1f); accBtn.setLayoutParams(abl);
            accBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){}
            }});
            accBtnRow.addView(accBtn);
            root.addView(accBtnRow);
        }

        // ◇ SCAN
        TextView scanHdr=new TextView(this); scanHdr.setText("◇ SCAN");
        scanHdr.setTextColor(GOLD); scanHdr.setTextSize(11); scanHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        scanHdr.setLetterSpacing(0.1f); scanHdr.setPadding(2,4,0,6); root.addView(scanHdr);

        scanStatusTv=new TextView(this);
        scanStatusTv.setText(lastScanStatus.isEmpty()?"tap Scan to auto-fill gold, level & augments":lastScanStatus);
        scanStatusTv.setTextColor(lastScanStatus.startsWith("✓")?GREEN:(lastScanStatus.startsWith("✗")?BLOODL:ASH));
        scanStatusTv.setTextSize(11); scanStatusTv.setPadding(2,0,0,8); root.addView(scanStatusTv);

        TextView scanBtn=new TextView(this); scanBtn.setText("Scan now");
        scanBtn.setTextColor(BONE); scanBtn.setTextSize(13); scanBtn.setGravity(Gravity.CENTER);
        scanBtn.setPadding(0,12,0,12); scanBtn.setBackground(box(BLOOD,6,BLOODL,2));
        LinearLayout.LayoutParams sbl=new LinearLayout.LayoutParams(-1,-2); sbl.setMargins(0,0,0,4); scanBtn.setLayoutParams(sbl);
        scanBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ triggerScan(); }});
        root.addView(scanBtn);

        TextView scanHint=new TextView(this);
        scanHint.setText("Reads gold, level & augments from TFT. Enable Accessibility above for silent scan with no app switch.");
        scanHint.setTextColor(DIM); scanHint.setTextSize(10); scanHint.setPadding(2,4,0,0); root.addView(scanHint);

        // ◇ DEBUG LOG
        LinearLayout logHdrRow=new LinearLayout(this); logHdrRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lhrp=new LinearLayout.LayoutParams(-1,-2); lhrp.setMargins(0,14,0,4); logHdrRow.setLayoutParams(lhrp);
        TextView logHdr=new TextView(this); logHdr.setText("◇ DEBUG LOG");
        logHdr.setTextColor(GOLD); logHdr.setTextSize(11); logHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        logHdr.setLetterSpacing(0.1f); logHdr.setPadding(2,0,0,0);
        LinearLayout.LayoutParams lhtp=new LinearLayout.LayoutParams(0,-2,1f); logHdr.setLayoutParams(lhtp);
        logHdrRow.addView(logHdr);
        TextView copyLogBtn=new TextView(this); copyLogBtn.setText("copy");
        copyLogBtn.setTextColor(ASH); copyLogBtn.setTextSize(10);
        copyLogBtn.setPadding(12,4,4,4);
        copyLogBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            StringBuilder sb=new StringBuilder();
            synchronized(scanLog){ for(String l:scanLog) sb.append(l).append("\n"); }
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("tft-scan-log",sb.toString()));
            Toast.makeText(OverlayService.this,"Log copied to clipboard",Toast.LENGTH_SHORT).show();
        }});
        logHdrRow.addView(copyLogBtn);
        TextView clearLogBtn=new TextView(this); clearLogBtn.setText("clear");
        clearLogBtn.setTextColor(ASH); clearLogBtn.setTextSize(10);
        clearLogBtn.setPadding(6,4,4,4);
        clearLogBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            clearScanLog(); mode=4; showPanel();
        }});
        logHdrRow.addView(clearLogBtn);
        root.addView(logHdrRow);

        // Debug scan: takes a popup screenshot and dumps every OCR block to the log
        // with its height and coordinates so you can diagnose detection failures.
        boolean canDbgScan = Build.VERSION.SDK_INT >= 31 && TFTAccessibilityService.instance != null;
        TextView dbgScanBtn=new TextView(this); dbgScanBtn.setText("Debug scan (dump all OCR blocks)");
        dbgScanBtn.setTextColor(canDbgScan?BONE:ASH); dbgScanBtn.setTextSize(11); dbgScanBtn.setGravity(Gravity.CENTER);
        dbgScanBtn.setPadding(0,10,0,10); dbgScanBtn.setBackground(box(CARD,6,canDbgScan?EDGE:DIM,1));
        LinearLayout.LayoutParams dbgl=new LinearLayout.LayoutParams(-1,-2); dbgl.setMargins(0,6,0,4); dbgScanBtn.setLayoutParams(dbgl);
        dbgScanBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(!canDbgScan){ Toast.makeText(OverlayService.this,"Enable Accessibility service first",Toast.LENGTH_SHORT).show(); return; }
            clearScanLog();
            addScanLog("=== DEBUG SCAN ===");
            debugScanPending=true;
            closePanel(); // close so TFT is visible when screenshot runs
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){ public void run(){
                triggerPopupScan();
            }}, 350);
        }});
        root.addView(dbgScanBtn);

        LinearLayout logBox=new LinearLayout(this); logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setBackground(box(CARD,4,EDGE,1)); logBox.setPadding(10,8,10,8);
        LinearLayout.LayoutParams lbp=new LinearLayout.LayoutParams(-1,-2); lbp.setMargins(0,0,0,0); logBox.setLayoutParams(lbp);
        synchronized(scanLog){
            if(scanLog.isEmpty()){
                TextView empty=new TextView(this); empty.setText("no scan log yet — tap Scan Now");
                empty.setTextColor(DIM); empty.setTextSize(10); logBox.addView(empty);
            } else {
                for(String line : scanLog){
                    TextView lt=new TextView(this); lt.setText(line);
                    lt.setTextColor(line.startsWith("ERR")?BLOODL:ASH);
                    lt.setTextSize(9); lt.setPadding(0,1,0,1);
                    logBox.addView(lt);
                }
            }
        }
        root.addView(logBox);

        // ◇ TEMPLATES
        TextView tplHdr=new TextView(this); tplHdr.setText("◇ TEMPLATES");
        tplHdr.setTextColor(GOLD); tplHdr.setTextSize(11); tplHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        tplHdr.setLetterSpacing(0.1f); tplHdr.setPadding(2,14,0,4); root.addView(tplHdr);
        int tplCount=ChampionTemplates.templateCount();
        TextView tplCountTv=new TextView(this);
        tplCountTv.setText(tplCount==0
            ?"No templates yet (auto-captured during board scans, not required for Auto Scan)"
            :tplCount+" template"+(tplCount==1?"":"s")+" saved (auto-captured during board scans)");
        tplCountTv.setTextColor(tplCount>0?ASH:DIM); tplCountTv.setTextSize(11); tplCountTv.setPadding(2,0,0,6);
        root.addView(tplCountTv);
        if(tplCount>0){
            TextView clearTpl=new TextView(this); clearTpl.setText("Clear all templates");
            clearTpl.setTextColor(BONE); clearTpl.setTextSize(12); clearTpl.setGravity(Gravity.CENTER);
            clearTpl.setPadding(0,10,0,10); clearTpl.setBackground(box(0xFF1A0C0E,6,BLOOD,2));
            LinearLayout.LayoutParams ctll=new LinearLayout.LayoutParams(-1,-2); ctll.setMargins(0,0,0,6); clearTpl.setLayoutParams(ctll);
            clearTpl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                ChampionTemplates.clearAll(OverlayService.this); mode=4; showPanel();
            }});
            root.addView(clearTpl);
        }

        // divider
        TextView scanDiv=new TextView(this); scanDiv.setText("────────────────────");
        scanDiv.setTextColor(EDGE); scanDiv.setTextSize(8);
        LinearLayout.LayoutParams sdl=new LinearLayout.LayoutParams(-1,-2); sdl.setMargins(0,14,0,14); scanDiv.setLayoutParams(sdl);
        root.addView(scanDiv);

        TextView hdr=new TextView(this); hdr.setText("◇ TRANSPARENCY");
        hdr.setTextColor(GOLD); hdr.setTextSize(11); hdr.setTypeface(null,android.graphics.Typeface.BOLD);
        hdr.setLetterSpacing(0.1f); hdr.setPadding(2,4,0,6); root.addView(hdr);

        int alphaPct=Math.round(pool.getAlpha()*100);
        final TextView alphaLabel=new TextView(this);
        alphaLabel.setText(alphaPct+"%");
        alphaLabel.setTextColor(BONE); alphaLabel.setTextSize(13); alphaLabel.setGravity(Gravity.END);
        LinearLayout.LayoutParams all=new LinearLayout.LayoutParams(-1,-2); all.setMargins(0,0,0,4); alphaLabel.setLayoutParams(all);
        root.addView(alphaLabel);

        android.widget.SeekBar alphaBar=new android.widget.SeekBar(this);
        alphaBar.setMax(80); // progress 0-80 maps to 20%-100%
        alphaBar.setProgress(Math.max(0,alphaPct-20));
        alphaBar.setProgressTintList(android.content.res.ColorStateList.valueOf(BLOODL));
        alphaBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(EDGE));
        alphaBar.setThumbTintList(android.content.res.ColorStateList.valueOf(BONE));
        LinearLayout.LayoutParams abl=new LinearLayout.LayoutParams(-1,-2); abl.setMargins(0,0,0,14); alphaBar.setLayoutParams(abl);
        alphaBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser){
                float av=(progress+20)/100f;
                pool.setAlpha(av);
                button.setAlpha(av);
                if(panel!=null) panel.setAlpha(av);
                alphaLabel.setText((progress+20)+"%");
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar){}
            public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
        root.addView(alphaBar);

        TextView hdr2=new TextView(this); hdr2.setText("◇ HAPTIC");
        hdr2.setTextColor(GOLD); hdr2.setTextSize(11); hdr2.setTypeface(null,android.graphics.Typeface.BOLD);
        hdr2.setLetterSpacing(0.1f); hdr2.setPadding(2,18,0,6); root.addView(hdr2);

        boolean curHaptic=pool.getHaptic();
        LinearLayout hRow=new LinearLayout(this); hRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] hLabels={"ON","OFF"}; boolean[] hVals={true,false};
        for(int i=0;i<2;i++){
            final boolean hv=hVals[i];
            TextView btn=new TextView(this); btn.setText(hLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(curHaptic==hv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.setHaptic(hv); showPanel();
            }});
            hRow.addView(btn);
        }
        root.addView(hRow);

        TextView hdr3=new TextView(this); hdr3.setText("◇ OPEN TAB");
        hdr3.setTextColor(GOLD); hdr3.setTextSize(11); hdr3.setTypeface(null,android.graphics.Typeface.BOLD);
        hdr3.setLetterSpacing(0.1f); hdr3.setPadding(2,18,0,6); root.addView(hdr3);

        int curStart=pool.getStartTab();
        String[] stLabels={"smart","always pool"}; int[] stVals={0,1};
        LinearLayout stRow=new LinearLayout(this); stRow.setGravity(Gravity.CENTER_VERTICAL);
        for(int i=0;i<2;i++){
            final int sv=stVals[i];
            TextView btn=new TextView(this); btn.setText(stLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(curStart==sv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.setStartTab(sv); showPanel();
            }});
            stRow.addView(btn);
        }
        root.addView(stRow);

        TextView hdr4=new TextView(this); hdr4.setText("◇ POSITION");
        hdr4.setTextColor(GOLD); hdr4.setTextSize(11); hdr4.setTypeface(null,android.graphics.Typeface.BOLD);
        hdr4.setLetterSpacing(0.1f); hdr4.setPadding(2,18,0,6); root.addView(hdr4);

        TextView resetPos=new TextView(this); resetPos.setText("Reset button position");
        resetPos.setTextColor(BONE); resetPos.setTextSize(12); resetPos.setGravity(Gravity.CENTER);
        resetPos.setPadding(0,10,0,10);
        resetPos.setBackground(box(CARD,6,EDGE,1));
        LinearLayout.LayoutParams rpl=new LinearLayout.LayoutParams(-1,-2); rpl.setMargins(0,0,0,0); resetPos.setLayoutParams(rpl);
        resetPos.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            btnLp.x=20; btnLp.y=300;
            try{ wm.updateViewLayout(button,btnLp); }catch(Exception e){}
        }});
        root.addView(resetPos);

        // ◇ CALIBRATE SCAN
        TextView calDiv=new TextView(this); calDiv.setText("────────────────────");
        calDiv.setTextColor(EDGE); calDiv.setTextSize(8);
        LinearLayout.LayoutParams cdlp=new LinearLayout.LayoutParams(-1,-2); cdlp.setMargins(0,14,0,14); calDiv.setLayoutParams(cdlp);
        root.addView(calDiv);

        String calMode = isPortrait ? "PORTRAIT" : "LANDSCAPE";
        TextView calHdr=new TextView(this); calHdr.setText("◇ CALIBRATE SCAN  (" + calMode + ")");
        calHdr.setTextColor(GOLD); calHdr.setTextSize(11); calHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        calHdr.setLetterSpacing(0.1f); calHdr.setPadding(2,4,0,4); root.addView(calHdr);

        TextView calInfo=new TextView(this);
        calInfo.setText("Tap to calibrate: follow the 5-step guide to set probe positions by tapping real units in TFT. Or fine-tune manually with the sliders below.");
        calInfo.setTextColor(ASH); calInfo.setTextSize(10); calInfo.setPadding(2,0,0,8);
        root.addView(calInfo);

        TextView tapCalBtn=new TextView(this); tapCalBtn.setText("TAP TO CALIBRATE (recommended)");
        tapCalBtn.setTextColor(BONE); tapCalBtn.setTextSize(13); tapCalBtn.setGravity(Gravity.CENTER);
        tapCalBtn.setPadding(0,12,0,12); tapCalBtn.setBackground(box(BLOOD,6,BLOODL,2));
        LinearLayout.LayoutParams tcbl=new LinearLayout.LayoutParams(-1,-2); tcbl.setMargins(0,0,0,12); tapCalBtn.setLayoutParams(tcbl);
        tapCalBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ startTapCalibration(); }});
        root.addView(tapCalBtn);

        String[] calLabels={"Board top row","Board bottom row","Board left edge","Board right edge","Bench row"};
        final TextView[] calValTvs=new TextView[5];
        for(int ci=0;ci<5;ci++){
            final int cii=ci;
            LinearLayout crow=new LinearLayout(this); crow.setOrientation(LinearLayout.HORIZONTAL);
            crow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams crp=new LinearLayout.LayoutParams(-1,-2); crp.setMargins(0,0,0,5); crow.setLayoutParams(crp);

            TextView clbl=new TextView(this); clbl.setText(calLabels[ci]);
            clbl.setTextColor(ASH); clbl.setTextSize(11);
            clbl.setLayoutParams(new LinearLayout.LayoutParams(0,-2,2.5f)); crow.addView(clbl);

            TextView cMinus=new TextView(this); cMinus.setText("−");
            cMinus.setTextColor(BONE); cMinus.setTextSize(18); cMinus.setGravity(Gravity.CENTER);
            cMinus.setPadding(0,6,0,6); cMinus.setBackground(box(CARD,4,EDGE,1));
            LinearLayout.LayoutParams cmp=new LinearLayout.LayoutParams(0,-2,0.8f); cmp.setMargins(0,0,4,0); cMinus.setLayoutParams(cmp);
            crow.addView(cMinus);

            TextView cValTv=new TextView(this); cValTv.setText(calGet(cii)+"%");
            cValTv.setTextColor(GOLD); cValTv.setTextSize(13); cValTv.setGravity(Gravity.CENTER);
            cValTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            calValTvs[ci]=cValTv; crow.addView(cValTv);

            TextView cPlus=new TextView(this); cPlus.setText("+");
            cPlus.setTextColor(BONE); cPlus.setTextSize(18); cPlus.setGravity(Gravity.CENTER);
            cPlus.setPadding(0,6,0,6); cPlus.setBackground(box(CARD,4,EDGE,1));
            LinearLayout.LayoutParams cpp=new LinearLayout.LayoutParams(0,-2,0.8f); cpp.setMargins(4,0,0,0); cPlus.setLayoutParams(cpp);
            crow.addView(cPlus);

            cMinus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ calSet(cii,calGet(cii)-1); calValTvs[cii].setText(calGet(cii)+"%"); }});
            cPlus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ calSet(cii,calGet(cii)+1); calValTvs[cii].setText(calGet(cii)+"%"); }});
            root.addView(crow);
        }

        LinearLayout calBtnRow=new LinearLayout(this); calBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cbrp=new LinearLayout.LayoutParams(-1,-2); cbrp.setMargins(0,10,0,4); calBtnRow.setLayoutParams(cbrp);

        TextView showDots=new TextView(this); showDots.setText("SHOW DOTS");
        showDots.setTextColor(BONE); showDots.setTextSize(12); showDots.setGravity(Gravity.CENTER);
        showDots.setPadding(0,10,0,10); showDots.setBackground(box(CARD,6,EDGE,2));
        LinearLayout.LayoutParams sdlp=new LinearLayout.LayoutParams(0,-2,1f); sdlp.setMargins(0,0,4,0); showDots.setLayoutParams(sdlp);
        showDots.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ closePanel(); showProbeDots(); }});
        calBtnRow.addView(showDots);

        TextView resetCal=new TextView(this); resetCal.setText("RESET");
        resetCal.setTextColor(BONE); resetCal.setTextSize(12); resetCal.setGravity(Gravity.CENTER);
        resetCal.setPadding(0,10,0,10); resetCal.setBackground(box(0xFF1A0C0E,6,BLOOD,1));
        resetCal.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        resetCal.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(isPortrait) pool.resetPortraitCalibration(); else pool.resetCalibration();
            mode=4; showPanel();
        }});
        calBtnRow.addView(resetCal);
        root.addView(calBtnRow);

        TextView calHint=new TextView(this);
        calHint.setText("Red = board probes  ·  Blue = bench  ·  dots vanish after 5s");
        calHint.setTextColor(DIM); calHint.setTextSize(10); calHint.setPadding(2,2,0,0);
        root.addView(calHint);

    }

    // ---- tap-to-calibrate ----

    private void startTapCalibration(){
        calStep=1;
        calTapMarks.clear();
        calDebugLine="";
        closePanel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            new Runnable(){ public void run(){ showCalCaptureOverlay(); }}, 300);
    }

    @SuppressWarnings("deprecation")
    private void showCalCaptureOverlay(){
        hideCalCaptureView();
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        final float spx=getResources().getDisplayMetrics().scaledDensity;

        calCaptureView=new View(OverlayService.this){
            private final android.graphics.Paint bgP=new android.graphics.Paint();
            private final android.graphics.Paint txtP=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                int W=getWidth(), H=getHeight();
                bgP.setColor(0x44000000);
                canvas.drawRect(0,0,W,H,bgP);

                // crosshairs for every tap captured so far — confirms WHERE the tap registered
                txtP.setTextAlign(android.graphics.Paint.Align.CENTER);
                for(int i=0;i<calTapMarks.size();i++){
                    float[] m=calTapMarks.get(i);
                    txtP.setStyle(android.graphics.Paint.Style.STROKE);
                    txtP.setStrokeWidth(3); txtP.setColor(0xFF39FF14);
                    canvas.drawCircle(m[0],m[1],26,txtP);
                    canvas.drawLine(m[0]-40,m[1],m[0]+40,m[1],txtP);
                    canvas.drawLine(m[0],m[1]-40,m[0],m[1]+40,txtP);
                    txtP.setStyle(android.graphics.Paint.Style.FILL);
                    txtP.setColor(0xFF39FF14); txtP.setTextSize(13*spx);
                    canvas.drawText(String.valueOf(i+1),m[0],m[1]-32,txtP);
                }

                float barH=H*0.20f;
                bgP.setColor(0xF00B0709);
                canvas.drawRect(0,0,W,barH,bgP);
                txtP.setStyle(android.graphics.Paint.Style.FILL);
                txtP.setTextSize(11*spx);
                txtP.setColor(0xFF7A6B60);
                String stepLabel, stepMsg;
                switch(calStep){
                    case 1: stepLabel="STEP 1 OF 5 — TAP TO CALIBRATE"; stepMsg="Tap the BACK row, LEFT-most unit"; break;
                    case 2: stepLabel="STEP 2 OF 5 — TAP TO CALIBRATE"; stepMsg="Tap the BACK row, RIGHT-most unit"; break;
                    case 3: stepLabel="STEP 3 OF 5 — TAP TO CALIBRATE"; stepMsg="Tap the FRONT row, LEFT-most unit"; break;
                    case 4: stepLabel="STEP 4 OF 5 — TAP TO CALIBRATE"; stepMsg="Tap the FRONT row, RIGHT-most unit"; break;
                    case 5: stepLabel="STEP 5 OF 5 — TAP TO CALIBRATE"; stepMsg="Tap any BENCH unit (or skip below)"; break;
                    default: stepLabel=""; stepMsg="";
                }
                canvas.drawText(stepLabel, W/2f, barH*0.30f, txtP);
                txtP.setTextSize(14*spx);
                txtP.setColor(0xFFE0D5C0);
                txtP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(stepMsg, W/2f, barH*0.58f, txtP);
                txtP.setTypeface(android.graphics.Typeface.DEFAULT);
                // live debug readout: window size + last tap px/percent
                txtP.setTextSize(9*spx);
                txtP.setColor(0xFFC9A227);
                String dbg="view "+W+"x"+H+(calDebugLine.isEmpty()?"":"   "+calDebugLine);
                canvas.drawText(dbg, W/2f, barH*0.85f, txtP);

                float cancelTop=H*0.88f;
                bgP.setColor(0xF00B0709);
                canvas.drawRect(0,cancelTop,W,H,bgP);
                txtP.setTextSize(13*spx);
                txtP.setColor(0xFFC1121F);
                canvas.drawText(calStep==5?"SKIP BENCH":"CANCEL", W/2f, (cancelTop+H)/2f+5*spx, txtP);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent e){
                if(e.getAction()==android.view.MotionEvent.ACTION_UP){
                    int W=getWidth(), H=getHeight();
                    float vx=e.getX(), vy=e.getY();
                    if(vy>=H*0.88f){
                        if(calStep==5) finishCalibration();
                        else{ calStep=0; hideCalCaptureView(); mode=4; showPanel(); }
                    } else if(vy<=H*0.20f){
                        // tapped inside the instruction banner — ignore
                    } else {
                        // view-local coords map 1:1 to the probe-dots window (same size, same 0,0 origin)
                        calTapMarks.add(new float[]{vx,vy});
                        int xPct=Math.round(vx*100/W);
                        int yPct=Math.round(vy*100/H);
                        calDebugLine="tap "+(int)vx+","+(int)vy+" = "+xPct+"%,"+yPct+"%";
                        handleCalTap(xPct,yPct,W>H?false:true);
                    }
                }
                return true;
            }
        };
        calCaptureView.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        WindowManager.LayoutParams clp=new WindowManager.LayoutParams(
            sw,sh,0,0,
            Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        clp.gravity=Gravity.TOP|Gravity.LEFT;
        try{ wm.addView(calCaptureView,clp); }catch(Exception ex){ calCaptureView=null; }
    }

    // xPct/yPct are already in the probe-dots coordinate space (0-100 of the same window)
    private void handleCalTap(int xPct, int yPct, boolean portrait){
        if(calStep==1){
            // Step 1: back-row, leftmost unit (column 0) — store its center directly
            calTmpTopY = yPct; calTmpTopLeft = xPct;
            if(portrait) pool.setPortraitBoardTopPct(yPct);
            else pool.setBoardTopPct(yPct);
            calStep=2; if(calCaptureView!=null) calCaptureView.invalidate();
        } else if(calStep==2){
            // Step 2: back-row, rightmost unit (column 6, same row) — store its center directly
            calTmpTopRight = xPct;
            calStep=3; if(calCaptureView!=null) calCaptureView.invalidate();
        } else if(calStep==3){
            // Step 3: front-row, leftmost unit (column 0) — measured directly.
            // TFT's hex grid staggers alternating rows sideways (pointy-top hexes), so the
            // old approach of inferring this corner via left-right symmetry was wrong —
            // the board is NOT a simple symmetric trapezoid. Tap it for real instead.
            calTmpBotY = yPct; calTmpBotLeft = xPct;
            calStep=4; if(calCaptureView!=null) calCaptureView.invalidate();
        } else if(calStep==4){
            // Step 4: front-row, rightmost unit (column 6) — all 4 corners now measured directly.
            // We store the raw column-0 / column-6 hex CENTERS for the back and front rows
            // (not edges — buildProbeGrid interpolates column centers directly between them,
            // and also derives the row-stagger amount from how these centers differ).
            int topLeft  = calTmpTopLeft;
            int topRight = calTmpTopRight;
            int botLeft  = calTmpBotLeft;
            int botRight = xPct;
            int boardBotPct = calTmpBotY;

            if(portrait){
                pool.setPortraitBoardBotPct(boardBotPct);
                pool.setPortraitBoardTopLeftPct(topLeft); pool.setPortraitBoardTopRightPct(topRight);
                pool.setPortraitBoardBotLeftPct(botLeft); pool.setPortraitBoardBotRightPct(botRight);
                pool.setPortraitBoardLeftPct((topLeft+botLeft)/2);
                pool.setPortraitBoardRightPct((topRight+botRight)/2);
            } else {
                pool.setBoardBotPct(boardBotPct);
                pool.setBoardTopLeftPct(topLeft); pool.setBoardTopRightPct(topRight);
                pool.setBoardBotLeftPct(botLeft); pool.setBoardBotRightPct(botRight);
                pool.setBoardLeftPct((topLeft+botLeft)/2);
                pool.setBoardRightPct((topRight+botRight)/2);
            }
            calDebugLine = "tL:"+topLeft+"% tR:"+topRight+"% bL:"+botLeft+"% bR:"+botRight+"%";
            calStep=5; if(calCaptureView!=null) calCaptureView.invalidate();
        } else if(calStep==5){
            if(portrait) pool.setPortraitBenchYPct(yPct);
            else pool.setBenchYPct(yPct);
            finishCalibration();
        }
    }

    private void finishCalibration(){
        calStep=0;
        hideCalCaptureView();
        isPortrait=false; // will be recalculated in showPanel()
        mode=4; showPanel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            new Runnable(){ public void run(){ showProbeDots(); }}, 400);
    }

    private void hideCalCaptureView(){
        if(calCaptureView!=null){ try{ wm.removeView(calCaptureView); }catch(Exception e){} calCaptureView=null; }
    }

    private int calGet(int idx){
        if(isPortrait) switch(idx){
            case 0: return pool.getPortraitBoardTopPct();
            case 1: return pool.getPortraitBoardBotPct();
            case 2: return pool.getPortraitBoardLeftPct();
            case 3: return pool.getPortraitBoardRightPct();
            default: return pool.getPortraitBenchYPct();
        }
        switch(idx){
            case 0: return pool.getBoardTopPct();
            case 1: return pool.getBoardBotPct();
            case 2: return pool.getBoardLeftPct();
            case 3: return pool.getBoardRightPct();
            default: return pool.getBenchYPct();
        }
    }
    private void calSet(int idx, int val){
        if(isPortrait) switch(idx){
            case 0: pool.setPortraitBoardTopPct(val); return;
            case 1: pool.setPortraitBoardBotPct(val); return;
            case 2: pool.setPortraitBoardLeftPct(val); return;
            case 3: pool.setPortraitBoardRightPct(val); return;
            default: pool.setPortraitBenchYPct(val); return;
        }
        switch(idx){
            case 0: pool.setBoardTopPct(val); break;
            case 1: pool.setBoardBotPct(val); break;
            case 2: pool.setBoardLeftPct(val); break;
            case 3: pool.setBoardRightPct(val); break;
            default: pool.setBenchYPct(val); break;
        }
    }

    @SuppressWarnings("deprecation")
    private void showProbeDots(){
        hideProbeDots();
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        final java.util.List<int[]> probes=buildProbeGrid(sw,sh);
        final int boardCount=autoTapBoardProbeCount;

        // The board is drawn in perspective — back rows sit closer together than front rows.
        // A fixed dot radius overlaps in the back rows, looking like a tangled mesh even though
        // the centers themselves form a clean trapezoid. Size the dots to the tightest gap
        // between any two neighboring probe points so they never overlap, capped at 28px.
        final int cols=7;
        float minGap=Float.MAX_VALUE;
        for(int i=0;i<boardCount;i++){
            int[] p=probes.get(i);
            int row=i/cols, col=i%cols;
            if(col<cols-1){
                int[] q=probes.get(i+1);
                float d=(float)Math.hypot(q[0]-p[0],q[1]-p[1]);
                if(d<minGap) minGap=d;
            }
            if(row<(boardCount/cols)-1){
                int[] q=probes.get(i+cols);
                float d=(float)Math.hypot(q[0]-p[0],q[1]-p[1]);
                if(d<minGap) minGap=d;
            }
        }
        final float dotR = (minGap==Float.MAX_VALUE) ? 28f : Math.max(10f, Math.min(28f, minGap*0.42f));
        final float txtSize = Math.max(11f, dotR*0.72f);

        android.view.View dots=new android.view.View(this){
            @Override protected void onDraw(android.graphics.Canvas canvas){
                android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                for(int i=0;i<probes.size();i++){
                    int[] pt=probes.get(i);
                    boolean bench=(i>=boardCount);
                    paint.setStyle(android.graphics.Paint.Style.FILL);
                    paint.setColor(bench?0x880044FF:0x88FF2200);
                    canvas.drawCircle(pt[0],pt[1],dotR,paint);
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setStrokeWidth(3);
                    paint.setColor(0xCCFFFFFF);
                    canvas.drawCircle(pt[0],pt[1],dotR,paint);
                    paint.setStyle(android.graphics.Paint.Style.FILL);
                    paint.setColor(0xFFFFFFFF);
                    paint.setTextSize(txtSize);
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    canvas.drawText(String.valueOf(i+1),pt[0],pt[1]+txtSize*0.35f,paint);
                }
            }
        };
        dots.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);

        WindowManager.LayoutParams dlp=new WindowManager.LayoutParams(
            sw,sh,0,0,
            Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                |WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        dlp.gravity=Gravity.TOP|Gravity.LEFT;
        probeDotsView=dots;
        try{ wm.addView(probeDotsView,dlp); }catch(Exception e){ probeDotsView=null; return; }
        probeDotsHandler.postDelayed(new Runnable(){ public void run(){ hideProbeDots(); }},5000);
    }

    private void hideProbeDots(){
        probeDotsHandler.removeCallbacksAndMessages(null);
        if(probeDotsView!=null){
            try{ wm.removeView(probeDotsView); }catch(Exception e){}
            probeDotsView=null;
        }
    }

    // ---- screen scanning ----

    private void triggerScan(){
        // Close panel first so TFT is visible when the screenshot runs.
        // 350ms is enough for WindowManager to remove the view before the capture.
        closePanel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){ public void run(){
            if(Build.VERSION.SDK_INT >= 31 && TFTAccessibilityService.instance != null){
                triggerScanAccessibility();
            } else {
                Intent si=new Intent(OverlayService.this,ScanPermActivity.class);
                si.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(si);
            }
        }}, 350);
    }

    @SuppressWarnings("NewApi")
    private void triggerScanAccessibility(){
        addScanLog("triggerScan: accessibility path (no app switch)");
        TFTAccessibilityService svc = TFTAccessibilityService.instance;
        if(svc == null){ // race: service disconnected between check and call
            closePanel();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
                Intent si=new Intent(OverlayService.this,ScanPermActivity.class);
                si.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(si);
            },150);
            return;
        }
        svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
            new AccessibilityService.TakeScreenshotCallback(){
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                    addScanLog("screenshot ok, running OCR");
                    try {
                        android.hardware.HardwareBuffer hb = result.getHardwareBuffer();
                        Bitmap hw = Bitmap.wrapHardwareBuffer(hb, null);
                        hb.close();
                        Bitmap bmp = hw.copy(Bitmap.Config.ARGB_8888, false);
                        hw.recycle();
                        new ScreenScanner(OverlayService.this, null).scanBitmap(bmp,
                            new ScreenScanner.ScanCallback(){
                                public void onResult(ScreenScanner.ScanResult r){ applyScanResult(r); }
                                public void onError(String msg){
                                    lastScanStatus="✗ "+msg;
                                    android.widget.Toast.makeText(OverlayService.this,"✗ "+msg,android.widget.Toast.LENGTH_SHORT).show();
                                    mode=4; showPanel();
                                }
                            });
                    } catch(Exception e){
                        addScanLog("ERR accessibility scan: "+e.getMessage());
                        lastScanStatus="✗ "+e.getMessage();
                        mode=4; showPanel();
                    }
                }
                @Override public void onFailure(int errorCode){
                    addScanLog("ERR screenshot failed: "+errorCode);
                    lastScanStatus="✗ screenshot failed ("+errorCode+")";
                    android.widget.Toast.makeText(OverlayService.this,"✗ screenshot failed",android.widget.Toast.LENGTH_SHORT).show();
                    mode=4; showPanel();
                }
            });
    }

    private void applyScanResult(ScreenScanner.ScanResult r){
        if(r.gold>=0) pool.setGold(r.gold);
        if(r.level>=0){ level=r.level; pool.setLevel(r.level); }
        // Report shop champions in status but don't auto-mark them —
        // seeing a unit in the shop doesn't mean it was bought from the pool.
        // The debug log shows which names were detected so the user can verify.
        if(!r.shopChampions.isEmpty()){
            addScanLog("shop champs found: "+r.shopChampions.toString());
            if(!r.starLevels.isEmpty()) addScanLog("star levels: "+r.starLevels.toString());
        }
        if(!r.benchChampions.isEmpty()){
            addScanLog("bench champs: "+r.benchChampions.toString());
        }
        lastScanStatus="✓ "+r.status;
        Toast.makeText(this,"✓ "+r.status,Toast.LENGTH_SHORT).show();
        mode=4; showPanel();
    }

    // ---- board scan mode ----

    private void startBoardScanMode(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,"Enable Accessibility service first",Toast.LENGTH_SHORT).show();
            return;
        }
        boardScanMode=true;
        boardScanDeadline=System.currentTimeMillis()+25000;
        boardScanResults=new java.util.ArrayList<>();
        closePanel();
        addScanLog("board scan: started 25s window");

        boardCountdownRunnable=new Runnable(){ public void run(){
            if(!boardScanMode) return;
            long rem=boardScanDeadline-System.currentTimeMillis();
            if(rem<=0){ stopBoardScanMode(); return; }
            if(btnLabel!=null) btnLabel.setText(((int)((rem+999)/1000))+"s");
            boardHandler.postDelayed(this,500);
        }};
        boardHandler.post(boardCountdownRunnable);

        boardPollRunnable=new Runnable(){ public void run(){
            if(!boardScanMode) return;
            if(System.currentTimeMillis()>=boardScanDeadline){ stopBoardScanMode(); return; }
            triggerPopupScan();
            boardHandler.postDelayed(this,2500);
        }};
        boardHandler.postDelayed(boardPollRunnable,600);
    }

    private void stopBoardScanMode(){
        boardScanMode=false;
        if(boardPollRunnable!=null){ boardHandler.removeCallbacks(boardPollRunnable); boardPollRunnable=null; }
        if(boardCountdownRunnable!=null){ boardHandler.removeCallbacks(boardCountdownRunnable); boardCountdownRunnable=null; }
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("board scan: stopped, found "+boardScanResults.size()+" champs");
        mode=0; showPanel();
    }

    @SuppressWarnings("NewApi")
    private void startAutoTapScan(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,"Enable Accessibility service first",Toast.LENGTH_SHORT).show();
            return;
        }
        autoScanPending=true;
        autoScanResults=new java.util.ArrayList<>();
        autoScanGold=-1;
        autoScanLevel=-1;
        autoTapIndex=0;
        autoTapConsecutiveMisses=0;
        autoTapBoardProbeCount=0;
        autoTapProbes=new java.util.ArrayList<>();
        closePanel();
        if(btnLabel!=null) btnLabel.setText("...");
        addScanLog("auto-tap: starting, getting screen size");
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        try{
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            final int sw=hw.getWidth(), sh=hw.getHeight();
                            final Bitmap goldLvlBmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                            hb.close();
                            autoTapProbes=buildProbeGrid(sw,sh);
                            hw.recycle();
                            addScanLog("auto-tap: "+autoTapProbes.size()+" probes "+sw+"x"+sh);
                            if(btnLabel!=null) btnLabel.setText("0/"+autoTapProbes.size());
                            // one-time full-screen OCR pass to grab gold + level before the
                            // tapping starts — these corners are only readable on the board
                            // view (not in the per-probe popup crop), so capture them up front
                            new ScreenScanner(OverlayService.this,null).scanBitmap(goldLvlBmp,
                                new ScreenScanner.ScanCallback(){
                                    public void onResult(ScreenScanner.ScanResult r){
                                        autoScanGold=r.gold;
                                        autoScanLevel=r.level;
                                        addScanLog("auto-tap: gold="+r.gold+" level="+r.level);
                                        autoTapNextProbe();
                                    }
                                    public void onError(String msg){
                                        addScanLog("auto-tap gold/level OCR err: "+msg);
                                        autoTapNextProbe();
                                    }
                                }, ScreenScanner.MODE_FULL);
                        }catch(Exception e){ autoScanPending=false; addScanLog("ERR auto-tap init: "+e.getMessage()); mode=0; showPanel(); }
                    }
                    @Override public void onFailure(int errorCode){ autoScanPending=false; addScanLog("ERR auto-tap init shot: "+errorCode); mode=0; showPanel(); }
                });
        }catch(Exception e){ autoScanPending=false; addScanLog("ERR startAutoTapScan: "+e.getMessage()); mode=0; showPanel(); }
    }

    private java.util.List<int[]> buildProbeGrid(int w, int h){
        java.util.List<int[]> pts=new java.util.ArrayList<>();
        boolean portrait = h > w;
        int top, bot, topLeft, topRight, botLeft, botRight, benchY;
        if (portrait) {
            top      = h * pool.getPortraitBoardTopPct()      / 100;
            bot      = h * pool.getPortraitBoardBotPct()      / 100;
            topLeft  = w * pool.getPortraitBoardTopLeftPct()  / 100;
            topRight = w * pool.getPortraitBoardTopRightPct() / 100;
            botLeft  = w * pool.getPortraitBoardBotLeftPct()  / 100;
            botRight = w * pool.getPortraitBoardBotRightPct() / 100;
            benchY   = h * pool.getPortraitBenchYPct()        / 100;
        } else {
            top      = h * pool.getBoardTopPct()      / 100;
            bot      = h * pool.getBoardBotPct()      / 100;
            topLeft  = w * pool.getBoardTopLeftPct()  / 100;
            topRight = w * pool.getBoardTopRightPct() / 100;
            botLeft  = w * pool.getBoardBotLeftPct()  / 100;
            botRight = w * pool.getBoardBotRightPct() / 100;
            benchY   = h * pool.getBenchYPct()        / 100;
        }
        // The TFT board is 4 rows x 7 columns, drawn in PERSPECTIVE: back rows are
        // visually compressed, front rows are spread apart. We tried adding an extra
        // alternating row-stagger correction (derived from real measured PC coordinates)
        // in v1.28, but on TFT Mobile it overcorrected — small differences between the
        // two measured corner rows get amplified into a big alternating swing, producing
        // a dense crisscross mesh instead of clean rows. Reverted to plain smooth
        // interpolation between the four measured corners, which users reported as close.
        //   topLeft/topRight  = measured centers of column 0 / column 6, BACK row
        //   botLeft/botRight  = measured centers of column 0 / column 6, FRONT row
        //   top = back-row hex-center Y   ·   bot = front-row hex-center Y
        // ROW_F[0]=back ... ROW_F[3]=front. Gaps grow toward the front (perspective).
        final float[] ROW_F = {0f, 0.27f, 0.58f, 1f};
        int cols=7;
        int frontWidth = botRight - botLeft;
        int[] btnLoc=new int[2]; int btnW=0,btnH=0;
        if(button!=null){ button.getLocationOnScreen(btnLoc); btnW=button.getWidth(); btnH=button.getHeight(); }
        for(int row=3;row>=0;row--){
            float t = ROW_F[row];
            int cy = top + (int)(t * (bot - top));
            int rowLeft  = (int)(topLeft  + t * (botLeft  - topLeft));
            int rowRight = (int)(topRight + t * (botRight - topRight));
            for(int col=0;col<cols;col++){
                int cx=rowLeft+col*(rowRight-rowLeft)/(cols-1);
                if(btnW>0&&cx>=btnLoc[0]-30&&cx<=btnLoc[0]+btnW+30
                          &&cy>=btnLoc[1]-30&&cy<=btnLoc[1]+btnH+30) continue;
                pts.add(new int[]{cx,cy});
            }
        }
        autoTapBoardProbeCount=pts.size();
        int benchCols=9;
        // Bench is below the board and physically wider (9 slots vs 7 hexes). botLeft/botRight
        // are the measured CENTERS of the front row's outer hexes, so nudge outward by half a
        // hex-gap on each side to approximate the bench's true span before spreading 9 slots.
        int benchHalfGap = frontWidth / 12;
        int benchLeft  = botLeft  - benchHalfGap;
        int benchRight = botRight + benchHalfGap;
        for(int col=0;col<benchCols;col++){
            int cx=benchLeft+(int)((col+0.5f)*(benchRight-benchLeft)/benchCols);
            if(btnW>0&&cx>=btnLoc[0]-30&&cx<=btnLoc[0]+btnW+30
                      &&benchY>=btnLoc[1]-30&&benchY<=btnLoc[1]+btnH+30) continue;
            pts.add(new int[]{cx,benchY});
        }
        return pts;
    }

    private void dispatchTap(float x, float y, final Runnable onDone){
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null){ onDone.run(); return; }
        try{
            android.graphics.Path path=new android.graphics.Path();
            path.moveTo(x,y);
            android.accessibilityservice.GestureDescription.StrokeDescription stroke=
                new android.accessibilityservice.GestureDescription.StrokeDescription(path,0,TAP_STROKE_MS);
            android.accessibilityservice.GestureDescription gesture=
                new android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(stroke).build();
            svc.dispatchGesture(gesture,
                new android.accessibilityservice.AccessibilityService.GestureResultCallback(){
                    @Override public void onCompleted(android.accessibilityservice.GestureDescription d){ onDone.run(); }
                    @Override public void onCancelled(android.accessibilityservice.GestureDescription d){ onDone.run(); }
                }, null);
        }catch(Exception e){ addScanLog("ERR dispatchTap: "+e.getMessage()); onDone.run(); }
    }

    @SuppressWarnings("NewApi")
    private void autoTapNextProbe(){
        if(!autoScanPending) return;
        if(autoTapIndex>=autoTapProbes.size()){ finishAutoTapScan(); return; }
        // reset miss counter when entering bench phase
        if(autoTapBoardProbeCount>0 && autoTapIndex==autoTapBoardProbeCount){
            autoTapConsecutiveMisses=0;
            addScanLog("auto-tap: bench phase");
        }
        if(btnLabel!=null) btnLabel.setText(autoTapIndex+"/"+autoTapProbes.size());
        int[] pt=autoTapProbes.get(autoTapIndex);
        final float px=pt[0], py=pt[1];
        addScanLog("auto-tap: probe "+(autoTapIndex+1)+"/"+autoTapProbes.size()+" @"+((int)px)+","+((int)py));
        dispatchTap(px, py, new Runnable(){ public void run(){
            // wait for popup to render before taking screenshot
            autoTapHandler.postDelayed(new Runnable(){ public void run(){
                if(!autoScanPending) return;
                TFTAccessibilityService svc=TFTAccessibilityService.instance;
                if(svc==null){ finishAutoTapScan(); return; }
                try{
                    svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                        new AccessibilityService.TakeScreenshotCallback(){
                            @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                                try{
                                    android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                                    Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                                    hb.close();
                                    final int sw=hw.getWidth(), sh=hw.getHeight();
                                    final Bitmap full=hw.copy(Bitmap.Config.ARGB_8888,false);
                                    hw.recycle();
                                    // crop to popup band — board rows 39-60%, bench 72%, popups appear above units
                                    boolean portrait=sh>sw;
                                    final int cropTop=portrait? sh*8/100 : sh*15/100;
                                    int cropBot=sh*78/100;
                                    Bitmap crop=Bitmap.createBitmap(full,0,cropTop,sw,cropBot-cropTop);
                                    new ScreenScanner(OverlayService.this,null).scanPopupZone(crop,sw,sh,
                                        new ScreenScanner.ScanCallback(){
                                            public void onResult(ScreenScanner.ScanResult r){
                                                if(r.detectedBoardUnit!=null&&!r.detectedBoardUnit.isEmpty()
                                                        &&r.detectedPopupBounds!=null){
                                                    r.detectedPopupBounds.offset(0,cropTop); // crop->full coords
                                                    applyAutoTapProbeResult(r,full);
                                                } else {
                                                    full.recycle();
                                                    applyAutoTapProbeResult(r,null);
                                                }
                                            }
                                            public void onError(String msg){ full.recycle(); addScanLog("auto-tap OCR err: "+msg); advanceAutoTap(); }
                                        });
                                }catch(Exception e){ addScanLog("ERR auto-tap probe: "+e.getMessage()); advanceAutoTap(); }
                            }
                            @Override public void onFailure(int errorCode){ addScanLog("ERR auto-tap shot: "+errorCode); advanceAutoTap(); }
                        });
                }catch(Exception e){ addScanLog("ERR auto-tap svc: "+e.getMessage()); advanceAutoTap(); }
            }},POPUP_WAIT_MS);
        }});
    }

    private void advanceAutoTap(){
        autoTapIndex++;
        autoTapHandler.postDelayed(new Runnable(){ public void run(){ autoTapNextProbe(); }},PROBE_GAP_MS);
    }

    private void applyAutoTapProbeResult(ScreenScanner.ScanResult r, final Bitmap sourceBmp){
        if(!autoScanPending){ if(sourceBmp!=null) sourceBmp.recycle(); return; }
        boolean inBenchPhase=(autoTapBoardProbeCount>0 && autoTapIndex>=autoTapBoardProbeCount);
        if(r.detectedBoardUnit!=null && !r.detectedBoardUnit.isEmpty()){
            final String name=r.detectedBoardUnit;
            int stars=Math.max(1,r.detectedBoardStars);
            pool.add(name,1);
            buzz();
            StringBuilder entry=new StringBuilder(name);
            for(int i=0;i<stars;i++) entry.append("★");
            autoScanResults.add(entry.toString());
            autoTapConsecutiveMisses=0;
            if(sourceBmp!=null){
                final android.graphics.Rect bounds=r.detectedPopupBounds;
                new Thread(new Runnable(){ public void run(){
                    ChampionTemplates.saveTemplate(OverlayService.this,name,sourceBmp,bounds);
                    sourceBmp.recycle();
                }}).start();
            }
            addScanLog("auto-tap: +"+name+" "+stars+"★");
            if(btnLabel!=null) btnLabel.setText("+"+name.split(" ")[0]);
        } else {
            if(inBenchPhase){
                if(r.detectedPopupBounds!=null){
                    // item/ability popup on bench — skip, don't count as miss
                    addScanLog("auto-tap: non-champion bench popup, skipping");
                } else {
                    // truly empty bench slot
                    autoTapConsecutiveMisses++;
                    if(autoTapConsecutiveMisses>=3){ finishAutoTapScan(); return; }
                }
            } else {
                // board: count ALL non-hits (empty hex AND stray augment/item popups)
                // so probes off the board don't keep the scan running indefinitely
                autoTapConsecutiveMisses++;
                if(autoTapConsecutiveMisses>=8){
                    // skip straight to bench — threshold 8 allows one full empty row (7 cols)
                    // before aborting, so an empty top/bottom row doesn't prematurely end the scan
                    autoTapIndex=autoTapBoardProbeCount-1; // -1 because advanceAutoTap adds 1
                    autoTapConsecutiveMisses=0;
                    addScanLog("auto-tap: 8 board misses, jumping to bench");
                }
            }
        }
        advanceAutoTap();
    }

    private void finishAutoTapScan(){
        autoScanPending=false;
        autoTapHandler.removeCallbacksAndMessages(null);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        if(autoScanGold>=0) pool.setGold(autoScanGold);
        if(autoScanLevel>=0){ level=autoScanLevel; pool.setLevel(autoScanLevel); }
        addScanLog("auto-tap: done, "+autoScanResults.size()+" hits / "+autoTapProbes.size()+" probes"
                +" · gold="+autoScanGold+" level="+autoScanLevel);
        mode=0; showPanel();
    }

    @SuppressWarnings("NewApi")
    private void triggerPopupScan(){
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null){ addScanLog("board scan: svc null"); return; }
        addScanLog("board scan: popup screenshot");
        try{
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            hb.close();
                            Bitmap bmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                            hw.recycle();
                            final Bitmap bmpForTemplate=bmp.copy(Bitmap.Config.ARGB_8888,false);
                            new ScreenScanner(OverlayService.this,null).scanBitmap(bmp,
                                new ScreenScanner.ScanCallback(){
                                    public void onResult(ScreenScanner.ScanResult r){
                                        if(oppScanMode) applyOppPopupScanResult(r,bmpForTemplate);
                                        else if(boardScanMode) applyPopupScanResult(r,bmpForTemplate);
                                        else { bmpForTemplate.recycle(); if(debugScanPending){
                                            debugScanPending=false;
                                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){ public void run(){
                                                mode=4; showPanel();
                                            }}, 500);
                                        }}
                                    }
                                    public void onError(String msg){ bmpForTemplate.recycle(); addScanLog("board scan OCR err: "+msg); }
                                }, ScreenScanner.MODE_POPUP);
                        }catch(Exception e){ addScanLog("ERR board scan: "+e.getMessage()); }
                    }
                    @Override public void onFailure(int errorCode){ addScanLog("ERR board scan shot: "+errorCode); }
                });
        }catch(Exception e){ addScanLog("ERR triggerPopupScan: "+e.getMessage()); }
    }

    private void applyPopupScanResult(ScreenScanner.ScanResult r, final Bitmap sourceBmp){
        if(r.detectedBoardUnit==null||r.detectedBoardUnit.isEmpty()){ sourceBmp.recycle(); return; }
        final String name=r.detectedBoardUnit;
        if(boardScanResults.contains(name)){ sourceBmp.recycle(); return; }
        boardScanResults.add(name);
        pool.add(name,1);
        buzz();
        if(r.detectedPopupBounds!=null){
            final android.graphics.Rect bounds=r.detectedPopupBounds;
            new Thread(new Runnable(){ public void run(){
                ChampionTemplates.saveTemplate(OverlayService.this, name, sourceBmp, bounds);
                sourceBmp.recycle();
            }}).start();
        } else {
            sourceBmp.recycle();
        }
        addScanLog("board scan: added "+name+(r.detectedPopupBounds!=null?" (template saved)":""));
        if(btnLabel!=null){
            btnLabel.setText("+"+name.split(" ")[0]);
            boardHandler.postDelayed(new Runnable(){ public void run(){
                if(boardScanMode&&btnLabel!=null){
                    long rem=boardScanDeadline-System.currentTimeMillis();
                    if(rem>0) btnLabel.setText(((int)((rem+999)/1000))+"s");
                }
            }},1200);
        }
    }

    private void startOppScanMode(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,"Enable Accessibility service first",Toast.LENGTH_SHORT).show();
            return;
        }
        oppScanMode=true;
        oppScanDeadline=System.currentTimeMillis()+30000;
        oppScanResults=new java.util.LinkedHashMap<>();
        closePanel();
        addScanLog("opp scan: started 30s window");

        oppCountdownRunnable=new Runnable(){ public void run(){
            if(!oppScanMode) return;
            long rem=oppScanDeadline-System.currentTimeMillis();
            if(rem<=0){ stopOppScanMode(); return; }
            if(btnLabel!=null) btnLabel.setText(((int)((rem+999)/1000))+"s");
            boardHandler.postDelayed(this,500);
        }};
        boardHandler.post(oppCountdownRunnable);

        oppPollRunnable=new Runnable(){ public void run(){
            if(!oppScanMode) return;
            if(System.currentTimeMillis()>=oppScanDeadline){ stopOppScanMode(); return; }
            triggerPopupScan();
            boardHandler.postDelayed(this,2500);
        }};
        boardHandler.postDelayed(oppPollRunnable,600);
    }

    private void stopOppScanMode(){
        oppScanMode=false;
        if(oppPollRunnable!=null){ boardHandler.removeCallbacks(oppPollRunnable); oppPollRunnable=null; }
        if(oppCountdownRunnable!=null){ boardHandler.removeCallbacks(oppCountdownRunnable); oppCountdownRunnable=null; }
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("opp scan: stopped, found "+oppScanResults.size()+" champs");
        mode=0; showPanel();
    }

    private void applyOppPopupScanResult(ScreenScanner.ScanResult r, final Bitmap sourceBmp){
        if(r.detectedBoardUnit==null||r.detectedBoardUnit.isEmpty()){ sourceBmp.recycle(); return; }
        final String name=r.detectedBoardUnit;
        if(oppScanResults.containsKey(name)){ sourceBmp.recycle(); return; }
        int stars=Math.max(1,r.detectedBoardStars);
        oppScanResults.put(name,stars);
        pool.addOpp(name,1);
        buzz();
        if(r.detectedPopupBounds!=null){
            final android.graphics.Rect bounds=r.detectedPopupBounds;
            new Thread(new Runnable(){ public void run(){
                ChampionTemplates.saveTemplate(OverlayService.this, name, sourceBmp, bounds);
                sourceBmp.recycle();
            }}).start();
        } else {
            sourceBmp.recycle();
        }
        StringBuilder flash=new StringBuilder("+").append(name.split(" ")[0]);
        for(int i=0;i<stars;i++) flash.append("★");
        addScanLog("opp scan: "+name+" "+stars+"★"+(r.detectedPopupBounds!=null?" (template saved)":""));
        if(btnLabel!=null){
            btnLabel.setText(flash.toString());
            boardHandler.postDelayed(new Runnable(){ public void run(){
                if(oppScanMode&&btnLabel!=null){
                    long rem=oppScanDeadline-System.currentTimeMillis();
                    if(rem>0) btnLabel.setText(((int)((rem+999)/1000))+"s");
                }
            }},1200);
        }
    }

    // opens the GitHub releases page in the user's browser. Uses an Intent,
    // which does NOT require the INTERNET permission -- the browser handles
    // the network, so the app stays fully offline.
    private void openLatest(){
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            closePanel();
        } catch(Exception e){}
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        // if tap-calibration was active, cancel it — the overlay will be wrong size
        if(calCaptureView!=null){ calStep=0; hideCalCaptureView(); }
        // screen rotated — rebuild panel with fresh dimensions so it fits the new orientation
        if(panel != null){
            int savedMode = mode;
            closePanel();
            mode = savedMode;
            showPanel();
        }
        // keep floating button on-screen after rotation
        if(button != null && btnLp != null){
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            btnLp.x = Math.min(btnLp.x, dm.widthPixels - 150);
            btnLp.y = Math.min(btnLp.y, dm.heightPixels - 150);
            try{ wm.updateViewLayout(button, btnLp); }catch(Exception e){}
        }
    }

    @Override public void onDestroy(){
        super.onDestroy();
        _instance=null;
        if(boardPollRunnable!=null){ boardHandler.removeCallbacks(boardPollRunnable); boardPollRunnable=null; }
        if(boardCountdownRunnable!=null){ boardHandler.removeCallbacks(boardCountdownRunnable); boardCountdownRunnable=null; }
        if(oppPollRunnable!=null){ boardHandler.removeCallbacks(oppPollRunnable); oppPollRunnable=null; }
        if(oppCountdownRunnable!=null){ boardHandler.removeCallbacks(oppCountdownRunnable); oppCountdownRunnable=null; }
        autoTapHandler.removeCallbacksAndMessages(null);
        hideCalCaptureView();
        hideProbeDots();
        try{ if(button!=null) wm.removeView(button); }catch(Exception e){}
        try{ if(closeView!=null) wm.removeView(closeView); }catch(Exception e){}
        closePanel();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
