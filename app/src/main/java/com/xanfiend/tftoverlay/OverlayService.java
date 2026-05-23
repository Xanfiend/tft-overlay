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
    private int level = 8;
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
        c.setBackground(box(0xF20B0709,40,BLOOD,3)); c.setPadding(30,22,30,22);
        TextView g=new TextView(this); g.setText("\u2720"); g.setTextColor(BLOODL); g.setTextSize(20); g.setGravity(Gravity.CENTER);
        TextView lb=new TextView(this); lb.setText("TFT"); lb.setTextColor(BONE); lb.setTextSize(9); lb.setGravity(Gravity.CENTER);
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
                    if(Math.abs(dx)>14||Math.abs(dy)>14) moved=true;
                    lp.x=ix+dx; lp.y=iy+dy; wm.updateViewLayout(button,lp); return true;
                } else if(a==MotionEvent.ACTION_UP){
                    if(!moved){
                        if(System.currentTimeMillis()-down>450){ mode=1; showPanel(); }
                        else { mode=0; showPanel(); }
                    }
                    return true;
                }
                return false;
            }
        });
        wm.addView(button, lp);
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
        title.setText(mode==1?"POOL TRACKER":"SCOUT \u2014 TAP TO COUNT");
        title.setTextColor(BLOODL); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView toggle=new TextView(this);
        toggle.setText(mode==1?"\u25A6 grid":"\u2261 pool");
        toggle.setTextColor(GOLD); toggle.setTextSize(13); toggle.setPadding(16,8,16,8);
        toggle.setBackground(box(CARD,6,EDGE,1));
        toggle.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ mode = mode==1?0:1; showPanel(); } });
        TextView close=new TextView(this); close.setText("  \u2715"); close.setTextColor(ASH); close.setTextSize(20); close.setPadding(18,0,4,0);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ closePanel(); } });
        head.addView(title); head.addView(toggle); head.addView(close);
        root.addView(head);

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
            b.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ level=lv; showPanel(); } });
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
        tipv.setText("Tap = +1 seen \u00b7 long-press = \u22121 \u00b7 switch to POOL for odds");
        tipv.setTextColor(DIM); tipv.setTextSize(10); tipv.setPadding(0,0,0,8);
        root.addView(tipv);

        for(int cost=1;cost<=5;cost++){
            TextView lbl=new TextView(this); lbl.setText(cost+"-COST");
            lbl.setTextColor(COSTC[cost]); lbl.setTextSize(11); lbl.setTypeface(null, android.graphics.Typeface.BOLD);
            lbl.setPadding(2,10,0,5); root.addView(lbl);

            LinearLayout row=null; String[] arr=Pool.CHAMPS[cost];
            for(int j=0;j<arr.length;j++){
                if(j%3==0){ row=new LinearLayout(this); root.addView(row); }
                final String name=arr[j]; final int fc=cost; final int myIdx=idx;
                final TextView chip=new TextView(this);
                chipViews[idx]=chip; chipNames[idx]=name; idx++;
                paintChip(chip, name, fc);
                chip.setOnClickListener(new View.OnClickListener(){
                    public void onClick(View v){ pool.add(name,1); buzz(); paintChip(chip,name,fc); }
                });
                chip.setOnLongClickListener(new View.OnLongClickListener(){
                    public boolean onLongClick(View v){ pool.add(name,-1); buzz(); paintChip(chip,name,fc); return true; }
                });
                LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,-2,1f); cl.setMargins(3,3,3,3); chip.setLayoutParams(cl);
                row.addView(chip);
            }
        }
        // big done button
        Button done=new Button(this); done.setText("DONE"); done.setAllCaps(false);
        done.setBackground(box(BLOOD,6,BLOODL,2)); done.setTextColor(BONE); done.setTextSize(15); done.setTypeface(null, android.graphics.Typeface.BOLD);
        done.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ closePanel(); } });
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2); dl.setMargins(0,14,0,0); done.setLayoutParams(dl);
        root.addView(done);
    }

    private void paintChip(TextView chip, String name, int cost){
        int seen=pool.seenCount(name);
        if(seen>0){
            chip.setText(name+"  "+seen);
            chip.setBackground(box(COSTC[cost],6,0xFFFFFFFF,2));
            chip.setTextColor(0xFF000000);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            chip.setText(name);
            chip.setBackground(box(CARD,6,EDGE,1));
            chip.setTextColor(BONE);
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        chip.setPadding(8,16,8,16); // tall = big tap target
        chip.setGravity(Gravity.CENTER);
        chip.setTextSize(13);
    }

    private void buildSummary(LinearLayout root){
        if(pool.isEmpty()){
            TextView e=new TextView(this); e.setText("\nNothing tracked yet.\nSwitch to the grid and tap champs you see.");
            e.setTextColor(ASH); e.setTextSize(13); e.setLineSpacing(6,1f); root.addView(e); return;
        }
        List<String> names=pool.seenSorted();
        for(final String name:names){
            int co=Pool.costOf(name); int s=pool.seenCount(name); int rem=pool.remaining(name);
            double ch=rerollChance(name)*100.0;
            LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(box(CARD,6,EDGE,1)); card.setPadding(12,10,10,10);
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2); cl.setMargins(0,0,0,6); card.setLayoutParams(cl);
            TextView dot=new TextView(this); dot.setText(""+co); dot.setTextColor(0xFF000000); dot.setTextSize(11); dot.setGravity(Gravity.CENTER);
            dot.setBackground(box(COSTC[co],4,0,0)); dot.setWidth(46); dot.setHeight(46); card.addView(dot);
            LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setPadding(12,0,0,0);
            mid.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView nm=new TextView(this); nm.setText(name); nm.setTextColor(BONE); nm.setTextSize(15); nm.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView sub=new TextView(this); sub.setText(s+" seen \u00b7 "+rem+" left"); sub.setTextColor(ASH); sub.setTextSize(11);
            mid.addView(nm); mid.addView(sub); card.addView(mid);
            TextView pct=new TextView(this); pct.setText(rem<=0?"GONE":String.format("%.0f%%",ch));
            pct.setTextColor(rem<=0?BLOODL:(ch>40?GREEN:(ch>15?GOLD:ASH))); pct.setTextSize(17);
            pct.setTypeface(null, android.graphics.Typeface.BOLD); pct.setPadding(8,0,10,0); card.addView(pct);
            TextView minus=new TextView(this); minus.setText("\u2212"); minus.setTextColor(BLOODL); minus.setTextSize(20); minus.setGravity(Gravity.CENTER);
            minus.setBackground(box(0xFF1A0C0E,5,BLOOD,1)); minus.setWidth(52); minus.setHeight(46);
            minus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.add(name,-1); buzz(); showPanel(); } });
            card.addView(minus);
            root.addView(card);
        }
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
        closePanel();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
