package com.xanfiend.tftoverlay;

import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.FrameLayout;
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
    private View glowView;
    private android.animation.ValueAnimator glowAnim;
    private View panel;
    private Pool pool;
    private int level = 8; // loaded from pool in onCreate
    private int mode = 0; // 0 = scout grid, 1 = summary
    private Vibrator vib;
    // bump this each release so the footer shows the current version
    private static final String APP_VERSION = "v1.99.17";
    // item builder: index of selected components (1-9), -1 = none
    private int itemA = -1, itemB = -1;
    private boolean[] itemsHeld = new boolean[11]; // my-components multi-select (index 1-10)
    // one-step undo for the pool grid: the inverse of the last mark + a label.
    // null when there's nothing to undo. Cleared on reset and panel-close.
    private Runnable undoAction = null;
    private String undoLabel = null;
    // guide tab sub-selection: 0 = augments, 1 = items
    private int guideTab = 0;
    // god tracker: which slot (1/2) is currently showing the god picker, 0 = none
    private int godPickSlot = 0;
    // probe dots overlay: shows all scan tap positions over TFT for calibration
    private View probeDotsView = null;
    private final android.os.Handler probeDotsHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // dev: "scan from saved image" overlay (validate OCR/detection without TFT)
    private View imageScanView = null;
    private Bitmap imageScanBmp = null;
    private int devTapCount = 0; // taps on the version label toward unlocking dev tools
    private static final String RELEASES_URL = "https://github.com/Xanfiend/tft-overlay/releases/latest";

    // Shop odds live in SetData so set updates stay one-file
    private static final int[][] ODDS = SetData.ODDS;
    private static final int VOID=0xF20B0709,
        BONE=0xFFE0D5C0, ASH=0xFF7A6B60, CARD=0xFF16100F, EDGE=0xFF3A2024,
        GOLD=0xFFC9A227, GREEN=0xFF5FA046, DIM=0xFF564044;
    // primary accent (button bg + bright highlight/sigil) — themeable, set from
    // the saved preset in onCreate before anything is drawn. Default = blood.
    private static int BLOOD=0xFF8B1A1A, BLOODL=0xFFC1121F;
    // accent presets: {dark button bg, bright highlight}. Index 0 = blood (default).
    static final int[][] THEMES = {
        {0xFF8B1A1A, 0xFFC1121F},  // 0 Blood   (default)
        {0xFF4A1A6B, 0xFF9B4DE0},  // 1 Void    (violet)
        {0xFF0E4D4A, 0xFF1FB8A8},  // 2 Abyss   (teal)
        {0xFF8B4A0A, 0xFFE0851F},  // 3 Ember   (amber)
    };
    static final String[] THEME_NAMES = {"blood","void","abyss","ember"};
    private static final int[] COSTC={0,0xFF9AA4B0,0xFF4E9E5A,0xFF3B82C4,0xFFB565D8,0xFFE0A93A};

    // chip references so we can update the count badge in place without rebuilding
    private TextView[] chipViews;
    private String[] chipNames;
    private String buildSel=null; // BUILDS tab: champion whose meta items are shown

    // economy tab: held so refreshEcon() can update without rebuilding the panel
    private TextView undoBar;   // pool-grid undo control, shown only when undoAction != null
    private TextView econGoldTv, econInterestTv, econBracketTv;
    private TextView[] econLadderTvs;
    private TextView econStreakTv, econBonusTv, econIncomeTv, econBreakTv;
    private TextView econNextRoundBtn;
    private TextView econHpTv, econStageTv, econEventTv, econRollBudgetTv;
    private TextView econRoundsLeftTv, econStreakRoiTv, econLossBtnTv, econRecordTv;
    private TextView econWonBtnTv;
    private TextView econTimelineTv, econProjectedTv;
    private TextView econEfficiencyTv;
    private int poolFilter = 0; // 0=all, 1-5=cost tier
    private String augFilter = ""; // ""=all, "S"/"A"/"B"/"C"=tier
    private java.util.List<String> augCompare = new java.util.ArrayList<>();
    private boolean trackingByScarcity = false;

    // hold-to-repeat gold buttons
    private final android.os.Handler goldHandler = new android.os.Handler();
    // auto-dim: fade the sigil to near-invisible after this many ms of no touch
    private static final int  DIM_DELAY_MS = 8000;
    private static final float DIM_ALPHA   = 0.18f;
    private final android.os.Handler dimHandler = new android.os.Handler();
    private Runnable dimRunnable = null;
    // panel auto-dismiss: close the panel after the configured timeout so it
    // never stays up through an entire planning phase without user interaction
    private final android.os.Handler panelDismissHandler = new android.os.Handler();
    private Runnable panelDismissRunnable = null;
    private Runnable goldRepeat;

    // floating button layout params promoted to field so buildSettings() can update alpha/position
    private WindowManager.LayoutParams btnLp;

    // ---- in-game HUD: two tiny draggable numbers, meant to sit right above the
    // game's own gold counter and XP/level button ----
    private TextView hudGoldView, hudXpView;
    private WindowManager.LayoutParams hudGoldLp, hudXpLp;
    private final android.os.Handler hudHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable hudTick;
    private android.animation.ValueAnimator hudGoldGlowAnim, hudXpGlowAnim;
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

    // hunt mode: polls the shop strip ~once a second (screenshot rate limit) and
    // auto-buys any marked champion the moment it appears in the shop
    private boolean huntMode = false;
    private boolean huntBusy = false; // a buy-tap sequence is in flight; skip captures
    private final java.util.List<String> huntBuys = new java.util.ArrayList<>();
    private final java.util.Map<String,Long> huntCooldown = new java.util.HashMap<>();
    // champs tapped-to-buy but not yet confirmed gone from the shop. A copy is only
    // counted into the pool once its card actually disappears, so an unaffordable
    // marked champ that keeps reappearing while you reroll at low gold (its tap does
    // nothing) no longer inflates the pool count on every poll.
    private final java.util.Map<String,Long> huntPendingBuys = new java.util.HashMap<>();
    private static final long HUNT_CONFIRM_MS = 900; // let the shop redraw before judging a tap
    private Runnable huntPollRunnable, huntCountdownRunnable;
    // fast hunt capture: a held MediaProjection streams frames with no rate limit,
    // so the shop check runs ~3x per second instead of the accessibility API's
    // hard 1/sec screenshot ceiling. Granted via the capture dialog when arming
    // the hunt; denying it falls back to the 1/sec path automatically.
    private android.media.projection.MediaProjection huntProjection;
    private android.hardware.display.VirtualDisplay huntVd;
    private android.media.ImageReader huntReader;
    private boolean huntFast = false;    // fast capture pipeline is live
    private boolean huntOcrBusy = false; // an OCR pass is in flight; skip frames

    // fast scan: an optional, persistent MediaProjection capture (separate from
    // the hunt's) that Board Scan / Opp Scan use instead of the 1/sec
    // accessibility screenshot when the user has enabled it in Settings.
    private android.media.projection.MediaProjection scanProjection;
    private android.hardware.display.VirtualDisplay scanVd;
    private android.media.ImageReader scanReader;
    private boolean scanFastReady = false;

    // always-on gold/XP reader: periodically OCRs the bottom-right gold counter and
    // the top-left level/XP and syncs them into the HUD, so the numbers stay live
    // with no manual taps. Off by default; toggled in Settings. Uses the silent
    // accessibility screenshot at a relaxed cadence and yields to hunt/scan loops.
    private boolean goldWatchOn = false;
    private boolean goldWatchBusy = false;
    private int goldWatchIdle = 0; // consecutive no-change reads — backs off the poll rate
    private Runnable goldWatchRunnable;
    private final android.os.Handler goldWatchHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    // on-screen STOP button shown while the hunt or an auto-scan loop runs, so it
    // can be stopped with one obvious tap instead of finding the small floating sigil
    private View stopBtnView = null;
    private WindowManager.LayoutParams stopBtnLp = null;

    // guards against two injected gestures overlapping: on some ROMs (HyperOS/MIUI)
    // an injected tap landing while the user's own finger is mid-drag can drop the
    // real touch's release, leaving the game feeling "stuck" until it is re-touched
    private boolean injecting = false;

    // opponent scan mode: same polling but routes into opponent tracking + records star levels
    private boolean oppScanMode = false;
    private long oppScanDeadline = 0;
    private java.util.Map<String, Integer> oppScanResults = new java.util.LinkedHashMap<>();
    private Runnable oppPollRunnable;
    private Runnable oppCountdownRunnable;

    // SCRY THE LOBBY (REAPER): one-pass scan of every calibrated enemy portrait.
    // Wraps the existing auto-opp board sweep; taps portrait i, sweeps that board,
    // files OPP slot, advances. Portrait positions + the settle delay are tuned
    // live (the only device-dependent bits) — see Pool cal_opp* and SCANALL_SETTLE_MS.
    private boolean scanAllMode = false;
    private int scanAllIdx = 0, scanAllTotal = 0;
    // wait after tapping a portrait for TFT's board-switch animation before scanning
    private static final long SCANALL_SETTLE_MS = 1200;
    // portrait-calibration overlay (records up to 7 tap positions)
    private View oppCalView;
    private int oppCalCount = 0;

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

    // adjust-grid: full-screen overlay showing the live probe grid with draggable
    // corner handles — visual calibration without blind taps
    private View gridAdjustView = null;

    // planner scan: reads the whole board in one pass by snapshotting the Team
    // Planner (flat 2D tiles, matched against bundled set icons) — zero unit taps
    private boolean plannerScanPending = false;
    private java.util.List<int[]> plannerUnits = null; // health-bar detection from the board shot (positions + stars)
    private final android.os.Handler plannerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // planner calibration overlay: records where the planner controls live
    private int plnCalStep = 0; // 0=idle, 1=planner btn, 2=snapshot btn, 3=first slot, 4=last slot, 5=close
    private View plnCalView = null;
    private boolean plnCalBusy = false;        // a pass-through tap is being replayed into the game
    private final int[][] plnCalPts = new int[6][2]; // percent coords per step (index 1-5)

    // auto-tap board scan: dispatches gestures to each hex, OCRs popup — no templates needed
    private boolean autoScanPending = false;
    // bumped on every (re)start; lets late-arriving background work (duplicate-skip
    // visual ID) detect whether it still belongs to the scan that's running/just finished
    private int autoScanGeneration = 0;
    private boolean autoOppMode = false;    // when true, results route to oppScanResults
    private java.util.List<String> autoScanResults = new java.util.ArrayList<>();
    private int autoScanGold = -1;
    private int autoScanLevel = -1;
    private int autoScanXpCur = -1, autoScanXpNeed = -1; // XP progress from the same OCR pass
    private String autoScanStage = "";                    // stage-round from the same OCR pass
    // set when a scan suggests the previous game's data is stale (level 2-4
    // seen while the pool still has entries) — POOL tab shows a reset banner
    private boolean newGameHint = false;
    // opponent slot the most recent enemy scry was filed under (for the UI note)
    private int lastOppSlot = 0;
    private int autoTapIndex = 0;
    private int autoTapConsecutiveMisses = 0;
    private int autoTapBoardProbeCount = 0; // index where bench probes start
    private java.util.List<int[]> autoTapProbes = new java.util.ArrayList<>();
    // board-probe indices recognized as a duplicate of an already-tapped unit
    // mid-scan (see applyAutoTapProbeResult) — skipped instead of tapped
    private java.util.Set<Integer> autoTapSkip = new java.util.HashSet<>();
    // Smart-scan resilience state:
    private boolean autoTapSmartBoard = false;     // board probes came from health-bar detection
    private boolean autoTapSwitchedToGrid = false; // mid-scan grid fallback already used
    private int autoTapNudgeStage = 0;             // 0=not retried, 1=tried lower, 2=tried higher
    private int autoTapHits = 0;                   // units identified this scan (popup or visual)
    private int autoScanVisualCount = 0;           // of which: recognized visually, no tap needed
    private int autoTapScreenH = 0;                // screen height, for the nudge distance
    private long autoScanStartMs = 0;              // for the duration in the done log
    private java.util.List<int[]> autoTapFallbackBoard = null; // occupancy-filtered grid board probes
    private java.util.List<int[]> autoTapBenchProbes = new java.util.ArrayList<>();
    private final android.os.Handler autoTapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // Per-probe timing. These three delays run once per probe (37+ per scan), so they
    // dominate the total scan time. Tuned for speed while leaving the popup enough time
    // to animate in before the screenshot.
    private static final int TAP_STROKE_MS   = 25;  // gesture press duration
    private static final int POPUP_WAIT_MS   = 260; // wait for the unit popup to render after the tap
    private static final int PROBE_GAP_MS    = 12;  // gap before moving to the next probe
    // Android rate-limits AccessibilityService.takeScreenshot() to one call per
    // second. Calling faster fails with ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
    // (errorCode 3). The auto-tap loop fires a screenshot every probe, so it MUST
    // space them at least this far apart, otherwise most shots fail and their
    // champions get silently missed. 1050ms leaves a small margin over the 1000ms
    // limit. This sets the hard floor on auto-scan speed: ~1 second per unit.
    private static final int MIN_SHOT_GAP_MS = 1050;
    private long lastShotMs = 0;          // uptimeMillis of the most recent takeScreenshot request
    private int  autoTapShotRetry = 0;    // retries for the current probe when rate-limited

    // in-app debug log — last 80 lines, shown in Settings
    private static final java.util.ArrayDeque<String> scanLog = new java.util.ArrayDeque<>();
    static void addScanLog(String msg){
        android.util.Log.d("TFTScryer", msg);
        synchronized(scanLog){ scanLog.addLast(msg); if(scanLog.size()>80) scanLog.removeFirst(); }
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

    // called by TFTAccessibilityService when Android binds/unbinds the service.
    // If the SETUP panel is open it still shows the stale "stuck"/"disabled"
    // status it was built with, so rebuild it live — otherwise a user who just
    // toggled the service on comes back to a panel that still says it is off.
    static void onAccessibilityChanged(){
        OverlayService s=_instance;
        if(s==null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable(){ public void run(){
            if(s.panel!=null && s.mode==4) s.showPanel();
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
        goForeground(); // keep the process alive so the accessibility service isn't killed with it
        RemoteData.loadCachedOrBundled(this); // overlay synced set data before anything reads it
        pool = new Pool(this);
        applyTheme();   // set the accent colors before the button/panel are built
        level = pool.getLevel();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        addButton();
        if(pool.getHudEnabled()) addHud();
        if(pool.getGoldWatch()) startGoldWatch();
        new Thread(new Runnable(){ public void run(){ ChampionTemplates.load(OverlayService.this); }}).start();
    }
    @Override public int onStartCommand(Intent i, int f, int id){
        goForeground(); // also covers a START_STICKY restart by the system
        return START_STICKY;
    }

    private static final String NOTIF_CHANNEL = "tft_scryer_overlay";
    private static final int NOTIF_ID = 0x5C27; // "SCRY"
    private boolean isForeground = false;

    // Run as a foreground service. The overlay button and the accessibility
    // service share this process; without foreground status, aggressive ROMs
    // (Xiaomi/HyperOS especially) kill the process in the background, which tears
    // down the accessibility service and makes Android flag it "malfunctioning".
    // A quiet, low-importance ongoing notification keeps the process resident.
    private void goForeground(){
        if(isForeground) return;
        try{
            if(Build.VERSION.SDK_INT >= 26){
                android.app.NotificationManager nm=
                        (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if(nm!=null && nm.getNotificationChannel(NOTIF_CHANNEL)==null){
                    android.app.NotificationChannel ch=new android.app.NotificationChannel(
                            NOTIF_CHANNEL, "Overlay running",
                            android.app.NotificationManager.IMPORTANCE_LOW);
                    ch.setDescription("Keeps the TFT Scryer overlay and silent scan alive.");
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
            }
            Intent open=new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int piFlags=Build.VERSION.SDK_INT>=23
                    ? android.app.PendingIntent.FLAG_IMMUTABLE : 0;
            android.app.PendingIntent pi=android.app.PendingIntent.getActivity(this,0,open,piFlags);
            android.app.Notification.Builder b = Build.VERSION.SDK_INT>=26
                    ? new android.app.Notification.Builder(this, NOTIF_CHANNEL)
                    : new android.app.Notification.Builder(this);
            b.setSmallIcon(R.drawable.ic_notify)
             .setContentTitle("TFT Scryer is watching")
             .setContentText("Overlay and silent scan stay ready. Tap to open.")
             .setOngoing(true)
             .setContentIntent(pi);
            if(Build.VERSION.SDK_INT>=21) b.setPriority(android.app.Notification.PRIORITY_LOW);
            android.app.Notification n=b.build();
            if(Build.VERSION.SDK_INT>=34){
                startForeground(NOTIF_ID, n,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
            isForeground=true;
        }catch(Exception e){
            // never let a notification/FGS quirk crash the overlay — it still runs,
            // just without the extra process priority
            android.util.Log.w("TFTScryer","goForeground failed: "+e.getMessage());
        }
    }

    private int wtype(){
        return Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                         : WindowManager.LayoutParams.TYPE_PHONE;
    }
    // subtle vertical gradient (lighter top, darker bottom) on every box, with a
    // brightened pressed state so buttons visibly react to touch
    // large-text helper: adds +2sp when the user has enabled large-text mode.
    // Use at the most-read text: chip names, section headers, major value displays.
    private float ts(float sp){ return pool!=null && pool.getLargeText() ? sp+2f : sp; }

    private Drawable box(int c,int r,int sc,int sw){
        GradientDrawable normal=grad(c,r,sc,sw);
        GradientDrawable pressed=grad(shade(c,1.35f),r,sc,sw);
        StateListDrawable sl=new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sl.addState(new int[]{}, normal);
        return sl;
    }
    private GradientDrawable grad(int c,int r,int sc,int sw){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{shade(c,1.18f), shade(c,0.85f)});
        g.setCornerRadius(r);
        if(sw>0) g.setStroke(sw,sc);
        return g;
    }
    private static int shade(int c, float f){
        int a=(c>>>24)&0xFF, r=(c>>16)&0xFF, gC=(c>>8)&0xFF, b=c&0xFF;
        r=Math.min(255,Math.round(r*f)); gC=Math.min(255,Math.round(gC*f)); b=Math.min(255,Math.round(b*f));
        return (a<<24)|(r<<16)|(gC<<8)|b;
    }
    // section header: small diamond glyph + bold colored label + a thin rule that
    // fades out to the right, so sections read at a glance while scrolling
    private void addSecHdr(LinearLayout root, String text, int color){
        LinearLayout h=new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(2,12,0,7);
        TextView t=new TextView(this); t.setText("◇ "+text);
        t.setTextColor(color); t.setTextSize(ts(11)); t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setLetterSpacing(0.12f);
        t.setShadowLayer(9,0,0,color);
        h.addView(t);
        View rule=new View(this);
        GradientDrawable rg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{(color&0x00FFFFFF)|0x77000000, color&0x00FFFFFF});
        LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(0,2,1f); rl.setMargins(10,3,8,0);
        rule.setLayoutParams(rl); rule.setBackground(rg);
        h.addView(rule);
        // small ornament caps the rule on the right so each section reads as finished
        TextView orn=new TextView(this); orn.setText("✦");
        orn.setTextColor((color&0x00FFFFFF)|0x88000000); orn.setTextSize(8);
        h.addView(orn);
        root.addView(h);
    }
    // small low-emphasis action chip (e.g. the "clear" links under scan results):
    // a real bordered button with a comfortable tap target instead of bare text
    private TextView miniChip(String text, View.OnClickListener l){
        TextView c=new TextView(this); c.setText(text);
        c.setTextColor(ASH); c.setTextSize(10); c.setGravity(Gravity.CENTER);
        c.setBackground(box(CARD,5,EDGE,1)); c.setPadding(26,8,26,8);
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-2,-2); cl.setMargins(2,2,0,10);
        c.setLayoutParams(cl);
        c.setOnClickListener(l); pressFeedback(c);
        return c;
    }
    // brief press-down feedback for panel buttons/tabs, without consuming the click
    private void pressFeedback(final View v){
        v.setOnTouchListener(new View.OnTouchListener(){
            public boolean onTouch(View view, MotionEvent e){
                int a = e.getAction();
                if(a==MotionEvent.ACTION_DOWN){
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start();
                } else if(a==MotionEvent.ACTION_UP || a==MotionEvent.ACTION_CANCEL){
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110)
                        .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                }
                return false;
            }
        });
    }
    // Rows of equal-width option buttons used throughout SETUP. The selected
    // button is highlighted (BLOOD/BLOODL); others are idle (CARD/EDGE).
    private interface PickSetter { void pick(int v); }
    private void pickRow(LinearLayout root, String[] labels, int[] vals, int cur, int btmDp, PickSetter cb){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.setMargins(0,0,0,btmDp); row.setLayoutParams(rlp);
        for(int i=0;i<labels.length;i++){
            final int v=vals[i]; boolean sel=(cur==v);
            TextView btn=new TextView(this); btn.setText(labels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER); btn.setPadding(0,10,0,10);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1f); lp.setMargins(i>0?4:0,0,0,0); btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View vw){ cb.pick(v); }});
            pressFeedback(btn); row.addView(btn);
        }
        root.addView(row);
    }
    // One-shot diagnostics dump for dev mode: build/device/screen/integrity/state/
    // calibration in a copyable block. Pure reads — useful when debugging a scan
    // issue on a specific device (or on the laptop) without attaching a debugger.
    private String devDiagnostics(){
        String vn="?"; int vc=-1;
        try{ android.content.pm.PackageInfo pi=getPackageManager().getPackageInfo(getPackageName(),0);
             vn=pi.versionName; vc=pi.versionCode; }catch(Exception e){}
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        boolean acc=Build.VERSION.SDK_INT>=30 && TFTAccessibilityService.instance!=null;
        StringBuilder s=new StringBuilder();
        s.append("app ").append(APP_VERSION).append(" (name ").append(vn).append(", code ").append(vc).append(")\n");
        s.append("device ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        s.append("android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        s.append("screen ").append(dm.widthPixels).append("x").append(dm.heightPixels)
         .append(" @").append(dm.density).append("x ").append(landscape?"landscape":"portrait").append("\n");
        s.append("accessibility ").append(acc?"bound":"OFF")
         .append("  ·  root ").append(DeviceIntegrity.isRooted())
         .append("  ·  emu ").append(DeviceIntegrity.isEmulator()).append("\n");
        s.append("state  lvl ").append(pool.getLevel()).append("  gold ").append(pool.getGold())
         .append("  streak ").append(pool.getStreak());
        String st=pool.getStageRound(); s.append("  stage ").append(st.isEmpty()?"-":st).append("\n");
        s.append("cal  landscapeGrid ").append(pool.hasLandscapeGridCal())
         .append("  oppPortraits ").append(pool.oppPortraitCount()).append("/7\n");
        s.append("set  ").append(SetData.SET_NAME).append(" patch ").append(SetData.PATCH)
         .append("  builds patch ").append(ChampItemData.PATCH).append("\n");
        s.append("icons  champ ").append(SetIcons.champCount())
         .append("  item ").append(ItemIcons.itemCount()).append(ItemIcons.isReady()?"":" (none bundled yet)");
        return s.toString();
    }

    private void buzz(){ try{ if(pool.getHaptic() && vib!=null) vib.vibrate(18); }catch(Exception e){} }
    // distinct double pulse so a finished scan can be felt without looking at the screen
    @SuppressWarnings("deprecation")
    private void buzzDone(){
        try{
            if(pool.getHaptic() && vib!=null) vib.vibrate(new long[]{0,35,110,35},-1);
        }catch(Exception e){}
    }

    private void scheduleDim(){
        if(dimRunnable!=null) dimHandler.removeCallbacks(dimRunnable);
        dimRunnable=new Runnable(){ public void run(){
            if(button!=null) button.animate().alpha(DIM_ALPHA).setDuration(700).start();
        }};
        dimHandler.postDelayed(dimRunnable, DIM_DELAY_MS);
    }
    private void cancelDim(){
        if(dimRunnable!=null){ dimHandler.removeCallbacks(dimRunnable); dimRunnable=null; }
        if(button!=null) button.animate().alpha(pool.getAlpha()).setDuration(150).start();
    }
    private void schedulePanelDismiss(){
        if(panelDismissRunnable!=null) panelDismissHandler.removeCallbacks(panelDismissRunnable);
        int secs=pool.getPanelTimeout();
        if(secs==0) return;
        panelDismissRunnable=new Runnable(){ public void run(){ if(panel!=null) closePanel(); } };
        panelDismissHandler.postDelayed(panelDismissRunnable, secs*1000L);
    }
    private void cancelPanelDismiss(){
        if(panelDismissRunnable!=null){ panelDismissHandler.removeCallbacks(panelDismissRunnable); panelDismissRunnable=null; }
    }

    // Load the saved accent preset into BLOOD/BLOODL. Called once in onCreate and
    // again when the user picks a theme (followed by a button + panel rebuild).
    private void applyTheme(){
        int t = pool.getAccentTheme();
        if(t < 0 || t >= THEMES.length) t = 0;
        BLOOD  = THEMES[t][0];
        BLOODL = THEMES[t][1];
    }

    // Apply a theme change live across every persistent surface — the floating
    // sigil, the in-game HUD, and the open panel — so recolor is instant with no
    // service restart.
    private void repaintTheme(){
        applyTheme();
        rebuildButton();
        if(hudGoldView!=null){ removeHud(); addHud(); } // rebuild only if it's showing
        showPanel();
    }

    // Recreate the floating sigil (e.g. after a size change) without moving it.
    private void rebuildButton(){
        int x = btnLp!=null?btnLp.x:20, y = btnLp!=null?btnLp.y:300;
        if(glowAnim!=null){ glowAnim.cancel(); glowAnim=null; }
        if(button!=null){ try{ wm.removeView(button); }catch(Exception e){} button=null; }
        addButton();
        btnLp.x=x; btnLp.y=y;
        try{ wm.updateViewLayout(button, btnLp); }catch(Exception e){}
    }

    private void addButton(){
        final float sig = pool.getSigilScalePct()/100f; // 0.8 / 1.0 / 1.25
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.setBackground(box(0xF20B0709,40,BLOOD,3)); c.setPadding((int)(28*sig),(int)(18*sig),(int)(28*sig),(int)(18*sig));
        // all-seeing sigil over the wordmark
        TextView g=new TextView(this); g.setText("\u29BF"); g.setTextColor(BLOODL); g.setTextSize(22*sig); g.setGravity(Gravity.CENTER);
        btnLabel=new TextView(this); btnLabel.setText("SCRY"); btnLabel.setTextColor(GOLD); btnLabel.setTextSize(8*sig);
        btnLabel.setGravity(Gravity.CENTER); btnLabel.setLetterSpacing(0.25f); btnLabel.setPadding(0,2,0,0);
        c.addView(g); c.addView(btnLabel);

        // soft radial glow ring behind the sigil, slow pulse so the floating
        // button feels alive without being distracting
        FrameLayout fc=new FrameLayout(this);
        glowView=new View(this);
        GradientDrawable glowD=new GradientDrawable();
        glowD.setShape(GradientDrawable.OVAL);
        glowD.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowD.setGradientRadius(85f*sig);
        glowD.setColors(new int[]{(BLOODL&0x00FFFFFF)|0x66000000, BLOODL&0x00FFFFFF}); // accent glow
        glowView.setBackground(glowD);
        FrameLayout.LayoutParams glp=new FrameLayout.LayoutParams((int)(170*sig),(int)(170*sig)); glp.gravity=Gravity.CENTER;
        fc.addView(glowView, glp);
        FrameLayout.LayoutParams clp=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity=Gravity.CENTER;
        fc.addView(c, clp);
        button=fc;
        button.setAlpha(pool.getAlpha());
        button.setScaleX(0f); button.setScaleY(0f);

        glowAnim=android.animation.ValueAnimator.ofFloat(0.4f,1f);
        glowAnim.setDuration(1500);
        glowAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        glowAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        glowAnim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener(){
            public void onAnimationUpdate(android.animation.ValueAnimator a){
                if(glowView!=null) glowView.setAlpha((Float)a.getAnimatedValue());
            }
        });
        glowAnim.start();

        // FLAG_HARDWARE_ACCELERATED is required for windows added from a Service:
        // without it the window renders in software mode and View.animate() skips
        // frames, so animations show as a flicker instead of a smooth transition.
        btnLp = new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        btnLp.gravity=Gravity.TOP|Gravity.START; btnLp.x=20; btnLp.y=300;
        button.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty; long down; boolean moved;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){
                    ix=btnLp.x;iy=btnLp.y;tx=e.getRawX();ty=e.getRawY();down=System.currentTimeMillis();moved=false;
                    cancelDim();
                    button.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start();
                    return true;
                }
                else if(a==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-tx),dy=(int)(e.getRawY()-ty);
                    if(Math.abs(dx)>14||Math.abs(dy)>14){ moved=true; showCloseTarget(true); }
                    btnLp.x=ix+dx; btnLp.y=iy+dy; wm.updateViewLayout(button,btnLp);
                    if(moved) highlightClose(e.getRawX(), e.getRawY());
                    return true;
                } else if(a==MotionEvent.ACTION_UP){
                    scheduleDim();
                    button.animate().scaleX(1f).scaleY(1f).setDuration(120)
                        .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
                    if(moved && overClose(e.getRawX(), e.getRawY())){
                        showCloseTarget(false);
                        stopSelf();
                        return true;
                    }
                    showCloseTarget(false);
                    if(moved){ snapButtonToEdge(); return true; }
                    if(!moved){
                        // All long-running modes (scans, hunt) are stopped ONLY by the
                        // on-screen STOP button. The sigil just opens the panel so the
                        // user can check their pool without killing the active scan.
                        if(oppScanMode||boardScanMode||plannerScanPending||autoScanPending){
                            mode=pool.isEmpty()?0:1; itemA=-1; itemB=-1; showPanel(); return true;
                        }
                        long held=System.currentTimeMillis()-down;
                        // While THE HUNT is running, the sigil just opens the panel — it must
                        // NOT cancel auto-buy (the dedicated STOP button ends the hunt), and we
                        // don't fire a competing scan while the hunt is tapping the shop.
                        if(huntMode){
                            mode=pool.isEmpty()?0:1; itemA=-1; itemB=-1; showPanel();
                        } else if(held>1500){ itemA=-1; itemB=-1; triggerScan(); }
                        else if(held<=450 && pool.getAutoScanOnOpen()
                                && Build.VERSION.SDK_INT>=31 && TFTAccessibilityService.instance!=null){
                            // quick tap = scan + open on the result (auto-scan on open)
                            itemA=-1; itemB=-1; triggerScan();
                        }
                        else {
                            if(held>450) mode=0;
                            else if(pool.getStartTab()==1) mode=0;
                            else if(pool.getStartTab()==2) mode=pool.getLastTab();
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
        button.animate().scaleX(1f).scaleY(1f).setDuration(280)
            .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
        scheduleDim();  // start the idle countdown from the moment the button appears
    }

    // In-game HUD: two tiny independent numbers, each draggable, meant to be
    // parked directly above the game's own counters — the gold one shows the
    // projected income next round, the XP one shows the gold still needed to
    // reach the next level. Both derive from values already tracked by scans
    // and manual corrections (no extra OCR or polling).
    private void addHud(){
        if(hudGoldView!=null) return;
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        // defaults: roughly above where TFT draws gold (bottom-right corner)
        // and the XP/level button (bottom left); the user drags them into place
        hudGoldLp=makeHudLp("hud_gx","hud_gy", dm.widthPixels*84/100, dm.heightPixels*85/100);
        hudXpLp  =makeHudLp("hud_xx","hud_xy", dm.widthPixels*4/100,  dm.heightPixels*85/100);
        hudGoldView=makeHudMini(GOLD, hudGoldLp, "hud_gx","hud_gy");
        hudXpView  =makeHudMini(BLOODL, hudXpLp,  "hud_xx","hud_xy"); // accent — follows the theme
        try{ wm.addView(hudGoldView, hudGoldLp); }catch(Exception e){}
        try{ wm.addView(hudXpView, hudXpLp); }catch(Exception e){}
        hudGoldGlowAnim=pulseGlow(hudGoldView, GOLD);
        hudXpGlowAnim=pulseGlow(hudXpView, BLOODL);
        refreshHud();

        hudTick=new Runnable(){ public void run(){
            refreshHud();
            hudHandler.postDelayed(this, 3000);
        }};
        hudHandler.postDelayed(hudTick, 3000);
    }
    private WindowManager.LayoutParams makeHudLp(String kx, String ky, int defX, int defY){
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;
        lp.x=pool.getHudPos(kx,defX); lp.y=pool.getHudPos(ky,defY);
        return lp;
    }
    private TextView makeHudMini(int color, final WindowManager.LayoutParams lp,
                                 final String kx, final String ky){
        final TextView t=new TextView(this);
        t.setTextColor(color); t.setTextSize(10);
        t.setTypeface(null,android.graphics.Typeface.BOLD);
        // glowing outline: a soft halo ring sits behind the pill, inset slightly
        // smaller so the halo peeks out around every edge, then pulses
        GradientDrawable glow=new GradientDrawable();
        glow.setShape(GradientDrawable.RECTANGLE);
        glow.setCornerRadius(16f);
        glow.setColor(0x00000000);
        glow.setStroke(10,(color&0x00FFFFFF)|0x55000000);
        GradientDrawable pill=new GradientDrawable();
        pill.setShape(GradientDrawable.RECTANGLE);
        pill.setCornerRadius(10f);
        pill.setColor(0xCC0B0709);
        pill.setStroke(3,color);
        android.graphics.drawable.LayerDrawable ld=new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{glow,pill});
        ld.setLayerInset(1,6,6,6,6);
        t.setBackground(ld);
        t.setPadding(18,10,18,10);
        t.setAlpha(pool.getAlpha());
        t.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){
                    ix=lp.x; iy=lp.y; tx=e.getRawX(); ty=e.getRawY();
                    return true;
                } else if(a==MotionEvent.ACTION_MOVE){
                    lp.x=ix+(int)(e.getRawX()-tx); lp.y=iy+(int)(e.getRawY()-ty);
                    try{ wm.updateViewLayout(t,lp); }catch(Exception ex){}
                    return true;
                } else if(a==MotionEvent.ACTION_UP){
                    pool.setHudPos(kx,lp.x); pool.setHudPos(ky,lp.y);
                    return true;
                }
                return false;
            }
        });
        return t;
    }
    private void removeHud(){
        if(hudTick!=null){ hudHandler.removeCallbacks(hudTick); hudTick=null; }
        if(hudGoldGlowAnim!=null){ hudGoldGlowAnim.cancel(); hudGoldGlowAnim=null; }
        if(hudXpGlowAnim!=null){ hudXpGlowAnim.cancel(); hudXpGlowAnim=null; }
        try{ if(hudGoldView!=null) wm.removeView(hudGoldView); }catch(Exception e){}
        try{ if(hudXpView!=null) wm.removeView(hudXpView); }catch(Exception e){}
        hudGoldView=null; hudXpView=null;
    }
    // slow pulse on the halo layer (layer 0) of a makeHudMini() background —
    // mirrors the floating sigil's glowAnim but only fades the outer ring
    private android.animation.ValueAnimator pulseGlow(final View v, final int color){
        final android.graphics.drawable.LayerDrawable ld=(android.graphics.drawable.LayerDrawable)v.getBackground();
        final GradientDrawable glow=(GradientDrawable)ld.getDrawable(0);
        android.animation.ValueAnimator anim=android.animation.ValueAnimator.ofInt(0x33,0xAA);
        anim.setDuration(1400);
        anim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener(){
            public void onAnimationUpdate(android.animation.ValueAnimator a){
                int alpha=(Integer)a.getAnimatedValue();
                glow.setStroke(10,(color&0x00FFFFFF)|(alpha<<24));
            }
        });
        anim.start();
        return anim;
    }
    // recompute HUD text from current pool state — income projection and
    // gold-to-next-level use the same math as the GOLD tab
    private void refreshHud(){
        if(hudGoldView==null) return;
        int gold=pool.getGold();
        int income=Pool.expectedIncome(gold, pool.getStreak());
        // Lead with the actual tracked gold (so it can be read at a glance and
        // verified against the game's own counter right below it), then the
        // projected next-round income as a smaller suffix.
        hudGoldView.setText(gold+"g  +"+income);

        int lvl=pool.getLevel();
        int xpNeed=pool.getXpNeed();
        int xpCur=pool.getXpCur();
        int trustedNeed=Pool.xpToNext(lvl);
        int into=(xpNeed==trustedNeed && xpCur>=0) ? xpCur : 0;
        int goldToLvl=Pool.goldToNextLevel(lvl, into);
        hudXpView.setText(trustedNeed<=0 ? "max" : goldToLvl+"g→"+(lvl+1));
    }

    // After a drag, glide the floating button to the nearest screen edge so it
    // never settles over the middle of the board, and clamp it back on screen if
    // it was dropped partly off. Same behavior as chat-head style overlays.
    @SuppressWarnings("deprecation")
    private void snapButtonToEdge(){
        try{
            android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int bw=button.getWidth(), bh=button.getHeight();
            final int sx=btnLp.x, sy=btnLp.y;
            final int tx=(sx+bw/2 <= dm.widthPixels/2) ? 12 : dm.widthPixels-bw-12;
            final int ty=Math.max(12, Math.min(sy, dm.heightPixels-bh-12));
            if(tx==sx && ty==sy) return;
            android.animation.ValueAnimator va=android.animation.ValueAnimator.ofFloat(0f,1f);
            va.setDuration(150);
            va.setInterpolator(new android.view.animation.DecelerateInterpolator());
            va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener(){
                public void onAnimationUpdate(android.animation.ValueAnimator a){
                    float f=(Float)a.getAnimatedValue();
                    btnLp.x=sx+(int)((tx-sx)*f); btnLp.y=sy+(int)((ty-sy)*f);
                    try{ wm.updateViewLayout(button,btnLp); }catch(Exception ex){}
                }
            });
            va.start();
        }catch(Exception e){}
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
        cancelPanelDismiss();
        if(panel!=null){ try{wm.removeView(panel);}catch(Exception e){} panel=null; panelLp=null; }
        if(goldRepeat!=null){ goldHandler.removeCallbacks(goldRepeat); goldRepeat=null; }
        econGoldTv=null; econInterestTv=null; econBracketTv=null;
        econLadderTvs=null; econStreakTv=null; econBonusTv=null;
        econIncomeTv=null; econBreakTv=null; econNextRoundBtn=null;
        econHpTv=null; econStageTv=null; econEventTv=null; econRollBudgetTv=null;
        econRoundsLeftTv=null; econStreakRoiTv=null; econLossBtnTv=null; econRecordTv=null;
        econWonBtnTv=null; econTimelineTv=null; econProjectedTv=null; econEfficiencyTv=null;
        scanStatusTv=null; buildSel=null; undoBar=null;
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
        econIncomeTv=null; econBreakTv=null; econNextRoundBtn=null;
        econHpTv=null; econStageTv=null; econEventTv=null; econRollBudgetTv=null;
        econRoundsLeftTv=null; econStreakRoiTv=null; econLossBtnTv=null; econRecordTv=null;
        econWonBtnTv=null; econTimelineTv=null; econProjectedTv=null; econEfficiencyTv=null;
        scanStatusTv=null;
        if(goldRepeat!=null){ goldHandler.removeCallbacks(goldRepeat); goldRepeat=null; }

        boolean reuse=panel!=null;
        LinearLayout root;
        if(panel==null){
            // first open: create the window and add to WindowManager
            ScrollView scroll=new ScrollView(this);
            root=new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(box(VOID,18,BLOOD,2));
            root.setPadding(22,18,22,18);
            scroll.addView(root);
            panel=scroll;
            panelLp=new WindowManager.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels*(pool.getPanelWidthPct()/100f)),
                (int)(getResources().getDisplayMetrics().heightPixels*0.86),
                wtype(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
            panelLp.gravity=Gravity.CENTER;
            // tap anywhere outside the panel (on the game) to dismiss it
            panel.setOnTouchListener(new View.OnTouchListener(){
                public boolean onTouch(View v, MotionEvent e){
                    if(e.getAction()==MotionEvent.ACTION_OUTSIDE){ itemA=-1; itemB=-1; closePanel(); return true; }
                    // any touch inside the panel extends the auto-dismiss window
                    if(e.getAction()==MotionEvent.ACTION_DOWN) schedulePanelDismiss();
                    return false;
                }
            });
            schedulePanelDismiss();
            wm.addView(panel,panelLp);
            // entrance: panel scales and fades in from the floating button. The
            // animation must start on the panel's FIRST DRAWN FRAME, not now: the
            // first measure+layout of the full panel tree can take longer than the
            // animation itself, so an animation started here would already be past
            // its end when the first frame appears — it would play invisibly and
            // the panel would just pop in.
            final float targetAlpha=pool.getAlpha();
            final View pv=panel;
            pv.setAlpha(0f); pv.setScaleX(0.92f); pv.setScaleY(0.92f);
            pv.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                public boolean onPreDraw(){
                    pv.getViewTreeObserver().removeOnPreDrawListener(this);
                    pv.animate().alpha(targetAlpha).scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    return true;
                }
            });
        } else {
            // panel already open: reuse the window, just clear and rebuild content — no flash.
            // root itself keeps the panel background/border and is never faded — only the
            // per-tab body (built into "content" below) cross-fades, so the panel frame
            // never disappears for a frame when switching tabs.
            root=(LinearLayout)((ScrollView)panel).getChildAt(0);
            root.removeAllViews();
            panel.setAlpha(pool.getAlpha());
        }

        // header: title + close
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        // app sigil anchors the brand in the corner of every tab
        TextView sigil=new TextView(this); sigil.setText("\u29bf");
        sigil.setTextColor(BLOODL); sigil.setTextSize(16); sigil.setPadding(0,0,10,0);
        sigil.setShadowLayer(14,0,0,BLOODL);
        TextView title=new TextView(this);
        title.setText(mode==5?"\u2694 BUILDS":mode==4?"\u2699 SETUP":mode==3?"\u00a7 GOLD":mode==2?"\u229e GUIDE":mode==1?"\u2738 ODDS":"\u2738 POOL");
        title.setTextColor(BLOODL); title.setTextSize(ts(14)); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        title.setShadowLayer(12,0,0,BLOOD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView verTv=new TextView(this); verTv.setText(APP_VERSION);
        verTv.setTextColor(DIM); verTv.setTextSize(9); verTv.setPadding(0,0,12,0);
        // hidden dev-mode unlock: tap the version 7x (standard Android pattern)
        verTv.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(pool.isDevMode()){
                if(++devTapCount>=7){ pool.setDevMode(false); devTapCount=0;
                    Toast.makeText(OverlayService.this,"Dev tools hidden",Toast.LENGTH_SHORT).show();
                    if(mode==4) showPanel(); }
                return;
            }
            if(++devTapCount>=7){ pool.setDevMode(true); devTapCount=0;
                Toast.makeText(OverlayService.this,"Dev tools unlocked (SETUP tab)",Toast.LENGTH_SHORT).show();
                if(mode==4) showPanel(); }
            else if(devTapCount>=4)
                Toast.makeText(OverlayService.this,(7-devTapCount)+" more to unlock dev tools",Toast.LENGTH_SHORT).show();
        }});
        TextView close=new TextView(this); close.setText("\u2715"); close.setTextColor(BONE); close.setTextSize(18);
        close.setGravity(Gravity.CENTER); close.setBackground(box(BLOOD,6,BLOODL,2)); close.setPadding(22,14,22,14);
        close.setShadowLayer(8,0,0,BLOODL);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        pressFeedback(close);
        head.addView(sigil); head.addView(title); head.addView(verTv); head.addView(close);
        root.addView(head);

        // ornamental gold flourish under the header — bright in the centre, fading to nothing at both ends
        View hdiv=new View(this);
        GradientDrawable hdg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0x00C9A227, GOLD, 0x00C9A227});
        hdg.setCornerRadius(2);
        LinearLayout.LayoutParams hdl=new LinearLayout.LayoutParams(-1,2); hdl.setMargins(6,9,6,0);
        hdiv.setLayoutParams(hdl); hdiv.setBackground(hdg);
        root.addView(hdiv);

        // tab row \u2014 ordered by in-game frequency of use
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,10,0,2);
        int[] tabModes={0,1,5,2,3,4}; // Pool | Odds | Builds | Guide | Gold | Setup
        String[] tabGlyphs={"\u25a6","\u2738","\u2694","\u229e","\u00a7","\u2699"};
        String[] tabNames={"POOL","ODDS","BUILDS","GUIDE","GOLD","SETUP"};
        for(int t=0;t<tabModes.length;t++){
            final int tm=tabModes[t]; boolean on=mode==tm;
            LinearLayout tabWrap=new LinearLayout(this); tabWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams twl=new LinearLayout.LayoutParams(0,-2,1f); twl.setMargins(2,0,2,0); tabWrap.setLayoutParams(twl);
            TextView tab=new TextView(this); tab.setText(tabGlyphs[t]+"\n"+tabNames[t]); tab.setGravity(Gravity.CENTER);
            tab.setTextColor(on?BONE:ASH); tab.setTextSize(ts(9)); tab.setLetterSpacing(0.05f);
            tab.setLineSpacing(2,1f);
            tab.setTypeface(null, on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            tab.setBackground(box(on?BLOOD:CARD,6,on?BLOODL:EDGE,on?2:1)); tab.setPadding(0,11,0,11);
            if(on) tab.setShadowLayer(10,0,0,BLOODL);
            tab.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ mode=tm; pool.setLastTab(tm); showPanel(); } });
            pressFeedback(tab);
            tabWrap.addView(tab);
            if(pool.getCompactTabs()) tab.setText(tabGlyphs[t]); // glyph only, no label
            // active tab gets a gold underbar that glows from the center and fades at
            // both ends (softer than a flat rule); inactive tabs reserve no height
            View underline=new View(this);
            LinearLayout.LayoutParams ul=new LinearLayout.LayoutParams(-1,on?3:0); ul.setMargins(6,4,6,0);
            underline.setLayoutParams(ul);
            if(on){
                GradientDrawable ug=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{GOLD&0x00FFFFFF, GOLD, GOLD&0x00FFFFFF});
                ug.setCornerRadius(2);
                underline.setBackground(ug);
            }
            tabWrap.addView(underline);
            tabs.addView(tabWrap);
        }
        root.addView(tabs);

        // body content for the active tab \u2014 wrapped in its own container so it can
        // cross-fade in on tab switches without touching root's background/border
        // or the header/tab row (which would otherwise flicker in and out)
        final LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        // occult divider under the header
        TextView div=new TextView(this);
        div.setText("\u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766");
        div.setTextColor(EDGE); div.setTextSize(9); div.setGravity(Gravity.CENTER); div.setPadding(0,8,0,2);
        content.addView(div);

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
            b.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ level=lv; pool.setLevel(lv); refreshHud(); showPanel(); } });
            LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,-2,1f); bl.setMargins(2,0,2,0); b.setLayoutParams(bl);
            lvl.addView(b);
        }
        content.addView(lvl);
        TextView lvlHint=new TextView(this);
        lvlHint.setText("set by scrying · tap to override");
        lvlHint.setTextColor(DIM); lvlHint.setTextSize(9); lvlHint.setGravity(Gravity.CENTER);
        lvlHint.setPadding(0,0,0,4);
        content.addView(lvlHint);
        }

        if(mode==5) buildBuilds(content);
        else if(mode==4) buildSettings(content);
        else if(mode==3) buildEconomy(content);
        else if(mode==2) buildGuide(content);
        else if(mode==1) buildSummary(content);
        else buildGrid(content);

        if(reuse){
            // cross-fade the new tab's body in, starting on its first drawn frame \u2014
            // starting now would finish before this frame is even visible
            content.setAlpha(0f);
            content.setTranslationY(12f);
            content.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                public boolean onPreDraw(){
                    content.getViewTreeObserver().removeOnPreDrawListener(this);
                    content.animate().alpha(1f).translationY(0f).setDuration(150)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    return true;
                }
            });
        }
    }

    private double rerollChance(String name){
        int cost=Pool.costOf(name); if(cost==0) return 0;
        int slot=ODDS[level][cost-1]; if(slot==0) return 0;
        int rem=pool.remaining(name); if(rem<=0) return 0;
        int total=0; for(String n:SetData.CHAMPS[cost]) total+=pool.remaining(n);
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
        int totalChamps=0; for(int c=1;c<=5;c++) totalChamps+=SetData.CHAMPS[c].length;
        chipViews=new TextView[totalChamps];
        chipNames=new String[totalChamps];
        int idx=0;

        // \u2609 new-game banner: a scan saw level 2-4 while the pool still holds
        // last game's data \u2014 offer the reset instead of silently poisoning odds
        if(newGameHint){
            LinearLayout ng=new LinearLayout(this); ng.setOrientation(LinearLayout.HORIZONTAL);
            ng.setGravity(Gravity.CENTER_VERTICAL);
            ng.setBackground(box(0xFF1A1400,8,GOLD,2)); ng.setPadding(12,10,12,10);
            LinearLayout.LayoutParams ngl=new LinearLayout.LayoutParams(-1,-2); ngl.setMargins(0,0,0,8); ng.setLayoutParams(ngl);
            TextView ngTv=new TextView(this);
            ngTv.setText("\u2609 New game? The pool still holds last game's data.");
            ngTv.setTextColor(GOLD); ngTv.setTextSize(11);
            ngTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            ng.addView(ngTv);
            TextView ngYes=new TextView(this); ngYes.setText("RESET");
            ngYes.setTextColor(BONE); ngYes.setTextSize(11); ngYes.setTypeface(null,android.graphics.Typeface.BOLD);
            ngYes.setBackground(box(BLOOD,6,BLOODL,2)); ngYes.setPadding(18,8,18,8);
            pressFeedback(ngYes);
            ngYes.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.reset(); autoScanResults.clear(); oppScanResults.clear();
                undoAction=null; undoLabel=null;
                newGameHint=false; lastOppSlot=0; buzz(); refreshHud(); showPanel();
            }});
            ng.addView(ngYes);
            TextView ngNo=new TextView(this); ngNo.setText("\u2715");
            ngNo.setTextColor(ASH); ngNo.setTextSize(13); ngNo.setPadding(16,8,6,8);
            ngNo.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                newGameHint=false; showPanel();
            }});
            ng.addView(ngNo);
            root.addView(ng);
        }

        // ↶ one-step undo for an accidental mark. Lives in-place (no panel rebuild
        // on each mark) and hides itself when there's nothing to undo.
        undoBar=new TextView(this);
        undoBar.setTextColor(ASH); undoBar.setTextSize(11); undoBar.setGravity(Gravity.CENTER);
        undoBar.setPadding(0,9,0,9); undoBar.setBackground(box(CARD,6,EDGE,1));
        LinearLayout.LayoutParams ubl=new LinearLayout.LayoutParams(-1,-2); ubl.setMargins(0,0,0,8); undoBar.setLayoutParams(ubl);
        undoBar.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(undoAction!=null){ undoAction.run(); undoAction=null; undoLabel=null; buzz(); showPanel(); }
        }});
        root.addView(undoBar);
        refreshUndoBar();

        // economy glance: gold / stage / HP without switching to GOLD tab
        int glGold=pool.getGold(), glHp=pool.getHp();
        String glStage=pool.getStageRound();
        int glStageN=pool.getStageNum(), glRoundN=pool.getRoundNum();
        StringBuilder glSb=new StringBuilder(glGold+"g");
        if(!glStage.isEmpty()) glSb.append("  ·  ").append(glStage);
        glSb.append("  ·  ").append(glHp).append(" HP");
        int intr2=Pool.interest(glGold);
        if(intr2>0) glSb.append("  ·  +").append(intr2).append("g/round");
        boolean glCrit=glHp>0 && glHp<=30;
        if(glCrit) glSb.insert(0, "⚠  ");
        TextView glance=new TextView(this); glance.setText(glSb.toString());
        glance.setTextColor(glCrit?BLOODL:ASH); glance.setTextSize(10); glance.setGravity(Gravity.CENTER);
        glance.setPadding(0,4,0,6);
        root.addView(glance);
        // augment-round banner — appears on 2-1 / 3-2 / 4-2 when stage info is known
        boolean isAugNow=(glStageN==2&&glRoundN==1)||(glStageN==3&&glRoundN==2)||(glStageN==4&&glRoundN==2);
        if(isAugNow){
            int augNum=(glStageN==2)?1:(glStageN==3)?2:3;
            TextView augBnr=new TextView(this);
            augBnr.setText("★ Augment offer "+augNum+"/3  ·  see GUIDE → AUGMENTS for tier ratings");
            augBnr.setTextColor(0xFF0A0800); augBnr.setTextSize(11);
            augBnr.setTypeface(null,android.graphics.Typeface.BOLD); augBnr.setGravity(Gravity.CENTER);
            augBnr.setBackground(box(GOLD,6,0xFFE8C030,2)); augBnr.setPadding(10,8,10,8);
            LinearLayout.LayoutParams abl=new LinearLayout.LayoutParams(-1,-2); abl.setMargins(0,0,0,6); augBnr.setLayoutParams(abl);
            root.addView(augBnr);
        }

        // lobby snapshot: 1-liner from any scouted opponent boards
        OppScout.Profile oppSnap=OppScout.analyzeUnits(pool.getAllOppUnits());
        if(oppSnap.hasData()){
            StringBuilder lb=new StringBuilder();
            lb.append(oppSnap.boards).append(oppSnap.boards==1?" board":" boards").append(" scouted");
            if(!oppSnap.topCarries.isEmpty()){
                lb.append("  ·  ").append(oppSnap.topCarries.get(0));
                if(oppSnap.topCarries.size()>1) lb.append("/").append(oppSnap.topCarries.get(1));
            }
            if(oppSnap.apCarries>oppSnap.adCarries*2) lb.append("  ·  AP-heavy");
            else if(oppSnap.adCarries>oppSnap.apCarries*2) lb.append("  ·  AD-heavy");
            if(oppSnap.flankHeavy) lb.append("  ·  divers");
            TextView lobbyTv=new TextView(this);
            lobbyTv.setText(lb.toString());
            lobbyTv.setTextColor(ASH); lobbyTv.setTextSize(10); lobbyTv.setGravity(Gravity.CENTER);
            lobbyTv.setPadding(0,0,0,4);
            root.addView(lobbyTv);
        }

        // tracked-champ list, sorted once and reused by every section below
        // (seenSorted allocates + sorts with per-compare map lookups — don't repeat it)
        java.util.List<String> sortedPool = pool.seenSorted();

        // contest alert: flag any tracked champ with ≥2 opponents and ≤3 copies left
        java.util.List<String> hotList = new java.util.ArrayList<>();
        for(String ch : sortedPool){
            if(pool.oppCount(ch)>=2 && pool.remaining(ch)<=3) hotList.add(ch);
        }
        if(!hotList.isEmpty()){
            StringBuilder sb=new StringBuilder("⚠ CONTESTED:");
            for(int i=0;i<hotList.size();i++){
                if(i>0) sb.append(" ·");
                sb.append(" ").append(hotList.get(i)).append("(").append(pool.remaining(hotList.get(i))).append(" left)");
            }
            TextView alertTv=new TextView(this);
            alertTv.setText(sb.toString());
            alertTv.setTextColor(BLOODL); alertTv.setTextSize(11); alertTv.setGravity(Gravity.CENTER);
            alertTv.setBackground(box(0xFF1A0806,6,BLOODL,2)); alertTv.setPadding(10,8,10,8);
            LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,-2); al.setMargins(0,0,0,6); alertTv.setLayoutParams(al);
            root.addView(alertTv);
        }

        // near-2★ alert: any tracked champ 1-2 copies from 2-starring with pool copies available
        java.util.List<String> nearStar=new java.util.ArrayList<>();
        for(String ch : sortedPool){
            int sc=pool.seenCount(ch); int need=Math.max(0,3-sc);
            if(need>0 && need<=2 && pool.remaining(ch)>=need) nearStar.add(ch);
        }
        if(!nearStar.isEmpty()){
            StringBuilder nsb=new StringBuilder("★★ close:");
            for(int i=0;i<nearStar.size();i++){
                String ch=nearStar.get(i); int need=3-pool.seenCount(ch);
                if(i>0) nsb.append("  ·");
                nsb.append(" ").append(ch).append(" (").append(need).append(" more)");
            }
            TextView nearTv=new TextView(this); nearTv.setText(nsb.toString());
            nearTv.setTextColor(GOLD); nearTv.setTextSize(11); nearTv.setGravity(Gravity.CENTER);
            nearTv.setBackground(box(0xFF1A1400,6,GOLD,2)); nearTv.setPadding(10,8,10,8);
            LinearLayout.LayoutParams ntl=new LinearLayout.LayoutParams(-1,-2); ntl.setMargins(0,0,0,6); nearTv.setLayoutParams(ntl);
            root.addView(nearTv);
        }

        // near-3★ alert: tracked champ 1-2 copies from 3-starring (not 5-cost, pool=9 near-impossible)
        java.util.List<String> near3Star=new java.util.ArrayList<>();
        for(String ch : sortedPool){
            int sc=pool.seenCount(ch); int need=Math.max(0,9-sc);
            if(sc>=7 && need<=2 && pool.remaining(ch)>=need && Pool.costOf(ch)<5) near3Star.add(ch);
        }
        if(!near3Star.isEmpty()){
            StringBuilder n3b=new StringBuilder("★★★ close:");
            for(int i=0;i<near3Star.size();i++){
                String ch=near3Star.get(i); int need=9-pool.seenCount(ch);
                if(i>0) n3b.append("  ·");
                n3b.append(" ").append(ch).append(" (").append(need).append(" more)");
            }
            TextView near3Tv=new TextView(this); near3Tv.setText(n3b.toString());
            near3Tv.setTextColor(ASH); near3Tv.setTextSize(11); near3Tv.setGravity(Gravity.CENTER);
            near3Tv.setBackground(box(CARD,6,EDGE,2)); near3Tv.setPadding(10,8,10,8);
            LinearLayout.LayoutParams n3l=new LinearLayout.LayoutParams(-1,-2); n3l.setMargins(0,0,0,6); near3Tv.setLayoutParams(n3l);
            root.addView(near3Tv);
        }

        // bail alert: pinned carry has shallow pool and you're not 2-starred yet
        String pinnedName=pool.getPinned();
        if(!pinnedName.isEmpty() && pool.remaining(pinnedName)<=6 && pool.seenCount(pinnedName)<9){
            int remLeft=pool.remaining(pinnedName);
            TextView bailTv=new TextView(this);
            bailTv.setText("BAIL?  ·  "+pinnedName+": only "+remLeft+" left in pool  ·  not 2-starred yet");
            bailTv.setTextColor(GOLD); bailTv.setTextSize(11); bailTv.setGravity(Gravity.CENTER);
            bailTv.setBackground(box(0xFF1A1400,6,GOLD,2)); bailTv.setPadding(10,8,10,8);
            LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2); bl.setMargins(0,0,0,6); bailTv.setLayoutParams(bl);
            root.addView(bailTv);
        }
        // pinned carry roll-check: hit % at current gold (same math as COACH roll check)
        if(!pinnedName.isEmpty()){
            int pco=Pool.costOf(pinnedName);
            if(pco>=1 && pco<=5){
                int prem=pool.remaining(pinnedName);
                int ptier=0; for(String nt:SetData.CHAMPS[pco]) ptier+=pool.remaining(nt);
                ptier=Math.max(0, ptier-pool.getJunk(pco));
                int plv=pool.getLevel(); int pgold=Math.min(60,pool.getGold());
                if(prem>0 && ptier>0 && pgold>=2 && plv>=1 && plv<=10){
                    double phit=RollMath.hitChances(plv,pco,Math.min(prem,ptier),ptier,pgold,1)[0];
                    int ppct=(int)Math.round(phit*100);
                    TextView rollTv=new TextView(this);
                    rollTv.setText("★ "+pinnedName+": ~"+ppct+"% to hit with "+pgold+"g at Lv "+plv);
                    rollTv.setTextColor(ppct>=70?GREEN:ppct>=40?GOLD:ASH);
                    rollTv.setTextSize(11); rollTv.setGravity(Gravity.CENTER);
                    rollTv.setBackground(box(CARD,6,ppct>=70?GREEN:ppct>=40?GOLD:EDGE,ppct>=70?2:1));
                    rollTv.setPadding(10,8,10,8);
                    LinearLayout.LayoutParams rtl=new LinearLayout.LayoutParams(-1,-2); rtl.setMargins(0,0,0,6); rollTv.setLayoutParams(rtl);
                    root.addView(rollTv);
                }
            }
        }

        // quick-access row: show tracked champs (those with seen or opp count > 0) at the
        // top so the player doesn't hunt through the grid every round
        java.util.List<String> tracked = new java.util.ArrayList<>(sortedPool);
        if(trackingByScarcity){
            java.util.Collections.sort(tracked, new java.util.Comparator<String>(){
                public int compare(String a, String b){ return pool.remaining(a)-pool.remaining(b); }
            });
        }
        if(!tracked.isEmpty()){
            // inline header with sort toggle
            LinearLayout thdr=new LinearLayout(this); thdr.setOrientation(LinearLayout.HORIZONTAL); thdr.setGravity(android.view.Gravity.CENTER_VERTICAL); thdr.setPadding(2,12,0,7);
            TextView thdrTxt=new TextView(this); thdrTxt.setText("◇ TRACKING ("+tracked.size()+")");
            thdrTxt.setTextColor(ASH); thdrTxt.setTextSize(10); thdrTxt.setLetterSpacing(0.08f); thdrTxt.setTypeface(null,android.graphics.Typeface.BOLD);
            thdrTxt.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView tSortBtn=new TextView(this);
            tSortBtn.setText(trackingByScarcity?"↕ scarce":"↕ contest");
            tSortBtn.setTextColor(trackingByScarcity?GOLD:DIM); tSortBtn.setTextSize(10);
            tSortBtn.setPadding(8,4,8,4); tSortBtn.setBackground(box(CARD,4,trackingByScarcity?GOLD:EDGE,1));
            tSortBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ trackingByScarcity=!trackingByScarcity; showPanel(); }});
            TextView tCopyBtn=new TextView(this); tCopyBtn.setText("⎘");
            tCopyBtn.setTextColor(DIM); tCopyBtn.setTextSize(12);
            tCopyBtn.setPadding(8,4,8,4); tCopyBtn.setBackground(box(CARD,4,EDGE,1));
            LinearLayout.LayoutParams tcbl=new LinearLayout.LayoutParams(-2,-2); tcbl.setMargins(4,0,0,0); tCopyBtn.setLayoutParams(tcbl);
            final java.util.List<String> tCopySnap=new java.util.ArrayList<>(tracked);
            tCopyBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                StringBuilder sb=new StringBuilder();
                for(String n:tCopySnap){ if(sb.length()>0) sb.append(", "); sb.append(n).append(" x").append(pool.seenCount(n)); }
                android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("pool",sb.toString()));
                Toast.makeText(OverlayService.this,"Pool list copied",Toast.LENGTH_SHORT).show();
            }});
            thdr.addView(thdrTxt); thdr.addView(tSortBtn); thdr.addView(tCopyBtn);
            root.addView(thdr);
            LinearLayout tRow=null;
            for(int ti=0;ti<tracked.size();ti++){
                if(ti%4==0){
                    tRow=new LinearLayout(this);
                    LinearLayout.LayoutParams trl=new LinearLayout.LayoutParams(-1,-2); trl.setMargins(0,ti==0?2:4,0,0); tRow.setLayoutParams(trl);
                    root.addView(tRow);
                }
                final String tn=tracked.get(ti); int fc=Pool.costOf(tn);
                LinearLayout wrapper=new LinearLayout(this);
                wrapper.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(0,-2,1f); wlp.setMargins(ti%4>0?4:0,0,0,0); wrapper.setLayoutParams(wlp);
                LinearLayout tcell=buildChipCell(tn, fc);
                tcell.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
                wrapper.addView(tcell);
                int remLeft=pool.remaining(tn);
                TextView remTv=new TextView(this);
                remTv.setText(remLeft==0?"none left":remLeft+" left");
                remTv.setTextColor(remLeft==0?BLOODL:remLeft<=3?BLOODL:remLeft<=6?GOLD:DIM);
                remTv.setTextSize(9); remTv.setGravity(Gravity.CENTER); remTv.setPadding(0,1,0,1);
                wrapper.addView(remTv);
                int seen2=pool.seenCount(tn); int need2=Math.max(0,3-seen2);
                if(need2>0 && need2<=2 && remLeft>=need2){
                    TextView starTv=new TextView(this);
                    starTv.setText(need2+" more → ★★");
                    starTv.setTextColor(need2==1?GOLD:ASH);
                    starTv.setTextSize(9); starTv.setGravity(Gravity.CENTER); starTv.setPadding(0,0,0,1);
                    wrapper.addView(starTv);
                    if(need2==1 && fc>0 && pool.getGold()>=fc){
                        TextView buyTv=new TextView(this); buyTv.setText("✓ buy now!");
                        buyTv.setTextColor(GREEN); buyTv.setTextSize(9); buyTv.setGravity(Gravity.CENTER); buyTv.setPadding(0,0,0,3);
                        wrapper.addView(buyTv);
                    }
                } else if(seen2>=9){
                    TextView star3dTv=new TextView(this);
                    star3dTv.setText("★★★ 3-starred");
                    star3dTv.setTextColor(GOLD);
                    star3dTv.setTextSize(9); star3dTv.setGravity(Gravity.CENTER); star3dTv.setPadding(0,0,0,3);
                    wrapper.addView(star3dTv);
                } else if(seen2>=3 && seen2<9 && fc<5){
                    int need3=9-seen2;
                    TextView star3Tv=new TextView(this);
                    star3Tv.setText(need3+" more → ★★★");
                    star3Tv.setTextColor(need3<=2?GOLD:need3<=4?ASH:DIM);
                    star3Tv.setTextSize(9); star3Tv.setGravity(Gravity.CENTER); star3Tv.setPadding(0,0,0,3);
                    wrapper.addView(star3Tv);
                }
                tRow.addView(wrapper);
            }
            // fill last row if not full
            if(tRow!=null){ int rem=tracked.size()%4; if(rem>0) for(int k=rem;k<4;k++){ View sp=new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); tRow.addView(sp); } }
        }

        // \u26e7 THE RITE \u2014 automatic scrying is the heart of the overlay. One press
        // reads gold, level and every unit; the chips further down exist only to
        // amend the rare miss.
        boolean accAvail=Build.VERSION.SDK_INT>=31&&TFTAccessibilityService.instance!=null;
        addSecHdr(root, "\u26e7 THE RITE", GOLD);
        if(!pool.getStageRound().isEmpty()){
            TextView stTv=new TextView(this);
            stTv.setText("last scryed at stage "+pool.getStageRound());
            stTv.setTextColor(DIM); stTv.setTextSize(9); stTv.setPadding(2,0,2,4);
            root.addView(stTv);
        }

        if(autoScanPending){
            int total=autoTapProbes.size(); int done=autoTapIndex;
            String prog=total>0?(done+"/"+total+" hexes"):"starting...";
            String label=autoOppMode
                    ? ("\u25C9 Scrying the enemy: "+prog+" \u00b7 tap sigil to stop")
                    : ("\u29bf Scrying your board: "+prog+" \u00b7 tap sigil to stop");
            TextView asActive=new TextView(this); asActive.setText(label);
            asActive.setTextColor(GOLD); asActive.setTextSize(12); asActive.setGravity(Gravity.CENTER);
            asActive.setBackground(box(BLOOD,6,BLOODL,2)); asActive.setPadding(0,12,0,12);
            LinearLayout.LayoutParams asal=new LinearLayout.LayoutParams(-1,-2); asal.setMargins(0,0,0,4); asActive.setLayoutParams(asal);
            root.addView(asActive);
        } else {
            // the two scrying rites, side by side: your board and the enemy's
            LinearLayout row1=new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams r1p=new LinearLayout.LayoutParams(-1,-2); r1p.setMargins(0,0,0,4); row1.setLayoutParams(r1p);

            LinearLayout asBtn=ritualBtn("\u29bf SCRY MY BOARD","level \u00b7 gold \u00b7 every unit",
                    accAvail?BLOOD:0xFF0D0909, accAvail?BLOODL:DIM, accAvail);
            LinearLayout.LayoutParams asl=new LinearLayout.LayoutParams(0,-2,1f); asl.setMargins(0,0,3,0); asBtn.setLayoutParams(asl);
            asBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ openAccessibilitySettings(); return; }
                startAutoTapScan();
            }});

            LinearLayout aoBtn=ritualBtn("\u25C9 SCRY THE ENEMY","scout a foe's board",
                    accAvail?CARD:0xFF0D0909, accAvail?GOLD:DIM, accAvail);
            LinearLayout.LayoutParams aol=new LinearLayout.LayoutParams(0,-2,1f); aol.setMargins(3,0,0,0); aoBtn.setLayoutParams(aol);
            aoBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ openAccessibilitySettings(); return; }
                startAutoOppScan();
            }});

            row1.addView(asBtn); row1.addView(aoBtn);
            root.addView(row1);

            // PLANNER SCAN \u2014 whole board from one Team Planner snapshot, no unit taps
            final boolean plnReady = accAvail && pool.plannerCalibrated();
            LinearLayout plnBtn=ritualBtn("\u2742 SCRY THE PLANNER",
                    plnReady ? "whole board in one snapshot \u00b7 no unit taps"
                             : "calibrate the planner in SETUP first",
                    plnReady?0xFF1A1400:0xFF0D0909, plnReady?GOLD:DIM, plnReady);
            LinearLayout.LayoutParams pbl=new LinearLayout.LayoutParams(-1,-2); pbl.setMargins(0,0,0,4); plnBtn.setLayoutParams(pbl);
            plnBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ openAccessibilitySettings(); return; }
                if(!pool.plannerCalibrated()){ Toast.makeText(OverlayService.this,"Calibrate the planner in the SETUP tab first",Toast.LENGTH_LONG).show(); return; }
                startPlannerScan();
            }});
            root.addView(plnBtn);

            // SCRY THE LOBBY \u2014 one-pass scan of every enemy board (REAPER)
            final boolean lobbyReady = accAvail && pool.hasOppPortraitCal();
            int lobbyN = pool.oppPortraitCount();
            LinearLayout lobbyBtn=ritualBtn("\u25c9 SCRY THE LOBBY",
                    lobbyReady ? ("scan all "+lobbyN+" enemy boards in one pass")
                               : "calibrate enemy portraits in SETUP first",
                    lobbyReady?0xFF1A1400:0xFF0D0909, lobbyReady?GOLD:DIM, lobbyReady);
            LinearLayout.LayoutParams lbl2=new LinearLayout.LayoutParams(-1,-2); lbl2.setMargins(0,0,0,4); lobbyBtn.setLayoutParams(lbl2);
            lobbyBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ openAccessibilitySettings(); return; }
                if(!pool.hasOppPortraitCal()){ Toast.makeText(OverlayService.this,"Calibrate enemy portraits in the SETUP tab first",Toast.LENGTH_LONG).show(); return; }
                startScanAllOpponents();
            }});
            root.addView(lobbyBtn);

            // THE HUNT \u2014 shop watcher / auto-buy
            final java.util.List<String> huntList=pool.getHunt();
            if(huntMode){
                TextView huntActive=new TextView(this);
                huntActive.setText("\u2726 The hunt is on \u2014 marked champs are bought on sight \u00b7 tap the STOP button to end (the sigil just opens this panel)");
                huntActive.setTextColor(GOLD); huntActive.setTextSize(12); huntActive.setGravity(Gravity.CENTER);
                huntActive.setBackground(box(BLOOD,6,GOLD,2)); huntActive.setPadding(0,12,0,12);
                LinearLayout.LayoutParams hal=new LinearLayout.LayoutParams(-1,-2); hal.setMargins(0,0,0,4); huntActive.setLayoutParams(hal);
                root.addView(huntActive);
            } else {
                String huntSub=huntList.isEmpty()
                        ? "hold a champ's name below to mark prey"
                        : "auto-buys: "+joinNames(huntList);
                LinearLayout huntBtn=ritualBtn("\u2726 BEGIN THE HUNT \u00b7 AUTO-BUY",huntSub,
                        accAvail&&!huntList.isEmpty()?0xFF1A1400:0xFF0D0909,
                        accAvail&&!huntList.isEmpty()?GOLD:DIM, accAvail&&!huntList.isEmpty());
                LinearLayout.LayoutParams hbl=new LinearLayout.LayoutParams(-1,-2); hbl.setMargins(0,0,0,4); huntBtn.setLayoutParams(hbl);
                huntBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    if(!accAvail){ openAccessibilitySettings(); return; }
                    startHuntMode();
                }});
                root.addView(huntBtn);
                // one-tap way to turn auto-buy OFF: clears every ✦ prey mark so the
                // hunt has nothing to buy. (Unmarking one at a time is a long-press on
                // the name, which is easy to miss — this is the obvious off switch.)
                if(!huntList.isEmpty()){
                    root.addView(miniChip("✕ clear auto-buy marks ("+huntList.size()+")",
                        new View.OnClickListener(){ public void onClick(View v){
                            pool.clearHunt(); buzz();
                            Toast.makeText(OverlayService.this,"Auto-buy off — all marks cleared",Toast.LENGTH_SHORT).show();
                            showPanel();
                        }}));
                }
            }
            if(!huntBuys.isEmpty()){
                TextView hb=new TextView(this);
                hb.setText("\u2726 hunted down: "+joinNames(huntBuys));
                hb.setTextColor(GOLD); hb.setTextSize(10); hb.setPadding(2,0,2,4);
                root.addView(hb);
            }

            TextView autoHint=new TextView(this);
            autoHint.setText("\u2726  the rite reads gold, level and every unit by itself \u2014 nothing to type");
            autoHint.setTextColor(DIM); autoHint.setTextSize(9); autoHint.setGravity(Gravity.CENTER);
            autoHint.setPadding(2,0,2,6); root.addView(autoHint);

            if(!accAvail){
                TextView accHint=new TextView(this); accHint.setText("Enable Accessibility in SETUP tab to use scan features");
                accHint.setTextColor(DIM); accHint.setTextSize(10); accHint.setPadding(2,0,2,4); root.addView(accHint);
            }
        }

        // auto scan results
        if(!autoScanResults.isEmpty()){
        addSecHdr(root, "REVEALED · YOUR BOARD  ("+autoScanResults.size()+")", GOLD);
            if(autoScanGold>=0||autoScanLevel>=0){
                StringBuilder glSb2=new StringBuilder();
                if(autoScanLevel>=0) glSb2.append("Lv ").append(autoScanLevel);
                if(autoScanGold>=0){ if(glSb2.length()>0) glSb2.append("  ·  "); glSb2.append(autoScanGold).append("g"); }
                TextView glTv=new TextView(this); glTv.setText(glSb2.toString());
                glTv.setTextColor(BONE); glTv.setTextSize(12); glTv.setTypeface(null,android.graphics.Typeface.BOLD);
                glTv.setPadding(2,0,2,4); root.addView(glTv);
            }
            StringBuilder asrSb=new StringBuilder();
            boolean anyVisual=false;
            for(String s:autoScanResults){
                if(asrSb.length()>0) asrSb.append(" · ");
                asrSb.append(s);
                if(s.endsWith("≈")) anyVisual=true;
            }
            TextView asrTv=new TextView(this); asrTv.setText(asrSb.toString());
            asrTv.setTextColor(BONE); asrTv.setTextSize(12); asrTv.setPadding(2,0,2,4); root.addView(asrTv);
            if(anyVisual){
                TextView visHint=new TextView(this);
                visHint.setText("★ = star level  ·  ≈ = recognized by sprite, no tap needed");
                visHint.setTextColor(DIM); visHint.setTextSize(9); visHint.setPadding(2,0,2,4);
                root.addView(visHint);
            }
            root.addView(miniChip("✕ clear results", new View.OnClickListener(){ public void onClick(View v){ autoScanResults.clear(); showPanel(); }}));
        }

        // opponent scan results
        if(!oppScanResults.isEmpty()){
        addSecHdr(root, "REVEALED · ENEMY  ("+oppScanResults.size()+")", GOLD);
            StringBuilder osrSb=new StringBuilder();
            for(java.util.Map.Entry<String,Integer> e:oppScanResults.entrySet()){
                if(osrSb.length()>0) osrSb.append(" \u00b7 ");
                osrSb.append(e.getKey());
                int st=e.getValue(); if(st>0){ osrSb.append(" "); for(int si=0;si<st;si++) osrSb.append("\u2605"); }
            }
            TextView osrTv=new TextView(this); osrTv.setText(osrSb.toString());
            osrTv.setTextColor(BONE); osrTv.setTextSize(12); osrTv.setPadding(2,0,2,4); root.addView(osrTv);
            if(lastOppSlot>0){
                TextView filed=new TextView(this);
                filed.setText("filed as OPP "+lastOppSlot+" — scry the next enemy board to file OPP "+(lastOppSlot%7+1));
                filed.setTextColor(DIM); filed.setTextSize(9); filed.setPadding(2,0,2,2); root.addView(filed);
            }
            root.addView(miniChip("✕ clear results", new View.OnClickListener(){ public void onClick(View v){ oppScanResults.clear(); showPanel(); }}));
        }

        // ◉ ENEMIES REMEMBERED — one line per scouted opponent board (slots 1-7).
        // Scry each enemy in turn during scouting; boards stack up here.
        {
            boolean anyOpp=false;
            for(int s=1;s<=7;s++) if(!pool.getOppBoard(s).isEmpty()){ anyOpp=true; break; }
            if(anyOpp){
                addSecHdr(root, "◉ ENEMIES REMEMBERED", GOLD);
                for(int s=1;s<=7;s++){
                    final int slot=s;
                    java.util.Map<String,Integer> board=pool.getOppBoard(s);
                    if(board.isEmpty()) continue;
                    LinearLayout orow=new LinearLayout(this); orow.setOrientation(LinearLayout.HORIZONTAL);
                    orow.setGravity(Gravity.CENTER_VERTICAL);
                    orow.setBackground(box(CARD,6,EDGE,1)); orow.setPadding(10,8,10,8);
                    LinearLayout.LayoutParams orl=new LinearLayout.LayoutParams(-1,-2); orl.setMargins(0,0,0,4); orow.setLayoutParams(orl);
                    TextView oId=new TextView(this); oId.setText("OPP "+slot);
                    oId.setTextColor(GOLD); oId.setTextSize(10); oId.setTypeface(null,android.graphics.Typeface.BOLD);
                    oId.setPadding(0,0,10,0); orow.addView(oId);
                    StringBuilder ob=new StringBuilder();
                    for(java.util.Map.Entry<String,Integer> e:board.entrySet()){
                        if(ob.length()>0) ob.append(" · ");
                        ob.append(e.getKey());
                        for(int st=0;st<e.getValue();st++) ob.append("★");
                    }
                    TextView oTv=new TextView(this); oTv.setText(ob.toString());
                    oTv.setTextColor(BONE); oTv.setTextSize(10);
                    oTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                    orow.addView(oTv);
                    TextView oClr=new TextView(this); oClr.setText("✕");
                    oClr.setTextColor(ASH); oClr.setTextSize(12); oClr.setPadding(10,4,4,4);
                    oClr.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        pool.clearOppBoard(slot); showPanel();
                    }});
                    orow.addView(oClr);
                    root.addView(orow);
                }
            }
        }

        // \u2720 GRIMOIRE \u2014 the manual chips. The rite records everything by itself;
        // these remain only to amend the rare miss (a unit the scry could not
        // read, or freeing copies when a player dies).
        addSecHdr(root, "\u2720 GRIMOIRE \u00B7 CORRECTIONS", GOLD);
        LinearLayout howCard=new LinearLayout(this); howCard.setOrientation(LinearLayout.VERTICAL);
        howCard.setBackground(box(CARD,8,EDGE,1)); howCard.setPadding(14,11,14,11);
        LinearLayout.LayoutParams hcl=new LinearLayout.LayoutParams(-1,-2); hcl.setMargins(0,4,0,8); howCard.setLayoutParams(hcl);
        String[] howItems={"The rite records all \u2014 touch these only to amend it","Tap a name = +1 copy seen","Tap the count badge = \u22121 copy","Tap the \u25C9 badge = +1 player contesting","Hold a name = mark \u2726 prey for THE HUNT (auto-buy)"};
        for(String h:howItems){
            TextView hv=new TextView(this); hv.setText("\u2726  "+h);
            hv.setTextColor(ASH); hv.setTextSize(10); hv.setPadding(0,2,0,2); hv.setLineSpacing(2,1f); howCard.addView(hv);
        }
        root.addView(howCard);

        // RECENT: the champs you've tapped this game, for instant re-tapping
        java.util.List<String> rec = pool.recentList();
        if(!rec.isEmpty()){
        addSecHdr(root, "RECENT", GOLD);

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

        pickRow(root,
            new String[]{"ALL","1◈","2◈","3◈","4◈","5◈"},
            new int[]{0,1,2,3,4,5},
            poolFilter, 10,
            new PickSetter(){ public void pick(int v){ poolFilter=v; showPanel(); }});

        for(int cost=1;cost<=5;cost++){
        if(poolFilter!=0 && poolFilter!=cost) continue;
        int tierLeft=0; for(String n:SetData.CHAMPS[cost]) tierLeft+=pool.remaining(n);
        addSecHdr(root, cost+"-COST  ·  "+tierLeft+" left", COSTC[cost]);

            LinearLayout row=null; String[] arr=SetData.CHAMPS[cost];
            for(int j=0;j<arr.length;j++){
                if(j%3==0){ row=new LinearLayout(this); root.addView(row); }
                final String name=arr[j]; final int fc=cost;
                if(idx<chipNames.length){ chipNames[idx]=name; idx++; }
                LinearLayout cell=buildChipCell(name, fc);
                row.addView(cell);
            }
        }
        // reset pool: clears seen/opp tracking only — keeps gold, HP, streak, stage
        final TextView rp=new TextView(this); rp.setText("RESET POOL");
        rp.setTextColor(DIM); rp.setTextSize(12); rp.setGravity(Gravity.CENTER); rp.setPadding(0,10,0,10);
        rp.setBackground(box(CARD,6,EDGE,1));
        LinearLayout.LayoutParams rpl=new LinearLayout.LayoutParams(-1,-2); rpl.setMargins(0,18,0,4); rp.setLayoutParams(rpl);
        rp.setOnClickListener(new View.OnClickListener(){
            boolean armed=false;
            public void onClick(View v){
                if(armed){ pool.resetPool(); buzz(); showPanel(); return; }
                armed=true; rp.setText("CONFIRM RESET POOL  (tap again)");
                rp.setTextColor(BLOODL); rp.setBackground(box(0xFF1A0806,6,BLOODL,2));
                rp.postDelayed(new Runnable(){ public void run(){
                    armed=false; rp.setText("RESET POOL");
                    rp.setTextColor(DIM); rp.setBackground(box(CARD,6,EDGE,1));
                }}, 3000);
            }
        });
        root.addView(rp);

        // big done button
        Button done=new Button(this); done.setText("DONE"); done.setAllCaps(false);
        done.setBackground(box(BLOOD,6,BLOODL,2)); done.setTextColor(BONE); done.setTextSize(15); done.setTypeface(null, android.graphics.Typeface.BOLD);
        done.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2); dl.setMargins(0,14,0,0); done.setLayoutParams(dl);
        root.addView(done);
    }

    // unit-tier badge colour: S gold, A green, B ash, C dim
    private int tierColor(String t){
        if("S".equals(t)) return GOLD;
        if("A".equals(t)) return 0xFF4E9E5A;
        if("B".equals(t)) return ASH;
        if("C".equals(t)) return DIM;
        return EDGE;
    }

    // ---- BUILDS TAB: tap a champion -> the items that are meta on them ----
    private void buildBuilds(LinearLayout root){
        addSecHdr(root, "⚔ META BUILDS", GOLD);

        TextView intro=new TextView(this);
        intro.setText("Tap a champion for the items that carry on them this patch.  ✦ = meta itemizer · letter = unit tier (S best).");
        intro.setTextColor(ASH); intro.setTextSize(10); intro.setLineSpacing(2,1f); intro.setPadding(2,0,2,2);
        root.addView(intro);

        TextView patch=new TextView(this);
        patch.setText("meta snapshot · patch "+ChampItemData.PATCH+" · verify vs your live tier list");
        patch.setTextColor(DIM); patch.setTextSize(9); patch.setPadding(2,0,2,8);
        root.addView(patch);

        // detail card for the currently selected champion
        if(buildSel!=null){
            final int cost=Pool.costOf(buildSel);
            ChampItemData.Build b=ChampItemData.get(buildSel);
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(box(CARD,10, b!=null?GOLD:EDGE, 2)); card.setPadding(14,12,14,12);
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2); cl.setMargins(0,2,0,10); card.setLayoutParams(cl);

            LinearLayout hdr=new LinearLayout(this); hdr.setOrientation(LinearLayout.HORIZONTAL); hdr.setGravity(Gravity.CENTER_VERTICAL);
            TextView nm=new TextView(this); nm.setText(buildSel);
            nm.setTextColor(BONE); nm.setTextSize(17); nm.setTypeface(null,android.graphics.Typeface.BOLD);
            nm.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            hdr.addView(nm);
            TextView pip=new TextView(this); pip.setText(cost+"◈");
            pip.setTextColor(COSTC[cost]); pip.setTextSize(14); pip.setTypeface(null,android.graphics.Typeface.BOLD);
            hdr.addView(pip);
            String selTier=ChampItemData.tierOf(buildSel);
            if(!selTier.isEmpty()){
                TextView tb=new TextView(this); tb.setText(selTier);
                tb.setTextColor(0xFF000000); tb.setTextSize(12); tb.setTypeface(null,android.graphics.Typeface.BOLD);
                tb.setGravity(Gravity.CENTER);
                tb.setBackground(box(tierColor(selTier),5,tierColor(selTier),0)); tb.setPadding(13,4,13,4);
                LinearLayout.LayoutParams tbl=new LinearLayout.LayoutParams(-2,-2); tbl.setMargins(10,0,0,0); tb.setLayoutParams(tbl);
                hdr.addView(tb);
            }
            card.addView(hdr);

            if(b!=null){
                TextView role=new TextView(this); role.setText(b.role+"  ·  "+b.comp);
                role.setTextColor(GOLD); role.setTextSize(11); role.setTypeface(null,android.graphics.Typeface.BOLD);
                role.setPadding(0,4,0,9); card.addView(role);

                for(int i=0;i<b.items.length;i++){
                    boolean bis=(i==0);
                    LinearLayout irow=new LinearLayout(this); irow.setOrientation(LinearLayout.HORIZONTAL); irow.setGravity(Gravity.CENTER_VERTICAL);
                    irow.setBackground(box(bis?0xFF1A1400:VOID,6,bis?GOLD:EDGE,bis?2:1)); irow.setPadding(11,10,11,10);
                    LinearLayout.LayoutParams irl=new LinearLayout.LayoutParams(-1,-2); irl.setMargins(0,0,0,4); irow.setLayoutParams(irl);
                    TextView it=new TextView(this); it.setText("◆  "+b.items[i]);
                    it.setTextColor(bis?GOLD:BONE); it.setTextSize(13);
                    it.setTypeface(null, bis?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                    it.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                    irow.addView(it);
                    if(bis){
                        TextView tag=new TextView(this); tag.setText("BIS");
                        tag.setTextColor(0xFF000000); tag.setTextSize(9); tag.setTypeface(null,android.graphics.Typeface.BOLD);
                        tag.setBackground(box(GOLD,4,GOLD,0)); tag.setPadding(8,3,8,3); irow.addView(tag);
                    }
                    card.addView(irow);
                }
                if(b.note!=null && !b.note.isEmpty()){
                    TextView note=new TextView(this); note.setText("✦  "+b.note);
                    note.setTextColor(ASH); note.setTextSize(10); note.setLineSpacing(3,1f); note.setPadding(0,7,0,0);
                    card.addView(note);
                }
            } else {
                TextView fb=new TextView(this);
                String tip = cost<=2
                    ? "No meta build this patch — at 1-2 cost it's usually a trait-bot or reroll piece. Hold your items for your comp's marked carry (✦). If it must tank, slam Warmog's / Bramble / Gargoyle."
                    : "No meta build this patch. Itemize your comp's marked carry (✦) instead. AP backline flexes Blue Buff / Jeweled Gauntlet / Deathcap; frontline wants Warmog's / Bramble / Gargoyle.";
                fb.setText(tip);
                fb.setTextColor(ASH); fb.setTextSize(11); fb.setLineSpacing(3,1f); fb.setPadding(0,8,0,2);
                card.addView(fb);
            }
            root.addView(card);
        } else {
            TextView pick=new TextView(this);
            pick.setText("— pick a champion below —");
            pick.setTextColor(DIM); pick.setTextSize(10); pick.setGravity(Gravity.CENTER); pick.setPadding(0,4,0,8);
            root.addView(pick);
        }

        // champion picker, grouped by cost
        for(int cost=1;cost<=5;cost++){
            int metaCt=0; for(String n:SetData.CHAMPS[cost]) if(ChampItemData.has(n)) metaCt++;
            addSecHdr(root, cost+"-COST"+(metaCt>0?"  ("+metaCt+" meta)":""), COSTC[cost]);
            LinearLayout row=null; String[] arr=SetData.CHAMPS[cost];
            for(int j=0;j<arr.length;j++){
                if(j%3==0){ row=new LinearLayout(this); root.addView(row); }
                final String name=arr[j];
                boolean meta=ChampItemData.has(name);
                boolean sel=name.equals(buildSel);
                String tier=ChampItemData.tierOf(name);
                TextView chip=new TextView(this);
                String label=(meta?"✦ ":"")+name+(tier.isEmpty()?"":"  "+tier);
                if(!tier.isEmpty() && !sel){
                    android.text.SpannableString ss=new android.text.SpannableString(label);
                    int st=label.length()-tier.length();
                    ss.setSpan(new android.text.style.ForegroundColorSpan(tierColor(tier)), st, label.length(), 0);
                    ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, label.length(), 0);
                    chip.setText(ss);
                } else {
                    chip.setText(label);
                }
                chip.setTextColor(sel?0xFF000000:(meta?BONE:ASH));
                chip.setTextSize(13); chip.setGravity(Gravity.CENTER); chip.setPadding(8,16,8,16);
                chip.setTypeface(null, (sel||meta)?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                chip.setBackground(box(sel?GOLD:CARD,6, sel?0xFFFFFFFF:(meta?GOLD:EDGE), (sel||meta)?2:1));
                LinearLayout.LayoutParams chl=new LinearLayout.LayoutParams(0,-2,1f); chl.setMargins(3,3,3,3); chip.setLayoutParams(chl);
                chip.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ buildSel=name; buzz(); showPanel(); } });
                pressFeedback(chip);
                row.addView(chip);
            }
            if(row!=null){ int fillN=(3-(arr.length%3))%3; for(int k=0;k<fillN;k++){ View sp=new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(0,1,1f)); row.addView(sp);} }
        }

        Button done=new Button(this); done.setText("DONE"); done.setAllCaps(false);
        done.setBackground(box(BLOOD,6,BLOODL,2)); done.setTextColor(BONE); done.setTextSize(15); done.setTypeface(null, android.graphics.Typeface.BOLD);
        done.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2); dl.setMargins(0,14,0,0); done.setLayoutParams(dl);
        root.addView(done);
    }

    // A big two-line "rite" button: bold title over a small subtitle. These are
    // the primary controls of the overlay — everything else is correction.
    private LinearLayout ritualBtn(String title, String subtitle, int bg, int border, boolean enabled){
        LinearLayout btn=new LinearLayout(this); btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER); btn.setPadding(8,16,8,14);
        btn.setBackground(box(bg,8,border,enabled?2:1));
        TextView t=new TextView(this); t.setText(title);
        t.setTextColor(enabled?BONE:ASH); t.setTextSize(13); t.setGravity(Gravity.CENTER);
        t.setTypeface(null, enabled?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
        btn.addView(t);
        TextView s=new TextView(this); s.setText(subtitle);
        s.setTextColor(enabled?ASH:DIM); s.setTextSize(9); s.setGravity(Gravity.CENTER);
        s.setPadding(0,3,0,0);
        btn.addView(s);
        pressFeedback(btn);
        return btn;
    }

    // Builds one champ cell: [ chip: name(+1) | count(-1) ][ opp badge ]
    // Used by both the RECENT row and the cost grid.
    // Record the inverse of a "copies seen" change as the pending undo, but only
    // if the count actually moved (pool.add floors at 0, so a -1 at 0 is a no-op
    // and must NOT arm an undo that would wrongly add a copy back).
    // Toggle the in-place undo bar to match the pending undo (no panel rebuild).
    private void refreshUndoBar(){
        if(undoBar==null) return;
        if(undoAction!=null){
            undoBar.setText("↶ undo  "+undoLabel);
            undoBar.setVisibility(View.VISIBLE);
        } else {
            undoBar.setVisibility(View.GONE);
        }
    }

    private void recordSeenUndo(final String name, int before){
        int applied = pool.seenCount(name) - before;
        if(applied == 0) return;
        final int inv = -applied;
        undoAction = new Runnable(){ public void run(){ pool.add(name, inv); }};
        undoLabel = (inv>0?"+":"") + inv + " " + name;
    }

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
            public void onClick(View v){ int b=pool.seenCount(name); pool.add(name,1); recordSeenUndo(name,b); buzz(); paintChipPair(chip,nameTv,countTv,name,fc); refreshUndoBar(); }
        });
        // hold a name to mark/unmark it as prey for THE HUNT (auto-buy)
        nameTv.setOnLongClickListener(new View.OnLongClickListener(){
            public boolean onLongClick(View v){
                if(!pool.toggleHunt(name)){
                    Toast.makeText(OverlayService.this,"The hunt tracks at most 5 marks",Toast.LENGTH_SHORT).show();
                    return true;
                }
                buzz();
                Toast.makeText(OverlayService.this,
                    pool.isHunted(name)?("✦ "+name+" marked — THE HUNT will buy it on sight")
                                       :(name+" unmarked"),Toast.LENGTH_SHORT).show();
                showPanel(); // refresh the hunt button subtitle + chip mark
                return true;
            }
        });
        countTv.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){ int b=pool.seenCount(name); pool.add(name,-1); recordSeenUndo(name,b); buzz(); paintChipPair(chip,nameTv,countTv,name,fc); refreshUndoBar(); }
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
            nameTv.setText((pool.isHunted(name)?"✦ ":"")+name);
            nameTv.setTextColor(0xFF000000);
            nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
            nameTv.setTextSize(ts(15));
            nameTv.setGravity(Gravity.CENTER);
            nameTv.setPadding(10,22,4,22);
            LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(0,-2,1f); nameTv.setLayoutParams(nlp);

            // count shows the number with a tiny minus hint; tapping it = -1
            countTv.setText(seen+" \u2212");
            countTv.setTextColor(0xFF000000);
            countTv.setTypeface(null, android.graphics.Typeface.BOLD);
            countTv.setTextSize(ts(15));
            countTv.setGravity(Gravity.CENTER);
            countTv.setPadding(8,22,10,22);
            countTv.setBackground(box(0x33000000,0,0,0)); // subtle darken to show it's a separate tap zone
            countTv.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-2,-2); countTv.setLayoutParams(clp);
        } else {
            // inactive: just the name, full width, tap to +1
            chip.setBackground(box(CARD,6,pool.isHunted(name)?GOLD:EDGE,1));
            nameTv.setText((pool.isHunted(name)?"✦ ":"")+name);
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

    // extracts the champion name from an auto-scan results entry
    // (entries are "Name" + "★"*stars + optional " ≈" visual-ID marker)
    private static String scanEntryName(String entry){
        int cut=entry.indexOf('★');
        String n=cut>=0?entry.substring(0,cut):entry;
        return n.replace(" ≈","").trim();
    }

    private static String joinNames(java.util.List<String> l){
        StringBuilder sb=new StringBuilder();
        for(String s:l){ if(sb.length()>0) sb.append(", "); sb.append(s); }
        return sb.toString();
    }

    // next trait breakpoint at or above count, from TraitData ("2 / 4 / 6 / 9").
    // 0 = trait unknown or already at/above the top breakpoint.
    private static int nextBreakpoint(String trait, int count){
        for(String[] tr : TraitData.TRAITS){
            if(!tr[0].equals(trait)) continue;
            int best=0;
            for(String part : tr[1].split("/")){
                try{
                    int bp=Integer.parseInt(part.trim());
                    if(bp>=count){ best=bp; break; }
                }catch(Exception e){}
            }
            return best;
        }
        return 0;
    }

    // AUGMENTS TAB: per-augment tier list + comp priorities + exclusions + mechanics.
    private void buildAugments(LinearLayout root){
        // augment round context banner
        int augStg=pool.getStageNum(), augRnd=pool.getRoundNum();
        boolean isAugRound=(augStg==2&&augRnd==1)||(augStg==3&&augRnd==2)||(augStg==4&&augRnd==2);
        if(isAugRound){
            int augNum=(augStg==2)?1:(augStg==3)?2:3;
            TextView augBanner=new TextView(this);
            augBanner.setText("★ AUGMENT OFFER NOW ("+augNum+"/3)  ·  pick S or A tier");
            augBanner.setTextColor(0xFF0A0800); augBanner.setTextSize(12);
            augBanner.setTypeface(null,android.graphics.Typeface.BOLD);
            augBanner.setGravity(android.view.Gravity.CENTER);
            augBanner.setBackground(box(GOLD,6,0xFFE8C030,2)); augBanner.setPadding(10,10,10,10);
            LinearLayout.LayoutParams abl=new LinearLayout.LayoutParams(-1,-2); abl.setMargins(0,0,0,10); augBanner.setLayoutParams(abl);
            root.addView(augBanner);
        }
        // set label
        TextView lbl=new TextView(this); lbl.setText(AugmentData.SET_LABEL);
        lbl.setTextColor(DIM); lbl.setTextSize(9); lbl.setPadding(2,0,0,8); root.addView(lbl);

        // ✦ YOUR AUGMENTS — remembered from scans that spotted them on screen
        java.util.List<String> mine=pool.getMyAugments();
        if(!mine.isEmpty()){
            addSecHdr(root, "YOUR AUGMENTS  ("+mine.size()+")", GOLD);
            LinearLayout myCard=new LinearLayout(this); myCard.setOrientation(LinearLayout.VERTICAL);
            myCard.setBackground(box(CARD,6,GOLD,2)); myCard.setPadding(12,10,12,10);
            LinearLayout.LayoutParams mcl=new LinearLayout.LayoutParams(-1,-2); mcl.setMargins(0,2,0,10); myCard.setLayoutParams(mcl);
            for(String a:mine){
                String tier=""; String comps="";
                for(AugmentData.AugmentEntry ae:AugmentData.AUGMENTS){
                    if(ae.name.equals(a)){ tier=ae.tier; comps=ae.comps; break; }
                }
                TextView at=new TextView(this);
                at.setText((tier.isEmpty()?"":("["+tier+"]  "))+a+(comps.isEmpty()?"":("  ·  "+comps)));
                at.setTextColor(BONE); at.setTextSize(11); at.setPadding(0,2,0,2);
                myCard.addView(at);
            }
            root.addView(myCard);
        }

        // ---- tier-grouped augment list ----
        int augVisCount=0; for(AugmentData.AugmentEntry a:AugmentData.AUGMENTS) if(augFilter.isEmpty()||augFilter.equals(a.tier)) augVisCount++;
        addSecHdr(root, augFilter.isEmpty()?"AUGMENTS  ("+augVisCount+")":"AUGMENTS — "+augFilter+"-Tier  ("+augVisCount+")", GOLD);
        // compare row: up to 3 pinned augments shown at the top; tap an aug card below to pin/unpin
        if(!augCompare.isEmpty()){
            LinearLayout cmpRow=new LinearLayout(this); cmpRow.setOrientation(LinearLayout.HORIZONTAL); cmpRow.setPadding(0,0,0,8);
            for(String ca:augCompare){
                String ct=""; int cClr=EDGE;
                for(AugmentData.AugmentEntry ae:AugmentData.AUGMENTS){ if(ae.name.equals(ca)){ct=ae.tier;break;} }
                if(ct.equals("S"))cClr=GOLD; else if(ct.equals("A"))cClr=GREEN; else if(ct.equals("B"))cClr=ASH;
                TextView cc=new TextView(this); cc.setText((ct.isEmpty()?"":("["+ct+"]  "))+ca);
                cc.setTextColor(BONE); cc.setTextSize(11); cc.setGravity(Gravity.CENTER);
                cc.setBackground(box(CARD,6,cClr,2)); cc.setPadding(10,6,10,6);
                LinearLayout.LayoutParams ccl=new LinearLayout.LayoutParams(-2,-2); ccl.setMargins(0,0,6,4); cc.setLayoutParams(ccl);
                cmpRow.addView(cc);
            }
            TextView clrCmp=new TextView(this); clrCmp.setText("✕");
            clrCmp.setTextColor(BLOODL); clrCmp.setTextSize(12); clrCmp.setGravity(Gravity.CENTER);
            clrCmp.setBackground(box(CARD,6,EDGE,1)); clrCmp.setPadding(10,6,10,6);
            clrCmp.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ augCompare.clear(); showPanel(); }});
            cmpRow.addView(clrCmp);
            root.addView(cmpRow);
        }
        int augFIdx=augFilter.isEmpty()?0:augFilter.equals("S")?1:augFilter.equals("A")?2:augFilter.equals("B")?3:4;
        pickRow(root, new String[]{"ALL","S","A","B","C"}, new int[]{0,1,2,3,4}, augFIdx, 8,
            new PickSetter(){ public void pick(int v){ String[] ts={"","S","A","B","C"}; augFilter=ts[v]; showPanel(); }});

        String[] tiers   = {"S",   "A",    "B",  "C"};
        int[]    tierClr = {GOLD, GREEN,   ASH,  DIM};
        int[]    tierCts = new int[tiers.length];
        for(AugmentData.AugmentEntry a:AugmentData.AUGMENTS) for(int ti=0;ti<tiers.length;ti++) if(a.tier.equals(tiers[ti])){ tierCts[ti]++; break; }
        java.util.List<String> takenAugs=pool.getMyAugments();
        for(int t=0;t<tiers.length;t++){
            if(!augFilter.isEmpty() && !augFilter.equals(tiers[t])) continue;
            boolean headerAdded=false;
            for(AugmentData.AugmentEntry aug : AugmentData.AUGMENTS){
                if(!aug.tier.equals(tiers[t])) continue;
                if(!headerAdded){
                    TextView th=new TextView(this); th.setText(tiers[t]+"-Tier  ("+tierCts[t]+")");
                    th.setTextColor(tierClr[t]); th.setTextSize(10);
                    th.setTypeface(null, android.graphics.Typeface.BOLD);
                    th.setPadding(2,8,0,4); root.addView(th);
                    headerAdded=true;
                }
                boolean pinned=augCompare.contains(aug.name);
                boolean taken=takenAugs.contains(aug.name);
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(box(taken?0xFF0D2210:CARD,6,taken?GREEN:pinned?tierClr[t]:EDGE,(taken||pinned)?2:1)); card.setPadding(10,8,10,8);
                LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2); cl.setMargins(0,0,0,4); card.setLayoutParams(cl);
                final String augName=aug.name; final int tClr=tierClr[t];
                card.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    if(augCompare.contains(augName)){ augCompare.remove(augName); }
                    else { if(augCompare.size()>=3) augCompare.remove(0); augCompare.add(augName); }
                    showPanel();
                }});
                card.setOnLongClickListener(new View.OnLongClickListener(){ public boolean onLongClick(View v){
                    pool.addMyAugment(augName); buzz(); showPanel(); return true;
                }});

                LinearLayout row=new LinearLayout(this); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                // tier badge
                TextView badge=new TextView(this); badge.setText(tiers[t]);
                badge.setTextColor(0xFF000000); badge.setTextSize(10);
                badge.setTypeface(null, android.graphics.Typeface.BOLD); badge.setGravity(android.view.Gravity.CENTER);
                badge.setBackground(box(tierClr[t],4,0,0)); badge.setPadding(10,4,10,4);
                LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-2,-2); bl.setMargins(0,0,8,0); badge.setLayoutParams(bl);
                // name
                TextView nm=new TextView(this); nm.setText((taken?"✓ ":"")+aug.name);
                nm.setTextColor(taken?GREEN:BONE); nm.setTextSize(13); nm.setTypeface(null, android.graphics.Typeface.BOLD);
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
        addSecHdr(root, "COMP PRIORITIES", GOLD);
        for(String[] c : AugmentData.COMP_PRIORITIES){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(box(CARD,6,EDGE,1)); row.setPadding(12,9,12,9);
            LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2); rl.setMargins(0,0,0,5); row.setLayoutParams(rl);
            TextView nm=new TextView(this); nm.setText(c[0]); nm.setTextColor(BONE); nm.setTextSize(13); nm.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView pr=new TextView(this); pr.setText(c[1]); pr.setTextColor(ASH); pr.setTextSize(11); pr.setLineSpacing(3,1f);
            row.addView(nm); row.addView(pr); root.addView(row);
        }

        // exclusions
        addSecHdr(root, "KEY EXCLUSIONS", GOLD);
        for(String ex : AugmentData.EXCLUSIONS){
            TextView e=new TextView(this); e.setText("\u2022  "+ex);
            e.setTextColor(BONE); e.setTextSize(11); e.setLineSpacing(3,1f); e.setPadding(2,0,2,5); root.addView(e);
        }

        // mechanics
        addSecHdr(root, "MECHANICS", GOLD);
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
            emptyCard.setBackground(box(CARD,8,EDGE,1)); emptyCard.setPadding(18,16,18,16);
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
        addSecHdr(root, "JUNK ON BENCH (thins the pool)", GOLD);
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
        TextView thinHint=new TextView(this); thinHint.setText("tap +1 junk of a cost, long-press \u22121 \u00b7 auto-filled by scrying your bench");
        thinHint.setTextColor(DIM); thinHint.setTextSize(9); thinHint.setPadding(2,4,2,10); root.addView(thinHint);

        // \u2726 SYNERGIES \u2014 computed from your last board scry, using the champ\u2192trait
        // mapping the overlay learns from unit popups. Unknown until scryed once.
        if(!autoScanResults.isEmpty()){
            java.util.LinkedHashMap<String,Integer> traitCounts=new java.util.LinkedHashMap<>();
            java.util.List<String> unlearned=new java.util.ArrayList<>();
            java.util.Set<String> boardNames=new java.util.LinkedHashSet<>();
            for(String e:autoScanResults) boardNames.add(scanEntryName(e));
            for(String n:boardNames){
                java.util.List<String> ts=pool.traitsOf(n);
                if(ts.isEmpty()){ if(!unlearned.contains(n)) unlearned.add(n); continue; }
                for(String t:ts){
                    Integer c=traitCounts.get(t);
                    traitCounts.put(t, c==null?1:c+1);
                }
            }
            if(!traitCounts.isEmpty() || !unlearned.isEmpty()){
                addSecHdr(root, "SYNERGIES \u00b7 LAST SCRY", GOLD);
                if(!traitCounts.isEmpty()){
                    StringBuilder syn=new StringBuilder();
                    for(java.util.Map.Entry<String,Integer> e:traitCounts.entrySet()){
                        int next=nextBreakpoint(e.getKey(), e.getValue());
                        if(syn.length()>0) syn.append("   ");
                        syn.append(e.getKey()).append(" ").append(e.getValue());
                        if(next>0) syn.append("/").append(next);
                    }
                    TextView synTv=new TextView(this); synTv.setText(syn.toString());
                    synTv.setTextColor(BONE); synTv.setTextSize(12); synTv.setLineSpacing(4,1f);
                    synTv.setPadding(2,0,2,2); root.addView(synTv);
                    // 1-more gap: traits where adding 1 unit hits the next breakpoint
                    java.util.List<String> gapList=new java.util.ArrayList<>();
                    for(java.util.Map.Entry<String,Integer> ge:traitCounts.entrySet()){
                        int nb=nextBreakpoint(ge.getKey(),ge.getValue());
                        if(nb>0 && nb==ge.getValue()+1) gapList.add(ge.getKey()+" →"+nb);
                    }
                    if(!gapList.isEmpty()){
                        TextView gapTv=new TextView(this);
                        gapTv.setText("1 more: "+android.text.TextUtils.join("  ·  ",gapList));
                        gapTv.setTextColor(GOLD); gapTv.setTextSize(12);
                        gapTv.setTypeface(null,android.graphics.Typeface.BOLD);
                        gapTv.setPadding(2,3,2,4); root.addView(gapTv);
                    }
                }
                if(!unlearned.isEmpty()){
                    TextView un=new TextView(this);
                    un.setText("traits not yet learned: "+joinNames(unlearned)+" \u2014 scry them once with a tap");
                    un.setTextColor(DIM); un.setTextSize(9); un.setPadding(2,2,2,8); root.addView(un);
                }
            }
        }

        // tier totals (pool remaining per cost, minus junk) for the rolldown sim
        int[] tierTotal=new int[6];
        for(int co=1;co<=5;co++){
            int t=0; for(String n:SetData.CHAMPS[co]) t+=pool.remaining(n);
            t-=pool.getJunk(co); tierTotal[co]=Math.max(0,t);
        }
        int rollGold=Math.min(60, pool.getGold());

        for(final String name:names){
            int co=Pool.costOf(name); int s=pool.seenCount(name); int rem=pool.remaining(name);
            int players=pool.oppCount(name);
            int poolSize=SetData.SIZE[co];
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
            // ⛧ rolldown forecast: P(finding 1/2/3 copies) spending current gold.
            // Monte Carlo over the real shop process, so the shrinking pool and
            // tier totals are modeled exactly.
            if(rem>0 && rollGold>=2 && level>=1 && level<=10 && tierTotal[co]>0){
                double[] hc=RollMath.hitChances(level, co, Math.min(rem,tierTotal[co]), tierTotal[co], rollGold, 3);
                StringBuilder rd=new StringBuilder(rollGold+"g rolldown:");
                rd.append("  ≥1 ").append(Math.round(hc[0]*100)).append("%");
                if(rem>=2) rd.append("  ≥2 ").append(Math.round(hc[1]*100)).append("%");
                if(rem>=3) rd.append("  ≥3 ").append(Math.round(hc[2]*100)).append("%");
                int eg=RollMath.expectedGoldToFirst(level, co, Math.min(rem,tierTotal[co]), tierTotal[co], 80);
                if(eg>0) rd.append("  ·  1st ≈").append(eg).append("g");
                TextView rdTv=new TextView(this); rdTv.setText(rd.toString());
                rdTv.setTextColor(hc[0]>=0.7?GREEN:hc[0]>=0.4?GOLD:ASH); rdTv.setTextSize(10);
                mid.addView(rdTv);
            }
            card.addView(mid);

            // right side: rough "per roll" estimate, rounded to a band (no false
            // precision, since it's computed only from what you've tapped).
            LinearLayout vbox=new LinearLayout(this); vbox.setOrientation(LinearLayout.VERTICAL); vbox.setGravity(Gravity.CENTER);
            vbox.setPadding(8,0,8,0);
            double perRoll = rem<=0 ? 0 : rerollChance(name)*100.0;
            TextView pct=new TextView(this);
            pct.setText(rem<=0 ? "--" : "~"+roundBand((int)Math.round(perRoll))+"%");
            // color tells the story at a glance: green = good roll, gold = ok, bone = thin
            pct.setTextColor(rem<=0?DIM:(perRoll>=50?GREEN:perRoll>=25?GOLD:BONE));
            pct.setTextSize(17); pct.setTypeface(null, android.graphics.Typeface.BOLD); pct.setGravity(Gravity.CENTER);
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
        legend.setText("% = roll hit odds at this level");
        legend.setTextColor(DIM); legend.setTextSize(10); legend.setPadding(2,8,2,0); root.addView(legend);

        // death-return reminder: eliminated players' units go back to the pool
        TextView deathTip=new TextView(this);
        deathTip.setText("\u2620 tap a count down when a player dies to release their units");
        deathTip.setTextColor(GOLD); deathTip.setTextSize(10); deathTip.setPadding(2,6,2,0); root.addView(deathTip);

        final Button wipe=new Button(this); wipe.setText("RESET ALL"); wipe.setAllCaps(false);
        wipe.setBackground(box(0xFF1A0C0E,6,BLOOD,2)); wipe.setTextColor(BLOODL); wipe.setTextSize(13);
        // two-tap confirm so a stray tap can't wipe the game mid-match: first tap
        // arms the button (label changes, auto-disarms after 3s); second tap wipes.
        final boolean[] armed={false};
        final Runnable disarm=new Runnable(){ public void run(){
            armed[0]=false; wipe.setText("RESET ALL");
            wipe.setBackground(box(0xFF1A0C0E,6,BLOOD,2)); wipe.setTextColor(BLOODL);
        }};
        wipe.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(armed[0]){ wipe.removeCallbacks(disarm); pool.reset(); undoAction=null; undoLabel=null; refreshHud(); showPanel(); return; }
            armed[0]=true; wipe.setText("TAP AGAIN TO WIPE");
            wipe.setBackground(box(BLOOD,6,BLOODL,2)); wipe.setTextColor(BONE); buzz();
            wipe.postDelayed(disarm, 3000);
        } });
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

        // ⛧ the rite comes first: one press reads gold and level off the screen
        LinearLayout econScry=ritualBtn("⛧ SCRY GOLD & LEVEL","read from the screen — nothing to type",
                BLOOD, BLOODL, true);
        LinearLayout.LayoutParams esl=new LinearLayout.LayoutParams(-1,-2); esl.setMargins(0,4,0,10); econScry.setLayoutParams(esl);
        econScry.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ triggerScan(); }});
        root.addView(econScry);

        TextView gh=new TextView(this); gh.setText("◇ GOLD");
        gh.setTextColor(GOLD); gh.setTextSize(11); gh.setTypeface(null,android.graphics.Typeface.BOLD);
        gh.setLetterSpacing(0.1f); gh.setPadding(2,0,0,8);
        root.addView(gh);

        LinearLayout goldRow=new LinearLayout(this); goldRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView gMinus=makeAdjBtn("−", 0xFF1A0C0E, BLOODL);
        econGoldTv=new TextView(this); econGoldTv.setText(gold+"g");
        econGoldTv.setTextColor(GOLD); econGoldTv.setTextSize(ts(28)); econGoldTv.setTypeface(null, android.graphics.Typeface.BOLD);
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
        TextView goldHint=new TextView(this); goldHint.setText("manual correction  ·  tap ±1  ·  hold to repeat");
        goldHint.setTextColor(DIM); goldHint.setTextSize(9); goldHint.setPadding(2,2,2,0); root.addView(goldHint);
        int curLv=pool.getLevel();
        int[] lvCosts={0,0,0,0,4,8,20,32,48,80};
        if(curLv>=4 && curLv<=9){
            TextView lvHint=new TextView(this);
            lvHint.setText("level up costs "+lvCosts[curLv]+"g  (Lv "+curLv+"→"+(curLv+1)+")");
            lvHint.setTextColor(DIM); lvHint.setTextSize(9); lvHint.setPadding(2,1,2,0); root.addView(lvHint);
        }

        // quick-set presets: snap straight to a common gold value (the interest
        // brackets + 0 for all-in) instead of holding ± across a big gap
        LinearLayout gqRow=new LinearLayout(this); gqRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams gqrl=new LinearLayout.LayoutParams(-1,-2); gqrl.setMargins(0,8,0,0); gqRow.setLayoutParams(gqrl);
        int[] presets={0,10,20,30,50};
        for(int i=0;i<presets.length;i++){
            final int pv=presets[i];
            TextView b=new TextView(this); b.setText(pv+"g");
            b.setTextColor(BONE); b.setTextSize(12); b.setGravity(Gravity.CENTER); b.setPadding(0,9,0,9);
            b.setBackground(box(CARD,6,EDGE,1));
            LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,-2,1f); bl.setMargins(i==0?0:4,0,0,0); b.setLayoutParams(bl);
            b.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setGold(pv); buzz(); refreshEcon(); }});
            pressFeedback(b);
            gqRow.addView(b);
        }
        root.addView(gqRow);
        econRollBudgetTv=new TextView(this);
        econRollBudgetTv.setTextColor(ASH); econRollBudgetTv.setTextSize(10);
        econRollBudgetTv.setPadding(2,3,2,0);
        econRollBudgetTv.setText(rollBudgetHint(gold));
        root.addView(econRollBudgetTv);

        // interest info
        LinearLayout iRow=new LinearLayout(this); iRow.setOrientation(LinearLayout.VERTICAL);
        iRow.setBackground(box(CARD,6,EDGE,1)); iRow.setPadding(12,10,12,10);
        LinearLayout.LayoutParams irl=new LinearLayout.LayoutParams(-1,-2); irl.setMargins(0,10,0,0); iRow.setLayoutParams(irl);
        TextView iLbl=new TextView(this); iLbl.setText("INTEREST");
        iLbl.setTextColor(ASH); iLbl.setTextSize(10); iLbl.setLetterSpacing(0.08f); iRow.addView(iLbl);
        econInterestTv=new TextView(this); econInterestTv.setText("+"+intr+"g per round");
        econInterestTv.setTextColor(BONE); econInterestTv.setTextSize(17); econInterestTv.setTypeface(null, android.graphics.Typeface.BOLD); iRow.addView(econInterestTv);
        econBracketTv=new TextView(this); econBracketTv.setText(gold>=50?"max interest (50g+)":"+"+toNext+"g to next bracket");
        econBracketTv.setTextColor(gold>=50?GOLD:toNext<=2?GOLD:ASH); econBracketTv.setTextSize(11); iRow.addView(econBracketTv);
        econEfficiencyTv=new TextView(this); econEfficiencyTv.setTextSize(11); econEfficiencyTv.setPadding(0,3,0,0);
        int effOverhead=gold>=50?gold-50:gold%10;
        if(effOverhead>0){ econEfficiencyTv.setText(gold>=50?effOverhead+"g above cap — spend freely":effOverhead+"g overhead — safe to spend without losing bracket"); econEfficiencyTv.setTextColor(GREEN); econEfficiencyTv.setVisibility(View.VISIBLE); }
        else { econEfficiencyTv.setVisibility(View.GONE); }
        iRow.addView(econEfficiencyTv);
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
        addSecHdr(root, "STREAK", GOLD);

        LinearLayout streakRow=new LinearLayout(this); streakRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sL=makeAdjBtn("L", BLOOD, BONE);
        sL.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); pool.setStreak(s>0?-1:s-1); buzz(); refreshEcon();
        }});
        econStreakTv=new TextView(this);
        String sText = streak==0?"—":Math.abs(streak)+(streak>0?"W":"L");
        int sColor = streak>0?GREEN:(streak<0?BLOODL:ASH);
        econStreakTv.setText(sText); econStreakTv.setTextColor(sColor);
        econStreakTv.setTextSize(ts(24)); econStreakTv.setTypeface(null, android.graphics.Typeface.BOLD);
        econStreakTv.setGravity(Gravity.CENTER); econStreakTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        // tap the number to flip the streak sign (W↔L) — one-tap fix for a misclick
        econStreakTv.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); if(s!=0){ pool.setStreak(-s); buzz(); refreshEcon(); }
        }});
        TextView sW=makeAdjBtn("W", 0xFF0D2210, GREEN);
        sW.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); pool.setStreak(s<0?1:s+1); buzz(); refreshEcon();
        }});
        streakRow.addView(sL); streakRow.addView(econStreakTv); streakRow.addView(sW);
        root.addView(streakRow);
        econBonusTv=new TextView(this);
        econBonusTv.setTextColor(sBonus>0?ASH:DIM); econBonusTv.setTextSize(11); econBonusTv.setPadding(2,4,2,0);
        econBonusTv.setText(streakHint(streak,sBonus)); econBonusTv.setVisibility(View.VISIBLE);
        root.addView(econBonusTv);
        econStreakRoiTv=new TextView(this);
        econStreakRoiTv.setTextColor(DIM); econStreakRoiTv.setTextSize(10); econStreakRoiTv.setPadding(2,2,2,0);
        int streakRoi=Pool.totalStreakGold(streak);
        if(streakRoi>0){ econStreakRoiTv.setText("earned "+streakRoi+"g this streak"); econStreakRoiTv.setVisibility(View.VISIBLE); }
        else { econStreakRoiTv.setVisibility(View.GONE); }
        root.addView(econStreakRoiTv);
        TextView streakScale=new TextView(this); streakScale.setText("2+ streak = +1g  ·  4+ = +2g  ·  6+ = +3g");
        streakScale.setTextColor(DIM); streakScale.setTextSize(10); streakScale.setPadding(2,2,2,0); root.addView(streakScale);
        TextView streakWhy=new TextView(this); streakWhy.setText("streak isn't readable as text — set by hand  ·  tap to flip W↔L");
        streakWhy.setTextColor(DIM); streakWhy.setTextSize(9); streakWhy.setPadding(2,2,2,0); root.addView(streakWhy);

        // expected income card
        LinearLayout incCard=new LinearLayout(this); incCard.setOrientation(LinearLayout.VERTICAL);
        incCard.setBackground(box(CARD,6,BLOODL,2)); incCard.setPadding(14,12,14,12);
        LinearLayout.LayoutParams icl=new LinearLayout.LayoutParams(-1,-2); icl.setMargins(0,14,0,0); incCard.setLayoutParams(icl);
        TextView icH=new TextView(this); icH.setText("EXPECTED NEXT ROUND");
        icH.setTextColor(ASH); icH.setTextSize(10); icH.setLetterSpacing(0.08f); incCard.addView(icH);
        econIncomeTv=new TextView(this); econIncomeTv.setText(income+"g");
        econIncomeTv.setTextColor(GOLD); econIncomeTv.setTextSize(ts(28)); econIncomeTv.setTypeface(null, android.graphics.Typeface.BOLD); incCard.addView(econIncomeTv);
        econBreakTv=new TextView(this); econBreakTv.setText("5 base  +  "+intr+"g interest  +  "+sBonus+"g streak"+(streak>0?"  +  1g win":""));
        econBreakTv.setTextColor(ASH); econBreakTv.setTextSize(11); incCard.addView(econBreakTv);
        econProjectedTv=new TextView(this);
        econProjectedTv.setText("~"+(gold+income*2)+"g in 2r  ·  ~"+(gold+income*4)+"g in 4r");
        econProjectedTv.setTextColor(DIM); econProjectedTv.setTextSize(10); incCard.addView(econProjectedTv);
        root.addView(incCard);
        econNextRoundBtn=new TextView(this);
        econNextRoundBtn.setText("→ NEXT ROUND  +"+income+"g");
        econNextRoundBtn.setTextColor(GOLD); econNextRoundBtn.setTextSize(14); econNextRoundBtn.setGravity(Gravity.CENTER);
        econNextRoundBtn.setBackground(box(CARD,6,GOLD,2)); econNextRoundBtn.setPadding(0,14,0,14);
        LinearLayout.LayoutParams nrl=new LinearLayout.LayoutParams(-1,-2); nrl.setMargins(0,8,0,0); econNextRoundBtn.setLayoutParams(nrl);
        econNextRoundBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int g=pool.getGold(), s=pool.getStreak();
            pool.setGold(g+Pool.expectedIncome(g,s)); advanceRound(1); buzz(); refreshEcon();
        }});
        root.addView(econNextRoundBtn);

        // ✦ ROUND RESULT — one tap handles income + streak + stage + HP (loss only)
        addSecHdr(root, "ROUND RESULT", ASH);
        LinearLayout resultRow=new LinearLayout(this);
        LinearLayout.LayoutParams rrl=new LinearLayout.LayoutParams(-1,-2); rrl.setMargins(0,4,0,0); resultRow.setLayoutParams(rrl);
        TextView wonBtn=new TextView(this); wonBtn.setText("✓  WON  +"+income+"g");
        wonBtn.setTextColor(0xFF0A1A0A); wonBtn.setTextSize(14); wonBtn.setGravity(Gravity.CENTER);
        wonBtn.setTypeface(null,android.graphics.Typeface.BOLD);
        wonBtn.setBackground(box(GREEN,8,0xFF3DCC47,2)); wonBtn.setPadding(0,16,0,16);
        LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-2,1f); wonBtn.setLayoutParams(wl);
        wonBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int g=pool.getGold(), s=pool.getStreak();
            pool.setGold(g+Pool.expectedIncome(g,s));
            pool.setStreak(s<0?1:s+1);
            pool.setWins(pool.getWins()+1);
            advanceRound(1); buzz(); refreshEcon();
        }});
        pressFeedback(wonBtn);
        econWonBtnTv=wonBtn;
        TextView lostBtn=new TextView(this); lostBtn.setText("✗  LOST");
        lostBtn.setTextColor(BLOODL); lostBtn.setTextSize(14); lostBtn.setGravity(Gravity.CENTER);
        lostBtn.setTypeface(null,android.graphics.Typeface.BOLD);
        lostBtn.setBackground(box(0xFF1A0806,8,BLOODL,2)); lostBtn.setPadding(0,16,0,16);
        LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(0,-2,1f); ll.setMargins(8,0,0,0); lostBtn.setLayoutParams(ll);
        lostBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int g=pool.getGold(), s=pool.getStreak();
            pool.setGold(g+Pool.expectedIncome(g,s));
            int stg=pool.getStageNum(); int[] sd=SetData.STAGE_BASE_DMG;
            pool.setHp(pool.getHp()-sd[Math.min(stg,sd.length-1)]);
            pool.setStreak(s>0?-1:s-1);
            pool.setLosses(pool.getLosses()+1);
            advanceRound(1); buzz(); refreshEcon();
        }});
        pressFeedback(lostBtn);
        resultRow.addView(wonBtn); resultRow.addView(lostBtn);
        root.addView(resultRow);
        TextView resultHint=new TextView(this);
        resultHint.setText("gold + streak + stage in one tap  ·  LOST also deducts HP");
        resultHint.setTextColor(DIM); resultHint.setTextSize(9); resultHint.setPadding(2,3,2,0); root.addView(resultHint);
        econRecordTv=new TextView(this);
        int initW=pool.getWins(), initL=pool.getLosses();
        int initTotal=initW+initL;
        if(initTotal>0){
            econRecordTv.setText(initW+"W  "+initL+"L  this game  ("+(initTotal>0?Math.round(100f*initW/initTotal)+"% winrate":"—")+")");
            econRecordTv.setVisibility(View.VISIBLE);
        } else { econRecordTv.setVisibility(View.GONE); }
        econRecordTv.setTextColor(ASH); econRecordTv.setTextSize(11); econRecordTv.setGravity(Gravity.CENTER);
        econRecordTv.setPadding(0,5,0,0); root.addView(econRecordTv);

        // ◇ STAGE / ROUND — upcoming augments and carousels
        int stgNow=pool.getStageNum(), rndNow=pool.getRoundNum();
        addSecHdr(root, "STAGE", ASH);
        LinearLayout stgRow=new LinearLayout(this); stgRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams strl=new LinearLayout.LayoutParams(-1,-2); strl.setMargins(0,4,0,0); stgRow.setLayoutParams(strl);
        TextView sPrev=makeAdjBtn("◀",CARD,ASH); sPrev.setLayoutParams(new LinearLayout.LayoutParams(80,-2));
        econStageTv=new TextView(this); econStageTv.setText(stgNow>0?stgNow+"-"+rndNow:"?");
        econStageTv.setTextColor(BONE); econStageTv.setTextSize(ts(22)); econStageTv.setTypeface(null,android.graphics.Typeface.BOLD);
        econStageTv.setGravity(Gravity.CENTER); econStageTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView sNext=makeAdjBtn("▶",CARD,ASH); sNext.setLayoutParams(new LinearLayout.LayoutParams(80,-2));
        sPrev.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ advanceRound(-1); buzz(); refreshEcon(); }});
        sNext.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ advanceRound(1); buzz(); refreshEcon(); }});
        stgRow.addView(sPrev); stgRow.addView(econStageTv); stgRow.addView(sNext);
        root.addView(stgRow);
        econEventTv=new TextView(this);
        econEventTv.setText(stgNow>0?nextTFTEvent(stgNow,rndNow):"tap ▶ to set stage");
        econEventTv.setTextColor(GOLD); econEventTv.setTextSize(12); econEventTv.setGravity(Gravity.CENTER); econEventTv.setPadding(0,4,0,4);
        root.addView(econEventTv);
        econTimelineTv=new TextView(this);
        String timelineStr=stgNow>0?buildStageSummary(stgNow,rndNow):"";
        econTimelineTv.setText(timelineStr);
        econTimelineTv.setTextColor(DIM); econTimelineTv.setTextSize(9); econTimelineTv.setGravity(Gravity.CENTER);
        econTimelineTv.setPadding(0,0,0,8);
        econTimelineTv.setVisibility(timelineStr.isEmpty()?View.GONE:View.VISIBLE);
        root.addView(econTimelineTv);

        // ◇ HEALTH — HP remaining this game
        int hpNow=pool.getHp();
        addSecHdr(root, "HEALTH", hpNow>50?GREEN:hpNow>20?GOLD:BLOODL);
        econHpTv=new TextView(this); econHpTv.setText(hpNow+" HP");
        econHpTv.setTextColor(hpNow>50?GREEN:hpNow>20?GOLD:BLOODL);
        econHpTv.setTextSize(ts(24)); econHpTv.setTypeface(null,android.graphics.Typeface.BOLD);
        econHpTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hptl=new LinearLayout.LayoutParams(-1,-2); hptl.setMargins(0,4,0,0); econHpTv.setLayoutParams(hptl);
        root.addView(econHpTv);
        LinearLayout dmgRow=new LinearLayout(this);
        LinearLayout.LayoutParams drl=new LinearLayout.LayoutParams(-1,-2); drl.setMargins(0,6,0,0); dmgRow.setLayoutParams(drl);
        int[] dmgs={-5,-10,-20,5}; String[] dmgLbls={"-5","-10","-20","+5"};
        for(int i=0;i<4;i++){
            final int delta=dmgs[i];
            TextView db=new TextView(this); db.setText(dmgLbls[i]);
            db.setTextColor(delta<0?BLOODL:GREEN); db.setTextSize(12); db.setGravity(Gravity.CENTER); db.setPadding(0,9,0,9);
            db.setBackground(box(CARD,6,delta<0?BLOODL:GREEN,1));
            LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1f); dl.setMargins(i>0?4:0,0,0,0); db.setLayoutParams(dl);
            db.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setHp(pool.getHp()+delta); buzz(); refreshEcon(); }});
            pressFeedback(db); dmgRow.addView(db);
        }
        TextView fullBtn=new TextView(this); fullBtn.setText("FULL");
        fullBtn.setTextColor(GREEN); fullBtn.setTextSize(12); fullBtn.setGravity(Gravity.CENTER); fullBtn.setPadding(0,9,0,9);
        fullBtn.setBackground(box(CARD,6,GREEN,1));
        LinearLayout.LayoutParams fbl=new LinearLayout.LayoutParams(0,-2,1f); fbl.setMargins(4,0,0,0); fullBtn.setLayoutParams(fbl);
        fullBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setHp(100); buzz(); refreshEcon(); }});
        pressFeedback(fullBtn); dmgRow.addView(fullBtn);
        root.addView(dmgRow);
        econRoundsLeftTv=new TextView(this);
        econRoundsLeftTv.setTextSize(10); econRoundsLeftTv.setPadding(2,3,2,0);
        int stgForHp=pool.getStageNum();
        int[] stageDmg=SetData.STAGE_BASE_DMG;
        int hpDmg=stageDmg[Math.min(stgForHp,stageDmg.length-1)];
        if(hpNow>0 && hpDmg>0){
            int losses=hpNow/hpDmg;
            String lossText=losses<=2?"~"+losses+" losses left before elim":"~"+losses+" losses left";
            econRoundsLeftTv.setText(lossText);
            econRoundsLeftTv.setTextColor(losses<=2?BLOODL:losses<=5?GOLD:ASH);
            econRoundsLeftTv.setVisibility(View.VISIBLE);
        } else { econRoundsLeftTv.setVisibility(View.GONE); }
        root.addView(econRoundsLeftTv);
        econLossBtnTv=new TextView(this);
        econLossBtnTv.setGravity(Gravity.CENTER); econLossBtnTv.setPadding(0,9,0,9); econLossBtnTv.setTextSize(12);
        LinearLayout.LayoutParams lbpl=new LinearLayout.LayoutParams(-1,-2); lbpl.setMargins(0,6,0,0); econLossBtnTv.setLayoutParams(lbpl);
        if(stgForHp>0 && hpDmg>0){
            econLossBtnTv.setText("LOSS  −"+hpDmg+" HP  (stage "+stgForHp+" base)");
            econLossBtnTv.setTextColor(BLOODL); econLossBtnTv.setBackground(box(0xFF1A0806,6,BLOODL,2));
            econLossBtnTv.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                int st=pool.getStageNum(); int[] sd=SetData.STAGE_BASE_DMG;
                pool.setHp(pool.getHp()-sd[Math.min(st,sd.length-1)]); buzz(); refreshEcon();
            }});
            pressFeedback(econLossBtnTv);
        } else {
            econLossBtnTv.setText("LOSS  (set stage first)");
            econLossBtnTv.setTextColor(DIM); econLossBtnTv.setBackground(box(CARD,6,EDGE,1));
        }
        root.addView(econLossBtnTv);

        // ✦ LEVELING — gold to the next level, from the XP the scry read off the
        // level button (worst case if XP progress is unknown). 4g buys 4 XP.
        addSecHdr(root, "LEVELING", GOLD);
        LinearLayout lvCard=new LinearLayout(this); lvCard.setOrientation(LinearLayout.VERTICAL);
        lvCard.setBackground(box(CARD,6,EDGE,1)); lvCard.setPadding(14,12,14,12);
        LinearLayout.LayoutParams lvl2=new LinearLayout.LayoutParams(-1,-2); lvl2.setMargins(0,4,0,0); lvCard.setLayoutParams(lvl2);
        int xpCur=pool.getXpCur(), xpNeed=pool.getXpNeed();
        int xpTable=Pool.xpToNext(level);
        // trust the scanned "need" only when it matches the current level's table row
        int into = (xpNeed==xpTable && xpCur>=0) ? xpCur : 0;
        if(level>=10 || xpTable<=0){
            TextView lt=new TextView(this); lt.setText("Level "+level+" — max");
            lt.setTextColor(BONE); lt.setTextSize(14); lt.setTypeface(null,android.graphics.Typeface.BOLD);
            lvCard.addView(lt);
        } else {
            int g=Pool.goldToNextLevel(level, into);
            TextView lt=new TextView(this);
            lt.setText("Lv "+level+" → "+(level+1)+":  "+g+"g");
            lt.setTextColor(GOLD); lt.setTextSize(18); lt.setTypeface(null,android.graphics.Typeface.BOLD);
            lvCard.addView(lt);
            TextView ld=new TextView(this);
            ld.setText(into>0
                ? ("xp "+into+"/"+xpTable+" (scryed)")
                : ("xp 0/"+xpTable+" assumed — scry to read your real xp"));
            ld.setTextColor(ASH); ld.setTextSize(10); lvCard.addView(ld);
            // roll-vs-level nudge using current gold
            TextView lr=new TextView(this);
            if(gold>=g+10) lr.setText("you can level AND keep "+(gold-g)+"g — leveling is cheap here");
            else if(gold>=g) lr.setText("leveling now spends down to "+(gold-g)+"g");
            else lr.setText((g-gold)+"g short of the level-up");
            lr.setTextColor(DIM); lr.setTextSize(10); lr.setPadding(0,4,0,0); lvCard.addView(lr);
        }
        root.addView(lvCard);

        // ✦ STAGE FORECAST — last scryed round, what a loss costs, what's coming
        String stage=pool.getStageRound();
        if(!stage.isEmpty()){
            addSecHdr(root, "STAGE "+stage, GOLD);
            LinearLayout stCard=new LinearLayout(this); stCard.setOrientation(LinearLayout.VERTICAL);
            stCard.setBackground(box(CARD,6,EDGE,1)); stCard.setPadding(14,12,14,12);
            LinearLayout.LayoutParams stl=new LinearLayout.LayoutParams(-1,-2); stl.setMargins(0,4,0,0); stCard.setLayoutParams(stl);
            int stg=0, rnd=0;
            try{ String[] sr=stage.split("-"); stg=Integer.parseInt(sr[0]); rnd=Integer.parseInt(sr[1]); }catch(Exception e){}
            int base=SetData.STAGE_BASE_DMG[Math.min(stg, SetData.STAGE_BASE_DMG.length-1)];
            TextView dmg=new TextView(this);
            dmg.setText("a loss costs ~"+base+" HP + 1 per surviving enemy unit");
            dmg.setTextColor(BONE); dmg.setTextSize(12); stCard.addView(dmg);
            String coming;
            if(stg>=2 && stg<=4 && rnd==3)      coming="⛧ Realm of Gods NEXT round ("+stg+"-4) — know your pick";
            else if(stg>=2 && stg<=4 && rnd<3)  coming="Realm of Gods at "+stg+"-4 · PvE at "+stg+"-7";
            else if(rnd<7)                       coming="PvE round at "+stg+"-7";
            else                                 coming="new stage next round — base damage rises";
            TextView nx=new TextView(this); nx.setText(coming);
            nx.setTextColor(rnd==3&&stg<=4?GOLD:ASH); nx.setTextSize(11); nx.setPadding(0,4,0,0); stCard.addView(nx);
            if(rnd==5||rnd==6){
                TextView slamTv=new TextView(this);
                slamTv.setText("Slam items — "+(7-rnd)+"r before PvE");
                slamTv.setTextColor(GOLD); slamTv.setTextSize(11); slamTv.setTypeface(null,android.graphics.Typeface.BOLD);
                slamTv.setPadding(0,4,0,0); stCard.addView(slamTv);
            }
            root.addView(stCard);
        }

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
        refreshHud();
        if(econGoldTv==null) return;
        int gold=pool.getGold(); int streak=pool.getStreak();
        int intr=Pool.interest(gold); int toNext=Pool.toNextBracket(gold);
        int sBonus=Pool.streakBonus(streak); int income=Pool.expectedIncome(gold,streak);
        econGoldTv.setText(gold+"g");
        econInterestTv.setText("+"+intr+"g per round");
        econBracketTv.setText(gold>=50?"max interest (50g+)":"+"+toNext+"g to next bracket");
        econBracketTv.setTextColor(gold>=50?GOLD:toNext<=2?GOLD:ASH);
        if(econEfficiencyTv!=null){
            int eff=gold>=50?gold-50:gold%10;
            if(eff>0){ econEfficiencyTv.setText(gold>=50?eff+"g above cap — spend freely":eff+"g overhead — safe to spend without losing bracket"); econEfficiencyTv.setTextColor(GREEN); econEfficiencyTv.setVisibility(View.VISIBLE); }
            else { econEfficiencyTv.setVisibility(View.GONE); }
        }
        int[] brackets={10,20,30,40,50};
        for(int i=0;i<5;i++){
            int b=brackets[i]; boolean reached=gold>=b; boolean cur=(gold/10)*10==b||(b==50&&gold>=50);
            econLadderTvs[i].setTextColor(reached?GOLD:EDGE);
            econLadderTvs[i].setTypeface(null,cur?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            econLadderTvs[i].setBackground(box(reached?0xFF1A1400:CARD,4,reached?GOLD:EDGE,reached?2:1));
        }
        econStreakTv.setText(streak==0?"—":Math.abs(streak)+(streak>0?"W":"L"));
        econStreakTv.setTextColor(streak>0?GREEN:(streak<0?BLOODL:ASH));
        econBonusTv.setText(streakHint(streak,sBonus));
        econBonusTv.setTextColor(sBonus>0?ASH:DIM); econBonusTv.setVisibility(View.VISIBLE);
        if(econRecordTv!=null){
            int rW=pool.getWins(), rL=pool.getLosses(), rT=rW+rL;
            if(rT>0){ econRecordTv.setText(rW+"W  "+rL+"L  this game  ("+Math.round(100f*rW/rT)+"% winrate)"); econRecordTv.setVisibility(View.VISIBLE); }
            else { econRecordTv.setVisibility(View.GONE); }
        }
        if(econStreakRoiTv!=null){
            int roi=Pool.totalStreakGold(streak);
            if(roi>0){ econStreakRoiTv.setText("earned "+roi+"g this streak"); econStreakRoiTv.setVisibility(View.VISIBLE); }
            else { econStreakRoiTv.setVisibility(View.GONE); }
        }
        econIncomeTv.setText(income+"g");
        econBreakTv.setText("5 base  +  "+intr+"g interest  +  "+sBonus+"g streak");
        if(econProjectedTv!=null) econProjectedTv.setText("~"+(gold+income*2)+"g in 2r  ·  ~"+(gold+income*4)+"g in 4r");
        if(econWonBtnTv!=null) econWonBtnTv.setText("✓  WON  +"+income+"g");
        if(econNextRoundBtn!=null) econNextRoundBtn.setText("→ NEXT ROUND  +"+income+"g");
        if(econRollBudgetTv!=null) econRollBudgetTv.setText(rollBudgetHint(gold));
        if(econHpTv!=null){
            int hp=pool.getHp();
            econHpTv.setText(hp+" HP");
            econHpTv.setTextColor(hp>50?GREEN:hp>20?GOLD:BLOODL);
            if(econRoundsLeftTv!=null){
                int stgForDmg=pool.getStageNum();
                int[] sdmg=SetData.STAGE_BASE_DMG;
                int dmgPer=sdmg[Math.min(stgForDmg,sdmg.length-1)];
                if(hp>0 && dmgPer>0){
                    int losses=hp/dmgPer;
                    String lt=losses<=2?"~"+losses+" losses before elim":"~"+losses+" losses left";
                    econRoundsLeftTv.setText(lt);
                    econRoundsLeftTv.setTextColor(losses<=2?BLOODL:losses<=5?GOLD:ASH);
                    econRoundsLeftTv.setVisibility(View.VISIBLE);
                } else { econRoundsLeftTv.setVisibility(View.GONE); }
            }
            if(econLossBtnTv!=null){
                int stgL=pool.getStageNum(); int[] sdL=SetData.STAGE_BASE_DMG;
                int dmgL=stgL>0?sdL[Math.min(stgL,sdL.length-1)]:0;
                if(stgL>0 && dmgL>0){
                    econLossBtnTv.setText("LOSS  −"+dmgL+" HP  (stage "+stgL+" base)");
                    econLossBtnTv.setTextColor(BLOODL); econLossBtnTv.setBackground(box(0xFF1A0806,6,BLOODL,2));
                } else {
                    econLossBtnTv.setText("LOSS  (set stage first)");
                    econLossBtnTv.setTextColor(DIM); econLossBtnTv.setBackground(box(CARD,6,EDGE,1));
                }
            }
        }
        if(econStageTv!=null){
            int stg=pool.getStageNum(), rnd=pool.getRoundNum();
            econStageTv.setText(stg>0?stg+"-"+rnd:"?");
            if(econEventTv!=null){
                String evtTxt=stg>0?nextTFTEvent(stg,rnd):"tap ▶ to set stage";
                if(evtTxt.contains("Carousel")){
                    int hp=pool.getHp();
                    evtTxt+=(hp<50?"  ·  take a unit to stabilize":hp>=70?"  ·  take a component":"");
                }
                econEventTv.setText(evtTxt);
            }
            if(econTimelineTv!=null){
                String tl=stg>0?buildStageSummary(stg,rnd):"";
                econTimelineTv.setText(tl);
                econTimelineTv.setVisibility(tl.isEmpty()?View.GONE:View.VISIBLE);
            }
        }
    }

    private static String streakHint(int streak, int sBonus){
        int abs=Math.abs(streak); boolean win=streak>0;
        if(sBonus>0){
            String dir=win?"win":"loss";
            if(abs>=6) return "+3g "+dir+" streak  —  MAX bonus, protect it";
            if(abs>=4) return "+2g "+dir+" streak  —  strong, maintain";
            return "+1g "+dir+" streak bonus  —  maintain";
        }
        if(abs==0) return "no streak  —  2W or 2L activates +1g/round bonus";
        return (win?"one more win":"one more loss")+"  →  +1g streak bonus";
    }

    private static String rollBudgetHint(int gold){
        if(gold>=50) return "max interest locked  ·  safe to level or roll freely";
        if(gold>=30) return "roll budget: "+(gold-30)+"g  (save at 30g)";
        return "below 30g  ·  build back before rolling";
    }

    private void advanceRound(int delta){
        int stg=pool.getStageNum(), rnd=pool.getRoundNum();
        if(stg==0){ pool.setStageRoundNums(1,1); return; }
        int[] maxR={0,4,7,7,7,5,3};
        rnd+=delta;
        int max=maxR[Math.min(stg,maxR.length-1)];
        if(rnd>max){ stg=Math.min(stg+1,maxR.length-1); rnd=1; }
        else if(rnd<1){ stg=Math.max(stg-1,1); rnd=maxR[Math.min(stg,maxR.length-1)]; }
        pool.setStageRoundNums(stg, rnd);
    }

    private static String nextTFTEvent(int stage, int round){
        int[][] augs={{2,1},{3,2},{4,2}};
        for(int i=0;i<augs.length;i++){
            int as=augs[i][0], ar=augs[i][1];
            if(stage<as||(stage==as&&round<=ar)){
                if(stage==as&&round==ar) return "★ AUGMENT OFFER NOW ("+(i+1)+"/3)";
                int diff=(as-stage)*7+(ar-round);
                return "★ "+(i+1)+". augment in ~"+diff+" rounds  ("+as+"-"+ar+")";
            }
        }
        int[] maxR={0,4,7,7,7,5,3};
        if(stage>0&&stage<maxR.length){
            int max=maxR[stage];
            if(round>=max) return "◉ Carousel next";
        }
        return "all augments past · late game";
    }

    // forward timeline of upcoming events from (stage,round) exclusive — up to 5 entries
    private static String buildStageSummary(int stage, int round){
        if(stage<=0) return "";
        // all game events in chronological order: {stage, round, 0=aug/1=carousel}
        int[][] events={{1,4,1},{2,1,0},{2,7,1},{3,2,0},{3,7,1},{4,2,0},{4,7,1},{5,5,1},{6,3,1}};
        StringBuilder sb=new StringBuilder();
        int count=0;
        for(int[] ev : events){
            int es=ev[0], er=ev[1], et=ev[2];
            if(es<stage||(es==stage&&er<=round)) continue;
            if(count>0) sb.append("  ·  ");
            sb.append(es).append("-").append(er).append(et==0?" ★":" ◉");
            count++;
            if(count>=5) break;
        }
        return sb.toString();
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

        // 10 component chips in two rows of 5 (Frying Pan joins Spatula)
        int[][] rows={{1,2,3,4,5},{6,7,8,9,10}};
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

        // ── MY COMPONENTS — multi-select: tap all components you're holding, see every craftable item ──
        addSecHdr(root, "MY COMPONENTS", ASH);
        int[][] hRows={{1,2,3,4,5},{6,7,8,9,10}};
        for(int[] hRow : hRows){
            LinearLayout hr=new LinearLayout(this); hr.setPadding(0,0,0,4);
            for(int i : hRow){
                final int ci=i; boolean held=itemsHeld[ci];
                TextView hchip=new TextView(this); hchip.setText(ItemData.COMPONENT_SHORT[ci]);
                hchip.setGravity(Gravity.CENTER); hchip.setTextSize(11);
                hchip.setTextColor(held?0xFF000000:BONE);
                hchip.setTypeface(null, held?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
                hchip.setBackground(box(held?GOLD:CARD,6,held?0xFFFFFFFF:EDGE,held?2:1));
                hchip.setPadding(4,10,4,10);
                LinearLayout.LayoutParams chl=new LinearLayout.LayoutParams(0,-2,1f); chl.setMargins(3,0,3,0); hchip.setLayoutParams(chl);
                hchip.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemsHeld[ci]=!itemsHeld[ci]; showPanel(); }});
                hr.addView(hchip);
            }
            root.addView(hr);
        }
        java.util.List<Integer> heldList=new java.util.ArrayList<>();
        for(int i=1;i<=10;i++) if(itemsHeld[i]) heldList.add(i);
        if(!heldList.isEmpty()){
            TextView hClr=new TextView(this); hClr.setText("clear all");
            hClr.setTextColor(DIM); hClr.setTextSize(10); hClr.setPadding(2,4,2,0);
            hClr.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ java.util.Arrays.fill(itemsHeld,false); showPanel(); }});
            root.addView(hClr);
            int craftCt=0; for(int ai:heldList) for(int bi:heldList){ if(bi<ai) continue; String cr=ItemData.COMBOS[ai][bi]; if(cr!=null&&!cr.isEmpty()) craftCt++; }
            addSecHdr(root, "CRAFTABLE  ("+craftCt+")", GOLD);
            boolean anyCraft=false;
            for(int ai : heldList){
                for(int bi : heldList){
                    if(bi<ai) continue; // each pair once (allow same component: BF+BF=IE)
                    String res=ItemData.COMBOS[ai][bi];
                    if(res==null||res.isEmpty()) continue;
                    anyCraft=true;
                    LinearLayout rc=new LinearLayout(this); rc.setOrientation(LinearLayout.HORIZONTAL); rc.setGravity(Gravity.CENTER_VERTICAL);
                    rc.setBackground(box(CARD,6,EDGE,1)); rc.setPadding(12,8,12,8);
                    LinearLayout.LayoutParams rcl=new LinearLayout.LayoutParams(-1,-2); rcl.setMargins(0,0,0,4); rc.setLayoutParams(rcl);
                    TextView parts=new TextView(this); parts.setText(ItemData.COMPONENT_SHORT[ai]+" + "+ItemData.COMPONENT_SHORT[bi]);
                    parts.setTextColor(ASH); parts.setTextSize(10);
                    TextView arr=new TextView(this); arr.setText("  →  "); arr.setTextColor(DIM); arr.setTextSize(12);
                    TextView nm=new TextView(this); nm.setText(res);
                    nm.setTextColor(GOLD); nm.setTextSize(13); nm.setTypeface(null,android.graphics.Typeface.BOLD);
                    nm.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                    rc.addView(parts); rc.addView(arr); rc.addView(nm); root.addView(rc);
                }
            }
            if(!anyCraft){
                TextView noItems=new TextView(this); noItems.setText("no items from these components");
                noItems.setTextColor(DIM); noItems.setTextSize(11); noItems.setPadding(2,4,2,0); root.addView(noItems);
            }
        }

        // traits section
        TextView tdiv=new TextView(this);
        tdiv.setText("❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦");
        tdiv.setTextColor(EDGE); tdiv.setTextSize(9); tdiv.setGravity(Gravity.CENTER); tdiv.setPadding(0,14,0,4);
        root.addView(tdiv);

        addSecHdr(root, "TRAITS  ("+TraitData.TRAITS.length+")", GOLD);

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
        buildGodTracker(root);
        // sub-tab row
        LinearLayout gtRow=new LinearLayout(this); gtRow.setPadding(0,0,0,10);
        String[] gtNames={"COACH","POSITION","OPENER","AUGMENTS","ITEMS"};
        for(int i=0;i<gtNames.length;i++){
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
        if(guideTab==0) buildCoach(root);
        else if(guideTab==1) buildPosition(root);
        else if(guideTab==2) buildOpener(root);
        else if(guideTab==3) buildAugments(root);
        else buildItems(root);
    }

    // OPENER sub-tab: evergreen early-game tempo/econ arc + item-slam priority.
    // Static reference (OpenerData) — no scan needed, holds across patches.
    private void buildOpener(LinearLayout root){
        addSecHdr(root, "EARLY GAME", GOLD);
        int curStg=pool.getStageNum();
        int curPhaseIdx=curStg>=1?Math.min(curStg-1,OpenerData.PHASES.length-1):-1;
        for(int pi=0;pi<OpenerData.PHASES.length;pi++){
            String[] ph=OpenerData.PHASES[pi];
            boolean here=(pi==curPhaseIdx);
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(box(here?0xFF1A1400:CARD,8,here?GOLD:EDGE,here?2:1)); card.setPadding(14,10,14,10);
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,6); card.setLayoutParams(clp);
            TextView h=new TextView(this); h.setText(ph[0]+(here?"   ◀ now":""));
            h.setTextColor(GOLD); h.setTextSize(12); h.setTypeface(null,android.graphics.Typeface.BOLD); card.addView(h);
            TextView b=new TextView(this); b.setText(ph[1]);
            b.setTextColor(BONE); b.setTextSize(11); b.setLineSpacing(4,1f); b.setPadding(0,3,0,0); card.addView(b);
            root.addView(card);
        }

        addSecHdr(root, "ITEM SLAM PRIORITY", GOLD);
        // pinned-carry slam hint: surface the carry's actual item plan from the build data
        String slamPin=pool.getPinned();
        if(!slamPin.isEmpty()){
            ChampItemData.Build sb=ChampItemData.get(slamPin);
            if(sb!=null && sb.items!=null && sb.items.length>0){
                TextView slamHint=new TextView(this);
                slamHint.setText("★ "+slamPin+" wants:  "+android.text.TextUtils.join("  ·  ", sb.items));
                slamHint.setTextColor(VOID); slamHint.setTextSize(11); slamHint.setTypeface(null,android.graphics.Typeface.BOLD);
                slamHint.setGravity(Gravity.CENTER); slamHint.setBackground(box(GOLD,6,0xFFE8C030,2)); slamHint.setPadding(10,9,10,9);
                LinearLayout.LayoutParams shl=new LinearLayout.LayoutParams(-1,-2); shl.setMargins(0,0,0,8); slamHint.setLayoutParams(shl);
                root.addView(slamHint);
            }
        }
        for(String[] s:OpenerData.SLAMS){
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.setMargins(0,1,0,1); row.setLayoutParams(rlp);
            TextView tag=new TextView(this); tag.setText(s[0]); tag.setGravity(Gravity.CENTER);
            tag.setTextColor(GOLD); tag.setTextSize(11); tag.setTypeface(null,android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,0.32f); tl.setMargins(0,0,8,0); tag.setLayoutParams(tl);
            row.addView(tag);
            TextView d=new TextView(this); d.setText(s[1]);
            d.setTextColor(BONE); d.setTextSize(11); d.setLineSpacing(3,1f);
            d.setLayoutParams(new LinearLayout.LayoutParams(0,-2,0.68f)); row.addView(d);
            root.addView(row);
        }

        addSecHdr(root, "PRINCIPLES", GOLD);
        for(String pr:OpenerData.PRINCIPLES){
            TextView tv=new TextView(this); tv.setText("•  "+pr);
            tv.setTextColor(BONE); tv.setTextSize(12); tv.setPadding(2,3,2,3); root.addView(tv);
        }
    }

    // Champion names on your board right now, from the most recent scan (auto scan
    // preferred, else the manual board scan). Stars are stripped.
    private java.util.List<String> currentBoardNames(){
        java.util.List<String> names=new java.util.ArrayList<>();
        if(!autoScanResults.isEmpty()){
            for(String e:autoScanResults) names.add(scanEntryName(e));
        } else if(!boardScanResults.isEmpty()){
            names.addAll(boardScanResults);
        }
        return names;
    }

    // COACH sub-tab: recommend a line from the scanned board + econ state.
    private void buildCoach(LinearLayout root){
        java.util.List<String> board=currentBoardNames();
        CompAdvisor.Rec rec=CompAdvisor.recommend(board);

        if(!rec.hasBoard){
            addSecHdr(root, "COACH", GOLD);
            TextView t=new TextView(this);
            t.setText("Scan your board first — hold the sigil to Auto Scan (or use Board Scan in the POOL tab). Then come back here for a recommended line and your next move.");
            t.setTextColor(ASH); t.setTextSize(12); t.setPadding(2,2,2,8); root.addView(t);
            return;
        }

        // HP urgency: when low, stabilizing trumps all comp strategy
        int coachHp=pool.getHp();
        if(coachHp>0 && coachHp<40){
            TextView hpWarn=new TextView(this);
            boolean crit=coachHp<=20;
            hpWarn.setText(crit
                ? "⚠ CRITICAL — "+coachHp+" HP.  2-star a unit, add frontline, or slam a full item NOW.  Win this round or you may die."
                : "⚠ Low HP ("+coachHp+").  Prioritize stabilizing before leveling or rolling for upgrades.  Find a 2-star first.");
            hpWarn.setTextColor(crit?0xFF0A0800:VOID);
            hpWarn.setTextSize(12); hpWarn.setTypeface(null,android.graphics.Typeface.BOLD);
            hpWarn.setGravity(Gravity.CENTER); hpWarn.setBackground(box(crit?BLOODL:GOLD,8,crit?BLOODL:0xFFE8C030,2));
            hpWarn.setPadding(12,12,12,12);
            LinearLayout.LayoutParams hwl=new LinearLayout.LayoutParams(-1,-2); hwl.setMargins(0,0,0,10); hpWarn.setLayoutParams(hwl);
            root.addView(hpWarn);
        }

        // ---- recommended line ----
        addSecHdr(root, "RECOMMENDED LINE", GOLD);
        if(!rec.comp.isEmpty()){
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(box(CARD,8,GOLD,2)); card.setPadding(14,12,14,12);
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,8); card.setLayoutParams(clp);

            LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
            TextView cn=new TextView(this); cn.setText(rec.comp);
            cn.setTextColor(BONE); cn.setTextSize(16); cn.setTypeface(null,android.graphics.Typeface.BOLD);
            cn.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            top.addView(cn);
            if(!rec.tier.isEmpty()){
                TextView tb=new TextView(this); tb.setText(rec.tier);
                tb.setTextColor(VOID); tb.setTextSize(13); tb.setTypeface(null,android.graphics.Typeface.BOLD);
                tb.setGravity(Gravity.CENTER); tb.setBackground(box(tierColor(rec.tier),5,tierColor(rec.tier),2));
                tb.setPadding(16,3,16,3); top.addView(tb);
            }
            card.addView(top);

            TextView carry=new TextView(this); carry.setText("Carry: "+rec.carry);
            carry.setTextColor(GOLD); carry.setTextSize(13); carry.setPadding(0,6,0,0); card.addView(carry);
            int carryContest=pool.oppCount(rec.carry);
            if(carryContest>=2){
                TextView contWarn=new TextView(this);
                contWarn.setText("⚠ "+carryContest+" opponents on "+rec.carry+" — 3-star fast or consider pivoting.");
                contWarn.setTextColor(BLOODL); contWarn.setTextSize(11); contWarn.setPadding(0,4,0,0); card.addView(contWarn);
            }

            int carryCost=Pool.costOf(rec.carry);
            if(carryCost==2||carryCost==3){
                int rollLv=carryCost==2?6:7;
                TextView rlv=new TextView(this);
                rlv.setText("Roll at Lv "+rollLv+" ("+carryCost+"-cost slow roll)");
                rlv.setTextColor(ASH); rlv.setTextSize(11); rlv.setPadding(0,3,0,0); card.addView(rlv);
            }

            TextView items=new TextView(this);
            items.setText("Items: "+android.text.TextUtils.join("  ·  ", rec.items));
            items.setTextColor(BONE); items.setTextSize(12); items.setPadding(0,3,0,0); card.addView(items);

            if(!rec.note.isEmpty()){
                TextView note=new TextView(this); note.setText(rec.note);
                note.setTextColor(ASH); note.setTextSize(11); note.setPadding(0,5,0,0); card.addView(note);
            }
            root.addView(card);

            if(!rec.alsoCarries.isEmpty()){
                TextView also=new TextView(this);
                also.setText("Other carries on your board: "+android.text.TextUtils.join(", ", rec.alsoCarries));
                also.setTextColor(DIM); also.setTextSize(11); also.setPadding(2,0,2,8); root.addView(also);
            }
            // LOOK FOR: meta carries for this comp not yet on your board
            java.util.List<String> lookFor=new java.util.ArrayList<>();
            java.util.Set<String> boardSet=new java.util.HashSet<>(board);
            for(int lci=1;lci<SetData.CHAMPS.length;lci++){
                for(String lname:SetData.CHAMPS[lci]){
                    ChampItemData.Build lb=ChampItemData.get(lname);
                    if(lb==null||!rec.comp.equals(lb.comp)) continue;
                    if(!boardSet.contains(lname)) lookFor.add(lname);
                }
            }
            if(!lookFor.isEmpty()){
                TextView lfHdr=new TextView(this); lfHdr.setText("look for:");
                lfHdr.setTextColor(ASH); lfHdr.setTextSize(10); lfHdr.setTypeface(null,android.graphics.Typeface.BOLD);
                lfHdr.setPadding(2,0,0,4); root.addView(lfHdr);
                LinearLayout lfRow=new LinearLayout(this); lfRow.setOrientation(LinearLayout.HORIZONTAL);
                lfRow.setPadding(0,0,0,8);
                for(String lname:lookFor){
                    int lco=Pool.costOf(lname);
                    TextView lchip=new TextView(this); lchip.setText(lname);
                    lchip.setTextColor(lco>0?COSTC[lco]:BONE); lchip.setTextSize(11); lchip.setGravity(Gravity.CENTER);
                    lchip.setBackground(box(CARD,6,lco>0?COSTC[lco]:EDGE,1)); lchip.setPadding(10,6,10,6);
                    LinearLayout.LayoutParams lcl=new LinearLayout.LayoutParams(-2,-2); lcl.setMargins(0,0,6,0); lchip.setLayoutParams(lcl);
                    lfRow.addView(lchip);
                }
                root.addView(lfRow);
            }
        } else {
            TextView t=new TextView(this);
            t.setText("No marked meta carry on your board yet. Itemize your strongest, least-contested unit and look for a carry to commit to.");
            t.setTextColor(ASH); t.setTextSize(12); t.setPadding(2,2,2,8); root.addView(t);
        }

        // unique board names, computed once and reused by BOARD ITEMS + YOUR UNITS
        java.util.LinkedHashSet<String> uniqueBoard=new java.util.LinkedHashSet<>(board);

        // ---- board items reference: all scanned board champs with known item builds ----
        java.util.LinkedHashMap<String,ChampItemData.Build> boardBuilds=new java.util.LinkedHashMap<>();
        for(String bn : uniqueBoard){
            ChampItemData.Build bb=ChampItemData.get(bn);
            if(bb!=null) boardBuilds.put(bn, bb);
        }
        if(!boardBuilds.isEmpty()){
            addSecHdr(root, "BOARD ITEMS", GOLD);
            for(java.util.Map.Entry<String,ChampItemData.Build> be : boardBuilds.entrySet()){
                String bn=be.getKey(); ChampItemData.Build bb=be.getValue();
                int bco=Pool.costOf(bn);
                LinearLayout biRow=new LinearLayout(this); biRow.setOrientation(LinearLayout.HORIZONTAL); biRow.setGravity(Gravity.CENTER_VERTICAL);
                biRow.setBackground(box(CARD,6,EDGE,1)); biRow.setPadding(10,7,10,7);
                LinearLayout.LayoutParams bil=new LinearLayout.LayoutParams(-1,-2); bil.setMargins(0,0,0,4); biRow.setLayoutParams(bil);
                TextView bnTv=new TextView(this); bnTv.setText(bn);
                bnTv.setTextColor(bco>0?COSTC[bco]:BONE); bnTv.setTextSize(12); bnTv.setTypeface(null,android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams bnl=new LinearLayout.LayoutParams(-2,-2); bnl.setMargins(0,0,8,0); bnTv.setLayoutParams(bnl);
                TextView biTv=new TextView(this); biTv.setText(android.text.TextUtils.join("  ·  ", bb.items));
                biTv.setTextColor(ASH); biTv.setTextSize(11); biTv.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                biRow.addView(bnTv); biRow.addView(biTv); root.addView(biRow);
            }
        }

        // ---- your units (ranked + contested) ----
        addSecHdr(root, "YOUR UNITS", GOLD);
        int perRow=0; LinearLayout row=null;
        for(String name:uniqueBoard){
            if(perRow%3==0){ row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); root.addView(row); }
            String t=ChampItemData.tierOf(name);
            int contest=pool.oppCount(name);
            TextView chip=new TextView(this);
            String label=name+(t.isEmpty()?"":("  "+t))+(contest>0?("  ◉"+contest):"");
            chip.setText(label);
            chip.setTextColor(t.isEmpty()?ASH:tierColor(t)); chip.setTextSize(11); chip.setGravity(Gravity.CENTER);
            chip.setBackground(box(CARD,6,t.isEmpty()?EDGE:tierColor(t),t.isEmpty()?1:2)); chip.setPadding(0,8,0,8);
            LinearLayout.LayoutParams chl=new LinearLayout.LayoutParams(0,-2,1f); chl.setMargins(2,2,2,2); chip.setLayoutParams(chl);
            row.addView(chip); perRow++;
        }
        // pad the last row so chips keep even widths
        while(perRow%3!=0){ TextView sp=new TextView(this); LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(0,-2,1f); spl.setMargins(2,2,2,2); sp.setLayoutParams(spl); row.addView(sp); perRow++; }

        TextView legend=new TextView(this);
        legend.setText("◉ = players contesting this unit (from your scans)");
        legend.setTextColor(DIM); legend.setTextSize(10); legend.setPadding(2,4,2,8); root.addView(legend);

        // ---- next move (econ/tempo) ----
        addSecHdr(root, "NEXT MOVE", GOLD);
        TextView econ=new TextView(this);
        econ.setText(CompAdvisor.econCall(pool.getLevel(), pool.getGold(), pool.getStageRound()));
        econ.setTextColor(BONE); econ.setTextSize(12); econ.setPadding(2,2,2,6); root.addView(econ);

        String lvCurve = CompAdvisor.levelCurve(pool.getLevel(), pool.getStageRound());
        if(!lvCurve.isEmpty()){
            TextView lvc = new TextView(this);
            lvc.setText(lvCurve);
            int lvExp = CompAdvisor.expectedLevel(pool.getStageRound());
            int lvDiff = pool.getLevel() - lvExp;
            lvc.setTextColor(lvDiff >= 1 ? GREEN : lvDiff == 0 ? GOLD : BLOODL);
            lvc.setTextSize(12); lvc.setPadding(2,0,2,6); root.addView(lvc);
        }

        // roll-or-hold: real Monte-Carlo odds of hitting the recommended carry
        // rolling current gold at current level (same sim the ODDS tab uses), so
        // the coach answers "should I roll?" with a number instead of a platitude.
        if(!rec.carry.isEmpty()){
            int co=Pool.costOf(rec.carry);
            if(co>=1 && co<=5){
                int rem=pool.remaining(rec.carry);
                int tier=0; for(String n:SetData.CHAMPS[co]) tier+=pool.remaining(n);
                tier=Math.max(0, tier-pool.getJunk(co));
                int lvl=pool.getLevel(); int rollGold=Math.min(60, pool.getGold());
                if(rem>0 && tier>0 && rollGold>=2 && lvl>=1 && lvl<=10){
                    double pHit=RollMath.hitChances(lvl, co, Math.min(rem,tier), tier, rollGold, 1)[0];
                    int pct=(int)Math.round(pHit*100);
                    String verdict = pHit>=0.7 ? "ROLL — strong odds to hit."
                                   : pHit>=0.4 ? "Roll only if you need the board now; otherwise bank."
                                   :             "HOLD — bank or level up, the odds are thin.";
                    TextView roll=new TextView(this);
                    roll.setText("Roll check: "+pct+"% to hit "+rec.carry+" with "+rollGold+"g at lv"+lvl+".  "+verdict);
                    roll.setTextColor(pHit>=0.7?GREEN:pHit>=0.4?GOLD:ASH); roll.setTextSize(12);
                    roll.setPadding(2,0,2,6); root.addView(roll);
                }
            }
        }

        // ---- tech vs the scouted lobby (OppScout) — defensive itemization ----
        OppScout.Profile opp=OppScout.analyzeUnits(pool.getAllOppUnits());
        if(!opp.techTips.isEmpty()){
            addSecHdr(root, "TECH vs LOBBY", BLOODL);
            for(String tip:opp.techTips){
                TextView tv=new TextView(this); tv.setText("•  "+tip);
                tv.setTextColor(BONE); tv.setTextSize(12); tv.setPadding(2,3,2,4); root.addView(tv);
            }
        }

        TextView ctx=new TextView(this);
        String stage=pool.getStageRound();
        ctx.setText("lv "+pool.getLevel()+"  ·  "+pool.getGold()+"g"+(stage.isEmpty()?"":("  ·  "+stage))
                +"  ·  builds: patch "+ChampItemData.PATCH);
        ctx.setTextColor(DIM); ctx.setTextSize(10); ctx.setPadding(2,0,2,4); root.addView(ctx);
    }

    // POSITION sub-tab: where to stand. Sorts the scanned board front/back/flank
    // and lists the evergreen fundamentals (PositionAdvisor) — no opponent read,
    // no per-patch upkeep, just the reliable half of positioning.
    private void buildPosition(LinearLayout root){
        java.util.List<String> board=currentBoardNames();
        PositionAdvisor.Plan p=PositionAdvisor.plan(board, pool.getStageRound());

        if(!p.hasBoard){
            addSecHdr(root, "POSITION", GOLD);
            TextView t=new TextView(this);
            t.setText("Scan your board first — hold the sigil to Auto Scan (or use Board Scan in the POOL tab). Then come back here for a front/back placement map and the positioning checklist.");
            t.setTextColor(ASH); t.setTextSize(12); t.setPadding(2,2,2,8); root.addView(t);
            return;
        }

        // ---- carry corner callout ----
        if(!p.carry.isEmpty()){
            addSecHdr(root, "PROTECT YOUR CARRY", GOLD);
            LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(box(CARD,8,GOLD,2)); card.setPadding(14,12,14,12);
            LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,0,0,8); card.setLayoutParams(clp);
            TextView cn=new TextView(this); cn.setText(p.carry+"  →  "+p.carryCorner);
            cn.setTextColor(BONE); cn.setTextSize(15); cn.setTypeface(null,android.graphics.Typeface.BOLD); card.addView(cn);
            TextView sub=new TextView(this); sub.setText("Back row, hugging the wall. Switch corners next round so it can't be pre-aimed.");
            sub.setTextColor(ASH); sub.setTextSize(11); sub.setPadding(0,5,0,0); card.addView(sub);
            root.addView(card);
        }

        // ---- front / back / flank lists ----
        addSecHdr(root, "PLACEMENT MAP", GOLD);
        addPlaceRow(root, "BACK",  p.backline,  GOLD,  "hypercarries + casters, far from melee");
        addPlaceRow(root, "FRONT", p.frontline, BLOODL,"tanks + bruisers, soak the damage");
        if(!p.flankers.isEmpty())
            addPlaceRow(root, "FLANK", p.flankers, GREEN, "divers — side/front corner toward their backline");

        // ---- counter the scouted lobby (OppScout) ----
        // Reads the enemy boards already remembered from manual scries (POOL tab);
        // when none exist this section is simply absent and POSITION behaves as before.
        OppScout.Profile opp=OppScout.analyzeUnits(pool.getAllOppUnits());
        if(opp.hasData()){
            addSecHdr(root, "COUNTER THE LOBBY", BLOODL);
            TextView mix=new TextView(this);
            StringBuilder mx=new StringBuilder();
            mx.append(opp.boards).append(opp.boards==1?" enemy scouted":" enemies scouted");
            mx.append("  ·  ").append(opp.flank).append(opp.flank==1?" diver":" divers");
            mx.append("  ·  ").append(opp.back).append(" backline  ·  ").append(opp.front).append(" frontline");
            if(opp.hooks>0) mx.append("  ·  ").append(opp.hooks).append(" hook");
            if(opp.aoe>0)   mx.append("  ·  ").append(opp.aoe).append(" AoE");
            mix.setText(mx.toString());
            mix.setTextColor(ASH); mix.setTextSize(11); mix.setPadding(2,0,2,6); root.addView(mix);
            for(String tip:opp.tips){
                TextView tv=new TextView(this); tv.setText("•  "+tip);
                tv.setTextColor(BONE); tv.setTextSize(12); tv.setPadding(2,3,2,3); root.addView(tv);
            }
        }

        // ---- fundamentals checklist ----
        addSecHdr(root, "FUNDAMENTALS", GOLD);
        for(String tip:p.tips){
            TextView tv=new TextView(this); tv.setText("•  "+tip);
            tv.setTextColor(BONE); tv.setTextSize(12); tv.setPadding(2,3,2,3); root.addView(tv);
        }

        TextView foot=new TextView(this);
        foot.setText(opp.hasData()
            ? "Positioning is meta-stable — these rules hold across patches. Counter advice above is built from the enemy boards you've scried."
            : "Positioning is meta-stable — these rules hold across patches. Scry enemy boards (POOL tab) to unlock counter-positioning here.");
        foot.setTextColor(DIM); foot.setTextSize(10); foot.setPadding(2,8,2,4); root.addView(foot);
    }

    // One labelled placement row: a colored zone tag + the units that go there.
    private void addPlaceRow(LinearLayout root, String label, java.util.List<String> units, int color, String hint){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2); rlp.setMargins(0,2,0,2); row.setLayoutParams(rlp);
        TextView tag=new TextView(this); tag.setText(label); tag.setGravity(Gravity.CENTER);
        tag.setTextColor(VOID); tag.setTextSize(11); tag.setTypeface(null,android.graphics.Typeface.BOLD);
        tag.setBackground(box(color,5,color,2)); tag.setPadding(0,6,0,6);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,0.28f); tl.setMargins(0,0,8,0); tag.setLayoutParams(tl);
        row.addView(tag);
        TextView names=new TextView(this);
        names.setText(units.isEmpty()?"—":android.text.TextUtils.join(", ", units));
        names.setTextColor(units.isEmpty()?DIM:BONE); names.setTextSize(12);
        names.setLayoutParams(new LinearLayout.LayoutParams(0,-2,0.72f)); row.addView(names);
        root.addView(row);
        TextView h=new TextView(this); h.setText(hint);
        h.setTextColor(ASH); h.setTextSize(10); h.setPadding(2,0,2,4); root.addView(h);
    }

    // ⛧ REALM OF GODS tracker (Set 17 mechanic). Two gods appear per game in the
    // Realm (replaces carousels at 2-4 / 3-4 / 4-4); favoring one with 2+ offering
    // picks earns their Boon armory at 4-7. Track your two gods and your picks.
    private void buildGodTracker(LinearLayout root){
        addSecHdr(root, "⛧ REALM OF GODS", GOLD);
        for(int slot=1;slot<=2;slot++){
            final int fs=slot;
            String god=pool.getGod(slot);
            if(godPickSlot==slot){
                // picker open for this slot: 9 god chips in 3 rows
                TextView pk=new TextView(this); pk.setText("choose god "+(slot==1?"I":"II")+":");
                pk.setTextColor(ASH); pk.setTextSize(10); pk.setPadding(2,2,2,2); root.addView(pk);
                LinearLayout grow=null;
                for(int g=0;g<SetData.GODS.length;g++){
                    if(g%3==0){ grow=new LinearLayout(this); root.addView(grow); }
                    final String gn=SetData.GODS[g];
                    TextView gc=new TextView(this); gc.setText(gn);
                    gc.setTextColor(BONE); gc.setTextSize(11); gc.setGravity(Gravity.CENTER);
                    gc.setBackground(box(CARD,6,EDGE,1)); gc.setPadding(0,10,0,10);
                    LinearLayout.LayoutParams gcl=new LinearLayout.LayoutParams(0,-2,1f); gcl.setMargins(2,2,2,2); gc.setLayoutParams(gcl);
                    pressFeedback(gc);
                    gc.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        pool.setGod(fs, gn); godPickSlot=0; buzz(); showPanel();
                    }});
                    grow.addView(gc);
                }
            } else {
                LinearLayout gr=new LinearLayout(this); gr.setOrientation(LinearLayout.HORIZONTAL);
                gr.setGravity(Gravity.CENTER_VERTICAL);
                gr.setBackground(box(CARD,6,god.isEmpty()?EDGE:GOLD,god.isEmpty()?1:2)); gr.setPadding(10,8,10,8);
                LinearLayout.LayoutParams grl=new LinearLayout.LayoutParams(-1,-2); grl.setMargins(0,0,0,4); gr.setLayoutParams(grl);
                TextView gl=new TextView(this);
                gl.setText(god.isEmpty()?("god "+(slot==1?"I":"II")+": tap to set"):god);
                gl.setTextColor(god.isEmpty()?ASH:BONE); gl.setTextSize(12);
                gl.setTypeface(null, god.isEmpty()?android.graphics.Typeface.NORMAL:android.graphics.Typeface.BOLD);
                gl.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
                gl.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    godPickSlot=fs; showPanel();
                }});
                gr.addView(gl);
                if(!god.isEmpty()){
                    int picks=pool.getGodPicks(slot);
                    TextView pc=new TextView(this);
                    StringBuilder ps=new StringBuilder("picks ");
                    for(int i=0;i<3;i++) ps.append(i<picks?"⛧":"·");
                    pc.setText(ps.toString());
                    pc.setTextColor(picks>=2?GOLD:ASH); pc.setTextSize(12);
                    pc.setPadding(8,4,8,4);
                    pc.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        pool.setGodPicks(fs, pool.getGodPicks(fs)+1); buzz(); showPanel();
                    }});
                    pc.setOnLongClickListener(new View.OnLongClickListener(){ public boolean onLongClick(View v){
                        pool.setGodPicks(fs, pool.getGodPicks(fs)-1); buzz(); showPanel(); return true;
                    }});
                    gr.addView(pc);
                    TextView gx=new TextView(this); gx.setText("✕");
                    gx.setTextColor(ASH); gx.setTextSize(12); gx.setPadding(10,4,4,4);
                    gx.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                        pool.setGod(fs,""); pool.setGodPicks(fs,0); showPanel();
                    }});
                    gr.addView(gx);
                }
                root.addView(gr);
            }
        }
        int p1=pool.getGodPicks(1), p2=pool.getGodPicks(2);
        TextView boon=new TextView(this);
        if(p1>=2 || p2>=2){
            String favored=p1>=p2?pool.getGod(1):pool.getGod(2);
            boon.setText("✦ "+favored+" is favored — Boon armory comes at 4-7");
            boon.setTextColor(GOLD);
        } else {
            boon.setText("offerings at 2-4 / 3-4 / 4-4 · favor one god 2+ times for their Boon at 4-7");
            boon.setTextColor(DIM);
        }
        boon.setTextSize(9); boon.setPadding(2,2,2,10);
        root.addView(boon);
    }

    // Used by scan buttons when accessibility is unavailable: tell the user the
    // exact problem — not enabled at all, or enabled in settings but not running.
    // Jump straight to TFT Scryer's own accessibility page when the OS supports
    // it (Android 12+), so a stuck toggle is one OFF/ON away instead of hidden
    // in the full service list. Falls back to the list if the deep link fails.
    private void openAccessibilitySettings(){
        if(Build.VERSION.SDK_INT >= 31){
            try{
                Intent i=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS");
                android.content.ComponentName cn=new android.content.ComponentName(
                        this, TFTAccessibilityService.class);
                android.os.Bundle args=new android.os.Bundle();
                args.putString(":settings:fragment_args_key", cn.flattenToString());
                i.putExtra(":settings:fragment_args_key", cn.flattenToString());
                i.putExtra(":settings:show_fragment_args", args);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            }catch(Exception ignored){}
        }
        try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){}
    }

    private String accErrorMsg(){
        return TFTAccessibilityService.enabledInSettings(this)
            ? "Accessibility is stuck: toggle TFT Scryer OFF then ON in Accessibility settings"
            : "Enable Accessibility service first";
    }

    private void buildSettings(LinearLayout root){
        boolean accEnabled = Build.VERSION.SDK_INT >= 31 && TFTAccessibilityService.instance != null;
        // ON in Android settings but service not bound = stuck (common after updates)
        boolean accStuck = !accEnabled && Build.VERSION.SDK_INT >= 31
                && TFTAccessibilityService.enabledInSettings(this);

        // ◇ PERMISSIONS
        addSecHdr(root, "PERMISSIONS", GOLD);

        LinearLayout accCard=new LinearLayout(this); accCard.setOrientation(LinearLayout.VERTICAL);
        accCard.setBackground(box(CARD,6,accEnabled?GREEN:EDGE,accEnabled?2:1)); accCard.setPadding(12,10,12,10);
        LinearLayout.LayoutParams acardl=new LinearLayout.LayoutParams(-1,-2); acardl.setMargins(0,0,0,8); accCard.setLayoutParams(acardl);
        TextView accLabel=new TextView(this); accLabel.setText("Accessibility (silent scan)");
        accLabel.setTextColor(ASH); accLabel.setTextSize(10); accLabel.setLetterSpacing(0.05f); accCard.addView(accLabel);
        TextView accStatus=new TextView(this);
        accStatus.setText(accEnabled ? "Enabled — scan works silently, no app switch"
                : accStuck ? "Stuck — switch shows ON but the service is not running"
                : "Disabled — scan buttons will not work");
        accStatus.setTextColor(accEnabled?GREEN:BLOODL);
        accStatus.setTextSize(13); accStatus.setTypeface(null,android.graphics.Typeface.BOLD); accCard.addView(accStatus);
        if(!accEnabled){
            String steps;
            if(accStuck){
                steps = "Android did not restart the service (happens after updates).\n"
                      + "Accessibility → TFT Scryer → toggle OFF, then ON again";
            } else {
                steps = Build.VERSION.SDK_INT >= 33
                    ? "1. App settings below → Allow restricted settings\n2. Accessibility → TFT Scryer → On"
                    : "Accessibility → TFT Scryer → On";
            }
            TextView accInstr=new TextView(this); accInstr.setText(steps);
            accInstr.setTextColor(ASH); accInstr.setTextSize(11); accInstr.setPadding(0,4,0,0); accCard.addView(accInstr);
        }
        root.addView(accCard);

        if(!accEnabled){
            LinearLayout accBtnRow=new LinearLayout(this); accBtnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams abrp=new LinearLayout.LayoutParams(-1,-2); abrp.setMargins(0,0,0,12); accBtnRow.setLayoutParams(abrp);
            // restricted-settings step only applies to a first-time enable, not a stuck toggle
            if(Build.VERSION.SDK_INT >= 33 && !accStuck){
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
                openAccessibilitySettings();
            }});
            accBtnRow.addView(accBtn);
            root.addView(accBtnRow);
        }

        // ◇ SCAN
        addSecHdr(root, "SCAN", GOLD);

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
        TextView copyLogBtn=new TextView(this); copyLogBtn.setText("⎘ copy");
        copyLogBtn.setTextColor(ASH); copyLogBtn.setTextSize(10); copyLogBtn.setGravity(Gravity.CENTER);
        copyLogBtn.setBackground(box(CARD,5,EDGE,1)); copyLogBtn.setPadding(18,7,18,7);
        LinearLayout.LayoutParams cplp=new LinearLayout.LayoutParams(-2,-2); cplp.setMargins(0,0,6,0); copyLogBtn.setLayoutParams(cplp);
        pressFeedback(copyLogBtn);
        copyLogBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            StringBuilder sb=new StringBuilder();
            synchronized(scanLog){ for(String l:scanLog) sb.append(l).append("\n"); }
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("tft-scan-log",sb.toString()));
            Toast.makeText(OverlayService.this,"Log copied to clipboard",Toast.LENGTH_SHORT).show();
        }});
        logHdrRow.addView(copyLogBtn);
        TextView clearLogBtn=new TextView(this); clearLogBtn.setText("✕ clear");
        clearLogBtn.setTextColor(ASH); clearLogBtn.setTextSize(10); clearLogBtn.setGravity(Gravity.CENTER);
        clearLogBtn.setBackground(box(CARD,5,EDGE,1)); clearLogBtn.setPadding(18,7,18,7);
        pressFeedback(clearLogBtn);
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
            if(!canDbgScan){ Toast.makeText(OverlayService.this,accErrorMsg(),Toast.LENGTH_LONG).show(); return; }
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
        addSecHdr(root, "TEMPLATES", GOLD);
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

        addSecHdr(root, "TRANSPARENCY", GOLD);

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
                scheduleDim(); // restart the countdown so next idle uses the new alpha
                if(panel!=null) panel.setAlpha(av);
                if(hudGoldView!=null) hudGoldView.setAlpha(av);
                if(hudXpView!=null) hudXpView.setAlpha(av);
                alphaLabel.setText((progress+20)+"%");
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar){}
            public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
        root.addView(alphaBar);

        addSecHdr(root, "HAPTIC", GOLD);
        pickRow(root, new String[]{"ON","OFF"}, new int[]{1,0}, pool.getHaptic()?1:0, 0,
            new PickSetter(){ public void pick(int v){ pool.setHaptic(v==1); showPanel(); }});

        addSecHdr(root, "IN-GAME HUD", GOLD);

        TextView hudHint=new TextView(this);
        hudHint.setText("Drag the GOLD pill (tracked gold + projected income) above your gold counter; drag the XP pill above the level button. With AUTO GOLD & XP on, accuracy depends on the GOLD pill position — place it directly above your counter.");
        hudHint.setTextColor(DIM); hudHint.setTextSize(10); hudHint.setPadding(2,0,0,8); root.addView(hudHint);

        pickRow(root, new String[]{"ON","OFF"}, new int[]{1,0}, pool.getHudEnabled()?1:0, 14,
            new PickSetter(){ public void pick(int v){ pool.setHudEnabled(v==1); if(v==1) addHud(); else removeHud(); showPanel(); }});

        addSecHdr(root, "AUTO GOLD & XP", GOLD);

        TextView gwHint=new TextView(this);
        gwHint.setText("Reads gold and XP in the background every few seconds. Position the GOLD pill right above your counter for an accurate read. Pauses during scans and hunts. Needs the accessibility service.");
        gwHint.setTextColor(DIM); gwHint.setTextSize(10); gwHint.setPadding(2,0,0,8); root.addView(gwHint);

        boolean curGw=pool.getGoldWatch();
        LinearLayout gwRow=new LinearLayout(this); gwRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams gwRowLp=new LinearLayout.LayoutParams(-1,-2); gwRowLp.setMargins(0,0,0,14); gwRow.setLayoutParams(gwRowLp);
        String[] gwLabels={"ON","OFF"}; boolean[] gwVals={true,false};
        for(int i=0;i<2;i++){
            final boolean gv=gwVals[i];
            TextView btn=new TextView(this); btn.setText(gwLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(curGw==gv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(gv && (Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null)){
                    Toast.makeText(OverlayService.this,accErrorMsg(),Toast.LENGTH_LONG).show();
                    return;
                }
                pool.setGoldWatch(gv);
                if(gv) startGoldWatch(); else stopGoldWatch();
                showPanel();
            }});
            gwRow.addView(btn);
        }
        root.addView(gwRow);

        addSecHdr(root, "SHOP POSITION (hunt)", GOLD);

        TextView spHint=new TextView(this);
        spHint.setText("Where THE HUNT looks for the shop. Auto reads the top in landscape and the bottom in portrait — switch it if auto-buy isn't seeing your shop.");
        spHint.setTextColor(DIM); spHint.setTextSize(10); spHint.setPadding(2,0,0,8); root.addView(spHint);

        pickRow(root, new String[]{"Auto","Top","Bottom"}, new int[]{0,1,2}, pool.getShopPos(), 14,
            new PickSetter(){ public void pick(int v){ pool.setShopPos(v); showPanel(); }});

        addSecHdr(root, "OPEN TAB", GOLD);

        pickRow(root, new String[]{"smart","always pool","last tab"}, new int[]{0,1,2}, pool.getStartTab(), 0,
            new PickSetter(){ public void pick(int v){ pool.setStartTab(v); showPanel(); }});

        // ---- AUTOMATION ----
        addSecHdr(root, "AUTOMATION", GOLD);
        boolean aso=pool.getAutoScanOnOpen();
        TextView asoBtn=new TextView(this);
        asoBtn.setText((aso?"✓ ":"")+"auto-scan on open");
        asoBtn.setTextColor(BONE); asoBtn.setTextSize(12); asoBtn.setGravity(Gravity.CENTER); asoBtn.setPadding(0,11,0,11);
        asoBtn.setBackground(box(aso?BLOOD:CARD,6,aso?BLOODL:EDGE,aso?2:1));
        LinearLayout.LayoutParams asol=new LinearLayout.LayoutParams(-1,-2); asol.setMargins(0,0,0,4); asoBtn.setLayoutParams(asol);
        asoBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setAutoScanOnOpen(!pool.getAutoScanOnOpen()); showPanel(); }});
        pressFeedback(asoBtn); root.addView(asoBtn);
        TextView asoHint=new TextView(this); asoHint.setText("a quick tap on the sigil reads gold & level straight away — no SCRY tap (needs the accessibility screenshot)");
        asoHint.setTextColor(DIM); asoHint.setTextSize(10); asoHint.setPadding(2,0,2,8); root.addView(asoHint);

        boolean sl=pool.getSmartLanding();
        TextView slBtn=new TextView(this);
        slBtn.setText((sl?"✓ ":"")+"open results after a scan");
        slBtn.setTextColor(BONE); slBtn.setTextSize(12); slBtn.setGravity(Gravity.CENTER); slBtn.setPadding(0,11,0,11);
        slBtn.setBackground(box(sl?BLOOD:CARD,6,sl?BLOODL:EDGE,sl?2:1));
        LinearLayout.LayoutParams sll=new LinearLayout.LayoutParams(-1,-2); sll.setMargins(0,0,0,4); slBtn.setLayoutParams(sll);
        slBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setSmartLanding(!pool.getSmartLanding()); showPanel(); }});
        pressFeedback(slBtn); root.addView(slBtn);
        TextView slHint=new TextView(this); slHint.setText("after a scan, jump to the GOLD tab to see what was read instead of staying on SETUP");
        slHint.setTextColor(DIM); slHint.setTextSize(10); slHint.setPadding(2,0,2,8); root.addView(slHint);

        // ---- AUTO-CLOSE PANEL ----
        addSecHdr(root, "AUTO-CLOSE PANEL", GOLD);
        TextView acHint=new TextView(this); acHint.setText("closes the panel automatically after this many seconds — matches the planning phase so you never leave it open mid-fight");
        acHint.setTextColor(DIM); acHint.setTextSize(10); acHint.setPadding(2,0,2,8); root.addView(acHint);
        pickRow(root, new String[]{"15s","30s","off"}, new int[]{15,30,0}, pool.getPanelTimeout(), 0,
            new PickSetter(){ public void pick(int v){ pool.setPanelTimeout(v); showPanel(); }});

        // ---- DISPLAY ----
        addSecHdr(root, "DISPLAY", GOLD);

        // compact tab row
        boolean compact=pool.getCompactTabs();
        LinearLayout ctRow=new LinearLayout(this); ctRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ctrl=new LinearLayout.LayoutParams(-1,-2); ctrl.setMargins(0,0,0,6); ctRow.setLayoutParams(ctrl);
        TextView ctOn=new TextView(this); ctOn.setText("compact tabs");
        ctOn.setTextColor(BONE); ctOn.setTextSize(12); ctOn.setGravity(Gravity.CENTER); ctOn.setPadding(0,10,0,10);
        ctOn.setBackground(box(compact?BLOOD:CARD,6,compact?BLOODL:EDGE,compact?2:1));
        ctOn.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        ctOn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setCompactTabs(!pool.getCompactTabs()); showPanel(); }});
        pressFeedback(ctOn); ctRow.addView(ctOn);
        TextView ltOn=new TextView(this); ltOn.setText("large text");
        boolean large=pool.getLargeText();
        ltOn.setTextColor(BONE); ltOn.setTextSize(12); ltOn.setGravity(Gravity.CENTER); ltOn.setPadding(0,10,0,10);
        ltOn.setBackground(box(large?BLOOD:CARD,6,large?BLOODL:EDGE,large?2:1));
        LinearLayout.LayoutParams ltlp=new LinearLayout.LayoutParams(0,-2,1f); ltlp.setMargins(6,0,0,0); ltOn.setLayoutParams(ltlp);
        ltOn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setLargeText(!pool.getLargeText()); showPanel(); }});
        pressFeedback(ltOn); ctRow.addView(ltOn);
        root.addView(ctRow);

        // panel width
        pickRow(root, new String[]{"slim","medium","full"}, new int[]{60,78,96}, pool.getPanelWidthPct(), 0,
            new PickSetter(){ public void pick(int v){ pool.setPanelWidthPct(v); showPanel(); }});
        TextView wHint=new TextView(this); wHint.setText("slim = less game obstruction  ·  full = default");
        wHint.setTextColor(DIM); wHint.setTextSize(10); wHint.setPadding(2,4,2,0); root.addView(wHint);

        // sigil size
        TextView sigLbl=new TextView(this); sigLbl.setText("SIGIL SIZE");
        sigLbl.setTextColor(ASH); sigLbl.setTextSize(10); sigLbl.setLetterSpacing(0.08f); sigLbl.setPadding(2,12,0,2);
        root.addView(sigLbl);
        pickRow(root, new String[]{"small","normal","large"}, new int[]{80,100,125}, pool.getSigilScalePct(), 0,
            new PickSetter(){ public void pick(int v){ pool.setSigilScalePct(v); rebuildButton(); showPanel(); }});

        // accent theme — recolors buttons / highlights / the sigil; each swatch
        // previews its own bright accent so the choice is visible before tapping
        TextView thLbl=new TextView(this); thLbl.setText("ACCENT");
        thLbl.setTextColor(ASH); thLbl.setTextSize(10); thLbl.setLetterSpacing(0.08f); thLbl.setPadding(2,12,0,2);
        root.addView(thLbl);
        int curTheme=pool.getAccentTheme();
        LinearLayout thRow=new LinearLayout(this); thRow.setGravity(Gravity.CENTER_VERTICAL);
        for(int i=0;i<THEMES.length;i++){
            final int tv=i; boolean sel=(curTheme==i);
            int bright=THEMES[i][1];
            TextView btn=new TextView(this); btn.setText(THEME_NAMES[i]);
            btn.setTextColor(sel?BONE:ASH); btn.setTextSize(11); btn.setGravity(Gravity.CENTER); btn.setPadding(0,10,0,10);
            // selected = filled with the theme's bright accent; otherwise the
            // accent is just the border so every option still shows its color
            btn.setBackground(box(sel?bright:CARD,6,bright,sel?2:2));
            LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(0,-2,1f); blp.setMargins(i>0?4:0,0,0,0); btn.setLayoutParams(blp);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.setAccentTheme(tv); repaintTheme();
            }});
            pressFeedback(btn); thRow.addView(btn);
        }
        root.addView(thRow);

        addSecHdr(root, "POSITION", GOLD);

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

        addSecHdr(root, "SMART SCAN", GOLD);

        TextView ssInfo=new TextView(this);
        ssInfo.setText("Auto Scan finds units by their health bar and taps the exact spot, so calibration barely matters. Turn off to tap the calibrated grid instead.");
        ssInfo.setTextColor(ASH); ssInfo.setTextSize(10); ssInfo.setPadding(2,0,0,6); root.addView(ssInfo);

        pickRow(root, new String[]{"ON","OFF"}, new int[]{1,0}, pool.getSmartScan()?1:0, 0,
            new PickSetter(){ public void pick(int v){ pool.setSmartScan(v==1); showPanel(); }});

        addSecHdr(root, "FAST SCAN", GOLD);

        TextView fsInfo=new TextView(this);
        fsInfo.setText(scanFastReady
            ? "Active — Board Scan and Opp Scan poll the live screen recording instantly, with no 1-second wait between checks."
            : "Keeps a screen-recording permission alive so Board Scan and Opp Scan poll instantly instead of waiting on the screenshot rate limit. One-time permission prompt; off by default.");
        fsInfo.setTextColor(scanFastReady?GREEN:ASH); fsInfo.setTextSize(10); fsInfo.setPadding(2,0,0,6); root.addView(fsInfo);

        LinearLayout fsRow=new LinearLayout(this); fsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fsRowLp=new LinearLayout.LayoutParams(-1,-2); fsRowLp.setMargins(0,0,0,14); fsRow.setLayoutParams(fsRowLp);
        String[] fsLabels={"ON","OFF"}; boolean[] fsVals={true,false};
        for(int i=0;i<2;i++){
            final boolean fv=fsVals[i];
            TextView btn=new TextView(this); btn.setText(fsLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(scanFastReady==fv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(fv && !scanFastReady){
                    closePanel();
                    startFastScanSetup();
                } else if(!fv && scanFastReady){
                    releaseScanCapture();
                    showPanel();
                }
            }});
            fsRow.addView(btn);
        }
        root.addView(fsRow);

        addSecHdr(root, "PLANNER SCAN", GOLD);

        SetIcons.load(this);
        ItemIcons.load(this); // Phase 2 scaffold: loads enemy-item icons if bundled (no-op until then)
        boolean plnCal=pool.plannerCalibrated();
        int plnIcons=SetIcons.champCount();
        TextView plnInfo=new TextView(this);
        plnInfo.setText("Reads your whole board in one pass with zero unit taps: the scan opens the "
                +"Team Planner, presses Snapshot, names every fielded unit from its flat tile, then "
                +"closes the planner without confirming (the game is untouched). Calibrate once so it "
                +"knows where the planner controls are.");
        plnInfo.setTextColor(ASH); plnInfo.setTextSize(10); plnInfo.setPadding(2,0,0,6); root.addView(plnInfo);

        TextView plnStatus=new TextView(this);
        plnStatus.setText((plnCal?"✓ calibrated":"not calibrated yet")
                +"  ·  "+plnIcons+" champion icons bundled"
                +(plnIcons==0?" — Planner Scan needs an app update with icons":""));
        plnStatus.setTextColor(plnCal&&plnIcons>0?GREEN:DIM); plnStatus.setTextSize(10);
        plnStatus.setPadding(2,0,0,6); root.addView(plnStatus);

        TextView plnCalBtn=new TextView(this);
        plnCalBtn.setText(plnCal?"RECALIBRATE PLANNER":"CALIBRATE PLANNER");
        plnCalBtn.setTextColor(BONE); plnCalBtn.setTextSize(13); plnCalBtn.setGravity(Gravity.CENTER);
        plnCalBtn.setPadding(0,12,0,12); plnCalBtn.setBackground(box(plnCal?CARD:BLOOD,6,plnCal?GOLD:BLOODL,2));
        LinearLayout.LayoutParams pcbl=new LinearLayout.LayoutParams(-1,-2); pcbl.setMargins(0,0,0,4); plnCalBtn.setLayoutParams(pcbl);
        plnCalBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
                Toast.makeText(OverlayService.this,accErrorMsg(),Toast.LENGTH_LONG).show(); return;
            }
            startPlannerCalibration();
        }});
        root.addView(plnCalBtn);

        TextView plnHint=new TextView(this);
        plnHint.setText("Do this on the board view during a planning phase. Your taps are replayed into "
                +"the game, so the planner really opens while you point things out. The instruction bar "
                +"is DRAGGABLE — if it covers the button you need to tap, drag it out of the way first. "
                +"Use the ✕ CANCEL button on the bar to stop.");
        plnHint.setTextColor(ASH); plnHint.setTextSize(10); plnHint.setPadding(2,0,0,6); root.addView(plnHint);

        if(plnCal){
            root.addView(miniChip("✕ clear planner calibration", new View.OnClickListener(){ public void onClick(View v){
                pool.clearPlannerCal(); showPanel();
            }}));
        }

        // ---- one-pass lobby scan calibration (REAPER) ----
        addSecHdr(root, "SCRY THE LOBBY", GOLD);
        boolean oppCal=pool.hasOppPortraitCal();
        int oppCalN=pool.oppPortraitCount();
        TextView oppInfo=new TextView(this);
        oppInfo.setText("Scan every opponent's board in one pass. Calibrate by tapping each enemy "
                +"portrait (the player health icons) once; the scan then taps through them in turn, "
                +"reading each board and filing it under OPP 1-7. Do this during a planning phase.");
        oppInfo.setTextColor(ASH); oppInfo.setTextSize(10); oppInfo.setPadding(2,0,0,6); root.addView(oppInfo);

        TextView oppStatus=new TextView(this);
        oppStatus.setText(oppCal?("✓ "+oppCalN+" portrait"+(oppCalN==1?"":"s")+" calibrated"):"not calibrated yet");
        oppStatus.setTextColor(oppCal?GREEN:DIM); oppStatus.setTextSize(10);
        oppStatus.setPadding(2,0,0,6); root.addView(oppStatus);

        TextView oppCalBtn=new TextView(this);
        oppCalBtn.setText(oppCal?"RECALIBRATE PORTRAITS":"CALIBRATE PORTRAITS");
        oppCalBtn.setTextColor(BONE); oppCalBtn.setTextSize(13); oppCalBtn.setGravity(Gravity.CENTER);
        oppCalBtn.setPadding(0,12,0,12); oppCalBtn.setBackground(box(oppCal?CARD:BLOOD,6,oppCal?GOLD:BLOODL,2));
        LinearLayout.LayoutParams ocbl=new LinearLayout.LayoutParams(-1,-2); ocbl.setMargins(0,0,0,4); oppCalBtn.setLayoutParams(ocbl);
        oppCalBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
                Toast.makeText(OverlayService.this,accErrorMsg(),Toast.LENGTH_LONG).show(); return;
            }
            startOppCalibration();
        }});
        root.addView(oppCalBtn);

        TextView oppHint=new TextView(this);
        oppHint.setText("Tap each enemy portrait in turn (up to 7), then ✓ DONE. The banner sits at the "
                +"bottom so it won't cover the portraits. Re-run SCRY THE LOBBY from the POOL tab.");
        oppHint.setTextColor(ASH); oppHint.setTextSize(10); oppHint.setPadding(2,0,0,6); root.addView(oppHint);

        if(oppCal){
            root.addView(miniChip("✕ clear portrait calibration", new View.OnClickListener(){ public void onClick(View v){
                pool.clearOppPortraits(); showPanel();
            }}));
        }

        addSecHdr(root, "INSTANT VISUAL ID", GOLD);

        TextView vidInfo=new TextView(this);
        int sprites=ChampionTemplates.boardTemplateCount();
        vidInfo.setText("Learns champions from popup reads; future scans skip the tap for known units. Uncertain matches still tap. Sprites learned: "+sprites+".");
        vidInfo.setTextColor(ASH); vidInfo.setTextSize(10); vidInfo.setPadding(2,0,0,6); root.addView(vidInfo);

        pickRow(root, new String[]{"ON","OFF"}, new int[]{1,0}, pool.getVisualId()?1:0, 0,
            new PickSetter(){ public void pick(int v){ pool.setVisualId(v==1); showPanel(); }});

        // ◇ CALIBRATE SCAN
        TextView calDiv=new TextView(this); calDiv.setText("────────────────────");
        calDiv.setTextColor(EDGE); calDiv.setTextSize(8);
        LinearLayout.LayoutParams cdlp=new LinearLayout.LayoutParams(-1,-2); cdlp.setMargins(0,14,0,14); calDiv.setLayoutParams(cdlp);
        root.addView(calDiv);

        String calMode = isPortrait ? "PORTRAIT" : "LANDSCAPE";
        addSecHdr(root, "CALIBRATE SCAN  (" + calMode + ")", GOLD);

        TextView calInfo=new TextView(this);
        calInfo.setText("Optional when Smart Scan is on — Auto Scan finds units by their health bars and does not use these positions. Calibration only sets the grid used as a fallback. Tap SHOW DOTS to see what Smart Scan actually detects.");
        calInfo.setTextColor(ASH); calInfo.setTextSize(10); calInfo.setPadding(2,0,0,8);
        root.addView(calInfo);

        TextView autoCalBtn=new TextView(this); autoCalBtn.setText("AUTO-CALIBRATE FROM BOARD");
        autoCalBtn.setTextColor(BONE); autoCalBtn.setTextSize(13); autoCalBtn.setGravity(Gravity.CENTER);
        autoCalBtn.setPadding(0,12,0,12); autoCalBtn.setBackground(box(0xFF0D1A1A,6,0xFF00B0B0,2));
        LinearLayout.LayoutParams acbl=new LinearLayout.LayoutParams(-1,-2); acbl.setMargins(0,0,0,6); autoCalBtn.setLayoutParams(acbl);
        autoCalBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ hexAutoCalibrate(); }});
        root.addView(autoCalBtn);

        TextView autoCalHint=new TextView(this);
        autoCalHint.setText("Detects the teal hex grid outlines automatically. Open TFT to planning phase, tap this, then SHOW DOTS to verify.");
        autoCalHint.setTextColor(ASH); autoCalHint.setTextSize(10); autoCalHint.setPadding(2,0,0,10);
        root.addView(autoCalHint);

        // Dev-only testing tool — hidden unless dev mode is unlocked (tap version 7x).
        // Lets the owner validate scanning on a saved screenshot; not for end users,
        // who will test in a live game.
        if(pool.isDevMode()){
            TextView imgScanBtn=new TextView(this); imgScanBtn.setText("SCAN FROM IMAGE (dev test)");
            imgScanBtn.setTextColor(BONE); imgScanBtn.setTextSize(13); imgScanBtn.setGravity(Gravity.CENTER);
            imgScanBtn.setPadding(0,12,0,12); imgScanBtn.setBackground(box(CARD,6,ASH,2));
            LinearLayout.LayoutParams isbl=new LinearLayout.LayoutParams(-1,-2); isbl.setMargins(0,0,0,6); imgScanBtn.setLayoutParams(isbl);
            imgScanBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                closePanel();
                try{
                    Intent i=new Intent(OverlayService.this, ImageScanActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }catch(Exception e){ Toast.makeText(OverlayService.this,"could not open picker: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
            }});
            root.addView(imgScanBtn);

            TextView imgScanHint=new TextView(this);
            imgScanHint.setText("DEV: pick a saved TFT screenshot and run the full scan on it — OCR readout + detected unit dots over the image. Tap the version label 7x again to hide dev tools.");
            imgScanHint.setTextColor(ASH); imgScanHint.setTextSize(10); imgScanHint.setPadding(2,0,0,10);
            root.addView(imgScanHint);

            // DIAGNOSTICS — copyable build/device/state dump for debugging
            LinearLayout diagRow=new LinearLayout(this); diagRow.setOrientation(LinearLayout.HORIZONTAL);
            diagRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView diagHdr=new TextView(this); diagHdr.setText("◇ DIAGNOSTICS");
            diagHdr.setTextColor(GOLD); diagHdr.setTextSize(11); diagHdr.setTypeface(null,android.graphics.Typeface.BOLD);
            diagHdr.setLetterSpacing(0.1f); diagHdr.setPadding(2,0,0,0);
            diagRow.addView(diagHdr, new LinearLayout.LayoutParams(0,-2,1f));
            TextView copyDiag=new TextView(this); copyDiag.setText("⎘ copy");
            copyDiag.setTextColor(ASH); copyDiag.setTextSize(10); copyDiag.setGravity(Gravity.CENTER);
            copyDiag.setBackground(box(CARD,5,EDGE,1)); copyDiag.setPadding(18,7,18,7);
            pressFeedback(copyDiag);
            copyDiag.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("tft-diagnostics",devDiagnostics()));
                Toast.makeText(OverlayService.this,"Diagnostics copied",Toast.LENGTH_SHORT).show();
            }});
            diagRow.addView(copyDiag);
            LinearLayout.LayoutParams drp=new LinearLayout.LayoutParams(-1,-2); drp.setMargins(0,4,0,4); diagRow.setLayoutParams(drp);
            root.addView(diagRow);

            TextView diagBox=new TextView(this); diagBox.setText(devDiagnostics());
            diagBox.setTextColor(ASH); diagBox.setTextSize(9);
            diagBox.setTypeface(android.graphics.Typeface.MONOSPACE);
            diagBox.setBackground(box(CARD,4,EDGE,1)); diagBox.setPadding(10,8,10,8);
            diagBox.setLineSpacing(2,1f);
            LinearLayout.LayoutParams dbxp=new LinearLayout.LayoutParams(-1,-2); dbxp.setMargins(0,0,0,10); diagBox.setLayoutParams(dbxp);
            root.addView(diagBox);
        }

        TextView tapCalBtn=new TextView(this); tapCalBtn.setText("TAP TO CALIBRATE (manual)");
        tapCalBtn.setTextColor(BONE); tapCalBtn.setTextSize(13); tapCalBtn.setGravity(Gravity.CENTER);
        tapCalBtn.setPadding(0,12,0,12); tapCalBtn.setBackground(box(BLOOD,6,BLOODL,2));
        LinearLayout.LayoutParams tcbl=new LinearLayout.LayoutParams(-1,-2); tcbl.setMargins(0,0,0,12); tapCalBtn.setLayoutParams(tcbl);
        tapCalBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ startTapCalibration(); }});
        root.addView(tapCalBtn);

        TextView adjGridBtn=new TextView(this); adjGridBtn.setText("ADJUST GRID (drag the dots)");
        adjGridBtn.setTextColor(BONE); adjGridBtn.setTextSize(13); adjGridBtn.setGravity(Gravity.CENTER);
        adjGridBtn.setPadding(0,12,0,12); adjGridBtn.setBackground(box(CARD,6,GOLD,2));
        LinearLayout.LayoutParams agbl=new LinearLayout.LayoutParams(-1,-2); agbl.setMargins(0,0,0,4); adjGridBtn.setLayoutParams(agbl);
        adjGridBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ startGridAdjust(); }});
        root.addView(adjGridBtn);

        TextView adjHint=new TextView(this);
        adjHint.setText("Shows every scan dot live over the game. Drag the gold rings until the dots sit on your units, then tap SAVE. The most precise way to calibrate.");
        adjHint.setTextColor(ASH); adjHint.setTextSize(10); adjHint.setPadding(2,0,0,12);
        root.addView(adjHint);

        String[] calLabels={"Board top row","Board bottom row","Board left edge","Board right edge","Bench row","Bench L/R shift"};
        final TextView[] calValTvs=new TextView[6];
        for(int ci=0;ci<6;ci++){
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

        // Smart Scan dot height: a vertical nudge for when the detected dots sit
        // uniformly above or below the units. ▼ moves them down, ▲ up; each tap
        // re-previews live with SHOW DOTS so you can dial it onto your champs.
        TextView nudgeHdr=new TextView(this); nudgeHdr.setText("◇ SMART SCAN DOT HEIGHT");
        nudgeHdr.setTextColor(GOLD); nudgeHdr.setTextSize(11); nudgeHdr.setTypeface(null,android.graphics.Typeface.BOLD);
        nudgeHdr.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams nhl=new LinearLayout.LayoutParams(-1,-2); nhl.setMargins(2,16,0,4); nudgeHdr.setLayoutParams(nhl);
        root.addView(nudgeHdr);

        LinearLayout nudgeRow=new LinearLayout(this); nudgeRow.setOrientation(LinearLayout.HORIZONTAL);
        nudgeRow.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));

        TextView nUp=new TextView(this); nUp.setText("▲ up");
        nUp.setTextColor(BONE); nUp.setTextSize(13); nUp.setGravity(Gravity.CENTER);
        nUp.setPadding(0,12,0,12); nUp.setBackground(box(CARD,6,EDGE,2));
        LinearLayout.LayoutParams nul=new LinearLayout.LayoutParams(0,-2,1f); nul.setMargins(0,0,4,0); nUp.setLayoutParams(nul);

        final TextView nVal=new TextView(this);
        nVal.setText((pool.getSmartNudgeY()>0?"+":"")+pool.getSmartNudgeY()+"%");
        nVal.setTextColor(pool.getSmartNudgeY()==0?ASH:GOLD); nVal.setTextSize(15);
        nVal.setTypeface(null,android.graphics.Typeface.BOLD); nVal.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nvl=new LinearLayout.LayoutParams(0,-2,0.8f); nVal.setLayoutParams(nvl);

        TextView nDown=new TextView(this); nDown.setText("▼ down");
        nDown.setTextColor(BONE); nDown.setTextSize(13); nDown.setGravity(Gravity.CENTER);
        nDown.setPadding(0,12,0,12); nDown.setBackground(box(CARD,6,EDGE,2));
        LinearLayout.LayoutParams ndl=new LinearLayout.LayoutParams(0,-2,1f); ndl.setMargins(4,0,0,0); nDown.setLayoutParams(ndl);

        nUp.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            pool.setSmartNudgeY(pool.getSmartNudgeY()-1); buzz(); closePanel(); showProbeDots();
        }});
        nDown.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            pool.setSmartNudgeY(pool.getSmartNudgeY()+1); buzz(); closePanel(); showProbeDots();
        }});
        nudgeRow.addView(nUp); nudgeRow.addView(nVal); nudgeRow.addView(nDown);
        root.addView(nudgeRow);

        TextView nudgeHint=new TextView(this);
        nudgeHint.setText("If the red dots sit above or below your units, nudge until they land on the champ bodies. Each tap previews the dots live.");
        nudgeHint.setTextColor(DIM); nudgeHint.setTextSize(10); nudgeHint.setPadding(2,2,0,0);
        root.addView(nudgeHint);

    }

    // ---- auto-calibrate from hex-outline detection ----

    @android.annotation.SuppressLint("NewApi")
    private void hexAutoCalibrate(){
        if(Build.VERSION.SDK_INT<30||TFTAccessibilityService.instance==null){
            Toast.makeText(this,"Enable Accessibility first (SETUP tab)",Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this,"Detecting hex grid — be in planning phase",Toast.LENGTH_SHORT).show();
        closePanel();
        try {
            TFTAccessibilityService.instance.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                getMainExecutor(),
                new android.accessibilityservice.AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(android.accessibilityservice.AccessibilityService.ScreenshotResult r){
                        android.hardware.HardwareBuffer hb=r.getHardwareBuffer();
                        android.graphics.Bitmap bmp=android.graphics.Bitmap.wrapHardwareBuffer(hb,null);
                        if(bmp==null){ hb.close(); onHexCalFail("bitmap null"); return; }
                        android.graphics.Bitmap soft=bmp.copy(android.graphics.Bitmap.Config.ARGB_8888,false);
                        bmp.recycle(); hb.close();
                        if(soft==null){ onHexCalFail("soft copy null"); return; }
                        // pixel scan is O(W*H) — run on a background thread
                        new Thread(()->applyHexCalibration(soft)).start();
                    }
                    @Override public void onFailure(int err){ onHexCalFail("screenshot err "+err); }
                });
        } catch(Exception e){ onHexCalFail(e.getMessage()); }
    }

    private void applyHexCalibration(android.graphics.Bitmap bmp){
        int W=bmp.getWidth(), H=bmp.getHeight();
        if(W<=H){ bmp.recycle(); onHexCalFail("rotate to landscape first"); return; }

        // Downsample 4x for speed (still sub-pixel accurate at % level)
        int step=4;
        int xL=W/10, xR=W*9/10, yT=H*15/100, yB=H*85/100;

        // Pass 1: bounding box of all teal/cyan hex-outline pixels
        int minX=W,maxX=0,minY=H,maxY=0,count=0;
        for(int y=yT;y<yB;y+=step){
            for(int x=xL;x<xR;x+=step){
                if(isTealHex(bmp.getPixel(x,y))){
                    if(x<minX) minX=x; if(x>maxX) maxX=x;
                    if(y<minY) minY=y; if(y>maxY) maxY=y;
                    count++;
                }
            }
        }
        if(count<80||maxX-minX<W/5||maxY-minY<H/6){
            bmp.recycle();
            onHexCalFail("no hex grid found ("+count+" teal pixels) — must be in planning phase");
            return;
        }

        // Pass 2: measure row widths at top and bottom to capture trapezoid perspective
        int sH=(maxY-minY)/5;
        int topMinX=W,topMaxX=0,botMinX=W,botMaxX=0;
        for(int y=minY;y<minY+sH;y+=step){
            for(int x=minX;x<=maxX;x+=step){
                if(isTealHex(bmp.getPixel(x,y))){
                    if(x<topMinX) topMinX=x; if(x>topMaxX) topMaxX=x;
                }
            }
        }
        for(int y=maxY-sH;y<=maxY;y+=step){
            for(int x=minX;x<=maxX;x+=step){
                if(isTealHex(bmp.getPixel(x,y))){
                    if(x<botMinX) botMinX=x; if(x>botMaxX) botMaxX=x;
                }
            }
        }
        bmp.recycle();

        if(topMaxX<=topMinX||botMaxX<=botMinX){
            onHexCalFail("could not measure row widths");
            return;
        }

        // Inset from outer outline edge to hex center (half a hex width for 7 cols,
        // fraction of measured board height for vertical)
        int hInsetTop=(topMaxX-topMinX)/14;
        int hInsetBot=(botMaxX-botMinX)/14;
        int vInset=(maxY-minY)/8;

        int tlX=topMinX+hInsetTop, trX=topMaxX-hInsetTop;
        int blX=botMinX+hInsetBot, brX=botMaxX-hInsetBot;
        int topY=minY+vInset,      botY=maxY-vInset;

        int calTL=tlX*100/W, calTR=trX*100/W;
        int calBL=blX*100/W, calBR=brX*100/W;
        int calTop=topY*100/H, calBot=botY*100/H;

        if(calTop>=calBot||calTL<3||calBR>97||calTop<10||calBot>90){
            onHexCalFail("detected bounds look implausible (tl="+calTL+" tr="+calTR+" bl="+calBL+" br="+calBR+" top="+calTop+" bot="+calBot+")");
            return;
        }

        pool.setBoardTopPct(calTop);
        pool.setBoardBotPct(calBot);
        pool.setBoardTopLeftPct(calTL);
        pool.setBoardTopRightPct(calTR);
        pool.setBoardBotLeftPct(calBL);
        pool.setBoardBotRightPct(calBR);

        android.util.Log.d("TFTScryer","hexCal: tl="+calTL+" tr="+calTR+" bl="+calBL+" br="+calBR+" top="+calTop+" bot="+calBot);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            Toast.makeText(this,"Grid detected — tap SHOW DOTS to verify",Toast.LENGTH_LONG).show();
            showProbeDots();
        });
    }

    // TFT planning-phase hex outlines are cyan/teal: high G and B, low R.
    // Excludes health bars (green: low B), mana bars (blue: low G), white text (high R).
    private boolean isTealHex(int px){
        int r=(px>>16)&0xFF, g=(px>>8)&0xFF, b=px&0xFF;
        return r<80 && g>120 && b>120 && Math.abs(g-b)<80;
    }

    private void onHexCalFail(String reason){
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            Toast.makeText(this,"Auto-cal: "+reason,Toast.LENGTH_LONG).show();
            mode=4; showPanel();
        });
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
                txtP.setColor(BLOODL);
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
            int boardTopPct = calTmpTopY;
            int boardBotPct = calTmpBotY;

            // Tap-order safety: if the front row was tapped before the back row, the
            // "back" Y lands below the "front" Y and the perspective grid runs backwards
            // (rows bunch up at the front). Swap rows so back is always the upper one.
            if(boardTopPct > boardBotPct){
                int t=boardTopPct; boardTopPct=boardBotPct; boardBotPct=t;
                t=topLeft;  topLeft=botLeft;   botLeft=t;
                t=topRight; topRight=botRight; botRight=t;
            }

            if(portrait){
                pool.setPortraitBoardTopPct(boardTopPct);
                pool.setPortraitBoardBotPct(boardBotPct);
                pool.setPortraitBoardTopLeftPct(topLeft); pool.setPortraitBoardTopRightPct(topRight);
                pool.setPortraitBoardBotLeftPct(botLeft); pool.setPortraitBoardBotRightPct(botRight);
                pool.setPortraitBoardLeftPct((topLeft+botLeft)/2);
                pool.setPortraitBoardRightPct((topRight+botRight)/2);
            } else {
                pool.setBoardTopPct(boardTopPct);
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

    // ---- adjust-grid: drag-based visual calibration ----

    private void startGridAdjust(){
        closePanel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            new Runnable(){ public void run(){ showGridAdjustOverlay(); }}, 300);
    }

    @SuppressWarnings("deprecation")
    private void showGridAdjustOverlay(){
        hideGridAdjustView();
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        final boolean portrait = sh > sw;
        final float spx=getResources().getDisplayMetrics().scaledDensity;
        final float grabR=42*getResources().getDisplayMetrics().density; // handle grab radius

        gridAdjustView=new View(OverlayService.this){
            // working copies in percent; written to Pool only on SAVE
            float wTop, wBot, wTL, wTR, wBL, wBR, wBenchY, wBenchL, wBenchR;
            float wF1, wF2; // middle-row spacing, percent of back-to-front span
            { // init from saved calibration for the current orientation
                if(portrait){
                    wTop=pool.getPortraitBoardTopPct();      wBot=pool.getPortraitBoardBotPct();
                    wTL =pool.getPortraitBoardTopLeftPct();  wTR =pool.getPortraitBoardTopRightPct();
                    wBL =pool.getPortraitBoardBotLeftPct();  wBR =pool.getPortraitBoardBotRightPct();
                    wBenchY=pool.getPortraitBenchYPct();
                    wF1=pool.getPortraitRowF1Pct();          wF2=pool.getPortraitRowF2Pct();
                    wBenchL=pool.getPortraitBenchLeftPct();  wBenchR=pool.getPortraitBenchRightPct();
                } else {
                    wTop=pool.getBoardTopPct();      wBot=pool.getBoardBotPct();
                    wTL =pool.getBoardTopLeftPct();  wTR =pool.getBoardTopRightPct();
                    wBL =pool.getBoardBotLeftPct();  wBR =pool.getBoardBotRightPct();
                    wBenchY=pool.getBenchYPct();
                    wF1=pool.getRowF1Pct();          wF2=pool.getRowF2Pct();
                    wBenchL=pool.getBenchLeftPct();  wBenchR=pool.getBenchRightPct();
                }
                if(wTop>wBot){ float t=wTop; wTop=wBot; wBot=t;
                               t=wTL; wTL=wBL; wBL=t;  t=wTR; wTR=wBR; wBR=t; }
                // no explicit bench span saved yet: derive it the same way the scan does
                if(wBenchL<0 || wBenchR<=wBenchL){
                    float halfGap=(wBR-wBL)/12f;
                    float shift=portrait?0:pool.getBenchXOffsetPct();
                    wBenchL=wBL-halfGap+shift;
                    wBenchR=wBR+halfGap+shift;
                }
            }
            int dragIdx=-1;       // 0..3 corners, 4..5 row spacing, 6..7 bench ends, -1 none
            boolean downOnBar=false; boolean downOnSave=false;
            private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

            // handle positions in px:
            // 0=back-left 1=back-right 2=front-left 3=front-right
            // 4=row-2 spacing 5=row-3 spacing 6=bench-left 7=bench-right
            private float[] handleX(int i){
                float topY=sh*wTop/100f, botY=sh*wBot/100f;
                switch(i){
                    case 0: return new float[]{sw*wTL/100f, topY};
                    case 1: return new float[]{sw*wTR/100f, topY};
                    case 2: return new float[]{sw*wBL/100f, botY};
                    case 3: return new float[]{sw*wBR/100f, botY};
                    case 4: case 5: {
                        float t=(i==4?wF1:wF2)/100f;
                        float cy=topY+t*(botY-topY);
                        float rowL=sw*wTL/100f+t*(sw*wBL/100f-sw*wTL/100f);
                        float rowR=sw*wTR/100f+t*(sw*wBR/100f-sw*wTR/100f);
                        return new float[]{(rowL+rowR)/2f, cy};
                    }
                    case 6: return new float[]{sw*wBenchL/100f, sh*wBenchY/100f};
                    default: return new float[]{sw*wBenchR/100f, sh*wBenchY/100f};
                }
            }

            @Override protected void onDraw(android.graphics.Canvas canvas){
                int W=getWidth(), H=getHeight();
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(0x26000000);
                canvas.drawRect(0,0,W,H,p);

                // probe dots — same interpolation as buildProbeGrid so what you see is what taps
                final float[] ROW_F={0f,wF1/100f,wF2/100f,1f};
                float topY=H*wTop/100f, botY=H*wBot/100f;
                float tlx=W*wTL/100f, trx=W*wTR/100f, blx=W*wBL/100f, brx=W*wBR/100f;
                p.setColor(0xFFE03131);
                for(int row=0;row<4;row++){
                    float t=ROW_F[row];
                    float cy=topY+t*(botY-topY);
                    float rowL=tlx+t*(blx-tlx), rowR=trx+t*(brx-trx);
                    for(int col=0;col<7;col++){
                        float cx=rowL+col*(rowR-rowL)/6f;
                        canvas.drawCircle(cx,cy,7,p);
                    }
                }
                // bench dots (blue) — span runs between the two bench end handles
                float bLx=W*wBenchL/100f, bRx=W*wBenchR/100f;
                float bY=H*wBenchY/100f;
                p.setColor(0xFF3B82F6);
                for(int col=0;col<9;col++){
                    float cx=bLx+(col+0.5f)*(bRx-bLx)/9f;
                    canvas.drawCircle(cx,bY,7,p);
                }

                // drag handles: gold rings — 4 corners, 2 middle-row spacing, 2 bench ends
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(4);
                for(int i=0;i<8;i++){
                    float[] hp=handleX(i);
                    p.setColor(i==dragIdx?0xFFFFE066:0xFFC9A227);
                    canvas.drawCircle(hp[0],hp[1],26,p);
                    canvas.drawCircle(hp[0],hp[1],34,p);
                }

                // banner
                p.setStyle(android.graphics.Paint.Style.FILL);
                float barH=H*0.115f;
                p.setColor(0xF00B0709);
                canvas.drawRect(0,0,W,barH,p);
                p.setTextAlign(android.graphics.Paint.Align.CENTER);
                p.setColor(0xFFE0D5C0); p.setTextSize(13*spx);
                p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText("ADJUST GRID", W/2f, barH*0.42f, p);
                p.setTypeface(android.graphics.Typeface.DEFAULT);
                p.setColor(0xFF7A6B60); p.setTextSize(10*spx);
                canvas.drawText("Drag the 4 corner rings onto the hex centres · middle rings adjust row spacing · SAVE when dots sit on champions", W/2f, barH*0.78f, p);

                // bottom bar: SAVE | CANCEL
                float btnTop=H*0.90f;
                p.setColor(0xF00B0709);
                canvas.drawRect(0,btnTop,W,H,p);
                p.setColor(0xFF39FF14); p.setTextSize(14*spx);
                canvas.drawText("SAVE", W*0.25f, (btnTop+H)/2f+5*spx, p);
                p.setColor(BLOODL);
                canvas.drawText("CANCEL", W*0.75f, (btnTop+H)/2f+5*spx, p);
                p.setColor(0xFF3A2024);
                canvas.drawRect(W/2f-1,btnTop+10,W/2f+1,H-10,p);
            }

            @Override public boolean onTouchEvent(android.view.MotionEvent e){
                int W=getWidth(), H=getHeight();
                float x=e.getX(), y=e.getY();
                int a=e.getAction();
                if(a==android.view.MotionEvent.ACTION_DOWN){
                    // handles win over the bottom bar — the bench handle can sit inside it
                    dragIdx=-1;
                    float best=grabR;
                    for(int i=0;i<8;i++){
                        float[] hp=handleX(i);
                        float d=(float)Math.hypot(x-hp[0],y-hp[1]);
                        if(d<best){ best=d; dragIdx=i; }
                    }
                    downOnBar = dragIdx<0 && y>=H*0.90f;
                    downOnSave = downOnBar && x<W/2f;
                    invalidate();
                    return true;
                } else if(a==android.view.MotionEvent.ACTION_MOVE){
                    if(dragIdx>=0){
                        float xp=Math.max(1f,Math.min(99f,x*100f/W));
                        float yp=Math.max(1f,Math.min(99f,y*100f/H));
                        switch(dragIdx){
                            case 0: wTL=xp; wTop=yp; break;
                            case 1: wTR=xp; wTop=yp; break;
                            case 2: wBL=xp; wBot=yp; break;
                            case 3: wBR=xp; wBot=yp; break;
                            case 4: case 5: {
                                // row spacing: convert drag Y to a fraction of the span
                                float span=wBot-wTop;
                                if(span>1f){
                                    float f=Math.max(5f,Math.min(95f,(yp-wTop)*100f/span));
                                    if(dragIdx==4) wF1=f; else wF2=f;
                                }
                                break;
                            }
                            case 6:
                                wBenchL=xp;
                                wBenchY=Math.max(50f,Math.min(95f,yp));
                                break;
                            default:
                                wBenchR=xp;
                                wBenchY=Math.max(50f,Math.min(95f,yp));
                        }
                        invalidate();
                    }
                    return true;
                } else if(a==android.view.MotionEvent.ACTION_UP){
                    if(dragIdx>=0){ dragIdx=-1; invalidate(); return true; }
                    if(downOnBar && y>=H*0.90f){
                        if(downOnSave && x<W/2f){
                            // normalize: back row must be the upper one, row 2 above row 3,
                            // bench left end left of the right end
                            if(wTop>wBot){ float t=wTop; wTop=wBot; wBot=t;
                                           t=wTL; wTL=wBL; wBL=t;  t=wTR; wTR=wBR; wBR=t; }
                            if(wF1>wF2){ float t=wF1; wF1=wF2; wF2=t; }
                            if(wBenchL>wBenchR){ float t=wBenchL; wBenchL=wBenchR; wBenchR=t; }
                            if(portrait){
                                pool.setPortraitBoardTopPct(Math.round(wTop));
                                pool.setPortraitBoardBotPct(Math.round(wBot));
                                pool.setPortraitBoardTopLeftPct(Math.round(wTL));
                                pool.setPortraitBoardTopRightPct(Math.round(wTR));
                                pool.setPortraitBoardBotLeftPct(Math.round(wBL));
                                pool.setPortraitBoardBotRightPct(Math.round(wBR));
                                pool.setPortraitBoardLeftPct(Math.round((wTL+wBL)/2f));
                                pool.setPortraitBoardRightPct(Math.round((wTR+wBR)/2f));
                                pool.setPortraitBenchYPct(Math.round(wBenchY));
                                pool.setPortraitRowF1Pct(Math.round(wF1));
                                pool.setPortraitRowF2Pct(Math.round(wF2));
                                pool.setPortraitBenchLeftPct(Math.round(wBenchL));
                                pool.setPortraitBenchRightPct(Math.round(wBenchR));
                            } else {
                                pool.setBoardTopPct(Math.round(wTop));
                                pool.setBoardBotPct(Math.round(wBot));
                                pool.setBoardTopLeftPct(Math.round(wTL));
                                pool.setBoardTopRightPct(Math.round(wTR));
                                pool.setBoardBotLeftPct(Math.round(wBL));
                                pool.setBoardBotRightPct(Math.round(wBR));
                                pool.setBoardLeftPct(Math.round((wTL+wBL)/2f));
                                pool.setBoardRightPct(Math.round((wTR+wBR)/2f));
                                pool.setBenchYPct(Math.round(wBenchY));
                                pool.setRowF1Pct(Math.round(wF1));
                                pool.setRowF2Pct(Math.round(wF2));
                                pool.setBenchLeftPct(Math.round(wBenchL));
                                pool.setBenchRightPct(Math.round(wBenchR));
                            }
                            Toast.makeText(OverlayService.this,"Grid saved",Toast.LENGTH_SHORT).show();
                        }
                        hideGridAdjustView();
                        mode=4; showPanel();
                    }
                    return true;
                }
                return true;
            }
        };
        WindowManager.LayoutParams glp=new WindowManager.LayoutParams(
            sw,sh,0,0,
            Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        glp.gravity=Gravity.TOP|Gravity.LEFT;
        try{ wm.addView(gridAdjustView,glp); }catch(Exception ex){ gridAdjustView=null; }
    }

    private void hideGridAdjustView(){
        if(gridAdjustView!=null){ try{ wm.removeView(gridAdjustView); }catch(Exception e){} gridAdjustView=null; }
    }

    private int calGet(int idx){
        if(isPortrait) switch(idx){
            case 0: return pool.getPortraitBoardTopPct();
            case 1: return pool.getPortraitBoardBotPct();
            case 2: return pool.getPortraitBoardLeftPct();
            case 3: return pool.getPortraitBoardRightPct();
            case 4: return pool.getPortraitBenchYPct();
            default: return pool.getBenchXOffsetPct();
        }
        switch(idx){
            case 0: return pool.getBoardTopPct();
            case 1: return pool.getBoardBotPct();
            case 2: return pool.getBoardLeftPct();
            case 3: return pool.getBoardRightPct();
            case 4: return pool.getBenchYPct();
            default: return pool.getBenchXOffsetPct();
        }
    }
    private void calSet(int idx, int val){
        if(isPortrait) switch(idx){
            case 0: pool.setPortraitBoardTopPct(val); return;
            case 1: pool.setPortraitBoardBotPct(val); return;
            case 2: pool.setPortraitBoardLeftPct(val); return;
            case 3: pool.setPortraitBoardRightPct(val); return;
            case 4: pool.setPortraitBenchYPct(val); return;
            default: pool.setBenchXOffsetPct(val); return;
        }
        switch(idx){
            case 0: pool.setBoardTopPct(val); break;
            case 1: pool.setBoardBotPct(val); break;
            case 2: pool.setBoardLeftPct(val); break;
            case 3: pool.setBoardRightPct(val); break;
            case 4: pool.setBenchYPct(val); break;
            default: pool.setBenchXOffsetPct(val); break;
        }
    }

    @SuppressWarnings({"deprecation","NewApi"})
    private void showProbeDots(){
        hideProbeDots();
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        // With Smart Scan on, SHOW DOTS previews what the scan ACTUALLY sees: take one
        // screenshot, detect units by their health bars, and draw a marker on each
        // real unit. This proves the scan finds your champs with no calibration. If
        // detection is unavailable or inconclusive, fall back to the calibrated grid.
        if(pool.getSmartScan() && Build.VERSION.SDK_INT>=31 && TFTAccessibilityService.instance!=null){
            final TFTAccessibilityService svc=TFTAccessibilityService.instance;
            try{
                // small delay so the just-closed panel is gone from the capture
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->
                svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback(){
                        @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                            try{
                                android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                                Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                                Bitmap bmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                                hb.close(); hw.recycle();
                                final int bw=bmp.getWidth(), bh=bmp.getHeight();
                                java.util.List<int[]> grid=buildProbeGrid(bw,bh);
                                int gridBoardCount=autoTapBoardProbeCount;
                                java.util.List<int[]> detected=detectHealthBarUnits(bmp,false);
                                java.util.List<int[]> out; int bc;
                                if(detected!=null){
                                    java.util.List<int[]> bench=new java.util.ArrayList<>(
                                            grid.subList(gridBoardCount, grid.size()));
                                    bench=filterByDetail(bmp,bench);
                                    out=new java.util.ArrayList<>(detected); out.addAll(bench); bc=detected.size();
                                    addScanLog("show dots: "+detected.size()+" units detected by health bar");
                                    renderProbeDots(out, bc, bw, bh, sw, sh,
                                        "SMART SCAN · "+detected.size()+" units · "+lastSmartTier);
                                } else { out=grid; bc=gridBoardCount; addScanLog("show dots: using grid (no detection)");
                                    renderProbeDots(out, bc, bw, bh, sw, sh,
                                        "GRID FALLBACK · no health bars detected"); }
                                bmp.recycle();
                            }catch(Exception e){ addScanLog("show dots err: "+e.getMessage());
                                renderProbeDots(buildProbeGrid(sw,sh), autoTapBoardProbeCount, sw, sh, sw, sh,
                                    "GRID FALLBACK · preview error"); }
                        }
                        @Override public void onFailure(int errorCode){
                            renderProbeDots(buildProbeGrid(sw,sh), autoTapBoardProbeCount, sw, sh, sw, sh,
                                "GRID FALLBACK · screenshot failed ("+errorCode+")");
                        }
                    }), 300);
                return;
            }catch(Exception e){ /* fall through to grid */ }
        }
        renderProbeDots(buildProbeGrid(sw,sh), autoTapBoardProbeCount, sw, sh, sw, sh,
            pool.getSmartScan() ? "GRID · Smart Scan needs Accessibility" : "GRID · Smart Scan is OFF");
    }

    // Draws probe markers over the screen. Board points are red, bench points blue.
    // A status banner at the top shows whether these are Smart-Scan-detected units or
    // the calibrated grid fallback, plus a count — so a screenshot of SHOW DOTS tells
    // us exactly what the scan sees. (bmpW/bmpH are the coordinate space the points
    // were computed in; sw/sh is the overlay window size — they match in practice.)
    @SuppressWarnings("deprecation")
    private void renderProbeDots(final java.util.List<int[]> probes, final int boardCount,
                                 final int bmpW, final int bmpH, final int sw, final int sh){
        renderProbeDots(probes, boardCount, bmpW, bmpH, sw, sh, "");
    }
    @SuppressWarnings("deprecation")
    private void renderProbeDots(final java.util.List<int[]> probes, final int boardCount,
                                 final int bmpW, final int bmpH, final int sw, final int sh,
                                 final String status){
        // Size the dots to the closest pair of board points so neighbours never overlap,
        // capped at 28px. Works for both the perspective grid and irregular detected
        // unit positions: a plain nearest-neighbour scan over the board points.
        float minGap=Float.MAX_VALUE;
        for(int i=0;i<boardCount;i++){
            int[] p=probes.get(i);
            for(int j=i+1;j<boardCount;j++){
                int[] q=probes.get(j);
                float d=(float)Math.hypot(q[0]-p[0],q[1]-p[1]);
                if(d>1 && d<minGap) minGap=d;
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
                if(status!=null && !status.isEmpty()){
                    paint.setStyle(android.graphics.Paint.Style.FILL);
                    paint.setColor(0xCC000000);
                    float bh2=Math.max(48f, sh*0.06f);
                    canvas.drawRect(0,0,sw,bh2,paint);
                    paint.setColor(0xFFFFD24A);
                    paint.setTextSize(Math.max(22f, sh*0.028f));
                    paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                    canvas.drawText(status, sw/2f, bh2*0.62f, paint);
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

    // ---- dev: scan from a saved image (no TFT needed) ----

    /** Called by ImageScanActivity with a decoded screenshot. Returns false if the
     *  overlay isn't running. Takes ownership of bmp (recycles it when done). */
    static boolean scanFromImage(final Bitmap bmp){
        final OverlayService s=_instance;
        if(s==null) return false;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->s.runImageScan(bmp));
        return true;
    }

    private void runImageScan(final Bitmap img){
        closePanel();
        hideImageScan();
        // OCR recycles the bitmap it's given, so feed it a copy and keep `img` for
        // health-bar detection and for drawing the result.
        Bitmap ocrCopy;
        try{ ocrCopy=img.copy(Bitmap.Config.ARGB_8888,false); }
        catch(Exception e){ ocrCopy=null; }
        final int bw=img.getWidth(), bh=img.getHeight();
        addScanLog("=== SCAN FROM IMAGE "+bw+"x"+bh+" ===");
        final ScreenScanner.ScanResult[] ocr={null};
        Runnable finish=()->{
            java.util.List<int[]> detected=null;
            try{ detected=detectHealthBarUnits(img,false); }catch(Exception e){}
            java.util.List<int[]> grid=buildProbeGrid(bw,bh);
            java.util.List<int[]> probes; int boardCount;
            if(detected!=null && !detected.isEmpty()){
                probes=new java.util.ArrayList<>(detected); boardCount=detected.size();
            } else {
                probes=grid; boardCount=autoTapBoardProbeCount;
            }
            String summary=imageScanSummary(ocr[0], detected, probes.size()-boardCount, bw, bh);
            renderImageScan(img, probes, boardCount, summary);
        };
        if(ocrCopy==null){ finish.run(); return; }
        new ScreenScanner(this,null).scanBitmap(ocrCopy, new ScreenScanner.ScanCallback(){
            public void onResult(ScreenScanner.ScanResult r){ ocr[0]=r; finish.run(); }
            public void onError(String msg){ addScanLog("image OCR err: "+msg); finish.run(); }
        }, ScreenScanner.MODE_FULL);
    }

    private String imageScanSummary(ScreenScanner.ScanResult r, java.util.List<int[]> detected,
                                    int benchProbes, int bw, int bh){
        StringBuilder sb=new StringBuilder();
        if(r!=null){
            sb.append("gold ").append(r.gold<0?"?":r.gold)
              .append("  lvl ").append(r.level<0?"?":r.level);
            if(r.xpNeed>0) sb.append("  xp ").append(r.xpCur).append("/").append(r.xpNeed);
            if(!r.stageRound.isEmpty()) sb.append("  ").append(r.stageRound);
            if(!r.shopChampions.isEmpty()) sb.append("\nshop: ").append(android.text.TextUtils.join(", ", r.shopChampions));
            if(!r.benchChampions.isEmpty()) sb.append("\nbench: ").append(android.text.TextUtils.join(", ", r.benchChampions));
            if(!r.augments.isEmpty()) sb.append("\naugs: ").append(android.text.TextUtils.join(", ", r.augments));
        } else sb.append("OCR failed");
        int units=detected==null?0:detected.size();
        sb.append("\nhealth-bar units: ").append(units)
          .append(units>0?(" (red dots)"):(" — using grid fallback"));
        addScanLog("image scan: "+sb.toString().replace("\n"," · "));
        return sb.toString();
    }

    // Draws the chosen image scaled-to-fit with the detected probe dots on top, plus
    // a result banner. Tap anywhere to dismiss. This makes both the OCR and the grid
    // verifiable against a real screenshot with no game running.
    @SuppressWarnings("deprecation")
    private void renderImageScan(final Bitmap img, final java.util.List<int[]> probes,
                                 final int boardCount, final String summary){
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        final float scale=Math.min((float)sw/img.getWidth(), (float)sh/img.getHeight());
        final float dispW=img.getWidth()*scale, dispH=img.getHeight()*scale;
        final float offX=(sw-dispW)/2f, offY=(sh-dispH)/2f;

        imageScanBmp=img;
        android.view.View v=new android.view.View(this){
            @Override protected void onDraw(android.graphics.Canvas c){
                android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                c.drawColor(0xFF000000);
                if(!img.isRecycled())
                    c.drawBitmap(img, null, new android.graphics.RectF(offX,offY,offX+dispW,offY+dispH), p);
                float r=Math.max(6f, dispW/60f);
                for(int i=0;i<probes.size();i++){
                    int[] pt=probes.get(i);
                    float x=offX+pt[0]*scale, y=offY+pt[1]*scale;
                    boolean bench=(i>=boardCount);
                    p.setStyle(android.graphics.Paint.Style.FILL);
                    p.setColor(bench?0x880044FF:0x88FF2200);
                    c.drawCircle(x,y,r,p);
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(2); p.setColor(0xCCFFFFFF);
                    c.drawCircle(x,y,r,p);
                }
                // result banner (multi-line) along the bottom
                String[] lines=summary.split("\n");
                p.setTextSize(Math.max(20f, sh*0.022f));
                float lh=p.getTextSize()*1.35f;
                float boxH=lh*(lines.length+1)+16;
                p.setStyle(android.graphics.Paint.Style.FILL); p.setColor(0xE6000000);
                c.drawRect(0, sh-boxH, sw, sh, p);
                p.setColor(0xFFFFD24A);
                float ty=sh-boxH+lh;
                for(String ln:lines){ c.drawText(ln, 16, ty, p); ty+=lh; }
                p.setColor(0xFF8A7A75); p.setTextSize(Math.max(16f, sh*0.016f));
                c.drawText("tap to close", 16, sh-10, p);
            }
        };
        v.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE,null);
        v.setOnClickListener(view->hideImageScan());

        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            sw,sh,0,0,
            Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE);
        lp.gravity=Gravity.TOP|Gravity.LEFT;
        imageScanView=v;
        try{ wm.addView(imageScanView,lp); }
        catch(Exception e){ imageScanView=null; if(!img.isRecycled()) img.recycle(); imageScanBmp=null; }
    }

    private void hideImageScan(){
        if(imageScanView!=null){
            try{ wm.removeView(imageScanView); }catch(Exception e){}
            imageScanView=null;
        }
        if(imageScanBmp!=null){
            if(!imageScanBmp.isRecycled()) imageScanBmp.recycle();
            imageScanBmp=null;
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
        if(!r.stageRound.isEmpty()) pool.setStageRound(r.stageRound);
        if(r.xpNeed>0) pool.setXp(r.xpCur, r.xpNeed);
        // scanned augments are mine — remember them for the AUGMENTS tab
        for(String a : r.augments) pool.addMyAugment(a);
        // Report shop champions in status but don't auto-mark them —
        // seeing a unit in the shop doesn't mean it was bought from the pool.
        // The debug log shows which names were detected so the user can verify.
        if(!r.shopChampions.isEmpty()){
            addScanLog("shop champs found: "+r.shopChampions.toString());
            if(!r.starLevels.isEmpty()) addScanLog("star levels: "+r.starLevels.toString());
        }
        // bench scan auto-fills the junk counters (bench units thin the pool);
        // the ODDS-tab steppers stay available to correct it
        if(!r.benchChampions.isEmpty()){
            addScanLog("bench champs: "+r.benchChampions.toString());
            int[] perCost=new int[6];
            for(String b : r.benchChampions){ int c=Pool.costOf(b); if(c>=1&&c<=5) perCost[c]++; }
            for(int c=1;c<=5;c++) if(perCost[c]>0) pool.setJunk(c, perCost[c]);
        }
        // new-game heuristic: a scan showing level 2-4 while the pool still has
        // lots of tracked copies almost certainly means the last game ended
        if(r.level>=2 && r.level<=4 && !pool.isEmpty()) newGameHint=true;
        lastScanStatus="✓ "+r.status;
        Toast.makeText(this,"✓ "+r.status,Toast.LENGTH_SHORT).show();
        refreshHud();
        // smart landing: drop the user on GOLD (mode 3) where the freshly-scanned
        // gold/level/income is shown, instead of SETUP. Off → keep SETUP (mode 4).
        mode = pool.getSmartLanding() ? 3 : 4;
        showPanel();
    }

    // ---- board scan mode ----

    private void startBoardScanMode(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
            return;
        }
        boardScanMode=true;
        boardScanDeadline=System.currentTimeMillis()+25000;
        boardScanResults=new java.util.ArrayList<>();
        closePanel();
        teardownStrayOverlays();
        showStopButton("✦ STOP SCAN");
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
            boardHandler.postDelayed(this,scanFastReady?900:2500);
        }};
        boardHandler.postDelayed(boardPollRunnable,600);
    }

    private void stopBoardScanMode(){
        boardScanMode=false;
        if(boardPollRunnable!=null){ boardHandler.removeCallbacks(boardPollRunnable); boardPollRunnable=null; }
        if(boardCountdownRunnable!=null){ boardHandler.removeCallbacks(boardCountdownRunnable); boardCountdownRunnable=null; }
        hideStopButton();
        teardownStrayOverlays();
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("board scan: stopped, found "+boardScanResults.size()+" champs");
        mode=0; showPanel();
    }

    @SuppressWarnings("NewApi")
    private void startAutoTapScan(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
            return;
        }
        autoScanPending=true;
        autoScanGeneration++;
        autoOppMode=false;
        autoScanResults=new java.util.ArrayList<>();
        autoScanGold=-1;
        autoScanLevel=-1;
        autoScanXpCur=-1; autoScanXpNeed=-1; autoScanStage="";
        autoTapIndex=0;
        autoTapConsecutiveMisses=0;
        autoTapBoardProbeCount=0;
        autoTapProbes=new java.util.ArrayList<>();
        autoTapSkip=new java.util.HashSet<>();
        autoTapSmartBoard=false; autoTapSwitchedToGrid=false; autoTapNudgeStage=0;
        autoTapHits=0; autoScanVisualCount=0;
        autoTapFallbackBoard=null; autoTapBenchProbes=new java.util.ArrayList<>();
        autoScanStartMs=android.os.SystemClock.uptimeMillis();
        closePanel();
        teardownStrayOverlays();
        showStopButton("✦ STOP SCAN");
        if(btnLabel!=null) btnLabel.setText("...");
        addScanLog("auto-tap: starting, getting screen size");
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        try{
            lastShotMs=android.os.SystemClock.uptimeMillis();
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        Bitmap goldTmp=null; // visible to catch so an early failure can recycle it
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            final int sw=hw.getWidth(), sh=hw.getHeight();
                            goldTmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                            final Bitmap goldLvlBmp=goldTmp;
                            hb.close();
                            java.util.List<int[]> grid=buildProbeGrid(sw,sh);
                            hw.recycle();
                            autoTapScreenH=sh;
                            int gridBoardCount=autoTapBoardProbeCount;
                            // Occupancy-filtered grid computed UP FRONT (one cheap variance
                            // pass on the screenshot we already have) so the scan can swap
                            // to it mid-run if the smart positions turn out to be wrong.
                            java.util.List<int[]> filteredGrid=filterOccupiedProbes(goldLvlBmp,grid,gridBoardCount);
                            int filteredBoardCount=autoTapBoardProbeCount;
                            // Smart Scan: find units by their health bars and tap the exact
                            // unit position. The calibrated grid is the fallback if detection
                            // is inconclusive. The bench keeps the grid (bench units have no
                            // health bar to detect) and is filtered for occupancy.
                            java.util.List<int[]> detected = pool.getSmartScan()
                                    ? detectHealthBarUnits(goldLvlBmp,false) : null;
                            if(detected!=null){
                                java.util.List<int[]> bench=new java.util.ArrayList<>(
                                        filteredGrid.subList(filteredBoardCount, filteredGrid.size()));
                                // Instant Visual ID: units whose board sprite was learned in
                                // an earlier scan are recorded straight from this screenshot
                                // and removed from the tap list. New/unknown units get tapped
                                // and their sprite is learned for next time.
                                java.util.List<int[]> toTap = pool.getVisualId()
                                        ? visualIdPass(goldLvlBmp, detected, false) : detected;
                                autoTapSmartBoard=true;
                                autoTapFallbackBoard=new java.util.ArrayList<>(
                                        filteredGrid.subList(0, filteredBoardCount));
                                autoTapBenchProbes=bench;
                                autoTapProbes=new java.util.ArrayList<>(toTap);
                                autoTapProbes.addAll(bench);
                                autoTapBoardProbeCount=toTap.size();
                                addScanLog("auto-tap: "+detected.size()+" units (health bar), "
                                        +autoScanVisualCount+" visual, "+toTap.size()+" to tap + "
                                        +bench.size()+" bench "+sw+"x"+sh);
                            } else {
                                autoTapProbes=filteredGrid;
                                autoTapBoardProbeCount=filteredBoardCount;
                                addScanLog("auto-tap: "+autoTapProbes.size()+" grid probes "+sw+"x"+sh);
                            }
                            if(btnLabel!=null) btnLabel.setText("0/"+autoTapProbes.size());
                            // one-time full-screen OCR pass to grab gold + level before the
                            // tapping starts — these corners are only readable on the board
                            // view (not in the per-probe popup crop), so capture them up front
                            new ScreenScanner(OverlayService.this,null).scanBitmap(goldLvlBmp,
                                new ScreenScanner.ScanCallback(){
                                    public void onResult(ScreenScanner.ScanResult r){
                                        autoScanGold=r.gold;
                                        autoScanLevel=r.level;
                                        autoScanXpCur=r.xpCur; autoScanXpNeed=r.xpNeed;
                                        autoScanStage=r.stageRound;
                                        addScanLog("auto-tap: gold="+r.gold+" level="+r.level
                                                +(r.xpNeed>0?(" xp="+r.xpCur+"/"+r.xpNeed):"")
                                                +(r.stageRound.isEmpty()?"":(" stage "+r.stageRound)));
                                        autoTapNextProbe();
                                    }
                                    public void onError(String msg){
                                        addScanLog("auto-tap gold/level OCR err: "+msg);
                                        autoTapNextProbe();
                                    }
                                }, ScreenScanner.MODE_FULL);
                        }catch(Exception e){
                            // scanBitmap never registered, so recycle the copy ourselves
                            if(goldTmp!=null) goldTmp.recycle();
                            autoScanPending=false; addScanLog("ERR auto-tap init: "+e.getMessage()); mode=0; showPanel();
                        }
                    }
                    @Override public void onFailure(int errorCode){ autoScanPending=false; addScanLog("ERR auto-tap init shot: "+errorCode); mode=0; showPanel(); }
                });
        }catch(Exception e){ autoScanPending=false; addScanLog("ERR startAutoTapScan: "+e.getMessage()); mode=0; showPanel(); }
    }

    @SuppressWarnings("NewApi")
    private void startAutoOppScan(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
            return;
        }
        autoScanPending=true;
        autoScanGeneration++;
        autoOppMode=true;
        autoScanResults=new java.util.ArrayList<>();
        autoScanGold=-1; autoScanLevel=-1;
        autoScanXpCur=-1; autoScanXpNeed=-1; autoScanStage="";
        autoTapIndex=0; autoTapConsecutiveMisses=0; autoTapBoardProbeCount=0;
        autoTapProbes=new java.util.ArrayList<>();
        autoTapSkip=new java.util.HashSet<>();
        autoTapSmartBoard=false; autoTapSwitchedToGrid=false; autoTapNudgeStage=0;
        autoTapHits=0; autoScanVisualCount=0;
        autoTapFallbackBoard=null; autoTapBenchProbes=new java.util.ArrayList<>();
        autoScanStartMs=android.os.SystemClock.uptimeMillis();
        oppScanResults=new java.util.LinkedHashMap<>();
        closePanel();
        if(btnLabel!=null) btnLabel.setText("...");
        addScanLog("auto-opp: starting");
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        try{
            lastShotMs=android.os.SystemClock.uptimeMillis();
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        Bitmap bmpTmp=null; // visible to catch so an early failure can recycle it
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            final int sw=hw.getWidth(), sh=hw.getHeight();
                            bmpTmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                            final Bitmap bmp=bmpTmp;
                            hb.close(); hw.recycle();
                            autoTapScreenH=sh;
                            java.util.List<int[]> oppGrid=buildOppProbeGrid(sw,sh);
                            int oppGridCount=autoTapBoardProbeCount;
                            java.util.List<int[]> filteredOpp=filterOccupiedProbes(bmp,oppGrid,oppGridCount);
                            int filteredOppCount=autoTapBoardProbeCount;
                            // Smart Scan for the opponent: enemy units in combat show RED
                            // health bars. Detect those for exact positions; fall back to the
                            // mirrored grid if inconclusive.
                            java.util.List<int[]> oppDetected = pool.getSmartScan()
                                    ? detectHealthBarUnits(bmp,true) : null;
                            if(oppDetected!=null){
                                java.util.List<int[]> toTap = pool.getVisualId()
                                        ? visualIdPass(bmp, oppDetected, true) : oppDetected;
                                autoTapSmartBoard=true;
                                autoTapFallbackBoard=new java.util.ArrayList<>(
                                        filteredOpp.subList(0, filteredOppCount));
                                autoTapProbes=new java.util.ArrayList<>(toTap);
                                autoTapBoardProbeCount=toTap.size();
                                addScanLog("auto-opp: "+oppDetected.size()+" enemy units (health bar), "
                                        +autoScanVisualCount+" visual, "+toTap.size()+" to tap "+sw+"x"+sh);
                            } else {
                                autoTapProbes=filteredOpp;
                                autoTapBoardProbeCount=filteredOppCount;
                                addScanLog("auto-opp: "+autoTapProbes.size()+" grid probes "+sw+"x"+sh);
                            }
                            if(btnLabel!=null) btnLabel.setText("0/"+autoTapProbes.size());
                            new ScreenScanner(OverlayService.this,null).scanBitmap(bmp,
                                new ScreenScanner.ScanCallback(){
                                    public void onResult(ScreenScanner.ScanResult r){
                                        autoScanGold=r.gold; autoScanLevel=r.level;
                                        autoTapNextProbe();
                                    }
                                    public void onError(String msg){ autoTapNextProbe(); }
                                }, ScreenScanner.MODE_FULL);
                        }catch(Exception e){
                            if(bmpTmp!=null) bmpTmp.recycle();
                            autoScanPending=false; addScanLog("ERR auto-opp init: "+e.getMessage()); mode=0; showPanel();
                        }
                    }
                    @Override public void onFailure(int errorCode){ autoScanPending=false; autoOppMode=false; addScanLog("ERR auto-opp shot: "+errorCode); mode=0; showPanel(); }
                });
        }catch(Exception e){ autoScanPending=false; autoOppMode=false; addScanLog("ERR startAutoOppScan: "+e.getMessage()); mode=0; showPanel(); }
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
            benchY   = h * pool.getBenchYPct()        / 100;
            if(pool.hasLandscapeGridCal()){
                topLeft  = w * pool.getBoardTopLeftPct()  / 100;
                topRight = w * pool.getBoardTopRightPct() / 100;
                botLeft  = w * pool.getBoardBotLeftPct()  / 100;
                botRight = w * pool.getBoardBotRightPct() / 100;
            } else {
                // Aspect-aware default: TFT draws the board height-fit and centered, so on
                // a wider screen it takes up a smaller fraction of the width. Deriving the
                // back/front row spans from the screen aspect makes the grid land on the
                // board on any device with no manual calibration. Measured spans: the back
                // row is ~0.67x and the front row ~0.98x the screen HEIGHT, centered.
                float aspect=(float)w/h;
                float backHalf=0.667f/aspect/2f, frontHalf=0.978f/aspect/2f;
                topLeft  = (int)((0.5f-backHalf)*w);  topRight = (int)((0.5f+backHalf)*w);
                botLeft  = (int)((0.5f-frontHalf)*w); botRight = (int)((0.5f+frontHalf)*w);
            }
        }
        // Repair swapped calibration saved by older versions (front row tapped before
        // the back row): the back-row Y would sit below the front-row Y and the
        // perspective fractions below would run backwards, bunching rows at the front.
        if(top > bot){
            int t=top; top=bot; bot=t;
            t=topLeft;  topLeft=botLeft;   botLeft=t;
            t=topRight; topRight=botRight; botRight=t;
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
        // The two middle fractions are adjustable via ADJUST GRID (row-spacing handles).
        final float[] ROW_F = {0f,
            (portrait ? pool.getPortraitRowF1Pct() : pool.getRowF1Pct())/100f,
            (portrait ? pool.getPortraitRowF2Pct() : pool.getRowF2Pct())/100f,
            1f};
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
        // Bench span: an explicit left/right saved by ADJUST GRID wins. Otherwise fall
        // back to deriving it from the board's front row: botLeft/botRight are the
        // measured CENTERS of the front row's outer hexes, so nudge outward by half a
        // hex-gap on each side to approximate the bench's true span before spreading 9 slots.
        int benchLeft, benchRight;
        int savedBenchL = portrait ? pool.getPortraitBenchLeftPct()  : pool.getBenchLeftPct();
        int savedBenchR = portrait ? pool.getPortraitBenchRightPct() : pool.getBenchRightPct();
        if(savedBenchL>=0 && savedBenchR>savedBenchL){
            benchLeft  = w * savedBenchL / 100;
            benchRight = w * savedBenchR / 100;
        } else {
            int benchHalfGap = frontWidth / 12;
            int benchXShift  = w * (portrait ? 0 : pool.getBenchXOffsetPct()) / 100;
            benchLeft  = botLeft  - benchHalfGap + benchXShift;
            benchRight = botRight + benchHalfGap + benchXShift;
        }
        for(int col=0;col<benchCols;col++){
            int cx=benchLeft+(int)((col+0.5f)*(benchRight-benchLeft)/benchCols);
            if(btnW>0&&cx>=btnLoc[0]-30&&cx<=btnLoc[0]+btnW+30
                      &&benchY>=btnLoc[1]-30&&benchY<=btnLoc[1]+btnH+30) continue;
            pts.add(new int[]{cx,benchY});
        }
        return pts;
    }

    // Probe grid for the opponent's board during combat. The opponent's units fight
    // from the top portion of the board — their front row (nearest the player) sits
    // near screen-centre and their back row is at the top. We mirror the player's
    // calibrated Y zone vertically: oppFrontY = 100 - boardBotPct, oppBackY = 100 -
    // boardTopPct. Perspective row fractions are also inverted so the wider gaps are
    // near the player (opponent front) and compress toward the top (opponent back).
    // No bench probes — opponent bench units don't fight.
    private java.util.List<int[]> buildOppProbeGrid(int w, int h){
        java.util.List<int[]> pts=new java.util.ArrayList<>();
        boolean portrait = h > w;
        int oppFrontY, oppBackY, oppLeft, oppRight;
        if(portrait){
            oppFrontY = h * (100 - pool.getPortraitBoardBotPct()) / 100;
            oppBackY  = h * (100 - pool.getPortraitBoardTopPct()) / 100;
            oppLeft   = w * pool.getPortraitBoardTopLeftPct()  / 100;
            oppRight  = w * pool.getPortraitBoardTopRightPct() / 100;
        } else {
            oppFrontY = h * (100 - pool.getBoardBotPct()) / 100;
            oppBackY  = h * (100 - pool.getBoardTopPct()) / 100;
            oppLeft   = w * pool.getBoardTopLeftPct()  / 100;
            oppRight  = w * pool.getBoardTopRightPct() / 100;
        }
        // Repair swapped calibration (see buildProbeGrid): opponent back row must be
        // the upper one, otherwise the mirrored perspective also runs backwards.
        if(oppBackY > oppFrontY){ int t=oppBackY; oppBackY=oppFrontY; oppFrontY=t; }
        // Inverted perspective: gaps widen toward oppFrontY (screen centre, closest to
        // player). Row 0 = opponent back row (top), row 3 = opponent front row (nearest
        // player, oppFrontY). ROW_F[0..3] is 0→1 mapping back→front. Middle fractions
        // follow the same adjustable row spacing as the player grid.
        final float[] ROW_F = {0f,
            (portrait ? pool.getPortraitRowF1Pct() : pool.getRowF1Pct())/100f,
            (portrait ? pool.getPortraitRowF2Pct() : pool.getRowF2Pct())/100f,
            1f};
        int cols=7;
        int[] btnLoc=new int[2]; int btnW=0,btnH=0;
        if(button!=null){ button.getLocationOnScreen(btnLoc); btnW=button.getWidth(); btnH=button.getHeight(); }
        for(int row=0;row<4;row++){
            float t=ROW_F[row];
            int cy=oppBackY-(int)(t*(oppBackY-oppFrontY)); // back=top, front=lower
            int rowLeft =(int)(oppLeft);
            int rowRight=(int)(oppRight);
            for(int col=0;col<cols;col++){
                int cx=rowLeft+col*(rowRight-rowLeft)/(cols-1);
                if(btnW>0&&cx>=btnLoc[0]-30&&cx<=btnLoc[0]+btnW+30
                          &&cy>=btnLoc[1]-30&&cy<=btnLoc[1]+btnH+30) continue;
                pts.add(new int[]{cx,cy});
            }
        }
        autoTapBoardProbeCount=pts.size();
        return pts;
    }

    // Decide which board hexes and bench slots actually have a unit on them, from one
    // clean board screenshot, so the auto-tap only visits occupied probes. A champion
    // sprite has a health bar and lots of detail; an empty hex or slot is flat ground.
    // We score each probe by luminance variance and keep those clearly above the baseline.
    //
    // Bias is deliberately toward KEEPING probes: a false keep wastes ~1 second, but a
    // false skip would miss a real unit. If board results look implausible the board
    // falls back to tapping every hex (old thorough behaviour). Bench is always filtered
    // — the few extra misses saved matter when scanning 9 bench slots at 1s/slot.
    private java.util.List<int[]> filterOccupiedProbes(Bitmap bmp, java.util.List<int[]> probes, int boardCount){
        try{
            if(boardCount<=0 || probes.size()<boardCount) return probes;
            int w=bmp.getWidth(), h=bmp.getHeight();
            int boxR=Math.max(10, Math.min(w,h)/45);
            int total=probes.size();

            float[] metric=new float[total];
            for(int i=0;i<total;i++){
                int[] p=probes.get(i);
                metric[i]=hexDetail(bmp,p[0],p[1],boxR,w,h);
            }

            // Board filtering
            float[] boardMetric=java.util.Arrays.copyOf(metric, boardCount);
            float[] boardSorted=boardMetric.clone();
            java.util.Arrays.sort(boardSorted);
            float boardMedian=boardSorted[boardCount/2];
            float boardThresh=boardMedian*1.12f+1f;
            java.util.List<int[]> out=new java.util.ArrayList<>();
            int keptBoard=0;
            for(int i=0;i<boardCount;i++){
                if(metric[i]>=boardThresh){ out.add(probes.get(i)); keptBoard++; }
            }
            if(keptBoard==0 || keptBoard>boardCount*4/5){
                addScanLog("occupancy: board inconclusive (kept "+keptBoard+"/"+boardCount+"), tapping all board hexes");
                for(int i=0;i<boardCount;i++) if(!out.contains(probes.get(i))) out.add(0,probes.get(i));
                // rebuild properly — fall back by keeping all board probes
                out.clear(); for(int i=0;i<boardCount;i++) out.add(probes.get(i)); keptBoard=boardCount;
            }
            addScanLog("occupancy: "+keptBoard+"/"+boardCount+" board probes occupied");
            autoTapBoardProbeCount=keptBoard;

            // Bench filtering — same approach, independent threshold
            int benchCount=total-boardCount;
            if(benchCount>0){
                float[] benchMetric=java.util.Arrays.copyOfRange(metric, boardCount, total);
                float[] benchSorted=benchMetric.clone();
                java.util.Arrays.sort(benchSorted);
                float benchMedian=benchSorted[benchCount/2];
                float benchThresh=benchMedian*1.12f+1f;
                int keptBench=0;
                for(int i=0;i<benchCount;i++){
                    if(benchMetric[i]>=benchThresh){ out.add(probes.get(boardCount+i)); keptBench++; }
                }
                addScanLog("occupancy: "+keptBench+"/"+benchCount+" bench slots occupied");
            }
            return out;
        }catch(Exception e){
            addScanLog("occupancy err: "+e.getMessage()+", tapping all");
            return probes;
        }
    }

    // Standard deviation of luminance in a box around (cx,cy) — a cheap "how much detail
    // is here" score. High for champion sprites, low for empty hex ground.
    private float hexDetail(Bitmap bmp, int cx, int cy, int r, int w, int h){
        int x0=Math.max(0,cx-r), x1=Math.min(w,cx+r);
        int y0=Math.max(0,cy-r), y1=Math.min(h,cy+r);
        if(x1<=x0 || y1<=y0) return 0f;
        int bw=x1-x0, bh=y1-y0;
        int[] px=new int[bw*bh];
        bmp.getPixels(px,0,bw,x0,y0,bw,bh);
        double sum=0,sumSq=0; int n=px.length;
        for(int c:px){
            int lum=((c>>16&0xFF)*30+(c>>8&0xFF)*59+(c&0xFF)*11)/100;
            sum+=lum; sumSq+=(double)lum*lum;
        }
        double mean=sum/n;
        double var=sumSq/n-mean*mean;
        return (float)Math.sqrt(Math.max(0,var));
    }

    // ---- Smart Scan: find units directly from their health bars ----
    //
    // Every unit on the board carries a health bar above it: a bright green
    // horizontal bar for your own units, a red one for an enemy's during combat.
    // Empty hexes and the board floor have no such bar. We scan a FIXED board
    // region of one clean screenshot for those bars, cluster the matching pixels
    // into units, and return the exact tap point under each bar. No calibration is
    // used at all — the region bounds below are fixed percentages of the screen.
    // Returns null when the result looks implausible so the caller can fall back
    // to the calibrated grid.
    // Human-readable note about how the last detection succeeded ("strict colors",
    // "relaxed colors") — shown in the SHOW DOTS banner and the debug log.
    private String lastSmartTier = "";

    private java.util.List<int[]> detectHealthBarUnits(Bitmap bmp, boolean opp){
        try{
            int w=bmp.getWidth(), h=bmp.getHeight();
            boolean portrait = h > w;
            // FIXED board-region bounds — no calibration needed. The TFT Mobile board
            // is a 4x7 hex grid drawn in perspective in the lower-centre of the screen
            // (your own units) or the upper half during combat (the enemy). These
            // generous percentage bounds cover the whole play area across phone aspect
            // ratios, while excluding the top augment/HUD bar and the bottom shop bar
            // so we don't read their text/icons as health bars. Detection finds the
            // actual units inside this region, so exact placement never matters.
            int zoneTop, zoneBot, leftExtreme, rightExtreme;
            if(opp){
                // enemy team fights from the TOP half during combat; red bars float
                // above their units, from the back row (~14%) to the front (~58%).
                zoneTop      = h * 11 / 100;
                zoneBot      = h * 60 / 100;
                leftExtreme  = w *  5 / 100;
                rightExtreme = w * 93 / 100;
            } else if(portrait){
                // portrait: the board sits higher and is taller on screen
                zoneTop      = h * 18 / 100;
                zoneBot      = h * 68 / 100;
                leftExtreme  = w *  3 / 100;
                rightExtreme = w * 97 / 100;
            } else {
                // landscape own board: back-row bars ~33%, front units ~70%
                zoneTop      = h * 27 / 100;
                zoneBot      = h * 76 / 100;
                leftExtreme  = w *  5 / 100;
                rightExtreme = w * 93 / 100;
            }
            zoneTop = Math.max(0, zoneTop);
            zoneBot = Math.min(h, zoneBot);
            leftExtreme  = Math.max(0, leftExtreme);
            rightExtreme = Math.min(w, rightExtreme);
            int zw=rightExtreme-leftExtreme, zh=zoneBot-zoneTop;
            if(zw<10 || zh<10) return null;

            // floating button bbox, to ignore its sigil
            int[] btnLoc=new int[2]; int btnW=0,btnH=0;
            if(button!=null){ button.getLocationOnScreen(btnLoc); btnW=button.getWidth(); btnH=button.getHeight(); }

            int[] px=new int[zw*zh];
            bmp.getPixels(px,0,zw,leftExtreme,zoneTop,zw,zh);

            // Two color tiers. RATIO rules instead of absolute channel differences:
            // g >= 1.8*r is true for a health-bar green whether the screen renders
            // it bright or dim, while absolute thresholds break whenever a device's
            // brightness/color profile shifts the values. Tier 2 (standard) accepts
            // a wider brightness range and is a strict superset of tier 1 — so it
            // is tried FIRST: on a normal screen it finds every bar. Only when it
            // is noisy (more hits than a board can hold, meaning this arena has
            // colors that fool the relaxed rule) does tier 1 re-run with vivid-only
            // thresholds. Arena foliage (olive/yellow greens, where red is more
            // than ~60% of green) fails the ratio test at both tiers, and anything
            // that slips through must still survive the structural checks below.
            java.util.List<int[]> units = detectBarsAtTier(px, zw, zh, w, h, opp, 2,
                    leftExtreme, zoneTop, btnLoc, btnW, btnH);
            if(units == null){
                addScanLog("smart: no health bars found, using grid");
                return null; // tier 1 is a subset of tier 2 — nothing to gain by trying it
            }
            if(units.size() <= 13){
                lastSmartTier = "standard colors";
                addScanLog("smart: "+units.size()+" units by health bar ("+lastSmartTier+")");
                return units;
            }
            int relaxedCount = units.size();
            units = detectBarsAtTier(px, zw, zh, w, h, opp, 1,
                    leftExtreme, zoneTop, btnLoc, btnW, btnH);
            if(units != null && units.size() <= 13){
                lastSmartTier = "strict colors";
                addScanLog("smart: standard tier noisy ("+relaxedCount+"), strict tier gave "
                        +units.size()+" units");
                return units;
            }
            addScanLog("smart: detection too noisy ("+relaxedCount+" standard"
                    +(units!=null?", "+units.size()+" strict":"")+"), using grid");
            return null;
        }catch(Exception e){
            addScanLog("smart err: "+e.getMessage()+", using grid");
            return null;
        }
    }

    // True if the pixel reads as health-bar fill at the given tier.
    private static boolean barPixel(int c, boolean opp, int tier){
        int r=(c>>16)&0xFF, g=(c>>8)&0xFF, b=c&0xFF;
        if(opp){
            // enemy red bar: red strongly dominant over both green and blue
            return tier==1 ? (r>=150 && r*5>=g*9 && r*5>=b*9)   // r ≥ 1.8g, 1.8b
                           : (r>=120 && r*5>=g*8 && r*5>=b*8);  // r ≥ 1.6g, 1.6b
        }
        // ally green bar: green strongly dominant. Foliage greens have r at
        // 60-80% of g and fail the ratio; bar greens have r under ~50% of g.
        return tier==1 ? (g>=150 && g*5>=r*9 && g*5>=b*8)       // g ≥ 1.8r, 1.6b
                       : (g>=115 && g*5>=r*8 && g*5>=b*7);      // g ≥ 1.6r, 1.4b
    }

    // One full detection pass at a single color tier: scanline runs -> clusters ->
    // structural validation -> tap points. Returns null when no unit survives.
    private java.util.List<int[]> detectBarsAtTier(int[] px, int zw, int zh, int w, int h,
                                                   boolean opp, int tier,
                                                   int zoneLeft, int zoneTop,
                                                   int[] btnLoc, int btnW, int btnH){
        int minLen=Math.max(6, w*2/100);          // a bar is at least ~2% of screen wide
        int maxLen=Math.max(minLen+4, w*11/100);  // and at most ~11% wide
        // Runs tolerate up to 2 non-matching pixels in a row, so the thin tick
        // marks and star icons drawn over the bar don't split one bar into two
        // short segments that both fail the minimum-length check.
        final int GAP_TOL=2;
        // each segment: {screenY, screenCx, len}
        java.util.List<int[]> segs=new java.util.ArrayList<>();
        for(int ry=0; ry<zh; ry+=2){
            int base=ry*zw; int runStart=-1, lastHit=-1;
            for(int rx=0; rx<zw; rx++){
                if(barPixel(px[base+rx], opp, tier)){
                    if(runStart<0) runStart=rx;
                    lastHit=rx;
                } else if(runStart>=0 && rx-lastHit>GAP_TOL){
                    addBarSeg(segs,runStart,lastHit,ry,minLen,maxLen,zoneLeft,zoneTop,btnLoc,btnW,btnH);
                    runStart=-1;
                }
            }
            if(runStart>=0) addBarSeg(segs,runStart,lastHit,ry,minLen,maxLen,zoneLeft,zoneTop,btnLoc,btnW,btnH);
        }
        if(segs.isEmpty()) return null;

        // cluster segments belonging to the same bar: similar centre-x, vertically
        // adjacent scanlines. segs are produced in increasing y already.
        int clusterXTol=Math.max(8, w*4/100);
        int rowGap=10; // px of vertical tolerance between a bar's scanlines
        // a health bar is a THIN strip: ~1-1.5% of screen height including border
        int maxBarThick=Math.max(8, h*25/1000);
        // cluster as {sumCx,count,minY,maxY,sumLen}
        java.util.List<int[]> cl=new java.util.ArrayList<>();
        for(int[] s : segs){
            int sy=s[0], scx=s[1], slen=s[2];
            int[] best=null;
            for(int[] c : cl){
                int ccx=c[0]/c[1];
                if(Math.abs(scx-ccx)<=clusterXTol && sy-c[3]<=rowGap){ best=c; break; }
            }
            if(best==null){ cl.add(new int[]{scx,1,sy,sy,slen}); }
            else { best[0]+=scx; best[1]++; if(sy<best[2])best[2]=sy; if(sy>best[3])best[3]=sy; best[4]+=slen; }
        }

        java.util.List<int[]> units=new java.util.ArrayList<>(); // {tapX,tapY}
        // bar bottom -> unit body, plus a user nudge for devices where the dots sit
        // uniformly above/below the units (SETUP -> Smart Scan dot height)
        int bodyDrop=Math.max(10, h*4/100) + h*pool.getSmartNudgeY()/100;
        StringBuilder clrLog=new StringBuilder("smart t"+tier+" bars:");
        for(int[] c : cl){
            int count=c[1], minY=c[2], maxY=c[3];
            if(count<3) continue;                 // needs >=3 scanlines (>=~6px tall)
            if(maxY-minY>maxBarThick) continue;   // tall blob = foliage, not a bar
            int cx=c[0]/count;
            int barW=c[4]/count;
            // VERTICAL ISOLATION: a real bar floats clear of other bar-colored
            // pixels. Foliage that happens to form a thin matching band is part of
            // a larger green region, so the rows just above and below it also
            // match. Reject the cluster if either flanking row is >40% bar-colored
            // across the bar's own width.
            if(flankMatches(px, zw, zh, cx-zoneLeft, minY-zoneTop-6, barW, opp, tier) ||
               flankMatches(px, zw, zh, cx-zoneLeft, maxY-zoneTop+6, barW, opp, tier)) continue;
            int tapY=Math.max(0, Math.min(h-1, maxY+bodyDrop));
            boolean dup=false;
            for(int[] u : units){
                if(Math.abs(u[0]-cx)<=clusterXTol && Math.abs(u[1]-tapY)<=h*5/100){ dup=true; break; }
            }
            if(dup) continue;
            // star level from the star icons drawn directly above the bar — COLOR
            // based (bronze/silver/gold), so unit size differences cannot fool it
            int stars=starsAboveBar(px,zw,zh,cx-zoneLeft,minY-zoneTop,maxY-minY,barW);
            units.add(new int[]{cx,tapY,stars});
            // log the bar's center pixel color so thresholds stay tunable from logs
            int relX=cx-zoneLeft, relY=(minY+maxY)/2-zoneTop;
            if(relX>=0 && relX<zw && relY>=0 && relY<zh){
                int pc=px[relY*zw+relX];
                clrLog.append(" (").append((pc>>16)&0xFF).append(",").append((pc>>8)&0xFF).append(",").append(pc&0xFF).append(")");
                if(stars>0) clrLog.append(stars).append("★");
            }
        }
        if(units.isEmpty()) return null;
        // scan order: back-to-front (top y first), then left-to-right
        java.util.Collections.sort(units,(a,b2)-> a[1]!=b2[1] ? a[1]-b2[1] : a[0]-b2[0]);
        addScanLog(clrLog.toString());
        return units;
    }

    // Star level read from the star icons that TFT draws directly above each
    // unit's health bar. Classification is by COLOR ONLY: gold = 3 stars,
    // silver = 2, bronze = 1. Sprite size grows with star level too, but size
    // also changes with naturally-large champions and combat buffs, so color is
    // the only reliable signal. Returns 0 when no clear star color is found
    // (callers treat 0 as unknown, not as 1 star).
    private static int starsAboveBar(int[] px,int zw,int zh,int relCx,int relBarTop,int barThick,int barW){
        int bandH=Math.max(6, barThick*3);
        int y1=relBarTop-2, y0=Math.max(0, y1-bandH);
        int x0=Math.max(0, relCx-barW/2), x1=Math.min(zw-1, relCx+barW/2);
        if(y1<=y0 || x1<=x0 || y1>=zh) return 0;
        int gold=0, silver=0, bronze=0;
        for(int y=y0;y<=y1;y++){
            int base=y*zw;
            for(int x=x0;x<=x1;x++){
                int c=px[base+x];
                int r=(c>>16)&0xFF, g=(c>>8)&0xFF, b=c&0xFF;
                if(r>=200 && g>=140 && g<r && b<=110 && b*2<r) gold++;
                else if(r>=150 && g>=150 && b>=150
                        && Math.abs(r-g)<=35 && Math.abs(g-b)<=45 && Math.abs(r-b)<=45) silver++;
                else if(r>=110 && r<200 && g*10>=r*4 && g*10<=r*8 && b*2<r) bronze++;
            }
        }
        int minPix=Math.max(4,(x1-x0)/6);
        if(gold>=minPix && gold>=silver && gold>=bronze) return 3;
        if(silver>=minPix && silver>=gold && silver>=bronze) return 2;
        if(bronze>=minPix) return 1;
        return 0;
    }

    // True if the zone-relative scanline at relY is more than 40% bar-colored
    // across [relCx-barW/2, relCx+barW/2] — meaning the candidate bar is actually
    // part of a taller colored region. Out-of-zone rows count as clear.
    private boolean flankMatches(int[] px, int zw, int zh, int relCx, int relY, int barW,
                                 boolean opp, int tier){
        if(relY<0 || relY>=zh) return false;
        int x0=Math.max(0, relCx-barW/2), x1=Math.min(zw-1, relCx+barW/2);
        if(x1<=x0) return false;
        int base=relY*zw, hits=0, total=x1-x0+1;
        for(int x=x0;x<=x1;x++) if(barPixel(px[base+x], opp, tier)) hits++;
        return hits*10 > total*4;
    }

    private void addBarSeg(java.util.List<int[]> segs,int xs,int xe,int ry,int minLen,int maxLen,
                           int zoneLeft,int zoneTop,int[] btnLoc,int btnW,int btnH){
        int len=xe-xs+1;
        if(len<minLen || len>maxLen) return;
        int cx=zoneLeft+(xs+xe)/2, cy=zoneTop+ry;
        if(btnW>0 && cx>=btnLoc[0]-30 && cx<=btnLoc[0]+btnW+30
                  && cy>=btnLoc[1]-30 && cy<=btnLoc[1]+btnH+30) return;
        segs.add(new int[]{cy,cx,len});
    }

    // Keep only probes that have real detail (a unit), used for the bench when
    // Smart Scan handled the board. Never returns empty: if every slot scores low
    // it returns the originals so we don't silently skip a full bench.
    private java.util.List<int[]> filterByDetail(Bitmap bmp, java.util.List<int[]> probes){
        try{
            int n=probes.size();
            if(n==0) return probes;
            int w=bmp.getWidth(), h=bmp.getHeight();
            int boxR=Math.max(10, Math.min(w,h)/45);
            float[] m=new float[n];
            for(int i=0;i<n;i++){ int[] p=probes.get(i); m[i]=hexDetail(bmp,p[0],p[1],boxR,w,h); }
            float[] s=m.clone(); java.util.Arrays.sort(s);
            float thresh=s[n/2]*1.12f+1f;
            java.util.List<int[]> out=new java.util.ArrayList<>();
            for(int i=0;i<n;i++) if(m[i]>=thresh) out.add(probes.get(i));
            return out.isEmpty()?probes:out;
        }catch(Exception e){ return probes; }
    }

    // overlap guard: cleared by dispatchTap's own completion path, and forced
    // clear on teardown so a torn-down flow never wedges injection shut
    private void clearInjecting(){ injecting=false; }

    private void dispatchTap(float x, float y, final Runnable onDone){
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null){ onDone.run(); return; }
        // never let two injected taps overlap — that is what can drop the user's
        // own touch on some ROMs; the caller still advances via onDone
        if(injecting){ addScanLog("skip overlapping tap"); onDone.run(); return; }
        injecting=true;
        // onDone MUST run exactly once. Normally the gesture's completion callback
        // fires it, but HyperOS/MIUI frequently DROP that callback — and without a
        // fallback the whole sequence stalls (the hunt buys one champ then freezes;
        // planner steps never advance). So a timeout fires onDone if the callback
        // never arrives, and a one-shot guard stops it running twice.
        final boolean[] fired={false};
        final Runnable finish=new Runnable(){ public void run(){
            if(fired[0]) return;
            fired[0]=true;
            injecting=false;
            boardHandler.removeCallbacks(this);
            onDone.run();
        }};
        boardHandler.postDelayed(finish, TAP_STROKE_MS+500L);
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
                    @Override public void onCompleted(android.accessibilityservice.GestureDescription d){ finish.run(); }
                    @Override public void onCancelled(android.accessibilityservice.GestureDescription d){ finish.run(); }
                }, null);
        }catch(Exception e){ addScanLog("ERR dispatchTap: "+e.getMessage()); finish.run(); }
    }

    @SuppressWarnings("NewApi")
    private void autoTapNextProbe(){
        if(!autoScanPending) return;
        // skip board probes already identified as a duplicate of an earlier tap
        while(autoTapIndex<autoTapProbes.size() && autoTapSkip.contains(autoTapIndex)) autoTapIndex++;
        if(autoTapIndex>=autoTapProbes.size()){ finishAutoTapScan(); return; }
        // reset miss counter when entering bench phase
        if(autoTapBoardProbeCount>0 && autoTapIndex==autoTapBoardProbeCount){
            autoTapConsecutiveMisses=0;
            addScanLog("auto-tap: bench phase");
        }
        if(btnLabel!=null) btnLabel.setText(autoTapIndex+"/"+autoTapProbes.size());
        int[] pt=autoTapProbes.get(autoTapIndex);
        final float px=pt[0], py=pt[1];
        autoTapShotRetry=0;
        addScanLog("auto-tap: probe "+(autoTapIndex+1)+"/"+autoTapProbes.size()+" @"+((int)px)+","+((int)py));
        dispatchTap(px, py, new Runnable(){ public void run(){
            // Wait long enough for the popup to render AND for the screenshot
            // rate limit to clear since the previous shot, whichever is longer.
            long sinceShot=android.os.SystemClock.uptimeMillis()-lastShotMs;
            long wait=Math.max(POPUP_WAIT_MS, MIN_SHOT_GAP_MS-sinceShot);
            autoTapHandler.postDelayed(new Runnable(){ public void run(){ captureProbeShot(); }}, wait);
        }});
    }

    @SuppressWarnings("NewApi")
    private void captureProbeShot(){
        if(!autoScanPending) return;
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null){ finishAutoTapScan(); return; }
        try{
            lastShotMs=android.os.SystemClock.uptimeMillis();
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        Bitmap fullTmp=null; // visible to catch so an early failure can recycle it
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            hb.close();
                            final int sw=hw.getWidth(), sh=hw.getHeight();
                            fullTmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                            final Bitmap full=fullTmp;
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
                        }catch(Exception e){
                            // scanPopupZone never registered its callback, so nothing else will
                            // recycle this full-screen bitmap — do it here or it leaks (OOM over a long scan)
                            if(fullTmp!=null) fullTmp.recycle();
                            addScanLog("ERR auto-tap probe: "+e.getMessage()); advanceAutoTap();
                        }
                    }
                    @Override public void onFailure(int errorCode){
                        // errorCode 3 = ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT: the
                        // rate limiter rejected us. The popup is still on screen, so wait
                        // a full second and retry the SAME probe instead of skipping it.
                        if(errorCode==3 && autoTapShotRetry<3){
                            autoTapShotRetry++;
                            addScanLog("auto-tap shot rate-limited, retry "+autoTapShotRetry);
                            autoTapHandler.postDelayed(new Runnable(){ public void run(){ captureProbeShot(); }}, MIN_SHOT_GAP_MS);
                        } else {
                            addScanLog("ERR auto-tap shot: "+errorCode);
                            advanceAutoTap();
                        }
                    }
                });
        }catch(Exception e){ addScanLog("ERR auto-tap svc: "+e.getMessage()); advanceAutoTap(); }
    }

    private void advanceAutoTap(){
        autoTapIndex++;
        autoTapNudgeStage=0; // each probe gets up to two position retries
        autoTapHandler.postDelayed(new Runnable(){ public void run(){ autoTapNextProbe(); }},PROBE_GAP_MS);
    }

    // Instant Visual ID: try to recognize each detected unit from the board
    // screenshot using sprites learned in earlier scans. Recognized units are
    // recorded immediately (zero taps, zero screenshots); the rest are returned
    // for the normal tap-and-read loop. Match thresholds live in
    // ChampionTemplates.matchBoardSprite and are strict enough to never guess —
    // an uncertain unit simply gets tapped like before.
    private java.util.List<int[]> visualIdPass(Bitmap bmp, java.util.List<int[]> units, boolean opp){
        java.util.List<int[]> toTap=new java.util.ArrayList<>();
        if(ChampionTemplates.boardTemplateCount()==0) return new java.util.ArrayList<>(units);
        int w=bmp.getWidth(), h=bmp.getHeight();
        int cs=Math.max(48, h*9/100); // crop edge: ~9% of screen height, covers the sprite
        int[] btnLoc=new int[2]; int btnW=0,btnH=0;
        if(button!=null){ button.getLocationOnScreen(btnLoc); btnW=button.getWidth(); btnH=button.getHeight(); }
        for(int[] u : units){
            ChampionTemplates.BoardMatch m=null;
            try{
                int x0=u[0]-cs/2, y0=u[1]-cs/2;
                boolean underButton = btnW>0 && x0<btnLoc[0]+btnW && x0+cs>btnLoc[0]
                                            && y0<btnLoc[1]+btnH && y0+cs>btnLoc[1];
                if(!underButton && x0>=0 && y0>=0 && x0+cs<=w && y0+cs<=h){
                    Bitmap crop=Bitmap.createBitmap(bmp,x0,y0,cs,cs);
                    m=ChampionTemplates.matchBoardSprite(crop,opp);
                    crop.recycle();
                }
            }catch(Exception e){ m=null; } // any failure: this unit just gets tapped normally
            if(m==null){ toTap.add(u); continue; }
            autoScanVisualCount++;
            autoTapHits++;
            // star level detected from the star-icon color above this unit's health bar
            int uStars = u.length>2 ? u[2] : 0;
            addScanLog("visual ID: "+m.name+(uStars>0?" "+uStars+"★":"")
                    +" sim="+(int)(m.sim*100)+"% margin="+(int)(m.margin*100)+"%");
            if(opp){
                if(!oppScanResults.containsKey(m.name)){
                    oppScanResults.put(m.name,Math.max(1,uStars));
                    pool.addOpp(m.name,1);
                }
            } else {
                pool.add(m.name,1);
                StringBuilder entry=new StringBuilder(m.name);
                for(int i=0;i<uStars;i++) entry.append("★");
                entry.append(" ≈"); // ≈ marks a visual match
                autoScanResults.add(entry.toString());
            }
        }
        if(autoScanVisualCount>0) buzz();
        return toTap;
    }

    private void applyAutoTapProbeResult(ScreenScanner.ScanResult r, final Bitmap sourceBmp){
        if(!autoScanPending){ if(sourceBmp!=null) sourceBmp.recycle(); return; }
        // (boardProbeCount can legitimately be 0 when Visual ID recognized every
        // board unit — then all remaining probes are bench probes)
        boolean inBenchPhase=(!autoOppMode && autoTapIndex>=autoTapBoardProbeCount);
        if(r.detectedBoardUnit!=null && !r.detectedBoardUnit.isEmpty()){
            final String name=r.detectedBoardUnit;
            final int[] probePos = autoTapIndex<autoTapProbes.size() ? autoTapProbes.get(autoTapIndex) : null;
            // star level: popup OCR first; if it missed the stars, fall back to the
            // star-icon color read above this unit's health bar during detection
            int stars=r.detectedBoardStars;
            if(stars<=0 && probePos!=null && probePos.length>2) stars=probePos[2];
            if(stars<=0) stars=1;
            buzz();
            autoTapConsecutiveMisses=0;
            autoTapHits++;
            // the popup lists the unit's traits — learn the champ→trait mapping
            // (persists across games; powers the SYNERGIES card on the ODDS tab)
            if(!r.popupTraits.isEmpty()) pool.learnTraits(name, r.popupTraits);
            final boolean onBoard = !inBenchPhase;
            if(autoOppMode){
                // opponent scan: record into oppScanResults, increment contest badge
                if(!oppScanResults.containsKey(name)){
                    oppScanResults.put(name,stars);
                    pool.addOpp(name,1);
                    addScanLog("auto-opp: +"+name+" "+stars+"★");
                    if(btnLabel!=null) btnLabel.setText("+"+name.split(" ")[0]);
                }
            } else {
                pool.add(name,1);
                StringBuilder entry=new StringBuilder(name);
                for(int i=0;i<stars;i++) entry.append("★");
                autoScanResults.add(entry.toString());
                addScanLog("auto-tap: +"+name+" "+stars+"★");
                if(btnLabel!=null) btnLabel.setText("+"+name.split(" ")[0]);
            }
            if(sourceBmp!=null){
                final android.graphics.Rect bounds=r.detectedPopupBounds;
                final boolean oppMode=autoOppMode;
                final int spriteSize=Math.max(48, autoTapScreenH*9/100);
                final int gen=autoScanGeneration;
                new Thread(new Runnable(){ public void run(){
                    // popup-portrait template (legacy board-vision path, own board only)
                    if(!oppMode) ChampionTemplates.saveTemplate(OverlayService.this,name,sourceBmp,bounds);
                    // board-sprite template for Instant Visual ID — only from board
                    // probes (bench sprites are framed differently), and only when
                    // the popup isn't covering the unit
                    if(onBoard && probePos!=null && bounds!=null){
                        android.graphics.Rect spriteRect=new android.graphics.Rect(
                                probePos[0]-spriteSize/2, probePos[1]-spriteSize/2,
                                probePos[0]+spriteSize/2, probePos[1]+spriteSize/2);
                        android.graphics.Rect popupGrown=new android.graphics.Rect(bounds);
                        popupGrown.inset(-20,-20);
                        if(!android.graphics.Rect.intersects(spriteRect, popupGrown)){
                            ChampionTemplates.saveBoardTemplate(OverlayService.this,name,sourceBmp,
                                    probePos[0],probePos[1],spriteSize,oppMode);
                            // Duplicate-skip: this same screenshot likely shows other
                            // copies of the champion just learned (2★/3★ units, or
                            // multiple 1-cost copies). Check the remaining un-tapped
                            // board probes against the sprite just learned so those
                            // copies are recorded now instead of needing their own tap.
                            checkDuplicateProbes(sourceBmp,spriteSize,oppMode,gen);
                        }
                    }
                    sourceBmp.recycle();
                }}).start();
            }
        } else {
            if(sourceBmp!=null) sourceBmp.recycle();
            if(inBenchPhase){
                if(r.detectedPopupBounds!=null){
                    addScanLog("auto-tap: non-champion bench popup, skipping");
                } else {
                    autoTapConsecutiveMisses++;
                    if(autoTapConsecutiveMisses>=3){ finishAutoTapScan(); return; }
                }
            } else {
                // BOARD phase miss. An empty tap on a board probe gets up to two
                // position retries before counting as a real miss: first a nudge
                // down (covers the smart-scan bar->body offset estimate), then a
                // nudge up from the original spot (covers calibrated-grid probes
                // whose error direction is unknown).
                boolean trulyEmpty = r.detectedPopupBounds==null;
                if(!autoTapSwitchedToGrid && trulyEmpty && autoTapNudgeStage<2
                        && autoTapIndex<autoTapBoardProbeCount){
                    int shift=Math.max(8, autoTapScreenH*3/100);
                    int[] pt=autoTapProbes.get(autoTapIndex);
                    if(autoTapNudgeStage==0){
                        pt[1]+=shift;
                        addScanLog("auto-tap: empty, retrying lower");
                    } else {
                        pt[1]-=2*shift;
                        addScanLog("auto-tap: empty, retrying higher");
                    }
                    autoTapNudgeStage++;
                    autoTapHandler.postDelayed(new Runnable(){ public void run(){ autoTapNextProbe(); }},PROBE_GAP_MS);
                    return; // same index, do not count a miss
                }
                autoTapConsecutiveMisses++;
                // If the smart positions produce ONLY empties (no popup hits and no
                // visual IDs), the detection was wrong for this screen — switch to
                // the occupancy-filtered calibrated grid instead of wasting the
                // remaining taps. One switch per scan.
                if(autoTapSmartBoard && !autoTapSwitchedToGrid && autoTapHits==0
                        && autoTapConsecutiveMisses>=3
                        && autoTapFallbackBoard!=null && !autoTapFallbackBoard.isEmpty()){
                    autoTapSwitchedToGrid=true;
                    autoTapProbes=new java.util.ArrayList<>(autoTapFallbackBoard);
                    autoTapProbes.addAll(autoTapBenchProbes);
                    autoTapBoardProbeCount=autoTapFallbackBoard.size();
                    autoTapIndex=-1; // advanceAutoTap() below moves to 0
                    autoTapConsecutiveMisses=0;
                    addScanLog("auto-tap: smart positions all empty, switching to calibrated grid ("
                            +autoTapBoardProbeCount+" probes)");
                    if(btnLabel!=null) btnLabel.setText("0/"+autoTapProbes.size());
                } else if(autoTapConsecutiveMisses>=8){
                    if(autoOppMode){
                        // opponent board has no bench — just finish
                        finishAutoTapScan(); return;
                    } else {
                        autoTapIndex=autoTapBoardProbeCount-1;
                        autoTapConsecutiveMisses=0;
                        addScanLog("auto-tap: 8 board misses, jumping to bench");
                    }
                }
            }
        }
        advanceAutoTap();
    }

    // Called from the sprite-learning background thread right after a new board
    // sprite is saved. Crops the same screenshot at every remaining un-tapped
    // board probe and matches it against the (now updated) sprite library — any
    // hit is almost certainly another copy of the champion just confirmed, so it
    // is recorded immediately and that probe is skipped instead of tapped.
    private void checkDuplicateProbes(final Bitmap sourceBmp, final int spriteSize, final boolean oppMode, final int gen){
        final java.util.List<Integer> dupIdx=new java.util.ArrayList<>();
        final java.util.List<ChampionTemplates.BoardMatch> dupMatch=new java.util.ArrayList<>();
        int w=sourceBmp.getWidth(), h=sourceBmp.getHeight();
        // snapshot mutable scan state — this runs on a background thread while the
        // main thread may advance/reassign these between probes
        final java.util.List<int[]> probes=autoTapProbes;
        final int boardCount=Math.min(autoTapBoardProbeCount, probes.size());
        final int fromIdx=autoTapIndex+1;
        for(int i=fromIdx;i<boardCount;i++){
            if(autoTapSkip.contains(i)) continue;
            int[] p=probes.get(i);
            int x0=p[0]-spriteSize/2, y0=p[1]-spriteSize/2;
            if(x0<0||y0<0||x0+spriteSize>w||y0+spriteSize>h) continue;
            Bitmap c=Bitmap.createBitmap(sourceBmp,x0,y0,spriteSize,spriteSize);
            ChampionTemplates.BoardMatch dm=ChampionTemplates.matchBoardSprite(c,oppMode);
            c.recycle();
            if(dm!=null){ dupIdx.add(i); dupMatch.add(dm); }
        }
        if(dupIdx.isEmpty()) return;
        autoTapHandler.post(new Runnable(){ public void run(){
            if(gen!=autoScanGeneration) return; // a new scan started; these results are stale
            boolean any=false;
            for(int k=0;k<dupIdx.size();k++){
                int i=dupIdx.get(k);
                if(autoTapSkip.contains(i)) continue; // already handled by another duplicate pass
                ChampionTemplates.BoardMatch dm=dupMatch.get(k);
                autoTapSkip.add(i);
                if(i>=probes.size()) continue; // probe list was reassigned mid-scan
                int[] p=probes.get(i);
                int dStars=p.length>2?p[2]:0;
                if(oppMode){
                    if(!oppScanResults.containsKey(dm.name)){
                        oppScanResults.put(dm.name,Math.max(1,dStars));
                        pool.addOpp(dm.name,1);
                    }
                } else {
                    pool.add(dm.name,1);
                    StringBuilder e=new StringBuilder(dm.name);
                    for(int s=0;s<dStars;s++) e.append("★");
                    e.append(" ≈");
                    autoScanResults.add(e.toString());
                }
                autoScanVisualCount++; autoTapHits++;
                addScanLog("dup visual ID: "+dm.name+" (probe "+(i+1)+")");
                any=true;
            }
            if(!any) return;
            buzz();
            // if the scan already finished and its results panel is showing, refresh
            // it now so this late-arriving duplicate is reflected in the pool/results
            if(!autoScanPending && panel!=null && mode==0) showPanel();
        }});
    }

    private void finishAutoTapScan(){
        autoScanPending=false;
        autoTapHandler.removeCallbacksAndMessages(null);
        hideStopButton();
        teardownStrayOverlays();
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        if(!autoOppMode){
            // one self-scry commits everything: gold, level, XP, stage (champs
            // were committed per probe as they were identified)
            if(autoScanGold>=0) pool.setGold(autoScanGold);
            if(autoScanLevel>=0){ level=autoScanLevel; pool.setLevel(autoScanLevel); }
            if(autoScanXpNeed>0) pool.setXp(autoScanXpCur, autoScanXpNeed);
            if(!autoScanStage.isEmpty()) pool.setStageRound(autoScanStage);
            // stale-game heuristic, same as the quick scan path
            if(autoScanLevel>=2 && autoScanLevel<=4 && !pool.isEmpty() && autoTapHits>0) newGameHint=true;
            refreshHud();
        } else if(!oppScanResults.isEmpty()){
            // file this enemy board into the next opponent slot (cycles 1-7) so
            // scouting the lobby is just repeated SCRY THE ENEMY presses
            int slot=pool.nextOppSlot();
            pool.setOppBoard(slot, new java.util.LinkedHashMap<>(oppScanResults));
            lastOppSlot=slot;
            addScanLog("auto-opp: filed as OPP "+slot);
        }
        long tookMs=autoScanStartMs>0 ? android.os.SystemClock.uptimeMillis()-autoScanStartMs : 0;
        int found=autoOppMode?oppScanResults.size():autoScanResults.size();
        addScanLog((autoOppMode?"auto-opp":"auto-tap")+": done, "+found+" units ("
                +autoScanVisualCount+" visual) in "+(tookMs/1000)+"."+(tookMs%1000/100)+"s");
        autoOppMode=false;
        buzzDone();
        // one-pass lobby scan: don't reopen the panel between boards — advance to
        // the next calibrated portrait instead.
        if(scanAllMode){ scanAllAdvance(); return; }
        mode=0; showPanel();
    }

    @SuppressWarnings("NewApi")
    private void triggerPopupScan(){
        // fast scan enabled: pull a frame from the live capture, no rate limit,
        // no accessibility round-trip. Falls through to the accessibility
        // screenshot if a frame isn't ready yet (capture still warming up).
        if(scanFastReady){
            Bitmap fast=captureScanFrame();
            if(fast!=null){
                addScanLog("board scan: fast frame");
                processPopupBitmap(fast);
                return;
            }
        }
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
                            processPopupBitmap(bmp);
                        }catch(Exception e){ addScanLog("ERR board scan: "+e.getMessage()); }
                    }
                    @Override public void onFailure(int errorCode){ addScanLog("ERR board scan shot: "+errorCode); }
                });
        }catch(Exception e){ addScanLog("ERR triggerPopupScan: "+e.getMessage()); }
    }

    // shared by the accessibility-screenshot and fast-capture paths: OCR the
    // popup zone and route the result into whichever scan mode is active
    private void processPopupBitmap(Bitmap bmp){
        final Bitmap bmpForTemplate=bmp.copy(Bitmap.Config.ARGB_8888,false);
        new ScreenScanner(this,null).scanBitmap(bmp,
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

    // ---- fast scan: optional persistent screen-recording capture for Board/Opp Scan ----

    private void startFastScanSetup(){
        try{
            ScanPermActivity.scanRequest=true;
            Intent si=new Intent(this,ScanPermActivity.class);
            si.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(si);
        }catch(Exception e){
            ScanPermActivity.scanRequest=false;
            onScanCaptureDenied();
        }
    }

    static void deliverScanProjection(android.media.projection.MediaProjection mp){
        OverlayService s=_instance;
        if(s==null){ try{ mp.stop(); }catch(Exception e){} return; }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->s.onScanCaptureGranted(mp));
    }
    static void deliverScanProjectionDenied(){
        OverlayService s=_instance;
        if(s==null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->s.onScanCaptureDenied());
    }

    private void onScanCaptureGranted(android.media.projection.MediaProjection mp){
        scanProjection=mp;
        try{
            android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w=dm.widthPixels, h=dm.heightPixels;
            scanProjection.registerCallback(new android.media.projection.MediaProjection.Callback(){}, boardHandler);
            scanReader=android.media.ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            scanVd=scanProjection.createVirtualDisplay("scryer-fastscan",w,h,dm.densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                scanReader.getSurface(),null,null);
            scanFastReady=true;
            addScanLog("fast scan: ready "+w+"x"+h);
            Toast.makeText(this,"⛧ Fast scan enabled — Board/Opp Scan now poll instantly",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            addScanLog("ERR fast scan setup: "+e.getMessage());
            releaseScanCapture();
            Toast.makeText(this,"Fast scan setup failed — scans stay on the 1-second path",Toast.LENGTH_LONG).show();
        }
        mode=4; showPanel();
    }
    private void onScanCaptureDenied(){
        Toast.makeText(this,"Fast scan permission denied — scans stay on the 1-second path",Toast.LENGTH_LONG).show();
        mode=4; showPanel();
    }
    private void releaseScanCapture(){
        scanFastReady=false;
        try{ if(scanVd!=null) scanVd.release(); }catch(Exception e){}
        scanVd=null;
        try{ if(scanReader!=null) scanReader.close(); }catch(Exception e){}
        scanReader=null;
        try{ if(scanProjection!=null) scanProjection.stop(); }catch(Exception e){}
        scanProjection=null;
        ScanService.stop(this);
    }
    // grab the latest streamed frame, full-screen, no rate limit — null if none ready yet
    private Bitmap captureScanFrame(){
        if(!scanFastReady||scanReader==null) return null;
        android.media.Image img=null;
        try{
            img=scanReader.acquireLatestImage();
            if(img==null) return null;
            android.media.Image.Plane plane=img.getPlanes()[0];
            int w=img.getWidth(), h=img.getHeight();
            int pixStride=plane.getPixelStride();
            int rowPadding=plane.getRowStride()-pixStride*w;
            Bitmap full=Bitmap.createBitmap(w+rowPadding/pixStride,h,Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(plane.getBuffer());
            return full;
        }catch(Exception e){
            return null;
        }finally{
            if(img!=null){ try{ img.close(); }catch(Exception e2){} }
        }
    }

    // ---- PLANNER SCAN: whole board from one Team Planner snapshot ----
    // The Team Planner's Snapshot button copies every fielded unit into the
    // planner as flat 2D tiles — the one place the game shows the whole board
    // as deterministic art instead of 3D sprites. The scan opens the planner,
    // presses Snapshot, names each tile against the bundled set icons, then
    // closes the planner. Nothing is confirmed, so the game is untouched.

    private static final int PLN_OPEN_WAIT_MS = 1100; // planner open animation
    private static final int PLN_SNAP_WAIT_MS = 900;  // snapshot tiles populate
    private static final int PLN_SLOTS = 10;          // snapshot slot row length

    private interface ShotCb { void onShot(Bitmap bmp); }

    // one full-screen shot via the fast capture when available, else the
    // accessibility screenshot (waits out the 1/sec limit, retries once on it)
    @SuppressWarnings("NewApi")
    private void plannerShot(final ShotCb cb){ plannerShotAttempt(cb, 0); }

    @SuppressWarnings("NewApi")
    private void plannerShotAttempt(final ShotCb cb, final int attempt){
        if(scanFastReady){
            Bitmap fast=captureScanFrame();
            if(fast!=null){ cb.onShot(fast); return; }
        }
        final TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null){ cb.onShot(null); return; }
        long sinceShot=android.os.SystemClock.uptimeMillis()-lastShotMs;
        long wait=Math.max(0, MIN_SHOT_GAP_MS-sinceShot);
        plannerHandler.postDelayed(new Runnable(){ public void run(){
            try{
                lastShotMs=android.os.SystemClock.uptimeMillis();
                svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback(){
                        @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                            try{
                                android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                                Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                                hb.close();
                                Bitmap bmp=hw.copy(Bitmap.Config.ARGB_8888,false);
                                hw.recycle();
                                cb.onShot(bmp);
                            }catch(Exception e){ cb.onShot(null); }
                        }
                        @Override public void onFailure(int errorCode){
                            if(errorCode==3 && attempt<2){
                                plannerHandler.postDelayed(new Runnable(){ public void run(){
                                    plannerShotAttempt(cb, attempt+1);
                                }}, MIN_SHOT_GAP_MS);
                            } else cb.onShot(null);
                        }
                    });
            }catch(Exception e){ cb.onShot(null); }
        }}, wait);
    }

    private void startPlannerScan(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
            return;
        }
        SetIcons.load(this);
        if(SetIcons.champCount()==0){
            Toast.makeText(this,"No set icons in this build — Planner Scan needs an app update",Toast.LENGTH_LONG).show();
            return;
        }
        clearInjecting();
        plannerScanPending=true;
        plannerUnits=null;
        autoScanResults=new java.util.ArrayList<>();
        autoScanGold=-1; autoScanLevel=-1;
        autoScanXpCur=-1; autoScanXpNeed=-1; autoScanStage="";
        autoScanStartMs=android.os.SystemClock.uptimeMillis();
        closePanel();
        if(btnLabel!=null) btnLabel.setText("PLAN");
        addScanLog("planner scan: board shot for positions/stars");
        plannerShot(new ShotCb(){ public void onShot(Bitmap bmp){
            if(!plannerScanPending){ if(bmp!=null) bmp.recycle(); return; }
            if(bmp!=null){
                // best-effort: unit count + star levels from health bars. Names come
                // from the planner; this only pairs stars to tiles, so failure is fine.
                try{ plannerUnits=detectHealthBarUnits(bmp,false); }catch(Exception e){ plannerUnits=null; }
                bmp.recycle();
            }
            addScanLog("planner scan: "+(plannerUnits==null?"no health-bar read"
                    :plannerUnits.size()+" units (health bar)")+", opening planner");
            plannerOpenPhase();
        }});
    }

    private void plannerOpenPhase(){
        if(!plannerScanPending) return;
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        // Make all our overlay views NOT_TOUCHABLE so the injected gestures reach TFT,
        // not our sigil or HUD chips which sit on top in the window stack.
        setOverlaysTouchable(false);
        plannerHandler.postDelayed(new Runnable(){ public void run(){
            dispatchTap(pool.getPln("btn_x")*sw/100f, pool.getPln("btn_y")*sh/100f, new Runnable(){ public void run(){
                setOverlaysTouchable(true);
                plannerHandler.postDelayed(new Runnable(){ public void run(){
                    if(!plannerScanPending) return;
                    setOverlaysTouchable(false);
                    plannerHandler.postDelayed(new Runnable(){ public void run(){
                        dispatchTap(pool.getPln("snap_x")*sw/100f, pool.getPln("snap_y")*sh/100f, new Runnable(){ public void run(){
                            setOverlaysTouchable(true);
                            plannerHandler.postDelayed(new Runnable(){ public void run(){ plannerReadPhase(); }}, PLN_SNAP_WAIT_MS);
                        }});
                    }}, 200);
                }}, PLN_OPEN_WAIT_MS);
            }});
        }}, 200);
    }

    private void plannerReadPhase(){
        if(!plannerScanPending) return;
        addScanLog("planner scan: reading snapshot tiles");
        plannerShot(new ShotCb(){ public void onShot(final Bitmap bmp){
            if(!plannerScanPending){ if(bmp!=null) bmp.recycle(); return; }
            // close the planner right away — nothing was confirmed, so the
            // snapshot is discarded and the board is exactly as it was
            android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            setOverlaysTouchable(false);
            final float closeX=pool.getPln("close_x")*dm.widthPixels/100f;
            final float closeY=pool.getPln("close_y")*dm.heightPixels/100f;
            plannerHandler.postDelayed(new Runnable(){ public void run(){
                dispatchTap(closeX,closeY,new Runnable(){ public void run(){ setOverlaysTouchable(true); }});
            }},200);
            if(bmp==null){ stopPlannerScan("no planner screenshot"); return; }
            plannerProcess(bmp);
        }});
    }

    // crop each snapshot slot along the calibrated first→last line, skip empties
    // by detail (flat empty hexes have almost no texture), and name the rest
    private void plannerProcess(final Bitmap shot){
        new Thread(new Runnable(){ public void run(){
            final java.util.List<String> slotNames=new java.util.ArrayList<>(); // one entry per OCCUPIED slot, null = unknown
            try{
                int w=shot.getWidth(), h=shot.getHeight();
                float x1=pool.getPln("s1_x")*w/100f, y1=pool.getPln("s1_y")*h/100f;
                float xN=pool.getPln("sn_x")*w/100f, yN=pool.getPln("sn_y")*h/100f;
                float dxs=(xN-x1)/(PLN_SLOTS-1), dys=(yN-y1)/(PLN_SLOTS-1);
                float spacing=(float)Math.hypot(dxs,dys);
                if(spacing<8f){
                    shot.recycle();
                    plannerHandler.post(new Runnable(){ public void run(){
                        stopPlannerScan("slot calibration too narrow — recalibrate in SETUP");
                    }});
                    return;
                }
                int cs=Math.max(24,(int)(spacing*0.80f));
                for(int i=0;i<PLN_SLOTS;i++){
                    int cx=(int)(x1+dxs*i), cy=(int)(y1+dys*i);
                    float detail=hexDetail(shot,cx,cy,cs/2,w,h);
                    if(detail<11f){ addScanLog("planner slot "+(i+1)+": empty (detail "+(int)detail+")"); continue; }
                    int inset=cs*12/100;
                    int tx=Math.max(0,cx-cs/2+inset), ty=Math.max(0,cy-cs/2+inset);
                    int ts=Math.min(cs-2*inset, Math.min(w-tx,h-ty));
                    if(ts<16){ slotNames.add(null); continue; }
                    Bitmap tile=Bitmap.createBitmap(shot,tx,ty,ts,ts);
                    SetIcons.IconMatch m=SetIcons.match(tile);
                    if(m!=null){
                        slotNames.add(m.name);
                        addScanLog("planner slot "+(i+1)+": "+m.name+" "+(int)(m.sim*100)
                                +"% (+"+(int)(m.margin*100)+"%)");
                    } else {
                        slotNames.add(null);
                        addScanLog("planner slot "+(i+1)+": unknown — closest "+SetIcons.debugBest(tile));
                    }
                    tile.recycle();
                }
            }catch(Exception e){ addScanLog("ERR planner process: "+e.getMessage()); }
            shot.recycle();
            plannerHandler.post(new Runnable(){ public void run(){ plannerFinish(slotNames); }});
        }}).start();
    }

    private void plannerFinish(java.util.List<String> slotNames){
        if(!plannerScanPending) return;
        plannerScanPending=false;
        plannerHandler.removeCallbacksAndMessages(null);
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        int occupied=slotNames.size();
        int matched=0;
        // stars pair tile-order to health-bar order — only trusted when the counts
        // agree, and they only affect the result list, never the pool counts
        boolean starsUsable = plannerUnits!=null && plannerUnits.size()==occupied;
        for(int i=0;i<occupied;i++){
            String name=slotNames.get(i);
            if(name==null) continue;
            matched++;
            int stars = starsUsable && plannerUnits.get(i).length>2 ? plannerUnits.get(i)[2] : 0;
            pool.add(name,1);
            StringBuilder e=new StringBuilder(name);
            for(int s2=0;s2<stars;s2++) e.append('★');
            e.append(" ≈");
            autoScanResults.add(e.toString());
        }
        int unknown=occupied-matched;
        long tookMs=autoScanStartMs>0?android.os.SystemClock.uptimeMillis()-autoScanStartMs:0;
        addScanLog("planner scan: done, "+matched+" named, "+unknown+" unknown of "+occupied
                +" tiles in "+(tookMs/1000)+"."+(tookMs%1000/100)+"s");
        if(occupied==0){
            Toast.makeText(this,"No snapshot tiles found — did the planner open? Recalibrate in SETUP if not.",Toast.LENGTH_LONG).show();
        } else if(unknown>0){
            Toast.makeText(this,unknown+" unit"+(unknown==1?"":"s")+" not recognized — run SCRY MY BOARD to read them by popup",Toast.LENGTH_LONG).show();
        }
        if(matched>0) buzzDone();
        mode=0; showPanel();
    }

    private void stopPlannerScan(String why){
        plannerScanPending=false;
        plannerHandler.removeCallbacksAndMessages(null);
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("planner scan: "+why);
        mode=0; showPanel();
    }

    // ---- planner calibration: tap-through capture of the planner controls ----
    // Unlike board calibration, the pointed-at controls must actually work while
    // calibrating (the planner has to open before its Snapshot button can be
    // shown), so steps 1, 2 and 5 replay the user's tap into the game.

    private void startPlannerCalibration(){
        plnCalStep=1;
        plnCalBusy=false;
        // If a previous scan left injecting=true (e.g. gesture callback dropped by HyperOS),
        // dispatchTap would silently skip every calibration tap — advancing the wizard without
        // ever opening the planner. Clear it before any calibration tap fires.
        clearInjecting();
        closePanel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            new Runnable(){ public void run(){ showPlnCalOverlay(); }}, 300);
    }

    @SuppressWarnings("deprecation")
    private void showPlnCalOverlay(){
        hidePlnCalView();
        android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        final int sw=dm.widthPixels, sh=dm.heightPixels;
        final float spx=getResources().getDisplayMetrics().scaledDensity;

        plnCalView=new View(OverlayService.this){
            private final android.graphics.Paint bgP=new android.graphics.Paint();
            private final android.graphics.Paint txtP=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            // instruction banner is DRAGGABLE — TFT's planner/menu buttons can sit
            // anywhere, so the user slides this strip off whatever they need to tap.
            float barTop=-1;   // px; -1 until first layout, then defaults to the top
            float barH=0;
            boolean downInBar=false, dragged=false;
            float downY=0, barTopAtDown=0;

            private void ensureLayout(){
                int H=getHeight(); if(H<=0) return;
                barH=H*0.16f;
                if(barTop<0) barTop=H*0.40f;           // start at mid-screen, away from top HUD and bottom shop
                if(barTop>H-barH) barTop=H-barH;
                if(barTop<0) barTop=0;
            }
            private android.graphics.RectF cancelRect(){
                int W=getWidth();
                float h=barH*0.40f, top=barTop+barH*0.54f, w=W*0.34f, left=W*0.5f-w/2f;
                return new android.graphics.RectF(left,top,left+w,top+h);
            }
            @Override protected void onDraw(android.graphics.Canvas canvas){
                ensureLayout();
                int W=getWidth();
                bgP.setColor(0xF00B0709);
                canvas.drawRect(0,barTop,W,barTop+barH,bgP);
                txtP.setStyle(android.graphics.Paint.Style.FILL);
                txtP.setTextAlign(android.graphics.Paint.Align.CENTER);
                String stepMsg, stepSub;
                switch(plnCalStep){
                    case 1: stepMsg="Tap the TEAM PLANNER button"; stepSub="the icon that opens the planner"; break;
                    case 2: stepMsg="Tap the SNAPSHOT button"; stepSub="inside the planner that just opened"; break;
                    case 3: stepMsg="Tap the FIRST snapshot slot"; stepSub="center of the LEFT-most slot in the row"; break;
                    case 4: stepMsg="Tap the LAST slot in that row"; stepSub="center of the RIGHT-most slot"; break;
                    case 5: stepMsg="Tap what CLOSES the planner"; stepSub="the X or back control"; break;
                    default: stepMsg=""; stepSub="";
                }
                txtP.setTextSize(10*spx); txtP.setColor(0xFF7A6B60);
                canvas.drawText("PLANNER CALIBRATION — STEP "+plnCalStep+" OF 5   ·   drag this bar if it covers a button",
                        W/2f, barTop+barH*0.18f, txtP);
                txtP.setTextSize(13*spx); txtP.setColor(0xFFE0D5C0);
                txtP.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(plnCalBusy?"…":stepMsg, W/2f, barTop+barH*0.38f, txtP);
                txtP.setTypeface(android.graphics.Typeface.DEFAULT);
                txtP.setTextSize(9*spx); txtP.setColor(0xFFC9A227);
                canvas.drawText(plnCalBusy?"replaying your tap into the game":stepSub,
                        W/2f, barTop+barH*0.50f, txtP);
                // CANCEL pill
                android.graphics.RectF cr=cancelRect();
                txtP.setColor(0xFF1A0E10); canvas.drawRoundRect(cr,10,10,txtP);
                txtP.setStyle(android.graphics.Paint.Style.STROKE); txtP.setStrokeWidth(2f);
                txtP.setColor(BLOODL); canvas.drawRoundRect(cr,10,10,txtP);
                txtP.setStyle(android.graphics.Paint.Style.FILL);
                txtP.setTextSize(11*spx);
                canvas.drawText("✕ CANCEL", cr.centerX(), cr.centerY()+4*spx, txtP);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent e){
                ensureLayout();
                if(plnCalBusy) return true;
                float vx=e.getX(), vy=e.getY();
                int a=e.getAction();
                boolean inBar = vy>=barTop && vy<=barTop+barH;
                if(a==android.view.MotionEvent.ACTION_DOWN){
                    downInBar=inBar; dragged=false; downY=vy; barTopAtDown=barTop;
                    return true;
                } else if(a==android.view.MotionEvent.ACTION_MOVE){
                    if(downInBar){
                        float dy=vy-downY;
                        if(Math.abs(dy)>12) dragged=true;
                        int H=getHeight();
                        barTop=Math.max(0,Math.min(H-barH, barTopAtDown+dy));
                        invalidate();
                    }
                    return true;
                } else if(a==android.view.MotionEvent.ACTION_UP){
                    if(downInBar){
                        // a tap (not a drag) on the CANCEL pill cancels; otherwise the
                        // banner tap does nothing so it can never be hit by accident
                        if(!dragged && cancelRect().contains(vx,vy)) cancelPlnCal();
                        return true;
                    }
                    // tap OUTSIDE the banner → record this spot and replay it into the game
                    handlePlnCalTap(vx,vy);
                    return true;
                }
                return true;
            }
        };
        plnCalView.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        WindowManager.LayoutParams clp=new WindowManager.LayoutParams(
            sw,sh,0,0,
            Build.VERSION.SDK_INT>=26
                ?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                :WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        clp.gravity=Gravity.TOP|Gravity.LEFT;
        try{ wm.addView(plnCalView,clp); }catch(Exception ex){ plnCalView=null; plnCalStep=0; }
    }

    private void handlePlnCalTap(final float vx, final float vy){
        final View v=plnCalView;
        if(v==null) return;
        int W=v.getWidth(), H=v.getHeight();
        plnCalPts[plnCalStep][0]=Math.round(vx*100/W);
        plnCalPts[plnCalStep][1]=Math.round(vy*100/H);
        boolean passThrough = plnCalStep==1 || plnCalStep==2 || plnCalStep==5;
        if(!passThrough){
            // slot taps are record-only: tapping a planner slot for real could
            // add or remove a planned unit
            advancePlnCal();
            return;
        }
        // Replay this tap into the game so the planner actually opens / snapshots /
        // closes. The OLD approach flipped FLAG_NOT_TOUCHABLE on this same full-screen
        // overlay and dispatched the tap 180ms later — but updateViewLayout is async,
        // so on many devices the flag had not propagated yet and the injected tap
        // landed straight back on this (still-touchable) overlay, which swallowed it.
        // The planner never opened, yet the wizard advanced anyway — exactly the
        // "tapping the icon just goes to the next step" bug. A REMOVED window cannot
        // intercept the gesture, so tear the overlay down completely first, replay,
        // then rebuild it for the next step.
        final boolean closing = plnCalStep==5;
        hidePlnCalView();              // removes the window; sets plnCalBusy=false
        plnCalBusy=true;              // still mid-replay
        setOverlaysTouchable(false);  // lift the sigil/HUD chips out of the way too
        plannerHandler.postDelayed(new Runnable(){ public void run(){
            dispatchTap(vx,vy,new Runnable(){ public void run(){
                // Allow extra time for TFT to open/close the planner before the next
                // calibration overlay appears — 1500ms covers slow animation on older devices.
                plannerHandler.postDelayed(new Runnable(){ public void run(){
                    setOverlaysTouchable(true);
                    plnCalBusy=false;
                    if(closing){ finishPlnCal(); return; }
                    plnCalStep++;              // advance, then rebuild the capture sheet
                    showPlnCalOverlay();
                }}, closing?500:1500);
            }});
        }}, 400);
    }

    private void advancePlnCal(){
        if(plnCalStep>=5){ finishPlnCal(); return; }
        plnCalStep++;
        if(plnCalView!=null) plnCalView.invalidate();
    }

    private void finishPlnCal(){
        String[] keys={null,"btn","snap","s1","sn","close"};
        for(int i=1;i<=5;i++){
            pool.setPln(keys[i]+"_x", plnCalPts[i][0]);
            pool.setPln(keys[i]+"_y", plnCalPts[i][1]);
        }
        plnCalStep=0;
        hidePlnCalView();
        // failsafe: every passThrough step makes overlays untouchable around the
        // replay; if any restore callback was dropped (e.g. handler cleared by a
        // racing scan-stop) the sigil/HUD could be left untouchable. Force them back.
        setOverlaysTouchable(true);
        Toast.makeText(this,"⛧ Planner calibrated — SCRY THE PLANNER is ready",Toast.LENGTH_LONG).show();
        mode=4; showPanel();
    }

    private void cancelPlnCal(){
        plnCalStep=0;
        hidePlnCalView();
        setOverlaysTouchable(true); // failsafe, same reason as finishPlnCal
        mode=4; showPanel();
    }

    private void hidePlnCalView(){
        if(plnCalView!=null){
            // Belt-and-suspenders: flag non-touchable first so even if removeView is
            // delayed by the compositor the upcoming dispatchGesture reaches TFT.
            try{
                WindowManager.LayoutParams lp=(WindowManager.LayoutParams)plnCalView.getLayoutParams();
                lp.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                wm.updateViewLayout(plnCalView,lp);
            }catch(Exception e){}
            try{ wm.removeView(plnCalView); }catch(Exception e){}
            plnCalView=null;
        }
        plnCalBusy=false;
    }

    // ---- on-screen STOP button + stray-overlay failsafe ----

    // A big, draggable STOP button shown while the hunt or an auto-scan runs. One
    // tap stops whatever is active; it can be dragged out of the way and remembers
    // its spot. Reusing this for every long-running mode means the user never has
    // to chase the small floating sigil to halt the auto-tapping.
    @SuppressWarnings("deprecation")
    private void showStopButton(final String label){
        hideStopButton();
        final TextView b=new TextView(this);
        b.setText(label);
        b.setTextColor(BONE); b.setTextSize(14); b.setTypeface(null,android.graphics.Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(box(0xE6B11A22,28,BONE,3));
        b.setPadding(48,26,48,26);
        b.setAlpha(0.97f);
        android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        final WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START;
        lp.x=pool.getHudPos("stop_x", dm.widthPixels*40/100);
        lp.y=pool.getHudPos("stop_y", dm.heightPixels*45/100);
        stopBtnView=b; stopBtnLp=lp;
        b.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty; boolean moved;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){ ix=lp.x; iy=lp.y; tx=e.getRawX(); ty=e.getRawY(); moved=false; return true; }
                else if(a==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-tx), dy=(int)(e.getRawY()-ty);
                    if(Math.abs(dx)>14||Math.abs(dy)>14) moved=true;
                    lp.x=ix+dx; lp.y=iy+dy; try{ wm.updateViewLayout(v,lp); }catch(Exception ex){}
                    return true;
                } else if(a==MotionEvent.ACTION_UP){
                    pool.setHudPos("stop_x",lp.x); pool.setHudPos("stop_y",lp.y);
                    if(!moved) stopActiveMode();
                    return true;
                }
                return false;
            }
        });
        try{ wm.addView(stopBtnView,lp); }catch(Exception ex){ stopBtnView=null; stopBtnLp=null; }
    }
    private void hideStopButton(){
        if(stopBtnView!=null){ try{ wm.removeView(stopBtnView); }catch(Exception e){} stopBtnView=null; }
        stopBtnLp=null;
    }
    // stop whichever long-running mode is active (called by the STOP button)
    private void stopActiveMode(){
        if(scanAllMode){ stopScanAll(); return; }
        if(huntMode){ stopHuntMode(); return; }
        if(boardScanMode){ stopBoardScanMode(); return; }
        if(oppScanMode){ stopOppScanMode(); return; }
        if(autoScanPending){ finishAutoTapScan(); return; }
        hideStopButton();
    }

    // Failsafe: strip any full-screen, touch-capturing calibration overlay that
    // could otherwise be left attached and swallow every touch. WindowManager views
    // outlive the code that added them, so a missed teardown blocks the whole screen
    // even after the overlay is "off". Called when starting a hunt/scan and on stop.
    private void teardownStrayOverlays(){
        hideCalCaptureView();
        hideGridAdjustView();
        hidePlnCalView();
        hideOppCalView();
        hideProbeDots();
        clearInjecting();
        setOverlaysTouchable(true);
    }

    // Make the sigil, HUD chips, and stop button touchable or not. Called around
    // injected planner taps so the gesture reaches TFT instead of landing on our
    // overlay views (which sit on top of the game in the window stack).
    private void setOverlaysTouchable(boolean touchable){
        int f=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if(button!=null && btnLp!=null){
            if(touchable) btnLp.flags&=~f; else btnLp.flags|=f;
            try{ wm.updateViewLayout(button,btnLp); }catch(Exception e){}
        }
        if(hudGoldView!=null && hudGoldLp!=null){
            if(touchable) hudGoldLp.flags&=~f; else hudGoldLp.flags|=f;
            try{ wm.updateViewLayout(hudGoldView,hudGoldLp); }catch(Exception e){}
        }
        if(hudXpView!=null && hudXpLp!=null){
            if(touchable) hudXpLp.flags&=~f; else hudXpLp.flags|=f;
            try{ wm.updateViewLayout(hudXpView,hudXpLp); }catch(Exception e){}
        }
        if(stopBtnView!=null && stopBtnLp!=null){
            if(touchable) stopBtnLp.flags&=~f; else stopBtnLp.flags|=f;
            try{ wm.updateViewLayout(stopBtnView,stopBtnLp); }catch(Exception e){}
        }
    }

    // ---- always-on gold/XP reader ----

    private void startGoldWatch(){
        if(goldWatchOn) return;
        if(Build.VERSION.SDK_INT<31) return;
        goldWatchOn=true;
        goldWatchBusy=false;
        goldWatchIdle=0;
        addScanLog("gold watch: on");
        goldWatchRunnable=new Runnable(){ public void run(){
            if(!goldWatchOn) return;
            goldWatchTick();
            // back off to 6s once the numbers have been static for a while, so a
            // parked screen between rounds barely costs anything; snap back to 2.5s
            // the moment a value changes
            goldWatchHandler.postDelayed(this, goldWatchIdle>=4 ? 6000 : 2500);
        }};
        goldWatchHandler.postDelayed(goldWatchRunnable, 1500);
    }
    private void stopGoldWatch(){
        goldWatchOn=false;
        if(goldWatchRunnable!=null){ goldWatchHandler.removeCallbacks(goldWatchRunnable); goldWatchRunnable=null; }
        goldWatchBusy=false;
    }
    @SuppressWarnings("NewApi")
    private void goldWatchTick(){
        // yield while any capture-heavy mode is running so we don't fight the
        // screenshot rate limit or interrupt a hunt/scan
        if(goldWatchBusy||huntMode||boardScanMode||oppScanMode||autoScanPending) return;
        if(panel!=null) return; // panel covers the game — nothing useful to read
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null) return;
        long sinceShot=android.os.SystemClock.uptimeMillis()-lastShotMs;
        if(sinceShot<MIN_SHOT_GAP_MS) return;
        goldWatchBusy=true;
        lastShotMs=android.os.SystemClock.uptimeMillis();
        // pin the gold-read region to the user's gold HUD pill: read the band just
        // below it (the pill is parked above the game's gold counter). If the HUD is
        // off, pass -1 so the scanner falls back to its corner heuristic.
        final int gCx, gBandTop;
        if(hudGoldView!=null && hudGoldLp!=null && hudGoldView.getHeight()>0){
            gCx = hudGoldLp.x + hudGoldView.getWidth()/2;
            gBandTop = hudGoldLp.y + hudGoldView.getHeight();
        } else { gCx=-1; gBandTop=-1; }
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
                            new ScreenScanner(OverlayService.this,null).scanGoldXp(bmp, gCx, gBandTop,
                                new ScreenScanner.ScanCallback(){
                                    public void onResult(ScreenScanner.ScanResult r){ goldWatchBusy=false; applyHudOnly(r); }
                                    public void onError(String msg){ goldWatchBusy=false; }
                                });
                        }catch(Exception e){ goldWatchBusy=false; }
                    }
                    @Override public void onFailure(int errorCode){ goldWatchBusy=false; }
                });
        }catch(Exception e){ goldWatchBusy=false; }
    }
    // apply ONLY the gold/level/XP from a full scan — never touches pool champs,
    // bench or augments, and never reopens the panel or toasts
    private void applyHudOnly(ScreenScanner.ScanResult r){
        boolean changed=false;
        // only write + repaint when a value actually moved — saves a SharedPreferences
        // commit and a HUD redraw on every static frame, and drives the idle backoff
        if(r.gold>=0 && r.gold!=pool.getGold()){ pool.setGold(r.gold); changed=true; }
        if(r.level>=0 && r.level!=level){ level=r.level; pool.setLevel(r.level); changed=true; }
        if(r.xpNeed>0 && (r.xpCur!=pool.getXpCur() || r.xpNeed!=pool.getXpNeed())){ pool.setXp(r.xpCur, r.xpNeed); changed=true; }
        if(!r.stageRound.isEmpty() && !r.stageRound.equals(pool.getStageRound())){ pool.setStageRound(r.stageRound); changed=true; }
        if(changed){ goldWatchIdle=0; refreshHud(); if(panel!=null && mode==3) refreshEcon(); }
        else goldWatchIdle++;
    }

    // Where the shop strip sits, honoring the SHOP POSITION override (0=auto,
    // 1=top, 2=bottom). Auto = top in landscape, bottom in portrait.
    private boolean shopAtTop(boolean portrait){
        int pos=pool.getShopPos();
        if(pos==1) return true;
        if(pos==2) return false;
        return !portrait;
    }

    // ---- THE HUNT: shop watcher / auto-buy ----
    // Polls the shop strip once a second (the hard screenshot rate limit) and
    // taps any marked champion's shop card the moment it appears. The player
    // rerolls by hand; the hunt does the buying. Stops on sigil tap or after
    // the 2-minute window.
    private void startHuntMode(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
            return;
        }
        if(pool.getHunt().isEmpty()){
            Toast.makeText(this,"Mark a champion first — hold its name in the GRIMOIRE",Toast.LENGTH_LONG).show();
            return;
        }
        closePanel();
        // Ask for a screen-capture token first: MediaProjection streams frames
        // continuously, so the hunt reacts ~3x faster than the accessibility
        // screenshot path. Denying the dialog falls back to 1/sec automatically.
        try{
            ScanPermActivity.huntRequest=true;
            Intent si=new Intent(this,ScanPermActivity.class);
            si.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(si);
        }catch(Exception e){
            ScanPermActivity.huntRequest=false;
            beginHunt(null);
        }
    }

    static void deliverHuntProjection(android.media.projection.MediaProjection mp){
        OverlayService s=_instance;
        if(s==null){ try{ mp.stop(); }catch(Exception e){} return; }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->s.beginHunt(mp));
    }
    static void deliverHuntDenied(){
        OverlayService s=_instance;
        if(s==null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->s.beginHunt(null));
    }

    private void beginHunt(android.media.projection.MediaProjection mp){
        closePanel(); // may have been reopened while the capture dialog was up
        teardownStrayOverlays();
        huntMode=true;
        huntBusy=false; huntOcrBusy=false;
        huntBuys.clear();
        huntCooldown.clear();
        huntPendingBuys.clear();
        huntProjection=mp;
        if(mp!=null) setupHuntCapture(); else huntFast=false;
        if(!huntFast && mp!=null){ // capture setup failed — drop the projection
            releaseHuntCapture();
        }
        addScanLog("hunt: started (runs until stopped), marks="+pool.getHunt()
                +(huntFast?" (fast capture, ~3 checks/sec)":" (1/sec fallback)"));
        Toast.makeText(this,"⛧ The hunt begins"+(huntFast?" — swift eyes":"")
                +" — reroll freely, marked champs are bought for you. Tap STOP to end.",Toast.LENGTH_LONG).show();

        if(btnLabel!=null) btnLabel.setText("HUNT");
        showStopButton("✦ STOP HUNT");

        if(huntFast){
            huntPollRunnable=new Runnable(){ public void run(){
                if(!huntMode) return;
                huntFastPoll();
                boardHandler.postDelayed(this, 300);
            }};
            // give TFT a moment to settle back in front after the capture dialog
            boardHandler.postDelayed(huntPollRunnable, 900);
        } else {
            huntPollRunnable=new Runnable(){ public void run(){
                if(!huntMode) return;
                if(!huntBusy) huntShopScan();
                boardHandler.postDelayed(this, MIN_SHOT_GAP_MS+80);
            }};
            boardHandler.postDelayed(huntPollRunnable, 600);
        }
    }

    private void setupHuntCapture(){
        try{
            android.util.DisplayMetrics dm=new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            int w=dm.widthPixels, h=dm.heightPixels;
            // Android 14 requires a registered callback before createVirtualDisplay()
            huntProjection.registerCallback(new android.media.projection.MediaProjection.Callback(){}, boardHandler);
            huntReader=android.media.ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            huntVd=huntProjection.createVirtualDisplay("scryer-hunt",w,h,dm.densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                huntReader.getSurface(),null,null);
            huntFast=true;
            addScanLog("hunt: fast capture ready "+w+"x"+h);
        }catch(Exception e){
            addScanLog("ERR hunt capture setup: "+e.getMessage()+" — using 1/sec fallback");
            huntFast=false;
        }
    }

    private void releaseHuntCapture(){
        huntFast=false;
        try{ if(huntVd!=null) huntVd.release(); }catch(Exception e){}
        huntVd=null;
        try{ if(huntReader!=null) huntReader.close(); }catch(Exception e){}
        huntReader=null;
        try{ if(huntProjection!=null) huntProjection.stop(); }catch(Exception e){}
        huntProjection=null;
        ScanService.stop(this);
    }

    // Fast path: pull the latest streamed frame (no screenshot call, no rate
    // limit), crop the shop strip, OCR it. Skips the frame if a buy-tap or an
    // OCR pass is still in flight — OCR latency is the only pacing left.
    private void huntFastPoll(){
        if(!huntMode||huntBusy||huntOcrBusy||huntReader==null) return;
        android.media.Image img=null;
        try{
            img=huntReader.acquireLatestImage();
            if(img==null) return;
            android.media.Image.Plane plane=img.getPlanes()[0];
            int w=img.getWidth(), h=img.getHeight();
            int pixStride=plane.getPixelStride();
            int rowPadding=plane.getRowStride()-pixStride*w;
            Bitmap full=Bitmap.createBitmap(w+rowPadding/pixStride,h,Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(plane.getBuffer());
            img.close(); img=null;
            boolean portrait=h>w;
            // The shop strip is NOT at the bottom in landscape: TFT Mobile draws the
            // shop cards along the TOP of the screen in landscape (and near the bottom
            // in portrait). The old fixed 65-85% band read the board/ground in
            // landscape and never saw the shop, so nothing was ever bought. Scanning
            // the shop's real band (not the whole frame) also avoids matching bench
            // unit labels, which would mis-tap the bench instead of a shop card.
            boolean shopTop=shopAtTop(portrait);
            final int cropTop=shopTop? h*5/100 : h*66/100;
            int cropBot   =   shopTop? h*47/100 : h*92/100;
            Bitmap crop=Bitmap.createBitmap(full,0,cropTop,w,cropBot-cropTop);
            full.recycle();
            huntOcrBusy=true;
            new ScreenScanner(this,null).scanShopStrip(crop,w,h,
                new ScreenScanner.ScanCallback(){
                    public void onResult(ScreenScanner.ScanResult r){ huntOcrBusy=false; handleHuntResult(r,cropTop); }
                    public void onError(String msg){ huntOcrBusy=false; }
                });
        }catch(Exception e){
            if(img!=null){ try{ img.close(); }catch(Exception e2){} }
            addScanLog("hunt frame err: "+e.getMessage());
        }
    }

    private void stopHuntMode(){
        huntMode=false;
        if(huntPollRunnable!=null){ boardHandler.removeCallbacks(huntPollRunnable); huntPollRunnable=null; }
        if(huntCountdownRunnable!=null){ boardHandler.removeCallbacks(huntCountdownRunnable); huntCountdownRunnable=null; }
        releaseHuntCapture();
        hideStopButton();
        teardownStrayOverlays();
        if(btnLabel!=null) btnLabel.setText("SCRY");
        buzzDone();
        addScanLog("hunt: stopped, bought "+huntBuys.size()+" "+huntBuys);
        refreshHud();
        mode=0; showPanel();
    }

    @SuppressWarnings("NewApi")
    private void huntShopScan(){
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        if(svc==null){ stopHuntMode(); return; }
        long sinceShot=android.os.SystemClock.uptimeMillis()-lastShotMs;
        if(sinceShot<MIN_SHOT_GAP_MS) return; // poll loop will come back around
        try{
            lastShotMs=android.os.SystemClock.uptimeMillis();
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        if(!huntMode) return;
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            hb.close();
                            Bitmap full=hw.copy(Bitmap.Config.ARGB_8888,false);
                            hw.recycle();
                            int sw=full.getWidth(), sh=full.getHeight();
                            boolean portrait=sh>sw;
                            // shop is at the TOP in landscape, near the bottom in portrait
                            // (overridable via the SHOP POSITION setting)
                            boolean shopTop=shopAtTop(portrait);
                            final int cropTop=shopTop? sh*5/100 : sh*66/100;
                            int cropBot   =   shopTop? sh*47/100 : sh*92/100;
                            Bitmap crop=Bitmap.createBitmap(full,0,cropTop,sw,cropBot-cropTop);
                            full.recycle();
                            new ScreenScanner(OverlayService.this,null).scanShopStrip(crop,sw,sh,
                                new ScreenScanner.ScanCallback(){
                                    public void onResult(ScreenScanner.ScanResult r){ handleHuntResult(r,cropTop); }
                                    public void onError(String msg){ addScanLog("hunt OCR err: "+msg); }
                                });
                        }catch(Exception e){ addScanLog("ERR hunt scan: "+e.getMessage()); }
                    }
                    @Override public void onFailure(int errorCode){ /* rate-limited shot — next poll retries */ }
                });
        }catch(Exception e){ addScanLog("ERR huntShopScan: "+e.getMessage()); }
    }

    private void handleHuntResult(ScreenScanner.ScanResult r, final int cropTop){
        if(!huntMode) return;
        long now=System.currentTimeMillis();
        // Resolve taps from a previous frame: once enough time has passed for the shop
        // to redraw, a marked champ that VANISHED was actually bought (count it); one
        // still sitting in the shop was NOT bought (couldn't afford it) — never count.
        if(!huntPendingBuys.isEmpty()){
            java.util.Iterator<java.util.Map.Entry<String,Long>> it=huntPendingBuys.entrySet().iterator();
            while(it.hasNext()){
                java.util.Map.Entry<String,Long> e=it.next();
                if(now-e.getValue()<HUNT_CONFIRM_MS) continue; // give the shop time to update
                String pn=e.getKey();
                if(!r.shopChampions.contains(pn)){
                    pool.add(pn,1); // a bought copy leaves the pool
                    huntBuys.add(pn);
                    addScanLog("hunt: confirmed buy "+pn);
                    refreshHud();
                } else {
                    addScanLog("hunt: "+pn+" still in shop — not bought (unaffordable)");
                }
                it.remove();
            }
        }
        // The shop band does NOT contain the player's gold counter (it sits bottom-
        // right in landscape, below the shop). The 1-2 digit numbers in this band are
        // the cards' COST badges, which must not be mistaken for gold — so don't sync
        // the HUD from here and buy on faith. TFT simply ignores a tap you can't
        // afford, and the marked champ is retried on the next poll once gold is up.
        final java.util.List<String> toBuy=new java.util.ArrayList<>();
        final java.util.List<int[]> tapAt=new java.util.ArrayList<>();
        for(int i=0;i<r.shopChampions.size();i++){
            String name=r.shopChampions.get(i);
            if(!pool.isHunted(name)) continue;
            Long cd=huntCooldown.get(name);
            if(cd!=null && now<cd) continue; // just bought — card may be stale in this frame
            toBuy.add(name);
            tapAt.add(r.shopChampPos.get(i));
        }
        if(toBuy.isEmpty()) return;
        huntBusy=true;
        huntBuyNext(toBuy, tapAt, 0, cropTop);
    }

    private void huntBuyNext(final java.util.List<String> names, final java.util.List<int[]> pos,
                             final int idx, final int cropTop){
        if(!huntMode || idx>=names.size()){ huntBusy=false; return; }
        final String name=names.get(idx);
        int[] pt=pos.get(idx);
        addScanLog("hunt: buying "+name+" @"+pt[0]+","+(cropTop+pt[1]));
        dispatchTap(pt[0], cropTop+pt[1], new Runnable(){ public void run(){
            // fast capture sees the next shop within ~0.5s, so a short cooldown is
            // enough to outlive the stale frame; the 1/sec path needs more margin
            huntCooldown.put(name, System.currentTimeMillis()+(huntFast?1200:2500));
            // don't count yet — wait until the next frame confirms the card left the
            // shop (handleHuntResult). This prevents counting taps that did nothing
            // because the champ was unaffordable.
            huntPendingBuys.put(name, System.currentTimeMillis());
            buzz();
            if(btnLabel!=null){
                btnLabel.setText("+"+name.split(" ")[0]);
                boardHandler.postDelayed(new Runnable(){ public void run(){
                    if(huntMode&&btnLabel!=null) btnLabel.setText("HUNT");
                }},1200);
            }
            boardHandler.postDelayed(new Runnable(){ public void run(){
                huntBuyNext(names,pos,idx+1,cropTop);
            }},200);
        }});
    }

    private void startOppScanMode(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
            return;
        }
        oppScanMode=true;
        oppScanDeadline=System.currentTimeMillis()+30000;
        oppScanResults=new java.util.LinkedHashMap<>();
        closePanel();
        teardownStrayOverlays();
        showStopButton("✦ STOP SCAN");
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
            boardHandler.postDelayed(this,scanFastReady?900:2500);
        }};
        boardHandler.postDelayed(oppPollRunnable,600);
    }

    private void stopOppScanMode(){
        oppScanMode=false;
        if(oppPollRunnable!=null){ boardHandler.removeCallbacks(oppPollRunnable); oppPollRunnable=null; }
        if(oppCountdownRunnable!=null){ boardHandler.removeCallbacks(oppCountdownRunnable); oppCountdownRunnable=null; }
        hideStopButton();
        teardownStrayOverlays();
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("opp scan: stopped, found "+oppScanResults.size()+" champs");
        mode=0; showPanel();
    }

    // ---- SCRY THE LOBBY: one-pass scan of all calibrated enemy portraits ----
    private void startScanAllOpponents(){
        if(Build.VERSION.SDK_INT<31||TFTAccessibilityService.instance==null){
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show(); return;
        }
        int n=pool.oppPortraitCount();
        if(n<1){ Toast.makeText(this,"Calibrate enemy portraits in SETUP first",Toast.LENGTH_LONG).show(); return; }
        // fresh lobby read: drop stale boards and file from OPP 1
        for(int i=1;i<=7;i++) pool.clearOppBoard(i);
        pool.resetOppCursor();
        scanAllMode=true; scanAllIdx=1; scanAllTotal=n;
        closePanel(); teardownStrayOverlays();
        showStopButton("✦ STOP SCAN");
        addScanLog("scan-all: starting "+n+" opponents");
        scanAllStep();
    }

    // tap the current portrait, wait for the board switch, then sweep that board
    private void scanAllStep(){
        if(!scanAllMode) return;
        if(scanAllIdx>scanAllTotal){ finishScanAll(); return; }
        final int[] pos=pool.getOppPortrait(scanAllIdx);
        if(pos==null){ scanAllIdx++; scanAllStep(); return; }
        // the per-board sweep hides the STOP button on finish; re-show it so the
        // user can abort the lobby pass at any point
        showStopButton("✦ STOP SCAN");
        if(btnLabel!=null) btnLabel.setText("OPP "+scanAllIdx+"/"+scanAllTotal);
        addScanLog("scan-all: tap portrait "+scanAllIdx+" ("+pos[0]+","+pos[1]+")");
        dispatchTap(pos[0],pos[1],new Runnable(){ public void run(){
            boardHandler.postDelayed(new Runnable(){ public void run(){
                if(!scanAllMode) return;
                // reuse the single-opponent board sweep; it ends in finishAutoTapScan,
                // which files the slot and (because scanAllMode is set) advances us
                startAutoOppScan();
            }}, SCANALL_SETTLE_MS);
        }});
    }

    // called from finishAutoTapScan after one board is filed, in scan-all mode
    private void scanAllAdvance(){
        if(!scanAllMode) return;
        scanAllIdx++;
        scanAllStep();
    }

    private void finishScanAll(){
        scanAllMode=false;
        hideStopButton();
        teardownStrayOverlays();
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("scan-all: done, "+pool.oppPortraitCount()+" portraits swept");
        buzzDone();
        mode=0; showPanel();
    }

    private void stopScanAll(){
        scanAllMode=false;
        autoScanPending=false; autoOppMode=false;
        autoTapHandler.removeCallbacksAndMessages(null);
        hideStopButton();
        teardownStrayOverlays();
        setOverlaysTouchable(true);
        if(btnLabel!=null) btnLabel.setText("SCRY");
        addScanLog("scan-all: stopped by user");
        mode=0; showPanel();
    }

    // ---- enemy-portrait calibration: record up to 7 portrait tap positions ----
    private void startOppCalibration(){
        clearInjecting();
        oppCalCount=0;
        pool.clearOppPortraits();   // start clean so re-calibrating doesn't mix old points
        closePanel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            new Runnable(){ public void run(){ showOppCalOverlay(); }}, 250);
    }

    @SuppressWarnings("deprecation")
    private void showOppCalOverlay(){
        hideOppCalView();
        final float spx=getResources().getDisplayMetrics().scaledDensity;
        oppCalView=new View(OverlayService.this){
            private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            private android.graphics.RectF doneRect(){
                int W=getWidth(),H=getHeight(); float h=H*0.07f, w=W*0.30f, top=H*0.90f;
                return new android.graphics.RectF(W*0.55f, top, W*0.55f+w, top+h);
            }
            private android.graphics.RectF cancelRect(){
                int W=getWidth(),H=getHeight(); float h=H*0.07f, w=W*0.30f, top=H*0.90f;
                return new android.graphics.RectF(W*0.15f, top, W*0.15f+w, top+h);
            }
            @Override protected void onDraw(android.graphics.Canvas c){
                int W=getWidth(),H=getHeight();
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(0x66000000); c.drawRect(0,0,W,H,p);
                p.setColor(0xF00B0709); c.drawRect(0,H*0.83f,W,H,p);
                p.setTextAlign(android.graphics.Paint.Align.CENTER);
                p.setColor(0xFFE0D5C0); p.setTextSize(13*spx); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                c.drawText("Tap each ENEMY PORTRAIT  ·  "+oppCalCount+"/7", W/2f, H*0.865f, p);
                p.setTypeface(android.graphics.Typeface.DEFAULT); p.setTextSize(9*spx); p.setColor(0xFFC9A227);
                c.drawText("tap the player health icons in turn, then DONE", W/2f, H*0.885f, p);
                // recorded points
                for(int i=1;i<=oppCalCount;i++){
                    int[] pt=pool.getOppPortrait(i); if(pt==null) continue;
                    p.setStyle(android.graphics.Paint.Style.FILL); p.setColor(BLOODL);
                    c.drawCircle(pt[0],pt[1],14,p);
                    p.setColor(0xFFE0D5C0); p.setTextSize(11*spx); c.drawText(""+i, pt[0], pt[1]+4*spx, p);
                }
                android.graphics.RectF dr=doneRect(), cr=cancelRect();
                p.setStyle(android.graphics.Paint.Style.FILL); p.setColor(0xFF1A1400); c.drawRoundRect(dr,12,12,p);
                p.setStyle(android.graphics.Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(0xFFC9A227); c.drawRoundRect(dr,12,12,p);
                p.setStyle(android.graphics.Paint.Style.FILL); p.setColor(0xFFC9A227); p.setTextSize(12*spx);
                c.drawText("✓ DONE", dr.centerX(), dr.centerY()+4*spx, p);
                p.setStyle(android.graphics.Paint.Style.FILL); p.setColor(0xFF1A0E10); c.drawRoundRect(cr,12,12,p);
                p.setStyle(android.graphics.Paint.Style.STROKE); p.setStrokeWidth(2f); p.setColor(BLOODL); c.drawRoundRect(cr,12,12,p);
                p.setStyle(android.graphics.Paint.Style.FILL); p.setColor(BLOODL); p.setTextSize(12*spx);
                c.drawText("✕ CANCEL", cr.centerX(), cr.centerY()+4*spx, p);
            }
            @Override public boolean onTouchEvent(android.view.MotionEvent e){
                if(e.getAction()!=android.view.MotionEvent.ACTION_DOWN) return true;
                float x=e.getX(), y=e.getY();
                if(doneRect().contains(x,y)){ finishOppCal(); return true; }
                if(cancelRect().contains(x,y)){ cancelOppCal(); return true; }
                if(oppCalCount>=7) return true;
                oppCalCount++;
                pool.setOppPortrait(oppCalCount,(int)x,(int)y);
                invalidate();
                return true;
            }
        };
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        try{ wm.addView(oppCalView,lp); }catch(Exception e){ oppCalView=null; }
    }

    private void finishOppCal(){
        hideOppCalView();
        int n=pool.oppPortraitCount();
        Toast.makeText(this,"⛧ "+n+" enemy portrait"+(n==1?"":"s")+" calibrated — SCRY THE LOBBY is ready",Toast.LENGTH_LONG).show();
        mode=4; showPanel();
    }
    private void cancelOppCal(){
        hideOppCalView();
        pool.clearOppPortraits();
        mode=4; showPanel();
    }
    private void hideOppCalView(){
        if(oppCalView!=null){ try{ wm.removeView(oppCalView); }catch(Exception e){} oppCalView=null; }
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
        // if tap-calibration or grid-adjust was active, cancel it — the overlay will be wrong size
        if(calCaptureView!=null){ calStep=0; hideCalCaptureView(); }
        if(gridAdjustView!=null) hideGridAdjustView();
        if(plnCalView!=null){ plnCalStep=0; hidePlnCalView(); setOverlaysTouchable(true); }
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
        if(hudGoldView != null){
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            hudGoldLp.x = Math.min(hudGoldLp.x, dm.widthPixels - 100);
            hudGoldLp.y = Math.min(hudGoldLp.y, dm.heightPixels - 100);
            hudXpLp.x = Math.min(hudXpLp.x, dm.widthPixels - 100);
            hudXpLp.y = Math.min(hudXpLp.y, dm.heightPixels - 100);
            try{ wm.updateViewLayout(hudGoldView, hudGoldLp); }catch(Exception e){}
            try{ wm.updateViewLayout(hudXpView, hudXpLp); }catch(Exception e){}
        }
    }

    @Override public void onDestroy(){
        super.onDestroy();
        _instance=null;
        if(boardPollRunnable!=null){ boardHandler.removeCallbacks(boardPollRunnable); boardPollRunnable=null; }
        if(boardCountdownRunnable!=null){ boardHandler.removeCallbacks(boardCountdownRunnable); boardCountdownRunnable=null; }
        if(oppPollRunnable!=null){ boardHandler.removeCallbacks(oppPollRunnable); oppPollRunnable=null; }
        if(oppCountdownRunnable!=null){ boardHandler.removeCallbacks(oppCountdownRunnable); oppCountdownRunnable=null; }
        if(huntPollRunnable!=null){ boardHandler.removeCallbacks(huntPollRunnable); huntPollRunnable=null; }
        if(huntCountdownRunnable!=null){ boardHandler.removeCallbacks(huntCountdownRunnable); huntCountdownRunnable=null; }
        // also drop the untracked anonymous boardHandler callbacks (flash restorers,
        // hunt-buy chains, etc.) so none fire after shutdown holding a stale reference
        boardHandler.removeCallbacksAndMessages(null);
        releaseHuntCapture();
        releaseScanCapture();
        autoTapHandler.removeCallbacksAndMessages(null);
        plannerHandler.removeCallbacksAndMessages(null);
        if(glowAnim!=null){ glowAnim.cancel(); glowAnim=null; }
        dimHandler.removeCallbacksAndMessages(null); dimRunnable=null;
        panelDismissHandler.removeCallbacksAndMessages(null); panelDismissRunnable=null;
        scanAllMode=false;
        hideCalCaptureView();
        hidePlnCalView();
        hideOppCalView();
        hideGridAdjustView();
        hideProbeDots();
        hideImageScan();
        hideStopButton();
        clearInjecting();
        stopGoldWatch();
        try{ if(button!=null) wm.removeView(button); }catch(Exception e){}
        try{ if(closeView!=null) wm.removeView(closeView); }catch(Exception e){}
        removeHud();
        closePanel();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
