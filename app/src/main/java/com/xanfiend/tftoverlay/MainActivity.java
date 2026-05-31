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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    // palette — matches OverlayService
    private static final int VOID   = 0xFF0B0709;
    private static final int CARD   = 0xFF16100F;
    private static final int BLOOD  = 0xFF8B1A1A;
    private static final int BLOODL = 0xFFC1121F;
    private static final int EDGE   = 0xFF3A2024;
    private static final int BONE   = 0xFFE0D5C0;
    private static final int GOLD   = 0xFFC9A227;
    private static final int ASH    = 0xFF7A6B60;
    private static final int DIM    = 0xFF4A3D38;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56,96,56,56);
        root.setBackgroundColor(VOID);

        // title
        TextView title=new TextView(this);
        title.setText("⦿ TFT SCRYER");
        title.setTextColor(BLOODL); title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD); title.setLetterSpacing(0.06f);
        root.addView(title);

        // subtitle
        TextView sub=new TextView(this);
        sub.setText("Set 17 pool tracker");
        sub.setTextColor(GOLD); sub.setTextSize(13);
        LinearLayout.LayoutParams subl=new LinearLayout.LayoutParams(-1,-2); subl.setMargins(0,2,0,20); sub.setLayoutParams(subl);
        root.addView(sub);

        // divider
        root.addView(divider());

        // action buttons
        root.addView(btnPrimary("2. Start overlay", new View.OnClickListener(){
            public void onClick(View v){
                if(!canDraw()){ toast("Grant overlay permission first"); return; }
                startService(new Intent(MainActivity.this, OverlayService.class));
                toast("Overlay started — open TFT.");
                moveTaskToBack(true);
            }
        }));
        root.addView(btn("1. Grant overlay permission", new View.OnClickListener(){
            public void onClick(View v){
                if(canDraw()){ toast("Already granted"); return; }
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            }
        }));
        root.addView(btnDestructive("Stop / reset pool", new View.OnClickListener(){
            public void onClick(View v){
                stopService(new Intent(MainActivity.this, OverlayService.class));
                new Pool(MainActivity.this).reset();
                toast("Stopped & pool cleared");
            }
        }));

        // divider
        root.addView(divider());

        // quick reference
        TextView helpHdr=new TextView(this);
        helpHdr.setText("◇ IN-GAME");
        helpHdr.setTextColor(GOLD); helpHdr.setTextSize(11);
        helpHdr.setTypeface(null, Typeface.BOLD); helpHdr.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams hhl=new LinearLayout.LayoutParams(-1,-2); hhl.setMargins(0,6,0,6); helpHdr.setLayoutParams(hhl);
        root.addView(helpHdr);

        String[] tips={
            "Tap the sigil → scout grid   /   long-press → board",
            "Tap a champion to mark a copy seen; tap the count to subtract",
            "◉ badge tracks how many players are contesting",
            "Drag the sigil anywhere; drag to ✕ to close the overlay",
            "Reset clears the pool between games"
        };
        for(String tip : tips){
            TextView tv=new TextView(this);
            tv.setText("· "+tip);
            tv.setTextColor(ASH); tv.setTextSize(12); tv.setLineSpacing(4,1f);
            LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2); tl.setMargins(0,0,0,3); tv.setLayoutParams(tl);
            root.addView(tv);
        }

        // footer
        root.addView(divider());
        TextView footer=new TextView(this);
        footer.setText("@xanfiend");
        footer.setTextColor(DIM); footer.setTextSize(11); footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,-2); fl.setMargins(0,10,0,0); footer.setLayoutParams(fl);
        root.addView(footer);

        setContentView(root);
    }

    private TextView btn(String txt, View.OnClickListener l){
        TextView b=new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BONE); b.setTextSize(14); b.setPadding(0,16,0,16);
        b.setBackground(shape(CARD, EDGE, 8, 1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,10); b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }

    private TextView btnPrimary(String txt, View.OnClickListener l){
        TextView b=new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BONE); b.setTextSize(15); b.setPadding(0,18,0,18);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(shape(BLOOD, BLOODL, 8, 2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,10); b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }

    private TextView btnDestructive(String txt, View.OnClickListener l){
        TextView b=new TextView(this); b.setText(txt); b.setGravity(Gravity.CENTER);
        b.setTextColor(BLOODL); b.setTextSize(13); b.setPadding(0,14,0,14);
        b.setBackground(shape(CARD, BLOOD, 8, 1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,10); b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }

    private GradientDrawable shape(int fill, int stroke, int radius, int strokeW){
        GradientDrawable g=new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(radius);
        g.setColor(fill);
        g.setStroke(strokeW, stroke);
        return g;
    }

    private TextView divider(){
        TextView d=new TextView(this);
        d.setText("❦ · ⋆ · ❦ · ⋆ · ❦ · ⋆ · ❦");
        d.setTextColor(EDGE); d.setTextSize(9); d.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2); dl.setMargins(0,14,0,14); d.setLayoutParams(dl);
        return d;
    }

    private boolean canDraw(){ return Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this); }
    private void toast(String m){ Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); }
}
