package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Intent;
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
    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60,100,60,60);
        root.setBackgroundColor(0xFF0B0709);

        TextView t=new TextView(this);
        t.setText("\u2720 TFT OVERLAY");
        t.setTextColor(0xFFC1121F); t.setTextSize(26);
        root.addView(t);
        TextView sub=new TextView(this);
        sub.setText("Set 17 pool tracker\n");
        sub.setTextColor(0xFF7A6B60); sub.setTextSize(13);
        root.addView(sub);

        root.addView(btn("1. Grant overlay permission", new View.OnClickListener(){
            public void onClick(View v){
                if(canDraw()){ toast("Already granted"); return; }
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:"+getPackageName())));
            }
        }));
        root.addView(btn("2. Start overlay", new View.OnClickListener(){
            public void onClick(View v){
                if(!canDraw()){ toast("Grant overlay permission first"); return; }
                startService(new Intent(MainActivity.this, OverlayService.class));
                toast("Overlay started! Open TFT.");
                moveTaskToBack(true);
            }
        }));
        root.addView(btn("Stop / reset pool", new View.OnClickListener(){
            public void onClick(View v){
                stopService(new Intent(MainActivity.this, OverlayService.class));
                new Pool(MainActivity.this).reset();
                toast("Stopped & cleared");
            }
        }));

        TextView help=new TextView(this);
        help.setText("\nIn-game:\n\u2022 Tap the floating button = scout grid\n\u2022 Tap a champ = +1 seen (long-press = \u22121)\n\u2022 Switch to POOL view for reroll odds\n\u2022 Long-press button = jump to pool\n\u2022 Drag button to move it");
        help.setTextColor(0xFF7A6B60); help.setTextSize(12); help.setLineSpacing(6,1f);
        root.addView(help);

        setContentView(root);
    }
    private Button btn(String txt, View.OnClickListener l){
        Button b=new Button(this); b.setText(txt); b.setAllCaps(false);
        b.setBackgroundColor(0xFF3A2024); b.setTextColor(0xFFE0D5C0); b.setTextSize(15);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,20); b.setLayoutParams(lp);
        b.setOnClickListener(l); return b;
    }
    private boolean canDraw(){ return Build.VERSION.SDK_INT<23 || Settings.canDrawOverlays(this); }
    private void toast(String m){ Toast.makeText(this,m,Toast.LENGTH_SHORT).show(); }
}
