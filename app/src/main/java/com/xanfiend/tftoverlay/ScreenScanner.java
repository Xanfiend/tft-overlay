package com.xanfiend.tftoverlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.util.DisplayMetrics;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class ScreenScanner {

    private static final String TAG = "TFTScryer";

    public static class ScanResult {
        public int gold = -1;
        public int level = -1;
        public List<String> augments = new ArrayList<>();
        public String status = "";
    }

    public interface ScanCallback {
        void onResult(ScanResult r);
        void onError(String msg);
    }

    private final Context ctx;
    private final MediaProjection projection;

    public ScreenScanner(Context ctx, MediaProjection mp) {
        this.ctx = ctx;
        this.projection = mp;
    }

    // Runs capture + OCR on a background thread; callback fires on main thread.
    public void scan(ScanCallback cb) {
        new Thread(() -> {
            try {
                Log.d(TAG, "ScreenScanner.scan start");
                Bitmap bmp = captureFrame();
                Log.d(TAG, "Frame captured " + bmp.getWidth() + "x" + bmp.getHeight());
                recognizeText(bmp, cb);
            } catch (Exception e) {
                Log.e(TAG, "ScreenScanner.scan error: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> cb.onError(e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "capture failed")));
            }
        }).start();
    }

    private Bitmap captureFrame() throws Exception {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        Log.d(TAG, "captureFrame " + w + "x" + h + " dpi=" + dm.densityDpi);

        ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        Log.d(TAG, "ImageReader created, calling createVirtualDisplay");
        VirtualDisplay vd = projection.createVirtualDisplay("scryer-scan",
                w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, null);
        Log.d(TAG, "VirtualDisplay created, polling for frame");

        // Poll for the first frame (max 3 s)
        long deadline = System.currentTimeMillis() + 3000;
        Image img = null;
        while (img == null && System.currentTimeMillis() < deadline) {
            img = reader.acquireLatestImage();
            if (img == null) { try { Thread.sleep(50); } catch (InterruptedException ignored) {} }
        }
        vd.release();

        if (img == null) {
            reader.close();
            Log.e(TAG, "No frame captured within 3s");
            throw new Exception("no frame captured");
        }
        Log.d(TAG, "Frame acquired after " + (3000 - (deadline - System.currentTimeMillis())) + "ms");
        Bitmap bmp = imageToBitmap(img, w, h);
        img.close();
        reader.close();
        return bmp;
    }

    private Bitmap imageToBitmap(Image img, int w, int h) {
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int paddedW = rowStride / pixelStride;
        Bitmap raw = Bitmap.createBitmap(paddedW, h, Bitmap.Config.ARGB_8888);
        raw.copyPixelsFromBuffer(buf);
        if (paddedW == w) return raw;
        Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, w, h);
        raw.recycle();
        return cropped;
    }

    private void recognizeText(Bitmap bmp, ScanCallback cb) {
        Log.d(TAG, "recognizeText start");
        InputImage image = InputImage.fromBitmap(bmp, 0);
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(image)
                .addOnSuccessListener(text -> {
                    Log.d(TAG, "OCR success blocks=" + text.getTextBlocks().size());
                    ScanResult r = parse(text, bmp.getWidth(), bmp.getHeight());
                    Log.d(TAG, "parse result: gold=" + r.gold + " level=" + r.level
                            + " augs=" + r.augments.size() + " status=" + r.status);
                    bmp.recycle();
                    cb.onResult(r);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "OCR failure: " + e.getMessage(), e);
                    bmp.recycle();
                    cb.onError("OCR: " + e.getMessage());
                });
    }

    private ScanResult parse(Text text, int bmpW, int bmpH) {
        ScanResult r = new ScanResult();
        int bottomStart = bmpH * 3 / 4;  // bottom quarter = gold zone
        int leftHalf = bmpW / 2;          // left half = level zone
        int goldBoxH = 0;

        for (Text.TextBlock block : text.getTextBlocks()) {
            String raw = block.getText().trim();
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null || raw.isEmpty()) continue;
            int cy = box.centerY();
            int cx = box.centerX();

            // gold: standalone 0-99 in bottom quarter
            if (raw.matches("\\d{1,2}") && cy > bottomStart) {
                int val = Integer.parseInt(raw);
                if (val >= 0 && val <= 99 && box.height() > goldBoxH) {
                    r.gold = val;
                    goldBoxH = box.height();
                }
            }

            // level: standalone 4-10 in lower-left half
            if (raw.matches("[4-9]|10") && cy > bmpH / 2 && cx < leftHalf && r.level == -1) {
                r.level = Integer.parseInt(raw);
            }

            // augment name matching against AugmentData
            for (AugmentData.AugmentEntry aug : AugmentData.AUGMENTS) {
                if (!r.augments.contains(aug.name) && fuzzyMatch(raw, aug.name)) {
                    r.augments.add(aug.name);
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (r.gold >= 0) sb.append(r.gold).append("g");
        if (r.level >= 0) { if (sb.length() > 0) sb.append(" · "); sb.append("Lv").append(r.level); }
        if (!r.augments.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(r.augments.size()).append(r.augments.size() == 1 ? " aug" : " augs"); }
        r.status = sb.length() > 0 ? sb.toString() : "nothing detected";
        return r;
    }

    // Returns true if the OCR text is a plausible match for the augment name.
    // Tries direct containment first; falls back to word-overlap at ≥60%.
    private boolean fuzzyMatch(String ocr, String target) {
        String ocrL = ocr.toLowerCase();
        String tarL = target.toLowerCase();
        if (ocrL.contains(tarL) || tarL.contains(ocrL)) return true;
        String[] words = tarL.split("[ ']+");
        if (words.length == 0) return false;
        int matched = 0;
        for (String w : words) { if (w.length() > 2 && ocrL.contains(w)) matched++; }
        return matched > 0 && (float) matched / words.length >= 0.6f;
    }
}
