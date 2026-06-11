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
    private static final String APP_VERSION = "v1.60";
    // item builder: index of selected components (1-9), -1 = none
    private int itemA = -1, itemB = -1;
    // guide tab sub-selection: 0 = augments, 1 = items
    private int guideTab = 0;
    // god tracker: which slot (1/2) is currently showing the god picker, 0 = none
    private int godPickSlot = 0;
    // probe dots overlay: shows all scan tap positions over TFT for calibration
    private View probeDotsView = null;
    private final android.os.Handler probeDotsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final String RELEASES_URL = "https://github.com/Xanfiend/tft-overlay/releases/latest";

    // Shop odds live in SetData so set updates stay one-file
    private static final int[][] ODDS = SetData.ODDS;
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

    // ---- in-game HUD: small persistent overlay showing live gold income + gold-to-level ----
    private View hudView;
    private WindowManager.LayoutParams hudLp;
    private TextView hudGoldTv, hudLevelTv;
    private final android.os.Handler hudHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable hudTick;
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

    // adjust-grid: full-screen overlay showing the live probe grid with draggable
    // corner handles — visual calibration without blind taps
    private View gridAdjustView = null;

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
        if(pool.getHudEnabled()) addHud();
        new Thread(new Runnable(){ public void run(){ ChampionTemplates.load(OverlayService.this); }}).start();
    }
    @Override public int onStartCommand(Intent i, int f, int id){ return START_STICKY; }

    private int wtype(){
        return Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                         : WindowManager.LayoutParams.TYPE_PHONE;
    }
    // subtle vertical gradient (lighter top, darker bottom) on every box, with a
    // brightened pressed state so buttons visibly react to touch
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
        t.setTextColor(color); t.setTextSize(11); t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setLetterSpacing(0.12f);
        h.addView(t);
        View rule=new View(this);
        GradientDrawable rg=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{(color&0x00FFFFFF)|0x77000000, color&0x00FFFFFF});
        LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(0,2,1f); rl.setMargins(10,3,2,0);
        rule.setLayoutParams(rl); rule.setBackground(rg);
        h.addView(rule);
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
    private void buzz(){ try{ if(pool.getHaptic() && vib!=null) vib.vibrate(18); }catch(Exception e){} }
    // distinct double pulse so a finished scan can be felt without looking at the screen
    @SuppressWarnings("deprecation")
    private void buzzDone(){
        try{
            if(pool.getHaptic() && vib!=null) vib.vibrate(new long[]{0,35,110,35},-1);
        }catch(Exception e){}
    }

    private void addButton(){
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.setBackground(box(0xF20B0709,40,BLOOD,3)); c.setPadding(28,18,28,18);
        // all-seeing sigil over the wordmark
        TextView g=new TextView(this); g.setText("\u29BF"); g.setTextColor(BLOODL); g.setTextSize(22); g.setGravity(Gravity.CENTER);
        btnLabel=new TextView(this); btnLabel.setText("SCRY"); btnLabel.setTextColor(GOLD); btnLabel.setTextSize(8);
        btnLabel.setGravity(Gravity.CENTER); btnLabel.setLetterSpacing(0.25f); btnLabel.setPadding(0,2,0,0);
        c.addView(g); c.addView(btnLabel);

        // soft radial glow ring behind the sigil, slow pulse so the floating
        // button feels alive without being distracting
        FrameLayout fc=new FrameLayout(this);
        glowView=new View(this);
        GradientDrawable glowD=new GradientDrawable();
        glowD.setShape(GradientDrawable.OVAL);
        glowD.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowD.setGradientRadius(85f);
        glowD.setColors(new int[]{0x66C1121F, 0x00C1121F});
        glowView.setBackground(glowD);
        FrameLayout.LayoutParams glp=new FrameLayout.LayoutParams(170,170); glp.gravity=Gravity.CENTER;
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
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        btnLp.gravity=Gravity.TOP|Gravity.START; btnLp.x=20; btnLp.y=300;
        button.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty; long down; boolean moved;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){
                    ix=btnLp.x;iy=btnLp.y;tx=e.getRawX();ty=e.getRawY();down=System.currentTimeMillis();moved=false;
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
        button.animate().scaleX(1f).scaleY(1f).setDuration(280)
            .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
    }

    // Small persistent HUD: shows live gold + projected income/round and gold
    // needed to hit the next level, both derived from values already tracked by
    // scans/manual corrections (no extra OCR or polling needed). Draggable like
    // the main sigil; position persists across restarts.
    private void addHud(){
        if(hudView!=null) return;
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(box(0xE6160B0D,8,GOLD,1)); c.setPadding(12,8,12,8);
        hudGoldTv=new TextView(this); hudGoldTv.setTextColor(GOLD); hudGoldTv.setTextSize(11);
        hudGoldTv.setTypeface(null,android.graphics.Typeface.BOLD); hudGoldTv.setLetterSpacing(0.04f);
        hudLevelTv=new TextView(this); hudLevelTv.setTextColor(BONE); hudLevelTv.setTextSize(10);
        hudLevelTv.setPadding(0,2,0,0);
        c.addView(hudGoldTv); c.addView(hudLevelTv);
        hudView=c;
        hudView.setAlpha(pool.getAlpha());

        hudLp=new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        hudLp.gravity=Gravity.TOP|Gravity.START;
        hudLp.x=pool.getHudX(); hudLp.y=pool.getHudY();

        hudView.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){
                    ix=hudLp.x; iy=hudLp.y; tx=e.getRawX(); ty=e.getRawY();
                    return true;
                } else if(a==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-tx), dy=(int)(e.getRawY()-ty);
                    hudLp.x=ix+dx; hudLp.y=iy+dy;
                    try{ wm.updateViewLayout(hudView,hudLp); }catch(Exception ex){}
                    return true;
                } else if(a==MotionEvent.ACTION_UP){
                    pool.setHudPos(hudLp.x, hudLp.y);
                    return true;
                }
                return false;
            }
        });

        try{ wm.addView(hudView, hudLp); }catch(Exception e){}
        refreshHud();

        hudTick=new Runnable(){ public void run(){
            refreshHud();
            hudHandler.postDelayed(this, 3000);
        }};
        hudHandler.postDelayed(hudTick, 3000);
    }
    private void removeHud(){
        if(hudTick!=null){ hudHandler.removeCallbacks(hudTick); hudTick=null; }
        try{ if(hudView!=null) wm.removeView(hudView); }catch(Exception e){}
        hudView=null; hudGoldTv=null; hudLevelTv=null;
    }
    // recompute HUD text from current pool state — gold income projection and
    // gold needed to reach the next level use the same math as the GOLD tab
    private void refreshHud(){
        if(hudGoldTv==null) return;
        int gold=pool.getGold();
        int streak=pool.getStreak();
        int income=Pool.expectedIncome(gold, streak);
        hudGoldTv.setText("⛧ "+gold+"g  →  +"+income+"g/rnd");

        int lvl=pool.getLevel();
        int xpNeed=pool.getXpNeed();
        int xpCur=pool.getXpCur();
        int trustedNeed=Pool.xpToNext(lvl);
        int into=(xpNeed==trustedNeed && xpCur>=0) ? xpCur : 0;
        int goldToLvl=Pool.goldToNextLevel(lvl, into);
        if(trustedNeed<=0){
            hudLevelTv.setText("Lv"+lvl+" — max");
        } else {
            hudLevelTv.setText("Lv"+lvl+" → "+goldToLvl+"g for Lv"+(lvl+1));
        }
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

        boolean reuse=panel!=null;
        LinearLayout root;
        if(panel==null){
            // first open: create the window and add to WindowManager
            ScrollView scroll=new ScrollView(this);
            root=new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackground(box(VOID,12,BLOOD,2));
            root.setPadding(22,18,22,18);
            scroll.addView(root);
            panel=scroll;
            panelLp=new WindowManager.LayoutParams(
                (int)(getResources().getDisplayMetrics().widthPixels*0.96),
                (int)(getResources().getDisplayMetrics().heightPixels*0.86),
                wtype(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
            panelLp.gravity=Gravity.CENTER;
            // tap anywhere outside the panel (on the game) to dismiss it
            panel.setOnTouchListener(new View.OnTouchListener(){
                public boolean onTouch(View v, MotionEvent e){
                    if(e.getAction()==MotionEvent.ACTION_OUTSIDE){ itemA=-1; itemB=-1; closePanel(); return true; }
                    return false;
                }
            });
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
        TextView title=new TextView(this);
        title.setText(mode==4?"\u2699 SETUP":mode==3?"\u00a7 GOLD":mode==2?"\u229e GUIDE":mode==1?"\u2738 ODDS":"\u2738 POOL");
        title.setTextColor(BLOODL); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView verTv=new TextView(this); verTv.setText(APP_VERSION);
        verTv.setTextColor(DIM); verTv.setTextSize(9); verTv.setPadding(0,0,12,0);
        TextView close=new TextView(this); close.setText("\u2715"); close.setTextColor(BONE); close.setTextSize(18);
        close.setGravity(Gravity.CENTER); close.setBackground(box(BLOOD,6,BLOODL,2)); close.setPadding(22,14,22,14);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        pressFeedback(close);
        head.addView(sigil); head.addView(title); head.addView(verTv); head.addView(close);
        root.addView(head);

        // tab row \u2014 ordered by in-game frequency of use
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,10,0,2);
        int[] tabModes={0,1,2,3,4}; // Pool | Odds | Guide | Gold | Setup
        String[] tabGlyphs={"\u25a6","\u2738","\u229e","\u00a7","\u2699"};
        String[] tabNames={"POOL","ODDS","GUIDE","GOLD","SETUP"};
        for(int t=0;t<5;t++){
            final int tm=tabModes[t]; boolean on=mode==tm;
            LinearLayout tabWrap=new LinearLayout(this); tabWrap.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams twl=new LinearLayout.LayoutParams(0,-2,1f); twl.setMargins(2,0,2,0); tabWrap.setLayoutParams(twl);
            TextView tab=new TextView(this); tab.setText(tabGlyphs[t]+"\n"+tabNames[t]); tab.setGravity(Gravity.CENTER);
            tab.setTextColor(on?BONE:ASH); tab.setTextSize(9); tab.setLetterSpacing(0.05f);
            tab.setLineSpacing(2,1f);
            tab.setTypeface(null, on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            tab.setBackground(box(on?BLOOD:CARD,6,on?BLOODL:EDGE,on?2:1)); tab.setPadding(0,11,0,11);
            tab.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ mode=tm; showPanel(); } });
            pressFeedback(tab);
            tabWrap.addView(tab);
            // gold underline marks the active tab
            View underline=new View(this);
            LinearLayout.LayoutParams ul=new LinearLayout.LayoutParams(-1,on?3:0); ul.setMargins(8,3,8,0);
            underline.setLayoutParams(ul);
            underline.setBackground(box(on?GOLD:0,2,0,0));
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

        if(mode==4) buildSettings(content);
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
                if(!accAvail){ try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){} return; }
                startAutoTapScan();
            }});

            LinearLayout aoBtn=ritualBtn("\u25C9 SCRY THE ENEMY","scout a foe's board",
                    accAvail?CARD:0xFF0D0909, accAvail?GOLD:DIM, accAvail);
            LinearLayout.LayoutParams aol=new LinearLayout.LayoutParams(0,-2,1f); aol.setMargins(3,0,0,0); aoBtn.setLayoutParams(aol);
            aoBtn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                if(!accAvail){ try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){} return; }
                startAutoOppScan();
            }});

            row1.addView(asBtn); row1.addView(aoBtn);
            root.addView(row1);

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
        addSecHdr(root, "REVEALED · YOUR BOARD", GOLD);
            if(autoScanGold>=0||autoScanLevel>=0){
                StringBuilder glSb=new StringBuilder();
                if(autoScanLevel>=0) glSb.append("Lv ").append(autoScanLevel);
                if(autoScanGold>=0){ if(glSb.length()>0) glSb.append("  ·  "); glSb.append(autoScanGold).append("g"); }
                TextView glTv=new TextView(this); glTv.setText(glSb.toString());
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
        addSecHdr(root, "REVEALED · ENEMY", GOLD);
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
        String[] howItems={"The rite records all \u2014 touch these only to amend it","Tap a name = +1 copy seen","Tap the count badge = \u22121 copy","Tap the \u25C9 badge = +1 player contesting"};
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

        for(int cost=1;cost<=5;cost++){
        addSecHdr(root, cost+"-COST", COSTC[cost]);

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
        // set label
        TextView lbl=new TextView(this); lbl.setText(AugmentData.SET_LABEL);
        lbl.setTextColor(DIM); lbl.setTextSize(9); lbl.setPadding(2,0,0,8); root.addView(lbl);

        // ✦ YOUR AUGMENTS — remembered from scans that spotted them on screen
        java.util.List<String> mine=pool.getMyAugments();
        if(!mine.isEmpty()){
            addSecHdr(root, "YOUR AUGMENTS", GOLD);
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
        addSecHdr(root, "AUGMENTS", GOLD);

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
            int t=0; for(String n:Pool.CHAMPS[co]) t+=pool.remaining(n);
            t-=pool.getJunk(co); tierTotal[co]=Math.max(0,t);
        }
        int rollGold=Math.min(60, pool.getGold());

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
        legend.setText("% = your odds to hit on a roll at this level \u00b7 you call it");
        legend.setTextColor(DIM); legend.setTextSize(10); legend.setPadding(2,8,2,0); root.addView(legend);

        // death-return reminder: eliminated players' units go back to the pool
        TextView deathTip=new TextView(this);
        deathTip.setText("\u2620 when a player dies, their units return to the pool. tap a count down to free those copies");
        deathTip.setTextColor(GOLD); deathTip.setTextSize(10); deathTip.setPadding(2,6,2,0); root.addView(deathTip);

        Button wipe=new Button(this); wipe.setText("RESET ALL"); wipe.setAllCaps(false);
        wipe.setBackground(box(0xFF1A0C0E,6,BLOOD,2)); wipe.setTextColor(BLOODL); wipe.setTextSize(13);
        wipe.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.reset(); refreshHud(); showPanel(); } });
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
        TextView goldHint=new TextView(this); goldHint.setText("manual correction  ·  tap ±1  ·  hold to repeat");
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
        TextView streakWhy=new TextView(this); streakWhy.setText("streak has no on-screen number to scry — set it by hand");
        streakWhy.setTextColor(DIM); streakWhy.setTextSize(9); streakWhy.setPadding(2,2,2,0); root.addView(streakWhy);

        // expected income card
        LinearLayout incCard=new LinearLayout(this); incCard.setOrientation(LinearLayout.VERTICAL);
        incCard.setBackground(box(CARD,6,BLOODL,2)); incCard.setPadding(14,12,14,12);
        LinearLayout.LayoutParams icl=new LinearLayout.LayoutParams(-1,-2); icl.setMargins(0,14,0,0); incCard.setLayoutParams(icl);
        TextView icH=new TextView(this); icH.setText("EXPECTED NEXT ROUND");
        icH.setTextColor(ASH); icH.setTextSize(10); icH.setLetterSpacing(0.08f); incCard.addView(icH);
        econIncomeTv=new TextView(this); econIncomeTv.setText(income+"g");
        econIncomeTv.setTextColor(GOLD); econIncomeTv.setTextSize(28); econIncomeTv.setTypeface(null, android.graphics.Typeface.BOLD); incCard.addView(econIncomeTv);
        econBreakTv=new TextView(this); econBreakTv.setText("5 base  +  "+intr+"g interest  +  "+sBonus+"g streak"+(streak>0?"  +  1g win":""));
        econBreakTv.setTextColor(ASH); econBreakTv.setTextSize(11); incCard.addView(econBreakTv);
        root.addView(incCard);

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
                ? ("xp "+into+"/"+xpTable+" (scryed)  ·  4g = 4 xp  ·  +2 xp passive each round")
                : ("xp 0/"+xpTable+" assumed — scry to read your real xp  ·  4g = 4 xp"));
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

        // traits section
        TextView tdiv=new TextView(this);
        tdiv.setText("❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦");
        tdiv.setTextColor(EDGE); tdiv.setTextSize(9); tdiv.setGravity(Gravity.CENTER); tdiv.setPadding(0,14,0,4);
        root.addView(tdiv);

        addSecHdr(root, "TRAITS", GOLD);

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
                try{ Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); }catch(Exception e){}
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
                if(panel!=null) panel.setAlpha(av);
                if(hudView!=null) hudView.setAlpha(av);
                alphaLabel.setText((progress+20)+"%");
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar){}
            public void onStopTrackingTouch(android.widget.SeekBar bar){}
        });
        root.addView(alphaBar);

        addSecHdr(root, "HAPTIC", GOLD);

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

        addSecHdr(root, "IN-GAME HUD", GOLD);

        TextView hudHint=new TextView(this);
        hudHint.setText("Small overlay showing gold + projected income/round and gold needed for your next level. Drag it anywhere on screen.");
        hudHint.setTextColor(DIM); hudHint.setTextSize(10); hudHint.setPadding(2,0,0,8); root.addView(hudHint);

        boolean curHud=pool.getHudEnabled();
        LinearLayout hudRow=new LinearLayout(this); hudRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hudRowLp=new LinearLayout.LayoutParams(-1,-2); hudRowLp.setMargins(0,0,0,14); hudRow.setLayoutParams(hudRowLp);
        String[] hudLabels={"ON","OFF"}; boolean[] hudVals={true,false};
        for(int i=0;i<2;i++){
            final boolean hv=hudVals[i];
            TextView btn=new TextView(this); btn.setText(hudLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(curHud==hv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.setHudEnabled(hv);
                if(hv) addHud(); else removeHud();
                showPanel();
            }});
            hudRow.addView(btn);
        }
        root.addView(hudRow);

        addSecHdr(root, "OPEN TAB", GOLD);

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

        boolean curSmart=pool.getSmartScan();
        LinearLayout ssRow=new LinearLayout(this); ssRow.setGravity(Gravity.CENTER_VERTICAL);
        String[] ssLabels={"ON","OFF"}; boolean[] ssVals={true,false};
        for(int i=0;i<2;i++){
            final boolean sv=ssVals[i];
            TextView btn=new TextView(this); btn.setText(ssLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(curSmart==sv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.setSmartScan(sv); showPanel();
            }});
            ssRow.addView(btn);
        }
        root.addView(ssRow);

        addSecHdr(root, "INSTANT VISUAL ID", GOLD);

        TextView vidInfo=new TextView(this);
        int sprites=ChampionTemplates.boardTemplateCount();
        vidInfo.setText("Every unit read by popup teaches the app how that champion looks on your board. "
                +"Next scan, learned units are recognized straight from the screenshot with no tapping, "
                +"so the scan gets faster every game. Only sure matches skip the tap. Anything uncertain "
                +"is tapped and read as usual. Learned sprites so far: "+sprites+".");
        vidInfo.setTextColor(ASH); vidInfo.setTextSize(10); vidInfo.setPadding(2,0,0,6); root.addView(vidInfo);

        boolean curVid=pool.getVisualId();
        LinearLayout vidRow=new LinearLayout(this); vidRow.setGravity(Gravity.CENTER_VERTICAL);
        for(int i=0;i<2;i++){
            final boolean sv=ssVals[i];
            TextView btn=new TextView(this); btn.setText(ssLabels[i]);
            btn.setTextColor(BONE); btn.setTextSize(12); btn.setGravity(Gravity.CENTER);
            btn.setPadding(0,10,0,10);
            boolean sel=(curVid==sv);
            btn.setBackground(box(sel?BLOOD:CARD,6,sel?BLOODL:EDGE,sel?2:1));
            LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1f); lp2.setMargins(0,0,4,0); btn.setLayoutParams(lp2);
            btn.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                pool.setVisualId(sv); showPanel();
            }});
            vidRow.addView(btn);
        }
        root.addView(vidRow);

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

        TextView tapCalBtn=new TextView(this); tapCalBtn.setText("TAP TO CALIBRATE (recommended)");
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
                canvas.drawText("Corners shape the board · middle rings space the rows · end rings stretch the bench", W/2f, barH*0.78f, p);

                // bottom bar: SAVE | CANCEL
                float btnTop=H*0.90f;
                p.setColor(0xF00B0709);
                canvas.drawRect(0,btnTop,W,H,p);
                p.setColor(0xFF39FF14); p.setTextSize(14*spx);
                canvas.drawText("SAVE", W*0.25f, (btnTop+H)/2f+5*spx, p);
                p.setColor(0xFFC1121F);
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
        mode=4; showPanel();
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
        if(btnLabel!=null) btnLabel.setText("...");
        addScanLog("auto-tap: starting, getting screen size");
        TFTAccessibilityService svc=TFTAccessibilityService.instance;
        try{
            lastShotMs=android.os.SystemClock.uptimeMillis();
            svc.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(),
                new AccessibilityService.TakeScreenshotCallback(){
                    @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            final int sw=hw.getWidth(), sh=hw.getHeight();
                            final Bitmap goldLvlBmp=hw.copy(Bitmap.Config.ARGB_8888,false);
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
                        }catch(Exception e){ autoScanPending=false; addScanLog("ERR auto-tap init: "+e.getMessage()); mode=0; showPanel(); }
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
                        try{
                            android.hardware.HardwareBuffer hb=result.getHardwareBuffer();
                            Bitmap hw=Bitmap.wrapHardwareBuffer(hb,null);
                            final int sw=hw.getWidth(), sh=hw.getHeight();
                            final Bitmap bmp=hw.copy(Bitmap.Config.ARGB_8888,false);
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
                        }catch(Exception e){ autoScanPending=false; addScanLog("ERR auto-opp init: "+e.getMessage()); mode=0; showPanel(); }
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
            topLeft  = w * pool.getBoardTopLeftPct()  / 100;
            topRight = w * pool.getBoardTopRightPct() / 100;
            botLeft  = w * pool.getBoardBotLeftPct()  / 100;
            botRight = w * pool.getBoardBotRightPct() / 100;
            benchY   = h * pool.getBenchYPct()        / 100;
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
        int bodyDrop=Math.max(10, h*4/100); // bar bottom -> unit body
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
            int tapY=maxY+bodyDrop;
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
            Toast.makeText(this,accErrorMsg(),Toast.LENGTH_LONG).show();
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
        // if tap-calibration or grid-adjust was active, cancel it — the overlay will be wrong size
        if(calCaptureView!=null){ calStep=0; hideCalCaptureView(); }
        if(gridAdjustView!=null) hideGridAdjustView();
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
        if(hudView != null && hudLp != null){
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            hudLp.x = Math.min(hudLp.x, dm.widthPixels - 100);
            hudLp.y = Math.min(hudLp.y, dm.heightPixels - 100);
            try{ wm.updateViewLayout(hudView, hudLp); }catch(Exception e){}
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
        if(glowAnim!=null){ glowAnim.cancel(); glowAnim=null; }
        hideCalCaptureView();
        hideGridAdjustView();
        hideProbeDots();
        try{ if(button!=null) wm.removeView(button); }catch(Exception e){}
        try{ if(closeView!=null) wm.removeView(closeView); }catch(Exception e){}
        removeHud();
        closePanel();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
