package com.xanfiend.tftoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class OverlayService extends Service {
    private WindowManager wm;
    private View button;
    private View panel;
    private Pool pool;
    private int level = 8;

    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay vd;
    private int sw, sh, dpi;
    private boolean scanning = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextRecognizer recognizer;

    private static final int[][] ODDS = {
        {0,0,0,0,0},{100,0,0,0,0},{100,0,0,0,0},{75,25,0,0,0},
        {55,30,15,0,0},{45,33,20,2,0},{30,40,25,5,0},{19,30,40,10,1},
        {17,24,32,24,3},{15,18,25,30,12},{5,10,20,40,25}
    };
    private static final int VOID=0xF2050304, BLOOD=0xFF8B0000, BLOODL=0xFFC1121F,
        BONE=0xFFD4C4A8, ASH=0xFF6B5E52, CARD=0xFF120A0C, EDGE=0xFF2A1518,
        GOLD=0xFFB8954A, GREEN=0xFF6B8E23, DIM=0xFF4A3438;
    private static final int[] COSTC={0,0xFF8A8A8A,0xFF5A8C5A,0xFF4A6FA5,0xFF8B5FBF,0xFFB8954A};
    private static final String CH="tft_overlay";

    @Override public void onCreate(){
        super.onCreate();
        pool = new Pool(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics m = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(m);
        sw=m.widthPixels; sh=m.heightPixels; dpi=m.densityDpi;
    }

    @Override public int onStartCommand(Intent intent, int flags, int id){
        startFg();
        // Add the floating button FIRST so it always shows, even if capture setup fails
        if(button==null){
            try { addButton(); toast("Overlay ready"); }
            catch(Exception e){ toast("Overlay err: "+e.getMessage()); }
        }
        if(intent!=null && intent.hasExtra("code")){
            try {
                int code = intent.getIntExtra("code",0);
                Intent data = (Intent) intent.getParcelableExtra("data");
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                projection = mpm.getMediaProjection(code, data);
                reader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2);
                vd = projection.createVirtualDisplay("cap", sw,sh,dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
            } catch(Exception e){ toast("Capture err: "+e.getMessage()); }
        }
        return START_STICKY;
    }

    private void startFg(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch = new NotificationChannel(CH,"TFT Overlay",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this,CH)
                .setContentTitle("TFT Overlay active")
                .setSmallIcon(android.R.drawable.ic_menu_search).build();
            startForeground(1,n);
        } else startForeground(1, new Notification());
    }

    private int wtype(){
        return Build.VERSION.SDK_INT>=26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                         : WindowManager.LayoutParams.TYPE_PHONE;
    }
    private GradientDrawable box(int color,int r,int sc,int sw){
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(r);
        if(sw>0) g.setStroke(sw,sc); return g;
    }

    private void addButton(){
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setBackground(box(0xF20A0608,4,BLOOD,3));
        c.setPadding(26,18,26,18);
        TextView glyph = new TextView(this);
        glyph.setText("\u2720"); glyph.setTextColor(BLOODL); glyph.setTextSize(20); glyph.setGravity(Gravity.CENTER);
        TextView lb = new TextView(this);
        lb.setText("SCAN"); lb.setTextColor(BONE); lb.setTextSize(9); lb.setGravity(Gravity.CENTER);
        c.addView(glyph); c.addView(lb);
        button = c;

        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2,-2,wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP|Gravity.START; lp.x=20; lp.y=280;

        button.setOnTouchListener(new View.OnTouchListener(){
            int ix,iy; float tx,ty; long down; boolean moved;
            public boolean onTouch(View v, MotionEvent e){
                int a=e.getAction();
                if(a==MotionEvent.ACTION_DOWN){ ix=lp.x;iy=lp.y;tx=e.getRawX();ty=e.getRawY();down=System.currentTimeMillis();moved=false; return true; }
                else if(a==MotionEvent.ACTION_MOVE){
                    int dx=(int)(e.getRawX()-tx),dy=(int)(e.getRawY()-ty);
                    if(Math.abs(dx)>12||Math.abs(dy)>12) moved=true;
                    lp.x=ix+dx; lp.y=iy+dy; wm.updateViewLayout(button,lp); return true;
                } else if(a==MotionEvent.ACTION_UP){
                    if(!moved){
                        long held=System.currentTimeMillis()-down;
                        if(held>500) showPanel(true);   // long-press = summary
                        else doScan();                   // tap = auto OCR scan
                    }
                    return true;
                }
                return false;
            }
        });
        wm.addView(button, lp);
    }

    // ---------- AUTO OCR SCAN with countdown ----------
    private void doScan(){
        if(scanning) return;
        scanning = true;
        // 2-second countdown so user can settle on the opponent board
        countdown(2);
    }
    private void countdown(final int sec){
        if(sec<=0){ grabAndOcr(); return; }
        setBtnLabel(""+sec);
        ui.postDelayed(new Runnable(){ public void run(){ countdown(sec-1); } }, 1000);
    }
    private void setBtnLabel(String s){
        if(button instanceof LinearLayout){
            LinearLayout c=(LinearLayout)button;
            TextView lb=(TextView)c.getChildAt(1);
            lb.setText(s);
        }
    }
    private void grabAndOcr(){
        setBtnLabel("...");
        Bitmap bmp = grab();
        if(bmp==null){ toast("No frame — try again"); reset(); return; }
        InputImage in = InputImage.fromBitmap(bmp, 0);
        recognizer.process(in)
            .addOnSuccessListener(new OnSuccessListener<Text>(){
                public void onSuccess(Text r){
                    Map<String,Integer> m = Pool.matchText(r.getText());
                    if(m.isEmpty()){ toast("No champs read — switch boards & retry"); }
                    else {
                        int n=0;
                        for(Map.Entry<String,Integer> e : m.entrySet()){ pool.add(e.getKey(), e.getValue()); n+=e.getValue(); }
                        toast("Read "+m.size()+" champs ("+n+" copies)");
                    }
                    reset();
                }
            })
            .addOnFailureListener(new OnFailureListener(){
                public void onFailure(Exception e){ toast("OCR failed"); reset(); }
            });
    }
    private void reset(){ scanning=false; setBtnLabel("SCAN"); }

    private Bitmap grab(){
        try {
            Image img = reader.acquireLatestImage();
            if(img==null) return null;
            Image.Plane[] p = img.getPlanes();
            ByteBuffer buf = p[0].getBuffer();
            int ps=p[0].getPixelStride(), rs=p[0].getRowStride();
            int pad = rs - ps*sw;
            Bitmap bmp = Bitmap.createBitmap(sw+pad/ps, sh, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            img.close();
            if(pad!=0) bmp = Bitmap.createBitmap(bmp,0,0,sw,sh);
            return bmp;
        } catch(Exception e){ return null; }
    }

    // ---------- PANEL ----------
    private void closePanel(){ if(panel!=null){ try{wm.removeView(panel);}catch(Exception e){} panel=null; } }

    private void showPanel(boolean summary){
        closePanel();
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(box(VOID,6,BLOOD,2));
        root.setPadding(26,22,26,22);
        scroll.addView(root);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView sig=new TextView(this); sig.setText("\u26B6 "); sig.setTextColor(BLOODL); sig.setTextSize(16);
        TextView title = new TextView(this);
        title.setText(summary ? "POOL \u2014 SOULS COUNTED" : "MARK THE FALLEN");
        title.setTextColor(BLOODL); title.setTextSize(14); title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
        TextView close=new TextView(this); close.setText("\u2715"); close.setTextColor(ASH); close.setTextSize(20); close.setPadding(24,0,8,0);
        close.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ closePanel(); } });
        head.addView(sig); head.addView(title); head.addView(close);
        root.addView(head);

        View dv=new View(this); dv.setBackgroundColor(BLOOD);
        LinearLayout.LayoutParams dvl=new LinearLayout.LayoutParams(-1,2); dvl.setMargins(0,12,0,0); dv.setLayoutParams(dvl);
        root.addView(dv);

        // level row
        LinearLayout lvl=new LinearLayout(this); lvl.setPadding(0,14,0,14);
        TextView ll=new TextView(this); ll.setText("RANK "); ll.setTextColor(ASH); ll.setTextSize(10); ll.setGravity(Gravity.CENTER_VERTICAL);
        lvl.addView(ll);
        int[] L={6,7,8,9,10};
        for(int i=0;i<L.length;i++){
            final int lv=L[i];
            TextView b=new TextView(this); b.setText(""+lv); b.setTextSize(13); b.setPadding(18,8,18,8); b.setGravity(Gravity.CENTER);
            boolean on=lv==level;
            b.setBackground(box(on?BLOOD:CARD,3,on?BLOODL:EDGE,on?2:1));
            b.setTextColor(on?BONE:ASH);
            final boolean sm=summary;
            b.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ level=lv; showPanel(sm); } });
            LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(0,-2,1f); bl.setMargins(3,0,3,0); b.setLayoutParams(bl);
            lvl.addView(b);
        }
        root.addView(lvl);

        if(summary) buildSummary(root); else buildGrid(root);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
            (int)(sw*0.94),(int)(sh*0.80), wtype(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
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

    private void buildSummary(LinearLayout root){
        if(pool.isEmpty()){
            TextView e=new TextView(this); e.setText("\nThe void is empty.\nTap SCAN on an opponent board, or short-tap to read.");
            e.setTextColor(ASH); e.setTextSize(13); e.setLineSpacing(6,1f); root.addView(e); return;
        }
        List<String> names = pool.seenSorted();
        for(final String name : names){
            int co=Pool.costOf(name); int s=pool.seenCount(name); int rem=pool.remaining(name);
            double ch=rerollChance(name)*100.0;
            LinearLayout card=new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(box(CARD,3,EDGE,1)); card.setPadding(12,10,10,10);
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2); cl.setMargins(0,0,0,6); card.setLayoutParams(cl);
            TextView dot=new TextView(this); dot.setText(""+co); dot.setTextColor(VOID); dot.setTextSize(11); dot.setGravity(Gravity.CENTER);
            dot.setBackground(box(COSTC[co],2,0,0)); dot.setWidth(46); dot.setHeight(46);
            card.addView(dot);
            LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.setPadding(12,0,0,0);
            mid.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1f));
            TextView nm=new TextView(this); nm.setText(name); nm.setTextColor(BONE); nm.setTextSize(15); nm.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView sub=new TextView(this); sub.setText(s+" taken \u00b7 "+rem+" remain"); sub.setTextColor(ASH); sub.setTextSize(11);
            mid.addView(nm); mid.addView(sub); card.addView(mid);
            TextView pct=new TextView(this); pct.setText(rem<=0?"VOID":String.format("%.0f%%",ch));
            pct.setTextColor(rem<=0?BLOODL:(ch>40?GREEN:(ch>15?GOLD:ASH))); pct.setTextSize(17);
            pct.setTypeface(null, android.graphics.Typeface.BOLD); pct.setPadding(8,0,10,0); card.addView(pct);
            TextView minus=new TextView(this); minus.setText("\u2212"); minus.setTextColor(BLOODL); minus.setTextSize(20); minus.setGravity(Gravity.CENTER);
            minus.setBackground(box(0xFF1A0C0E,3,BLOOD,1)); minus.setWidth(52); minus.setHeight(46);
            minus.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.add(name,-1); showPanel(true); } });
            card.addView(minus);
            root.addView(card);
        }
        Button wipe=new Button(this); wipe.setText("\u26B6  BANISH ALL  \u26B6"); wipe.setAllCaps(false);
        wipe.setBackground(box(0xFF1A0C0E,3,BLOOD,2)); wipe.setTextColor(BLOODL); wipe.setTextSize(13);
        wipe.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){ pool.reset(); showPanel(true); } });
        LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(-1,-2); wl.setMargins(0,12,0,0); wipe.setLayoutParams(wl);
        root.addView(wipe);
    }

    private void buildGrid(LinearLayout root){
        final Map<String,Integer> staged = new java.util.HashMap<>();
        for(int cost=1;cost<=5;cost++){
            TextView lbl=new TextView(this); lbl.setText("\u2014 "+cost+"-COST \u2014"); lbl.setTextColor(COSTC[cost]); lbl.setTextSize(11);
            lbl.setTypeface(null, android.graphics.Typeface.BOLD); lbl.setPadding(0,12,0,5); root.addView(lbl);
            LinearLayout row=null; String[] arr=Pool.CHAMPS[cost];
            for(int j=0;j<arr.length;j++){
                if(j%3==0){ row=new LinearLayout(this); root.addView(row); }
                final String name=arr[j]; final int fc=cost;
                final TextView chip=new TextView(this); chip.setText(name); chip.setTextColor(BONE); chip.setTextSize(12);
                chip.setBackground(box(CARD,3,EDGE,1)); chip.setPadding(10,11,10,11); chip.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,-2,1f); cl.setMargins(3,3,3,3); chip.setLayoutParams(cl);
                chip.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
                    int cur=staged.containsKey(name)?staged.get(name):0; cur++; staged.put(name,cur);
                    chip.setText(name+" \u00d7"+cur); chip.setBackground(box(BLOOD,3,BLOODL,2)); chip.setTextColor(BONE);
                    chip.setTypeface(null, android.graphics.Typeface.BOLD);
                }});
                row.addView(chip);
            }
        }
        View sp=new View(this); sp.setLayoutParams(new LinearLayout.LayoutParams(-1,16)); root.addView(sp);
        Button save=new Button(this); save.setText("\u2720  OFFER TO THE POOL  \u2720"); save.setAllCaps(false);
        save.setBackground(box(BLOOD,3,BLOODL,2)); save.setTextColor(BONE); save.setTextSize(14); save.setTypeface(null, android.graphics.Typeface.BOLD);
        save.setOnClickListener(new View.OnClickListener(){ public void onClick(View v){
            int total=0; for(Map.Entry<String,Integer> e:staged.entrySet()){ pool.add(e.getKey(),e.getValue()); total+=e.getValue(); }
            Toast.makeText(OverlayService.this, total+" souls claimed", Toast.LENGTH_SHORT).show(); showPanel(true);
        }});
        root.addView(save);
    }

    private void toast(final String s){ ui.post(new Runnable(){ public void run(){ Toast.makeText(OverlayService.this,s,Toast.LENGTH_SHORT).show(); } }); }

    @Override public void onDestroy(){
        super.onDestroy();
        try{ if(button!=null) wm.removeView(button); }catch(Exception e){}
        closePanel();
        if(vd!=null) vd.release();
        if(reader!=null) reader.close();
        if(projection!=null) projection.stop();
        if(recognizer!=null) recognizer.close();
    }
    @Override public IBinder onBind(Intent i){ return null; }
}
