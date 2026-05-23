package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_CAP = 1002;
    private MediaProjectionManager mpm;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60,100,60,60);
        root.setBackgroundColor(0xFF050304);

        TextView t = new TextView(this);
        t.setText("\u2720 TFT OVERLAY");
        t.setTextColor(0xFFC1121F); t.setTextSize(26);
        root.addView(t);
        TextView sub = new TextView(this);
        sub.setText("Set 17 \u00b7 auto-scan enabled\n");
        sub.setTextColor(0xFF6B5E52); sub.setTextSize(13);
        root.addView(sub);

        root.addView(btn("1. Grant overlay permission", new View.OnClickListener(){
            public void onClick(View v){
                if(canDraw()){ toast("Already granted"); return; }
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            }
        }));

        root.addView(btn("2. Start overlay + screen scan", new View.OnClickListener(){
            public void onClick(View v){
                if(!canDraw()){ toast("Grant overlay first"); return; }
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAP);
            }
        }));

        root.addView(btn("Stop / reset pool", new View.OnClickListener(){
            public void onClick(View v){
                stopService(new Intent(MainActivity.this, OverlayService.class));
                new Pool(MainActivity.this).reset();
                toast("Stopped & cleared");
            }
        }));

        TextView help = new TextView(this);
        help.setText("\nIn-game:\n\u2022 Tap floating sigil = champ grid (manual)\n\u2022 Tap SCAN = auto-read current board (timer runs)\n\u2022 Long-press = pool summary + odds\n\u2022 Drag to move");
        help.setTextColor(0xFF6B5E52); help.setTextSize(12); help.setLineSpacing(6,1f);
        root.addView(help);

        setContentView(root);
    }

    private Button btn(String txt, View.OnClickListener l){
        Button b = new Button(this);
        b.setText(txt); b.setAllCaps(false);
        b.setBackgroundColor(0xFF2A1518); b.setTextColor(0xFFD4C4A8); b.setTextSize(15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,20);
        b.setLayoutParams(lp); b.setOnClickListener(l);
        return b;
    }
    private boolean canDraw(){ return Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this); }
    private void toast(String m){ Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); }

    @Override protected void onActivityResult(int req, int res, Intent data){
        super.onActivityResult(req,res,data);
        if(req==REQ_CAP && res==RESULT_OK && data!=null){
            Intent svc = new Intent(this, OverlayService.class);
            svc.putExtra("code", res);
            svc.putExtra("data", data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(svc); else startService(svc);
            toast("Overlay live! Open TFT.");
            moveTaskToBack(true);
        } else if(req==REQ_CAP){
            toast("Screen capture denied");
        }
    }
}
