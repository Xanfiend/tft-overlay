package com.xanfiend.tftoverlay;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Tests for Updater's pure-logic helpers: version comparison and release-JSON parsing.
 * Robolectric runner needed because Updater has a static Handler(Looper.getMainLooper())
 * field that would throw on plain JVM.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UpdaterTest {

    // ---- isNewer: dotted numeric comparison (NOT lexicographic) ----

    @Test public void numericNotLexicographic() {
        // "1.7" vs "1.66": lexicographically "7" < "66" but numerically 7 < 66 — 1.66 is newer
        assertTrue("1.66 should be newer than 1.7", Updater.isNewer("1.66", "1.7"));
        assertFalse("1.7 should not be newer than 1.66", Updater.isNewer("1.7", "1.66"));
    }

    @Test public void majorVersionBump() {
        assertTrue(Updater.isNewer("2.0.0", "1.99.1"));
        assertFalse(Updater.isNewer("1.99.1", "2.0.0"));
    }

    @Test public void equalVersionsNotNewer() {
        assertFalse(Updater.isNewer("1.99.1", "1.99.1"));
        assertFalse(Updater.isNewer("2.0.0", "2.0.0"));
    }

    @Test public void patchIncrement() {
        assertTrue(Updater.isNewer("1.99.2", "1.99.1"));
        assertFalse(Updater.isNewer("1.99.1", "1.99.2"));
    }

    @Test public void missingSegmentTreatedAsZero() {
        // "1.0" and "1.0.0" are equal — missing third segment = 0
        assertFalse(Updater.isNewer("1.0", "1.0.0"));
        assertFalse(Updater.isNewer("1.0.0", "1.0"));
    }

    @Test public void badInputReturnsFalse() {
        assertFalse(Updater.isNewer("not-a-version", "1.0"));
        assertFalse(Updater.isNewer("1.0", "not-a-version"));
    }

    // ---- parseLatestVersion: title-first, assets fallback ----

    @Test public void titleVersionTrusted() throws Exception {
        String json = "{\"name\":\"TFT Scryer v1.99.1\",\"assets\":[]}";
        assertEquals("1.99.1", Updater.parseLatestVersion(json));
    }

    @Test public void titleWinsOverHigherStrayAsset() throws Exception {
        // The critical regression case: a stray tft-scryer-v2.0.apk asset must NOT
        // override the authoritative release title "TFT Scryer v1.99.1".
        String json = "{\"name\":\"TFT Scryer v1.99.1\",\"assets\":["
                + "{\"name\":\"tft-scryer.apk\"},"
                + "{\"name\":\"tft-scryer-v2.0.apk\"}"
                + "]}";
        assertEquals("1.99.1", Updater.parseLatestVersion(json));
    }

    @Test public void noTitleFallsBackToAsset() throws Exception {
        // Title absent → asset scanning is the fallback
        String json = "{\"name\":\"\",\"assets\":["
                + "{\"name\":\"tft-scryer-v1.98.0.apk\"},"
                + "{\"name\":\"tft-scryer.apk\"}"
                + "]}";
        assertEquals("1.98.0", Updater.parseLatestVersion(json));
    }

    @Test public void bestAssetWhenMultiple() throws Exception {
        // Best asset = newest among assets (no title)
        String json = "{\"name\":\"\",\"assets\":["
                + "{\"name\":\"tft-scryer-v1.24.apk\"},"
                + "{\"name\":\"tft-scryer-v1.98.apk\"},"
                + "{\"name\":\"tft-scryer-v1.5.apk\"}"
                + "]}";
        assertEquals("1.98", Updater.parseLatestVersion(json));
    }

    @Test public void noTitleNoMatchingAssetsReturnsNull() throws Exception {
        String json = "{\"name\":\"\",\"assets\":[{\"name\":\"tft-scryer.apk\"}]}";
        assertNull(Updater.parseLatestVersion(json));
    }
}
