package com.xanfiend.tftoverlay;

import android.app.Service;
import android.content.Intent;
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

        // header: title + mode toggle + close
        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText(mode==1?"\u2738 CONTEST BOARD":"\u2738 MARK CONTESTED");
        title.setTextColor(BLOODL); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.08f);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView toggle=new TextView(this);
        toggle.setText(mode==1?"\u25A6 grid":"\u2261 board");
        toggle.setTextColor(GOLD); toggle.setTextSize(13); toggle.setPadding(16,8,16,8);
        toggle.setBackground(box(CARD,6,EDGE,1));
        toggle.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ mode = mode==1?0:1; showPanel(); } });
        TextView close=new TextView(this); close.setText("  \u2715"); close.setTextColor(ASH); close.setTextSize(20); close.setPadding(18,0,4,0);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ closePanel(); } });
        head.addView(title); head.addView(toggle); head.addView(close);
        root.addView(head);

        // occult divider under the header
        TextView div=new TextView(this);
        div.setText("\u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766 \u00b7 \u22c6 \u00b7 \u2766");
        div.setTextColor(EDGE); div.setTextSize(9); div.setGravity(Gravity.CENTER); div.setPadding(0,8,0,2);
        root.addView(div);

        // level row (4-10)
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

        if(mode==1) buildSummary(root); else buildGrid(root);

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
        done.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ closePanel(); } });
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

        for(final String name:names){
            int co=Pool.costOf(name); int s=pool.seenCount(name); int rem=pool.remaining(name);
            int players=pool.oppCount(name);
            int poolSize=Pool.SIZE[co];
            double ch=rerollChance(name)*100.0;
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

            // right side: odds now + odds by a chunk of gold
            LinearLayout vbox=new LinearLayout(this); vbox.setOrientation(LinearLayout.VERTICAL); vbox.setGravity(Gravity.CENTER);
            vbox.setPadding(8,0,8,0);
            TextView pct=new TextView(this); pct.setText(rem<=0?"0%":String.format("%.0f%%",ch));
            pct.setTextColor(rem<=0?DIM:BONE); pct.setTextSize(17); pct.setTypeface(null, android.graphics.Typeface.BOLD); pct.setGravity(Gravity.CENTER);
            // "by ~30g" estimate: chance to see at least one across ~6 shops (30g ~ 6 rolls)
            double byGold = rem<=0 ? 0 : (1.0 - Math.pow(1.0 - rerollChance(name), 6))*100.0;
            TextView pl=new TextView(this); pl.setText(rem<=0?"gone":String.format("~%.0f%% / 30g", byGold));
            pl.setTextColor(ASH); pl.setTextSize(9); pl.setGravity(Gravity.CENTER);
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
    }

    @Override public void onDestroy(){
        super.onDestroy();
        try{ if(button!=null) wm.removeView(button); }catch(Exception e){}
        try{ if(closeView!=null) wm.removeView(closeView); }catch(Exception e){}
        closePanel();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
