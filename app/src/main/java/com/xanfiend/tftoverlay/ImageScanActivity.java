package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.Toast;

import java.io.InputStream;

/**
 * Dev tool: pick a saved screenshot and run the live scan pipeline against it,
 * so the OCR / health-bar detection / hex grid can be validated WITHOUT being in
 * a TFT game. Headless — it only opens the system image picker, decodes the
 * chosen image, and hands the bitmap to the running OverlayService, which draws
 * the result. No UI of its own (translucent theme).
 *
 * The overlay must already be running (the button that launches this lives in the
 * overlay's SETUP tab), so OverlayService._instance is non-null.
 */
public class ImageScanActivity extends Activity {

    private static final int PICK = 1;

    @Override protected void onCreate(Bundle s){
        super.onCreate(s);
        if(s == null){
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            try{ startActivityForResult(i, PICK); }
            catch(Exception e){ toastFinish("No image picker available"); }
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data){
        super.onActivityResult(req, res, data);
        if(req != PICK || res != RESULT_OK || data == null || data.getData() == null){
            finish(); return;
        }
        Bitmap bmp = decode(data.getData());
        if(bmp == null){ toastFinish("Could not read that image"); return; }
        if(!OverlayService.scanFromImage(bmp)){
            bmp.recycle();
            toastFinish("Start the overlay first");
            return;
        }
        finish();
    }

    // Decode at roughly screen resolution — screenshots are about screen-sized
    // already, but downsample defensively so an oversized image can't OOM.
    private Bitmap decode(Uri uri){
        try{
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int cap = Math.max(dm.widthPixels, dm.heightPixels) * 2;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            if(in != null) in.close();

            int sample = 1;
            int big = Math.max(bounds.outWidth, bounds.outHeight);
            while(big / sample > cap) sample *= 2;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            InputStream in2 = getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in2, null, o);
            if(in2 != null) in2.close();
            return b;
        }catch(Exception e){ return null; }
    }

    private void toastFinish(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }
}
