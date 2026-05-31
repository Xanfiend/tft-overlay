package com.xanfiend.tftoverlay;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    private static final String APP_VERSION = "v1.1";
    // item builder: index of selected components (1-9), -1 = none
    private int itemA = -1, itemB = -1;
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

    @Override public void onCreate(){
        super.onCreate();
        pool = new Pool(this);
        level = pool.getLevel();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        addButton();
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
    private void buzz(){ try{ if(vib!=null) vib.vibrate(18); }catch(Exception e){} }

    private void addButton(){
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER);
        c.setBackground(box(0xF20B0709,40,BLOOD,3)); c.setPadding(28,18,28,18);
        // all-seeing sigil over the wordmark
        TextView g=new TextView(this); g.setText("\u29BF"); g.setTextColor(BLOODL); g.setTextSize(22); g.setGravity(Gravity.CENTER);
        TextView lb=new TextView(this); lb.setText("SCRY"); lb.setTextColor(GOLD); lb.setTextSize(8);
        lb.setGravity(Gravity.CENTER); lb.setLetterSpacing(0.25f); lb.setPadding(0,2,0,0);
        c.addView(g); c.addView(lb); button=c;

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.START; lp.x=20; lp.y=300;
        button.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty; long down; boolean moved;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){ ix=lp.x;iy=lp.y;tx=e.getRawX();ty=e.getRawY();down=System.currentTimeMillis();moved=false; return true; }
                else if(a==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-tx),dy=(int)(e.getRawY()-ty);
                    if(Math.abs(dx)>14||Math.abs(dy)>14){ moved=true; showCloseTarget(true); }
                    lp.x=ix+dx; lp.y=iy+dy; wm.updateViewLayout(button,lp);
                    // highlight the X when the finger is over it
                    if(moved) highlightClose(e.getRawX(), e.getRawY());
                    return true;
                } else if(a==MotionEvent.ACTION_UP){
                    if(moved && overClose(e.getRawX(), e.getRawY())){
                        // dropped on the X -> shut the whole overlay down
                        showCloseTarget(false);
                        stopSelf();
                        return true;
                    }
                    showCloseTarget(false);
                    if(!moved){
                        boolean longpress = System.currentTimeMillis()-down>450;
                        if(longpress) mode=0;
                        else mode = pool.isEmpty() ? 0 : 1;
                        itemA=-1; itemB=-1;
                        showPanel();
                    }
                    return true;
                }
                return false;
            }
        });
        wm.addView(button, lp);
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

    private void closePanel(){ if(panel!=null){ try{wm.removeView(panel);}catch(Exception e){} panel=null; } }

    private void showPanel(){
        closePanel();
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(box(VOID,8,BLOOD,2));
        root.setPadding(22,18,22,18);
        scroll.addView(root);

        // header: title + close
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText(mode==4?"\u229e ITEMS":mode==3?"\u00a7 ECONOMY":mode==2?"\u2738 AUGMENTS":mode==1?"\u2738 CONTEST BOARD":"\u2738 MARK CONTESTED");
        title.setTextColor(BLOODL); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView close=new TextView(this); close.setText("  \u2715"); close.setTextColor(ASH); close.setTextSize(20); close.setPadding(18,0,4,0);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ itemA=-1; itemB=-1; closePanel(); } });
        head.addView(title); head.addView(close);
        root.addView(head);

        // five-way tab row: grid / board / augments / economy / items
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(0,10,0,2);
        String[] tabNames={"\u25A6 grid","\u2261 board","\u2756 augs","\u00A7 econ","\u229E items"};
        for(int t=0;t<5;t++){
            final int tm=t; boolean on=mode==t;
            TextView tab=new TextView(this); tab.setText(tabNames[t]); tab.setGravity(Gravity.CENTER);
            tab.setTextColor(on?BONE:ASH); tab.setTextSize(13); tab.setTypeface(null, on?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            tab.setBackground(box(on?BLOOD:CARD,6,on?BLOODL:EDGE,on?2:1)); tab.setPadding(0,12,0,12);
            LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1f); tl.setMargins(3,0,3,0); tab.setLayoutParams(tl);
            tab.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ mode=tm; showPanel(); } });
            tabs.addView(tab);
        }
        root.addView(tabs);

        // occult divider under the header
        TextView div=new TextView(this);
        div.setText("\u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766");
        div.setTextColor(EDGE); div.setTextSize(9); div.setGravity(Gravity.CENTER); div.setPadding(0,8,0,2);
        root.addView(div);

        // level row (4-10) -- only relevant for grid/board tabs
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

        if(mode==4) buildItems(root);
        else if(mode==3) buildEconomy(root);
        else if(mode==2) buildAugments(root);
        else if(mode==1) buildSummary(root);
        else buildGrid(root);

        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            (int)(getResources().getDisplayMetrics().widthPixels*0.96),
            (int)(getResources().getDisplayMetrics().heightPixels*0.86),
            wtype(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.CENTER; panel=scroll; wm.addView(panel,lp);
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

        TextView tipv=new TextView(this);
        tipv.setText("Tap a name = +1 copy \u00b7 tap the count = \u22121 \u00b7 tap \u25C9 = +1 player");
        tipv.setTextColor(DIM); tipv.setTextSize(10); tipv.setPadding(0,0,0,8);
        root.addView(tipv);

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
            TextView e=new TextView(this);
            e.setText("\n\u29BF  The board is silent.\n\nIn the grid, mark the champions you're contesting or chasing. Scryer reveals how hard each is contested and whether the roll still favors you.");
            e.setTextColor(ASH); e.setTextSize(13); e.setLineSpacing(6,1f); root.addView(e); return;
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
        TextView credit=new TextView(this); credit.setText("@ravriks"); credit.setTextColor(DIM); credit.setTextSize(10); credit.setGravity(Gravity.CENTER);
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

        // gold row
        TextView gh=new TextView(this); gh.setText("◇ GOLD");
        gh.setTextColor(GOLD); gh.setTextSize(11); gh.setTypeface(null, android.graphics.Typeface.BOLD);
        gh.setLetterSpacing(0.1f); gh.setPadding(2,4,0,8); root.addView(gh);

        LinearLayout goldRow=new LinearLayout(this); goldRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView gMinus=makeAdjBtn("−", 0xFF1A0C0E, BLOODL);
        gMinus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setGold(pool.getGold()-1); showPanel(); } });
        TextView gVal=new TextView(this); gVal.setText(gold+"g");
        gVal.setTextColor(GOLD); gVal.setTextSize(28); gVal.setTypeface(null, android.graphics.Typeface.BOLD);
        gVal.setGravity(Gravity.CENTER); gVal.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView gPlus=makeAdjBtn("+", 0xFF1A0C0E, BLOODL);
        gPlus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.setGold(pool.getGold()+1); showPanel(); } });
        goldRow.addView(gMinus); goldRow.addView(gVal); goldRow.addView(gPlus);
        root.addView(goldRow);

        // interest info
        LinearLayout iRow=new LinearLayout(this); iRow.setOrientation(LinearLayout.VERTICAL);
        iRow.setBackground(box(CARD,6,EDGE,1)); iRow.setPadding(12,10,12,10);
        LinearLayout.LayoutParams irl=new LinearLayout.LayoutParams(-1,-2); irl.setMargins(0,10,0,0); iRow.setLayoutParams(irl);
        TextView iLbl=new TextView(this); iLbl.setText("INTEREST");
        iLbl.setTextColor(ASH); iLbl.setTextSize(10); iLbl.setLetterSpacing(0.08f); iRow.addView(iLbl);
        TextView iVal=new TextView(this); iVal.setText("+"+intr+"g per round");
        iVal.setTextColor(BONE); iVal.setTextSize(17); iVal.setTypeface(null, android.graphics.Typeface.BOLD); iRow.addView(iVal);
        String bracketMsg = gold>=50 ? "max interest (50g+)" : "+"+toNext+"g to next bracket";
        TextView iSub=new TextView(this); iSub.setText(bracketMsg);
        iSub.setTextColor(gold>=50?GOLD:ASH); iSub.setTextSize(11); iRow.addView(iSub);
        // interest ladder dots: 10 / 20 / 30 / 40 / 50
        LinearLayout ladder=new LinearLayout(this); ladder.setPadding(0,8,0,0);
        int[] brackets={10,20,30,40,50};
        for(int b : brackets){
            boolean reached=gold>=b; boolean current=(gold/10)*10==b||(b==50&&gold>=50);
            TextView dot=new TextView(this); dot.setGravity(Gravity.CENTER);
            dot.setText(b+"g"); dot.setTextSize(10);
            dot.setTextColor(reached?GOLD:EDGE);
            dot.setTypeface(null, current?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);
            dot.setBackground(box(reached?0xFF1A1400:CARD,4,reached?GOLD:EDGE,reached?2:1));
            dot.setPadding(6,4,6,4);
            LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-2,1f); dl.setMargins(2,0,2,0); dot.setLayoutParams(dl);
            ladder.addView(dot);
        }
        iRow.addView(ladder); root.addView(iRow);

        // streak row
        TextView sh=new TextView(this); sh.setText("◇ STREAK");
        sh.setTextColor(GOLD); sh.setTextSize(11); sh.setTypeface(null, android.graphics.Typeface.BOLD);
        sh.setLetterSpacing(0.1f); sh.setPadding(2,14,0,8); root.addView(sh);

        LinearLayout streakRow=new LinearLayout(this); streakRow.setGravity(Gravity.CENTER_VERTICAL);
        // L button
        TextView sL=makeAdjBtn("L", BLOOD, BONE);
        sL.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); pool.setStreak(s>0?-1:s-1); showPanel();
        }});
        // streak display
        String sText = streak==0 ? "—" : Math.abs(streak)+(streak>0?"W":"L");
        int sColor = streak>0 ? GREEN : (streak<0 ? BLOODL : ASH);
        TextView sDisp=new TextView(this); sDisp.setText(sText);
        sDisp.setTextColor(sColor); sDisp.setTextSize(24); sDisp.setTypeface(null, android.graphics.Typeface.BOLD);
        sDisp.setGravity(Gravity.CENTER); sDisp.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        // W button
        TextView sW=makeAdjBtn("W", 0xFF0D2210, GREEN);
        sW.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int s=pool.getStreak(); pool.setStreak(s<0?1:s+1); showPanel();
        }});
        streakRow.addView(sL); streakRow.addView(sDisp); streakRow.addView(sW);
        root.addView(streakRow);
        if(sBonus>0){
            TextView sBonusTv=new TextView(this); sBonusTv.setText("+"+sBonus+"g streak bonus");
            sBonusTv.setTextColor(ASH); sBonusTv.setTextSize(11); sBonusTv.setPadding(2,4,2,0); root.addView(sBonusTv);
        }

        // expected income card
        LinearLayout incCard=new LinearLayout(this); incCard.setOrientation(LinearLayout.VERTICAL);
        incCard.setBackground(box(CARD,6,BLOODL,2)); incCard.setPadding(14,12,14,12);
        LinearLayout.LayoutParams icl=new LinearLayout.LayoutParams(-1,-2); icl.setMargins(0,14,0,0); incCard.setLayoutParams(icl);
        TextView icH=new TextView(this); icH.setText("EXPECTED NEXT ROUND");
        icH.setTextColor(ASH); icH.setTextSize(10); icH.setLetterSpacing(0.08f); incCard.addView(icH);
        TextView icV=new TextView(this); icV.setText(income+"g");
        icV.setTextColor(GOLD); icV.setTextSize(28); icV.setTypeface(null, android.graphics.Typeface.BOLD); incCard.addView(icV);
        TextView icBreak=new TextView(this);
        icBreak.setText("5 base  +  "+intr+"g interest  +  "+sBonus+"g streak");
        icBreak.setTextColor(ASH); icBreak.setTextSize(11); incCard.addView(icBreak);
        root.addView(incCard);

        // reset econ button (resets only gold+streak, not pool)
        Button resetEcon=new Button(this); resetEcon.setText("RESET ECON"); resetEcon.setAllCaps(false);
        resetEcon.setBackground(box(0xFF1A0C0E,6,BLOOD,2)); resetEcon.setTextColor(ASH); resetEcon.setTextSize(12);
        resetEcon.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            pool.setGold(0); pool.setStreak(0); showPanel();
        }});
        LinearLayout.LayoutParams rel=new LinearLayout.LayoutParams(-1,-2); rel.setMargins(0,16,0,0); resetEcon.setLayoutParams(rel);
        root.addView(resetEcon);
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

    @Override public void onDestroy(){
        super.onDestroy();
        try{ if(button!=null) wm.removeView(button); }catch(Exception e){}
        try{ if(closeView!=null) wm.removeView(closeView); }catch(Exception e){}
        closePanel();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
