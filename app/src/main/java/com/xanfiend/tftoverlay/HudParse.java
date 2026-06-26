package com.xanfiend.tftoverlay;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Pure parsing + sanity for the in-game HUD numbers the autoscan reads off OCR
 * text: gold, level, XP (cur/need), and stage-round. No Android, no bitmaps —
 * string in, validated value out — so it is fully unit-testable and the OCR /
 * zone code in ScreenScanner can delegate the fiddly digit logic here.
 *
 * GROUNDWORK (dormant): built + tested but not yet wired into the live scan
 * pipeline — adopting it in ScreenScanner is a device-verified step, since OCR
 * behaviour can only be confirmed on a real screen. Two hardening ideas:
 *   1) OCR digit normalization — ML Kit routinely misreads O->0, l/I->1, S->5,
 *      B->8, Z->2 in number blocks. Fixing those before matching turns "5O"
 *      into gold 50 and "lO" into level 10. (Deliberately conservative: we do
 *      NOT remap 'g'/'D'/'Q', so a "50g" gold label can't become 509.)
 *   2) Sanity bounds — a value only counts if it is in range for its field
 *      (level 1-10, gold 0-999, stage/round 1-7, xpCur<=xpNeed), so a stray
 *      block of text can't poison the scan with a nonsense number.
 */
public final class HudParse {

    public static final int NONE = -1;

    /* Map high-confidence OCR letter->digit confusions; leave everything else. */
    public static String normalizeDigits(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case 'O': case 'o': c = '0'; break;
                case 'l': case 'I': case 'i': case '|': case '!': c = '1'; break;
                case 'S': c = '5'; break;
                case 'B': c = '8'; break;
                case 'Z': case 'z': c = '2'; break;
                default: break;
            }
            b.append(c);
        }
        return b.toString();
    }

    /* Normalized digits only. */
    private static String digits(String s) {
        String n = normalizeDigits(s);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= '0' && c <= '9') b.append(c);
        }
        return b.toString();
    }

    /* Level 1..10 from the (bare-number) level badge. */
    public static int level(String raw) {
        String d = digits(raw);
        if (d.isEmpty() || d.length() > 2) return NONE;
        try {
            int v = Integer.parseInt(d);
            return (v >= 1 && v <= 10) ? v : NONE;
        } catch (NumberFormatException e) { return NONE; }
    }

    /* Gold 0..999. The counter can fuse with its coin icon, so find rather than
     * full-match the first 1-3 digit run after normalization. */
    public static int gold(String raw) {
        Matcher m = Pattern.compile("(\\d{1,3})").matcher(normalizeDigits(raw));
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v >= 0 && v <= 999) return v;
            } catch (NumberFormatException e) { /* fall through */ }
        }
        return NONE;
    }

    /* XP "cur/need" e.g. "4/6" -> {4,6}. Valid only when cur<=need and need is
     * a plausible XP-to-next (1..99). Returns null on no/invalid match. */
    public static int[] xp(String raw) {
        Matcher m = Pattern.compile("(\\d{1,2})\\s*/\\s*(\\d{1,3})").matcher(normalizeDigits(raw));
        if (m.find()) {
            try {
                int cur = Integer.parseInt(m.group(1));
                int need = Integer.parseInt(m.group(2));
                if (cur >= 0 && need >= 1 && need <= 99 && cur <= need) return new int[]{cur, need};
            } catch (NumberFormatException e) { /* fall through */ }
        }
        return null;
    }

    /* Stage-round "x-y" -> {stage,round}, both 1..7. Returns null on no match. */
    public static int[] stageRound(String raw) {
        Matcher m = Pattern.compile("\\b([1-7])\\s*-\\s*([1-7])\\b").matcher(normalizeDigits(raw));
        if (m.find()) {
            try {
                return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
            } catch (NumberFormatException e) { /* fall through */ }
        }
        return null;
    }

    private HudParse() {}
}
