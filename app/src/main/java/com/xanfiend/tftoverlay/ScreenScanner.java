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

public class ScreenScanner {

    private static void log(String m){ OverlayService.addScanLog(m); }
    private static void err(String m){ OverlayService.addScanLog("ERR " + m); }

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
                log("scanner start");
                Bitmap bmp = captureFrame();
                log("frame " + bmp.getWidth() + "x" + bmp.getHeight());
                recognizeText(bmp, cb);
            } catch (Exception e) {
                err(e.getClass().getSimpleName() + ": " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> cb.onError(e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "capture failed")));
            }
        }).start();
    }

    private Bitmap captureFrame() throws Exception {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int w = dm.widthPixels;
        int h = dm.heightPixels;
        log("captureFrame " + w + "x" + h + " dpi=" + dm.densityDpi);

        // Android 14 requires a callback registered before createVirtualDisplay()
        projection.registerCallback(new MediaProjection.Callback() {},
                new android.os.Handler(android.os.Looper.getMainLooper()));

        ImageReader reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        log("ImageReader ok, createVirtualDisplay...");
        VirtualDisplay vd = projection.createVirtualDisplay("scryer-scan",
                w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, null);
        log("VirtualDisplay ok, polling for frame");

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
            err("no frame within 3s");
            throw new Exception("no frame captured");
        }
        log("frame acquired");
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
        log("OCR start");
        InputImage image = InputImage.fromBitmap(bmp, 0);
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(image)
                .addOnSuccessListener(text -> {
                    log("OCR blocks=" + text.getTextBlocks().size());
                    ScanResult r = parse(text, bmp.getWidth(), bmp.getHeight());
                    log("parse: gold=" + r.gold + " lv=" + r.level
                            + " augs=" + r.augments.size() + " -> " + r.status);
                    bmp.recycle();
                    cb.onResult(r);
                })
                .addOnFailureListener(e -> {
                    err("OCR: " + e.getMessage());
                    bmp.recycle();
                    cb.onError("OCR: " + e.getMessage());
                });
    }

    private ScanResult parse(Text text, int bmpW, int bmpH) {
        ScanResult r = new ScanResult();
        // TFT Mobile layout — level is always top-left, gold is always bottom-right.
        // Portrait (h>w): HUD elements are in a smaller slice at the screen edges.
        boolean portrait = bmpH > bmpW;
        int bottomStart = portrait ? bmpH * 7 / 8 : bmpH * 3 / 4;
        int topEnd      = portrait ? bmpH / 8     : bmpH / 4;
        int leftHalf    = bmpW / 2;
        log("zones: portrait=" + portrait + " topEnd=" + topEnd + " bottomStart=" + bottomStart);
        int goldBoxH = 0;

        // Log every block so the debug panel shows exactly what OCR found
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            String raw = block.getText().trim().replace("\n", "|");
            if (box != null && !raw.isEmpty()) {
                log("blk \"" + raw + "\" x=" + box.centerX() + " y=" + box.centerY()
                        + " (goldZone: cy>" + bottomStart + " cx>" + leftHalf
                        + " | lvZone: cy<" + topEnd + " cx<" + leftHalf + ")");
            }
        }

        for (Text.TextBlock block : text.getTextBlocks()) {
            String raw = block.getText().trim();
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null || raw.isEmpty()) continue;
            int cy = box.centerY();
            int cx = box.centerX();

            // gold: standalone 0-99 in bottom-right corner
            if (raw.matches("\\d{1,2}") && cy > bottomStart && cx > leftHalf) {
                int val = Integer.parseInt(raw);
                if (val >= 0 && val <= 99 && box.height() > goldBoxH) {
                    r.gold = val;
                    goldBoxH = box.height();
                }
            }

            // level: standalone 2-10 in top-left corner (TFT Mobile shows level there)
            if (raw.matches("[2-9]|10") && cy < topEnd && cx < leftHalf && r.level == -1) {
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
