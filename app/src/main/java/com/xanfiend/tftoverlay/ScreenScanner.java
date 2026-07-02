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

    // ML Kit's recognizer is reusable and non-trivial to construct. Building a new
    // one for every probe (37+ per auto-scan) added avoidable latency — cache one.
    private static TextRecognizer sharedRecognizer;
    private static synchronized TextRecognizer recognizer(){
        if (sharedRecognizer == null)
            sharedRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        return sharedRecognizer;
    }

    // scan mode constants
    static final int MODE_FULL  = 0; // normal scan: gold, level, augments, shop, bench
    static final int MODE_POPUP = 1; // board scan mode: read champion name from unit popup zone only
    static final int MODE_BOARD = 2; // auto board scan: full-screen sweep, very low minH, returns all champion matches

    public static class ScanResult {
        public int gold = -1;
        public int level = -1;
        public String stageRound = "";       // e.g. "3-2" from the round indicator ("" = not seen)
        public int xpCur = -1, xpNeed = -1;  // XP progress "cur/need" from the level button
        public List<String> augments = new ArrayList<>();
        public List<String> shopChampions = new ArrayList<>();
        public List<int[]> shopChampPos = new ArrayList<>(); // shop strip scan: {cx,cy} per shopChampions entry, crop coords
        public List<String> benchChampions = new ArrayList<>();
        public Map<String, Integer> starLevels = new HashMap<>();
        public String detectedBoardUnit = ""; // board scan mode: champion name from popup
        public int detectedBoardStars = 0;   // board scan mode: star level 1-3 from popup (0 = not detected)
        public List<String> popupTraits = new ArrayList<>(); // trait names visible in the unit popup
        public android.graphics.Rect detectedPopupBounds = null; // popup scan: bounding rect of all popup-zone blocks
        public List<String> autoChampions = new ArrayList<>(); // auto board scan: all champion names found
        public List<BoardUnit> boardUnits = new ArrayList<>();  // board vision: template-matched units
        public String status = "";
    }

    public static class BoardUnit {
        public final String name;
        public final float confidence;
        public final int probeX, probeY;
        public BoardUnit(String n, float c, int x, int y){ name=n; confidence=c; probeX=x; probeY=y; }
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

    // Board vision: match board hex crops against saved templates. No OCR — pure image signatures.
    // Runs on a background thread; callback fires on main thread.
    void scanBoardVision(Bitmap bmp, Context ctx, ScanCallback cb) {
        new Thread(() -> {
            log("scanBoardVision " + bmp.getWidth() + "x" + bmp.getHeight());
            ChampionTemplates.load(ctx);
            if (ChampionTemplates.templateCount() == 0) {
                log("boardVision: no templates — run My Board scan first");
                ScanResult r = new ScanResult();
                r.status = "no templates — tap My Board and scan each unit first";
                bmp.recycle();
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> cb.onResult(r));
                return;
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int boardTop = h * 10 / 100;
            int boardBot = h * 68 / 100;
            int cropSize = Math.max(60, h / 14);
            int cols = 7, rows = 4;
            ScanResult r = new ScanResult();
            log("boardVision: " + ChampionTemplates.templateCount() + " templates, " + cols + "x" + rows + " grid, crop=" + cropSize);
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int cx = (int) ((col + 0.5f) * w / cols);
                    int cy = boardTop + (int) ((row + 0.5f) * (boardBot - boardTop) / rows);
                    int x0 = Math.max(0, cx - cropSize / 2);
                    int y0 = Math.max(0, cy - cropSize / 2);
                    int cw = Math.min(cropSize, w - x0);
                    int ch = Math.min(cropSize, h - y0);
                    if (cw <= 0 || ch <= 0) continue;
                    Bitmap crop = Bitmap.createBitmap(bmp, x0, y0, cw, ch);
                    ChampionTemplates.Match m = ChampionTemplates.match(crop);
                    crop.recycle();
                    if (m != null) {
                        boolean dup = false;
                        for (BoardUnit bu : r.boardUnits) { if (bu.name.equals(m.name)) { dup = true; break; } }
                        if (!dup) {
                            r.boardUnits.add(new BoardUnit(m.name, m.sim, cx, cy));
                            log("boardVision: " + m.name + " sim=" + (int)(m.sim*100) + "% at " + cx + "," + cy);
                        }
                    }
                }
            }
            r.status = r.boardUnits.size() + " unit" + (r.boardUnits.size() == 1 ? "" : "s") + " found";
            log("boardVision done: " + r.boardUnits.size() + " units");
            bmp.recycle();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> cb.onResult(r));
        }).start();
    }

    // Scan from bitmap with explicit mode (MODE_FULL or MODE_POPUP).
    void scanBitmap(Bitmap bmp, ScanCallback cb, int mode) {
        log("scanBitmap " + bmp.getWidth() + "x" + bmp.getHeight() + " mode=" + mode);
        recognizeText(bmp, cb, mode);
    }

    // Hot-path popup scan for the auto-tap flow. The caller passes a bitmap already
    // cropped to the popup vertical band (full width preserved). OCR only sees the
    // band — ~30% fewer pixels than the full screen, and no shop/bench/trait text to
    // misread. fullW/fullH are the original screen dims (orientation + thresholds).
    // Block coordinates are relative to the crop; the caller offsets popup bounds
    // back to full-screen space before saving a template. Recycles the crop. Lean
    // logging only — this runs dozens of times per scan.
    // Downscale factor applied to the popup band before OCR. ML Kit latency scales with
    // pixel count; the champion name in the popup is large enough to survive a moderate
    // shrink, so this cuts OCR time roughly in half with no loss in name detection. Block
    // coordinates come back in scaled space and are mapped back in parsePopupZone.
    private static final float POPUP_OCR_SCALE = 0.7f;

    void scanPopupZone(Bitmap crop, int fullW, int fullH, ScanCallback cb) {
        final Bitmap ocrBmp;
        if (POPUP_OCR_SCALE < 0.99f) {
            int sw = Math.max(1, Math.round(crop.getWidth()  * POPUP_OCR_SCALE));
            int sh = Math.max(1, Math.round(crop.getHeight() * POPUP_OCR_SCALE));
            ocrBmp = Bitmap.createScaledBitmap(crop, sw, sh, true);
        } else {
            ocrBmp = crop;
        }
        InputImage image = InputImage.fromBitmap(ocrBmp, 0);
        recognizer().process(image)
                .addOnSuccessListener(text -> {
                    ScanResult r = parsePopupZone(text, fullW, fullH, POPUP_OCR_SCALE);
                    if (ocrBmp != crop) ocrBmp.recycle();
                    crop.recycle();
                    cb.onResult(r);
                })
                .addOnFailureListener(e -> {
                    if (ocrBmp != crop) ocrBmp.recycle();
                    crop.recycle();
                    cb.onError("OCR: " + e.getMessage());
                });
    }

    // Lean popup parser for a pre-cropped band. The entire crop is the popup zone
    // vertically, so no cy filtering is needed — only the landscape sidebar skip and
    // the minimum text height (both derived from the original screen size).
    private ScanResult parsePopupZone(Text text, int fullW, int fullH, float scale) {
        ScanResult r = new ScanResult();
        boolean portrait = fullH > fullW;
        // Thresholds are derived in full-screen pixels, then scaled to match the
        // downscaled OCR coordinate space the blocks come back in.
        int popLeft = (int) ((portrait ? 0 : fullW * 12 / 100) * scale);
        int minH = (int) (Math.max(16, fullH / 52) * scale);

        ensureChampArrays();
        int bestH = 0;
        int pMinX = Integer.MAX_VALUE, pMinY = Integer.MAX_VALUE, pMaxX = 0, pMaxY = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim();
            if (raw.isEmpty()) continue;
            if (box.centerX() < popLeft) continue;
            if (box.height() < minH) continue;
            if (box.left < pMinX) pMinX = box.left;
            if (box.top < pMinY) pMinY = box.top;
            if (box.right > pMaxX) pMaxX = box.right;
            if (box.bottom > pMaxY) pMaxY = box.bottom;
            String ocrNorm = norm(raw);
            for (int i = 0; i < champNames.length; i++) {
                if (box.height() > bestH && matchChampNorm(ocrNorm, raw, champNorms[i], champWords[i])) {
                    r.detectedBoardUnit = champNames[i];
                    bestH = box.height();
                    break;
                }
            }
        }
        // single-letter OCR slip the containment loop missed — try edit distance
        if (r.detectedBoardUnit.isEmpty()) {
            String fb = fuzzyNameFallback(text, popLeft, 0, 0, minH, false);
            if (!fb.isEmpty()) { r.detectedBoardUnit = fb; log("popup unit (fuzzy): " + fb); }
        }
        // bounds set whenever any popup text appeared — lets auto-tap tell an item
        // popup (bounds, no champion) from an empty hex (no bounds at all). Map back
        // from scaled OCR space to full-crop pixels so template cropping stays correct.
        if (pMaxX > pMinX) {
            float inv = 1f / scale;
            r.detectedPopupBounds = new android.graphics.Rect(
                    (int) (pMinX * inv), (int) (pMinY * inv),
                    (int) (pMaxX * inv), (int) (pMaxY * inv));
        }
        if (!r.detectedBoardUnit.isEmpty()) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                if (block.getBoundingBox() == null) continue;
                int s = countStars(block.getText());
                if (s > r.detectedBoardStars) r.detectedBoardStars = s;
            }
            // trait names appear in small text under the unit name — learn them
            for (Text.TextBlock block : text.getTextBlocks()) {
                android.graphics.Rect b = block.getBoundingBox();
                if (b == null || b.centerX() < popLeft) continue;
                String raw = block.getText();
                for (String[] tr : TraitData.TRAITS) {
                    String tName = tr[0];
                    if (!r.popupTraits.contains(tName) && fuzzyMatch(raw, tName)) {
                        r.popupTraits.add(tName);
                    }
                }
            }
            log("popup unit: " + r.detectedBoardUnit + (r.detectedBoardStars > 0 ? " " + r.detectedBoardStars + "★" : "")
                    + (r.popupTraits.isEmpty() ? "" : " traits=" + r.popupTraits));
        } else if (r.detectedPopupBounds != null) {
            // popup appeared but no champion matched — log the raw text blocks to aid debugging
            StringBuilder sb = new StringBuilder("popup no-match text:");
            for (Text.TextBlock block : text.getTextBlocks()) {
                android.graphics.Rect b = block.getBoundingBox();
                if (b == null || b.centerX() < popLeft || b.height() < minH) continue;
                sb.append(" [").append(block.getText().trim().replace('\n', '|')).append("]");
            }
            log(sb.toString());
        }
        return r;
    }

    // Hunt-mode shop scan: the caller passes a bitmap already cropped to the shop
    // strip (full width preserved). Returns every champion name visible on a shop
    // card together with its tap position (crop coordinates), plus the gold counter
    // if it falls inside the strip — so the auto-buy loop can budget its purchases.
    // Recycles the crop. Lean logging — this runs once a second while hunting.
    void scanShopStrip(Bitmap crop, int fullW, int fullH, ScanCallback cb) {
        final Bitmap ocrBmp;
        if (POPUP_OCR_SCALE < 0.99f) {
            int sw = Math.max(1, Math.round(crop.getWidth()  * POPUP_OCR_SCALE));
            int sh = Math.max(1, Math.round(crop.getHeight() * POPUP_OCR_SCALE));
            ocrBmp = Bitmap.createScaledBitmap(crop, sw, sh, true);
        } else {
            ocrBmp = crop;
        }
        InputImage image = InputImage.fromBitmap(ocrBmp, 0);
        recognizer().process(image)
                .addOnSuccessListener(text -> {
                    ScanResult r = parseShopStrip(text, fullW, POPUP_OCR_SCALE);
                    if (ocrBmp != crop) ocrBmp.recycle();
                    crop.recycle();
                    cb.onResult(r);
                })
                .addOnFailureListener(e -> {
                    if (ocrBmp != crop) ocrBmp.recycle();
                    crop.recycle();
                    cb.onError("OCR: " + e.getMessage());
                });
    }

    private ScanResult parseShopStrip(Text text, int fullW, float scale) {
        ScanResult r = new ScanResult();
        ensureChampArrays();
        float inv = 1f / scale;
        int rightHalf = (int) (fullW / 2 * scale);
        int goldBoxH = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim();
            if (raw.isEmpty()) continue;
            // gold counter: standalone 0-99 in the right half of the strip
            if (raw.matches("\\d{1,2}") && box.centerX() > rightHalf && box.height() > goldBoxH) {
                r.gold = Integer.parseInt(raw);
                goldBoxH = box.height();
            }
            String rawNorm = norm(raw);
            for (int i = 0; i < champNames.length; i++) {
                String name = champNames[i];
                if (!r.shopChampions.contains(name) && matchChampNorm(rawNorm, raw, champNorms[i], champWords[i])) {
                    r.shopChampions.add(name);
                    r.shopChampPos.add(new int[]{(int)(box.centerX()*inv), (int)(box.centerY()*inv)});
                    break;
                }
            }
        }
        // Fuzzy fallback: blocks the containment loop missed due to a single-letter OCR slip
        ensureChampArrays();
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String btxt = block.getText().trim();
            String cand = NameMatch.bestMatch(btxt, champNames, champNorms);
            if (cand != null && !r.shopChampions.contains(cand)) {
                r.shopChampions.add(cand);
                r.shopChampPos.add(new int[]{(int)(box.centerX()*inv), (int)(box.centerY()*inv)});
                log("shopStrip (fuzzy): " + cand + " from \"" + btxt + "\"");
            }
        }
        if (!r.shopChampions.isEmpty())
            log("shopStrip: " + r.shopChampions + (r.gold >= 0 ? " gold=" + r.gold : ""));
        return r;
    }

    // ---- gold/XP corner scan (always-on watcher) ----
    // The HUD watcher only needs gold (bottom-right) and level/XP/stage (top-left).
    // OCRing the whole screen for that is wasteful, so we crop just those two
    // corners and composite them into ONE small bitmap (top-left band stacked above
    // the gold band) for a single OCR pass — roughly a quarter of the pixels of a
    // full-frame scan, with no shop/board/bench text to cause false matches.
    // Recycles `full`.
    // goldCx / goldBandTop let the caller pin the gold-read region to wherever the
    // user parked the in-game gold HUD pill (the pill sits just ABOVE the game's
    // gold counter, so the band we read starts at the pill's bottom edge and runs
    // down over the counter — and never includes the pill's own text). Pass -1 for
    // both to fall back to the fixed bottom-right corner heuristic.
    void scanGoldXp(Bitmap full, ScanCallback cb) { scanGoldXp(full, -1, -1, cb); }

    void scanGoldXp(Bitmap full, int goldCx, int goldBandTop, ScanCallback cb) {
        int w = full.getWidth(), h = full.getHeight();
        boolean portrait = h > w;
        // top-left quadrant holds the level badge, XP "cur/need" and stage "x-y";
        // staying in the left half avoids the screen-center shop cards in landscape
        int tlW = Math.max(1, w / 2);
        int tlH = Math.max(1, portrait ? h / 8 : h / 4);
        int brTop, brLeft, brW, brH;
        if (goldCx >= 0 && goldBandTop >= 0) {
            // user-pinned: read the band right below the gold pill. A wide, short
            // box centred on the pill catches the counter whether it sits dead
            // below or slightly to one side, and excludes the pill itself.
            brTop  = Math.min(h - 1, Math.max(0, goldBandTop));
            brLeft = Math.max(0, goldCx - w * 22 / 100);
            int right = Math.min(w, goldCx + w * 22 / 100);
            brW = Math.max(1, right - brLeft);
            brH = Math.max(1, Math.min(h - brTop, h * 16 / 100));
            log("goldXp pinned band: x=" + brLeft + "-" + (brLeft + brW) + " y=" + brTop + "-" + (brTop + brH));
        } else {
            // bottom-right corner holds the gold counter (drawn large, far to the
            // right). In portrait the shop cards occupy the bottom strip and their
            // cost labels sit in the right half — keep the crop tight to the far
            // corner to avoid picking up a cost pill instead of the gold.
            brTop  = portrait ? h * 84 / 100 : h * 74 / 100;
            brLeft = w * 76 / 100;
            brW = Math.max(1, w - brLeft);
            brH = Math.max(1, h - brTop);
        }
        final int seam = tlH; // blocks at/below the seam came from the gold band
        Bitmap combo;
        try {
            combo = Bitmap.createBitmap(Math.max(tlW, brW), tlH + brH, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(combo);
            cv.drawColor(0xFF000000);
            cv.drawBitmap(full, new android.graphics.Rect(0, 0, tlW, tlH),
                    new android.graphics.Rect(0, 0, tlW, tlH), null);
            cv.drawBitmap(full, new android.graphics.Rect(brLeft, brTop, brLeft + brW, brTop + brH),
                    new android.graphics.Rect(0, tlH, brW, tlH + brH), null);
        } catch (Exception e) {
            full.recycle();
            cb.onError("crop: " + e.getMessage());
            return;
        }
        full.recycle();
        final Bitmap fcombo = combo;
        InputImage image = InputImage.fromBitmap(fcombo, 0);
        recognizer().process(image)
                .addOnSuccessListener(text -> {
                    ScanResult r = parseGoldXp(text, seam);
                    fcombo.recycle();
                    cb.onResult(r);
                })
                .addOnFailureListener(e -> {
                    fcombo.recycle();
                    cb.onError("OCR: " + e.getMessage());
                });
    }

    private ScanResult parseGoldXp(Text text, int seam) {
        ScanResult r = new ScanResult();
        int goldBoxH = 0;
        int goldBoxX = 0; // rightmost wins among blocks of similar height
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim();
            if (raw.isEmpty()) continue;
            if (box.centerY() >= seam) {
                // gold band: pull the digit run out of the block (the gold coin
                // glyph often fuses onto the number, e.g. "⛃53", so a strict
                // all-digits match would miss it). Tallest box wins; among blocks
                // within 4px of the same height prefer the rightmost — the gold
                // counter is always in the far-right corner.
                java.util.regex.Matcher gm = java.util.regex.Pattern.compile("(\\d{1,3})").matcher(raw);
                if (gm.find()) {
                    int v = Integer.parseInt(gm.group(1));
                    if (v >= 0 && v <= 300) {
                        boolean taller = box.height() > goldBoxH + 4;
                        boolean sameHeight = box.height() >= goldBoxH - 4;
                        boolean moreRight = box.centerX() > goldBoxX;
                        if (taller || (sameHeight && moreRight)) {
                            r.gold = v; goldBoxH = box.height(); goldBoxX = box.centerX();
                        }
                    }
                }
                continue;
            }
            // top-left band: level, XP progress, stage-round
            if (r.level == -1 && raw.matches("[1-9]|10")) r.level = Integer.parseInt(raw);
            if (r.xpNeed < 0) {
                java.util.regex.Matcher xm = java.util.regex.Pattern
                        .compile("\\b(\\d{1,3})\\s*/\\s*(\\d{1,3})\\b").matcher(raw);
                if (xm.find()) {
                    int cur = Integer.parseInt(xm.group(1));
                    int need = Integer.parseInt(xm.group(2));
                    boolean valid = false;
                    for (int x : SetData.XP_TO_NEXT) if (x == need) { valid = true; break; }
                    if (valid && cur <= need) { r.xpCur = cur; r.xpNeed = need; }
                }
            }
            if (r.stageRound.isEmpty()) {
                java.util.regex.Matcher sm = java.util.regex.Pattern
                        .compile("\\b([1-9])-([1-7])\\b").matcher(raw);
                if (sm.find()) r.stageRound = sm.group(1) + "-" + sm.group(2);
            }
        }
        log("goldXp: gold=" + r.gold + " lvl=" + r.level
                + " xp=" + r.xpCur + "/" + r.xpNeed + " stage=" + r.stageRound);
        return r;
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
        TextRecognizer rec = recognizer();
        rec.process(image)
                .addOnSuccessListener(text -> {
                    log("OCR blocks=" + text.getTextBlocks().size());
                    ScanResult r;
                    if (mode == MODE_POPUP) {
                        r = parsePopup(text, bmp.getWidth(), bmp.getHeight());
                        log("popup parse: unit=" + (r.detectedBoardUnit.isEmpty() ? "none" : r.detectedBoardUnit));
                    } else if (mode == MODE_BOARD) {
                        r = parseBoard(text, bmp.getWidth(), bmp.getHeight());
                        log("auto-scan: " + r.autoChampions.size() + " champs found");
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

        ensureChampArrays();

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

            // gold: 0-99 in bottom-right corner. Use find() not matches() so a fused
            // coin glyph like "⛃53" still yields 53 instead of being discarded.
            if (cy > bottomStart && cx > leftHalf) {
                java.util.regex.Matcher gm = java.util.regex.Pattern.compile("(\\d{1,2})").matcher(raw);
                if (gm.find()) {
                    int val = Integer.parseInt(gm.group(1));
                    if (val >= 0 && val <= 99 && box.height() > goldBoxH) {
                        r.gold = val;
                        goldBoxH = box.height();
                    }
                }
            }

            // level: standalone 1-10 in top-left corner (1 = Tocker's Trials start)
            if (raw.matches("[1-9]|10") && cy < topEnd && cx < leftHalf && r.level == -1) {
                r.level = Integer.parseInt(raw);
            }

            // stage-round indicator: "3-2" style, top strip of the screen
            if (r.stageRound.isEmpty() && cy < topEnd) {
                java.util.regex.Matcher sm = java.util.regex.Pattern
                        .compile("\\b([1-9])-([1-7])\\b").matcher(raw);
                if (sm.find()) {
                    r.stageRound = sm.group(1) + "-" + sm.group(2);
                    log("stage round: " + r.stageRound + " from \"" + raw + "\"");
                }
            }

            // XP progress: "cur/need" near the level button, top-left.
            // Validate need against the XP table so score fractions don't match.
            if (r.xpNeed < 0 && cy < topEnd && cx < leftHalf) {
                java.util.regex.Matcher xm = java.util.regex.Pattern
                        .compile("\\b(\\d{1,3})\\s*/\\s*(\\d{1,3})\\b").matcher(raw);
                if (xm.find()) {
                    int cur = Integer.parseInt(xm.group(1));
                    int need = Integer.parseInt(xm.group(2));
                    boolean valid = false;
                    for (int x : SetData.XP_TO_NEXT) if (x == need) { valid = true; break; }
                    if (valid && cur <= need) {
                        r.xpCur = cur; r.xpNeed = need;
                        log("xp: " + cur + "/" + need + " from \"" + raw + "\"");
                    }
                }
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
                String rawNorm = norm(raw);
                for (int i = 0; i < champNames.length; i++) {
                    String name = champNames[i];
                    if (!r.shopChampions.contains(name) && matchChampNorm(rawNorm, raw, champNorms[i], champWords[i])) {
                        r.shopChampions.add(name);
                        log("shop champ: " + name + " from \"" + raw + "\"");
                        break;
                    }
                }
            }

            // bench champions: text in the bench zone (units waiting below the board)
            if (cy >= benchTop && cy <= benchBot) {
                String rawNorm = norm(raw);
                for (int i = 0; i < champNames.length; i++) {
                    String name = champNames[i];
                    if (!r.benchChampions.contains(name) && matchChampNorm(rawNorm, raw, champNorms[i], champWords[i])) {
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

        // Fuzzy fallback for shop and bench zones: catches single-letter OCR slips that
        // containment matching missed. Only adds a candidate when NameMatch resolves it
        // unambiguously and it isn't already in the list.
        ensureChampArrays();
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            int bcy = box.centerY();
            boolean inShop  = bcy >= shopTop  && bcy <= shopBot;
            boolean inBench = bcy >= benchTop && bcy <= benchBot;
            if (!inShop && !inBench) continue;
            String btxt = block.getText().trim();
            String cand = NameMatch.bestMatch(btxt, champNames, champNorms);
            if (cand == null) continue;
            if (inShop  && !r.shopChampions.contains(cand))  { r.shopChampions.add(cand);  log("shop champ (fuzzy): "  + cand + " from \"" + btxt + "\""); }
            if (inBench && !r.benchChampions.contains(cand)) { r.benchChampions.add(cand); log("bench champ (fuzzy): " + cand + " from \"" + btxt + "\""); }
        }

        StringBuilder sb = new StringBuilder();
        if (r.gold >= 0) sb.append(r.gold).append("g");
        if (r.level >= 0) { if (sb.length() > 0) sb.append(" · "); sb.append("Lv").append(r.level); }
        if (!r.stageRound.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(r.stageRound); }
        if (r.xpNeed > 0) { if (sb.length() > 0) sb.append(" · "); sb.append("xp ").append(r.xpCur).append("/").append(r.xpNeed); }
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
        // In landscape, skip the leftmost 12% — that's the trait sidebar (Brawler 6/6,
        // Eradicator 1/1, etc.) which has tall OCR blocks that can pollute the popup scan.
        // The stat popup always appears in the board area, never at the very left edge.
        int popLeft = portrait ? 0 : bmpW * 12 / 100;
        // Minimum block height to skip tiny UI labels (interest brackets, gold, etc.).
        int minH = Math.max(16, bmpH / 52);
        log("popup zone: y=" + popTop + "-" + popBot + " x>" + popLeft + " minH=" + minH + " bmp=" + bmpW + "x" + bmpH);

        ensureChampArrays();

        // Log every block in the vertical zone — show rejections so debug log is useful
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim().replace("\n", "|");
            int cx = box.centerX(), cy = box.centerY();
            if (cy < popTop || cy > popBot) continue;
            if (cx < popLeft) { log("skip sidebar x=" + cx + " \"" + raw + "\""); continue; }
            if (box.height() < minH) {
                log("skip h=" + box.height() + " \"" + raw + "\"");
            } else {
                log("cand h=" + box.height() + " x=" + cx + " \"" + raw + "\"");
            }
        }

        // Find the best champion match: prefer tallest block (most likely the name label)
        int bestH = 0;
        int pMinX = Integer.MAX_VALUE, pMinY = Integer.MAX_VALUE, pMaxX = 0, pMaxY = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim();
            if (raw.isEmpty()) continue;
            int cx = box.centerX(), cy = box.centerY();
            if (cy < popTop || cy > popBot) continue;
            if (cx < popLeft) continue;
            if (box.height() < minH) continue;
            // accumulate overall popup bounds from all qualifying blocks
            if (box.left < pMinX) pMinX = box.left;
            if (box.top < pMinY) pMinY = box.top;
            if (box.right > pMaxX) pMaxX = box.right;
            if (box.bottom > pMaxY) pMaxY = box.bottom;
            String rawNorm = norm(raw);
            for (int i = 0; i < champNames.length; i++) {
                if (box.height() > bestH && matchChampNorm(rawNorm, raw, champNorms[i], champWords[i])) {
                    r.detectedBoardUnit = champNames[i];
                    bestH = box.height();
                    log("match: " + champNames[i] + " h=" + box.height() + " from \"" + raw + "\"");
                    break;
                }
            }
        }
        // single-letter OCR slip the containment loop missed — try edit distance
        if (r.detectedBoardUnit.isEmpty()) {
            String fb = fuzzyNameFallback(text, popLeft, popTop, popBot, minH, true);
            if (!fb.isEmpty()) { r.detectedBoardUnit = fb; log("match (fuzzy): " + fb); }
        }
        // Always record popup bounds when any qualifying text appeared in the zone —
        // used by auto-tap to distinguish "item popup" (bounds set, no champion) from
        // "empty hex" (no bounds), so item taps don't count toward the miss streak.
        if (pMaxX > pMinX) {
            r.detectedPopupBounds = new android.graphics.Rect(pMinX, pMinY, pMaxX, pMaxY);
            log("popup bounds: " + pMinX + "," + pMinY + "-" + pMaxX + "," + pMaxY);
        }
        if (r.detectedBoardUnit.isEmpty()) {
            log("popup: no champion matched" + (r.detectedPopupBounds != null ? " (non-champion popup)" : " (empty hex)"));
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

        // Trait sweep — the popup lists the unit's traits under its name in small
        // text (no height filter). This is how the overlay learns champ→traits.
        if (!r.detectedBoardUnit.isEmpty()) {
            for (Text.TextBlock block : text.getTextBlocks()) {
                android.graphics.Rect box = block.getBoundingBox();
                if (box == null) continue;
                int cy = box.centerY(), cx = box.centerX();
                if (cy < popTop || cy > popBot || cx < popLeft) continue;
                String raw = block.getText();
                for (String[] tr : TraitData.TRAITS) {
                    String tName = tr[0];
                    if (!r.popupTraits.contains(tName) && fuzzyMatch(raw, tName)) {
                        r.popupTraits.add(tName);
                    }
                }
            }
            if (!r.popupTraits.isEmpty())
                log("popup traits: " + r.popupTraits + " for " + r.detectedBoardUnit);
        }
        return r;
    }

    // Auto board scan: sweep the entire screen with a very low height filter.
    // Catches small name labels on bench units, shop cards, and any popup visible.
    // Returns every champion name found anywhere on screen.
    private ScanResult parseBoard(Text text, int bmpW, int bmpH) {
        ScanResult r = new ScanResult();
        int minH = 8;
        ensureChampArrays();
        log("auto-scan: full screen minH=" + minH + " bmp=" + bmpW + "x" + bmpH + " blocks=" + text.getTextBlocks().size());
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            String raw = block.getText().trim();
            if (raw.isEmpty()) continue;
            String rawLog = raw.replace("\n", "|");
            if (box.height() < minH) {
                log("skip h=" + box.height() + " x=" + box.centerX() + " y=" + box.centerY() + " \"" + rawLog + "\"");
                continue;
            }
            log("cand h=" + box.height() + " x=" + box.centerX() + " y=" + box.centerY() + " \"" + rawLog + "\"");
            String rawNorm = norm(raw);
            for (int i = 0; i < champNames.length; i++) {
                String name = champNames[i];
                if (!r.autoChampions.contains(name) && matchChampNorm(rawNorm, raw, champNorms[i], champWords[i])) {
                    r.autoChampions.add(name);
                    log("auto match: " + name + " from \"" + rawLog + "\"");
                    break;
                }
            }
        }
        // Fuzzy fallback: blocks the containment loop missed due to single-letter OCR slips
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null || box.height() < minH) continue;
            String btxt = block.getText().trim();
            String cand = NameMatch.bestMatch(btxt, champNames, champNorms);
            if (cand != null && !r.autoChampions.contains(cand)) {
                r.autoChampions.add(cand);
                log("auto match (fuzzy): " + cand + " from \"" + btxt + "\"");
            }
        }
        return r;
    }

    private static List<String> champListCache = null;
    // Parallel arrays for the hot path: name, its normalized form (lowercase a-z only),
    // and its space-split word form ("TwistedFate" -> "Twisted Fate"). Built once.
    // Avoids recompiling regex and re-normalizing every champion for every OCR block
    // on every probe (37+ probes per auto-scan, ~60 champions per block).
    private static String[] champNames = null;
    private static String[] champNorms = null;
    private static String[] champWords = null;

    private static List<String> buildChampList() {
        if (champListCache == null) {
            champListCache = new ArrayList<>();
            for (String[] tier : SetData.CHAMPS)
                for (String name : tier) champListCache.add(name);
        }
        return champListCache;
    }

    private static synchronized void ensureChampArrays() {
        if (champNames != null) return;
        List<String> list = buildChampList();
        String[] names = list.toArray(new String[0]);
        String[] norms = new String[names.length];
        String[] words = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            norms[i] = norm(names[i]);
            words[i] = names[i].replaceAll("([A-Z])", " $1").trim();
        }
        champNorms = norms; champWords = words;
        champNames = names; // assign last: readers gate on champNames != null
    }

    // Lowercase, strip everything but a-z. Allocation-light replacement for
    // toLowerCase().replaceAll("[^a-z]","") — no Pattern compile per call.
    private static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'A' && c <= 'Z') c += 32;
            if (c >= 'a' && c <= 'z') b.append(c);
        }
        return b.toString();
    }


    private int countStars(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '★' || c == '⭐') n++;
        }
        return n;
    }

    // Additive edit-distance fallback for the popup name. ML Kit routinely misreads
    // a single letter (Corki->Corkl, Jhin->Jhln, Samira->Samlra); the containment
    // matcher can't catch a mid-word substitution, but Levenshtein within a
    // length-scaled tolerance can. Returns the champion on the TALLEST qualifying
    // block that resolves, or "" — callers run this ONLY after the containment loop
    // found nothing, so it can never override an exact/contains hit. NameMatch
    // guards short names (exact-only) and ambiguous ties itself.
    private String fuzzyNameFallback(Text text, int popLeft, int popTop, int popBot,
                                     int minH, boolean useCy) {
        ensureChampArrays();
        int bestH = 0; String found = "";
        for (Text.TextBlock block : text.getTextBlocks()) {
            android.graphics.Rect box = block.getBoundingBox();
            if (box == null) continue;
            if (box.centerX() < popLeft) continue;
            if (useCy && (box.centerY() < popTop || box.centerY() > popBot)) continue;
            if (box.height() < minH || box.height() <= bestH) continue;
            String cand = NameMatch.bestMatch(block.getText().trim(), champNames, champNorms);
            if (cand != null) { found = cand; bestH = box.height(); }
        }
        return found;
    }

    // Thin wrapper for callers that match against an arbitrary name (e.g. the star
    // pass re-checking already-detected shop champions). Normalizes on each call.
    private boolean fuzzyMatchChamp(String ocr, String target) {
        return matchChampNorm(norm(ocr), ocr, norm(target),
                target.replaceAll("([A-Z])", " $1").trim());
    }

    // Hot-path matcher: caller passes the already-normalized OCR string (computed once
    // per block) plus the cached norm and word forms (computed once at startup).
    private boolean matchChampNorm(String ocrNorm, String ocrRaw, String tarNorm, String tarWords) {
        // Short champion names (Zoe, Vex, Jhin, Fizz, Nami, Ornn…) — exact match only.
        // Fuzzy matching on 3-4 char strings causes too many false positives.
        if (tarNorm.length() <= 4) return ocrNorm.equals(tarNorm);
        // Longer names: require the OCR string itself to be at least 4 chars
        if (ocrNorm.length() < 4) return false;
        // Exact, or OCR wraps extra text around the full name
        if (ocrNorm.equals(tarNorm) || ocrNorm.contains(tarNorm)) return true;
        // Partial: OCR must cover >=80% of the target. Only applied to names 6+ chars
        // long — for shorter names (e.g. "Leona" 5 chars) the 80% rule lets "leon"
        // match, producing false positives.
        if (tarNorm.length() >= 6 && tarNorm.contains(ocrNorm) && ocrNorm.length() * 10 >= tarNorm.length() * 8) return true;
        return fuzzyMatch(ocrRaw, tarWords);
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
