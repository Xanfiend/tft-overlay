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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScreenScanner {

    private static void log(String m){ OverlayService.addScanLog(m); }
    private static void err(String m){ OverlayService.addScanLog("ERR " + m); }

    // scan mode constants
    static final int MODE_FULL  = 0; // normal scan: gold, level, augments, shop, bench
    static final int MODE_POPUP = 1; // board scan mode: read champion name from unit popup zone only

    public static class ScanResult {
        public int gold = -1;
        public int level = -1;
        public List<String> augments = new ArrayList<>();
        public List<String> shopChampions = new ArrayList<>();
        public List<String> benchChampions = new ArrayList<>();
        public Map<String, Integer> starLevels = new HashMap<>();
        public String detectedBoardUnit = ""; // board scan mode: champion name from popup
        public int detectedBoardStars = 0;   // board scan mode: star level 1-3 from popup (0 = not detected)
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
                recognizeText(bmp, cb, MODE_FULL);
            } catch (Exception e) {
                err(e.getClass().getSimpleName() + ": " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> cb.onError(e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "capture failed")));
            }
        }).start();
    }

    // Full scan from bitmap (accessibility screenshot path).
    void scanBitmap(Bitmap bmp, ScanCallback cb) {
        scanBitmap(bmp, cb, MODE_FULL);
    }

    // Scan from bitmap with explicit mode (MODE_FULL or MODE_POPUP).
    void scanBitmap(Bitmap bmp, ScanCallback cb, int mode) {
        log("scanBitmap " + bmp.getWidth() + "x" + bmp.getHeight() + " mode=" + mode);
        recognizeText(bmp, cb, mode);
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

    private void recognizeText(Bitmap bmp, ScanCallback cb, int mode) {
        log("OCR start mode=" + mode);
        InputImage image = InputImage.fromBitmap(bmp, 0);
        TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        rec.process(image)
                .addOnSuccessListener(text -> {
                    log("OCR blocks=" + text.getTextBlocks().size());
                    ScanResult r;
                    if (mode == MODE_POPUP) {
                        r = parsePopup(text, bmp.getWidth(), bmp.getHeight());
                        log("popup parse: unit=" + (r.detectedBoardUnit.isEmpty() ? "none" : r.detectedBoardUnit));
                    } else {
                        r = parse(text, bmp.getWidth(), bmp.getHeight());
                        log("parse: gold=" + r.gold + " lv=" + r.level
                                + " augs=" + r.augments.size()
                                + " shop=" + r.shopChampions.size()
                                + " bench=" + r.benchChampions.size()
                                + " stars=" + r.starLevels.size()
                                + " -> " + r.status);
                    }
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
        boolean portrait = bmpH > bmpW;
        int bottomStart = portrait ? bmpH * 7 / 8 : bmpH * 3 / 4;
        int topEnd      = portrait ? bmpH / 8     : bmpH / 4;
        int leftHalf    = bmpW / 2;
        // Shop bar: just above the gold row
        int shopTop = portrait ? bmpH * 70 / 100 : bmpH * 65 / 100;
        int shopBot = portrait ? bmpH * 90 / 100 : bmpH * 85 / 100;
        // Bench: row of waiting units between board and shop bar
        int benchTop = portrait ? bmpH * 53 / 100 : bmpH * 50 / 100;
        int benchBot = portrait ? bmpH * 65 / 100 : bmpH * 63 / 100;
        log("zones: portrait=" + portrait
                + " topEnd=" + topEnd + " bottomStart=" + bottomStart
                + " shopTop=" + shopTop + " shopBot=" + shopBot
                + " benchTop=" + benchTop + " benchBot=" + benchBot);
        int goldBoxH = 0;

        List<String> allChamps = buildChampList();

        // Log every block for debugging
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            String raw = block.getText().trim().replace("\n", "|");
            if (box != null && !raw.isEmpty()) {
                log("blk \"" + raw + "\" x=" + box.centerX() + " y=" + box.centerY());
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

            // level: standalone 2-10 in top-left corner
            if (raw.matches("[2-9]|10") && cy < topEnd && cx < leftHalf && r.level == -1) {
                r.level = Integer.parseInt(raw);
            }

            // augments: full-screen match
            for (AugmentData.AugmentEntry aug : AugmentData.AUGMENTS) {
                if (!r.augments.contains(aug.name) && fuzzyMatch(raw, aug.name)) {
                    r.augments.add(aug.name);
                    break;
                }
            }

            // shop champions: text in the shop bar zone
            if (cy >= shopTop && cy <= shopBot) {
                for (String name : allChamps) {
                    if (!r.shopChampions.contains(name) && fuzzyMatchChamp(raw, name)) {
                        r.shopChampions.add(name);
                        log("shop champ: " + name + " from \"" + raw + "\"");
                        break;
                    }
                }
            }

            // bench champions: text in the bench zone (units waiting below the board)
            if (cy >= benchTop && cy <= benchBot) {
                for (String name : allChamps) {
                    if (!r.benchChampions.contains(name) && fuzzyMatchChamp(raw, name)) {
                        r.benchChampions.add(name);
                        log("bench champ: " + name + " from \"" + raw + "\"");
                        break;
                    }
                }
            }

            // stars: ★ or ⭐ anywhere on screen (best-effort)
            int stars = countStars(raw);
            if (stars > 0) {
                log("stars x" + stars + " at cx=" + cx + " cy=" + cy + " from \"" + raw + "\"");
                String nearest = null;
                int nearestDx = Integer.MAX_VALUE;
                for (Text.TextBlock b2 : text.getTextBlocks()) {
                    android.graphics.Rect b2box = b2.getBoundingBox();
                    if (b2box == null) continue;
                    String b2raw = b2.getText().trim();
                    int b2cy = b2box.centerY();
                    if (b2cy < shopTop || b2cy > shopBot) continue;
                    for (String name : r.shopChampions) {
                        if (fuzzyMatchChamp(b2raw, name)) {
                            int dx = Math.abs(b2box.centerX() - cx);
                            if (dx < 400 && dx < nearestDx) { nearest = name; nearestDx = dx; }
                        }
                    }
                }
                if (nearest != null && !r.starLevels.containsKey(nearest)) {
                    r.starLevels.put(nearest, stars);
                    log("star level: " + nearest + " = " + stars + "★");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        if (r.gold >= 0) sb.append(r.gold).append("g");
        if (r.level >= 0) { if (sb.length() > 0) sb.append(" · "); sb.append("Lv").append(r.level); }
        if (!r.augments.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(r.augments.size()).append(r.augments.size() == 1 ? " aug" : " augs"); }
        if (!r.shopChampions.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(r.shopChampions.size()).append(r.shopChampions.size() == 1 ? " shop champ" : " shop champs"); }
        if (!r.benchChampions.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(r.benchChampions.size()).append(r.benchChampions.size() == 1 ? " bench" : " bench"); }
        r.status = sb.length() > 0 ? sb.toString() : "nothing detected";
        return r;
    }

    // Board scan mode: reads the unit stat popup wherever it appears on screen.
    // The popup can appear on either side depending on which unit was tapped.
    private ScanResult parsePopup(Text text, int bmpW, int bmpH) {
        ScanResult r = new ScanResult();
        boolean portrait = bmpH > bmpW;
        // Full width scan — popup can appear on either side of the screen.
        // Skip the very top (system bar, traits panel) and very bottom (shop/bench).
        int popTop = portrait ? bmpH * 8  / 100 : bmpH * 12 / 100;
        int popBot = portrait ? bmpH * 62 / 100 : bmpH * 82 / 100;
        // Minimum block height to skip tiny UI labels (interest brackets, gold, etc.).
        int minH = Math.max(16, bmpH / 52);
        log("popup zone: y=" + popTop + "-" + popBot + " minH=" + minH + " bmp=" + bmpW + "x" + bmpH);

        List<String> allChamps = buildChampList();

        // Log every block in the vertical zone — show rejections so debug log is useful
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim().replace("\n", "|");
            int cx = box.centerX(), cy = box.centerY();
            if (cy < popTop || cy > popBot) continue;
            if (box.height() < minH) {
                log("skip h=" + box.height() + " \"" + raw + "\"");
            } else {
                log("cand h=" + box.height() + " x=" + cx + " \"" + raw + "\"");
            }
        }

        // Find the best champion match: prefer tallest block (most likely the name label)
        int bestH = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim();
            if (raw.isEmpty()) continue;
            int cy = box.centerY();
            if (cy < popTop || cy > popBot) continue;
            if (box.height() < minH) continue;
            for (String name : allChamps) {
                if (fuzzyMatchChamp(raw, name) && box.height() > bestH) {
                    r.detectedBoardUnit = name;
                    bestH = box.height();
                    log("match: " + name + " h=" + box.height() + " from \"" + raw + "\"");
                    break;
                }
            }
        }
        if (r.detectedBoardUnit.isEmpty()) {
            log("popup: no champion matched");
        } else {
            log("popup unit: " + r.detectedBoardUnit + " (h=" + bestH + ")");
        }

        // Star sweep — same zone and height filter
        if (!r.detectedBoardUnit.isEmpty()) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                android.graphics.Rect box = block.getBoundingBox();
                if (box == null) continue;
                int cy = box.centerY();
                if (cy < popTop || cy > popBot) continue;
                int stars = countStars(block.getText());
                if (stars > r.detectedBoardStars) r.detectedBoardStars = stars;
            }
            if (r.detectedBoardStars > 0)
                log("popup stars: " + r.detectedBoardStars + " for " + r.detectedBoardUnit);
        }
        return r;
    }

    private List<String> buildChampList() {
        List<String> list = new ArrayList<>();
        for (String[] tier : SetData.CHAMPS) {
            for (String name : tier) list.add(name);
        }
        return list;
    }

    private int countStars(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '★' || c == '⭐') n++;
        }
        return n;
    }

    private boolean fuzzyMatchChamp(String ocr, String target) {
        String ocrNorm = ocr.toLowerCase().replaceAll("[^a-z]", "");
        String tarNorm = target.toLowerCase().replaceAll("[^a-z]", "");
        // Short champion names (Zoe, Vex, Jhin, Fizz, Nami, Ornn…) — exact match only.
        // Fuzzy matching on 3-4 char strings causes too many false positives.
        if (tarNorm.length() <= 4) return ocrNorm.equals(tarNorm);
        // Longer names: require the OCR string itself to be at least 4 chars
        if (ocrNorm.length() < 4) return false;
        // Exact, or OCR wraps extra text around the full name
        if (ocrNorm.equals(tarNorm) || ocrNorm.contains(tarNorm)) return true;
        // Partial: OCR must cover >=80% of the target (e.g. "Lissandr" → Lissandra OK,
        // "sandra" → Lissandra NOT OK — 6/9 = 67% < 80%)
        if (tarNorm.contains(ocrNorm) && ocrNorm.length() * 10 >= tarNorm.length() * 8) return true;
        String tarWords = target.replaceAll("([A-Z])", " $1").trim();
        return fuzzyMatch(ocr, tarWords);
    }

    private boolean fuzzyMatch(String ocr, String target) {
        String ocrL = ocr.toLowerCase();
        String tarL = target.toLowerCase();
        if (ocrL.length() < 4) return false;
        if (ocrL.contains(tarL)) return true;
        if (tarL.contains(ocrL) && ocrL.length() * 10 >= tarL.length() * 8) return true;
        String[] words = tarL.split("[ ']+");
        if (words.length == 0) return false;
        int matched = 0;
        for (String w : words) { if (w.length() > 3 && ocrL.contains(w)) matched++; }
        return matched > 0 && (float) matched / words.length >= 0.6f;
    }
}
