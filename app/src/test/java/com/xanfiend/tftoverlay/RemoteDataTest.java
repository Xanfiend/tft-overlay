package com.xanfiend.tftoverlay;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * RemoteData.validate() is the guard that prevents a truncated or garbled
 * network fetch from overwriting the bundled set data.
 * Robolectric runner for real org.json; validate() itself has no Android calls.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RemoteDataTest {

    // ---- helpers ----

    /** Build a minimal valid payload: setName + 6-element champs (tier 0 empty,
     *  tiers 1-5 each with one champ) + 6-element size. */
    private static JSONObject valid() throws Exception {
        JSONObject o = new JSONObject();
        o.put("setName", "Set 17");
        JSONArray champs = new JSONArray();
        champs.put(new JSONArray());                     // tier 0: empty placeholder
        for (int t = 1; t <= 5; t++) {
            JSONArray tier = new JSONArray();
            tier.put("Champ" + t);
            champs.put(tier);
        }
        o.put("champs", champs);
        JSONArray size = new JSONArray();
        for (int i = 0; i < 6; i++) size.put(t1SizeFor(i));
        o.put("size", size);
        return o;
    }

    private static int t1SizeFor(int i) { return i == 0 ? 0 : (30 - i * 3); }

    // ---- valid case ----

    @Test public void validPayloadPasses() throws Exception {
        assertTrue(RemoteData.validate(valid()));
    }

    @Test public void tier0EmptyIsOk() throws Exception {
        // index 0 is explicitly NOT required to be non-empty
        JSONObject o = valid();
        assertTrue(RemoteData.validate(o)); // already has empty tier 0 — just confirm
    }

    // ---- required field missing ----

    @Test public void missingSetNameFails() throws Exception {
        JSONObject o = valid();
        o.remove("setName");
        assertFalse(RemoteData.validate(o));
    }

    @Test public void missingChampsFails() throws Exception {
        JSONObject o = valid();
        o.remove("champs");
        assertFalse(RemoteData.validate(o));
    }

    @Test public void missingSizeFails() throws Exception {
        JSONObject o = valid();
        o.remove("size");
        assertFalse(RemoteData.validate(o));
    }

    // ---- wrong array lengths ----

    @Test public void champsShortFails() throws Exception {
        JSONObject o = valid();
        // replace champs with only 5 entries
        JSONArray short5 = new JSONArray();
        for (int t = 1; t <= 5; t++) { JSONArray tier = new JSONArray(); tier.put("C"+t); short5.put(tier); }
        o.put("champs", short5);
        assertFalse(RemoteData.validate(o));
    }

    @Test public void sizeLongFails() throws Exception {
        JSONObject o = valid();
        JSONArray size7 = new JSONArray();
        for (int i = 0; i < 7; i++) size7.put(10);
        o.put("size", size7);
        assertFalse(RemoteData.validate(o));
    }

    // ---- empty tier 1-5 ----

    @Test public void emptyTierOneFails() throws Exception {
        JSONObject o = valid();
        JSONArray champs = o.getJSONArray("champs");
        champs.put(1, new JSONArray()); // replace tier 1 with empty array
        assertFalse(RemoteData.validate(o));
    }

    @Test public void emptyTierFiveFails() throws Exception {
        JSONObject o = valid();
        JSONArray champs = o.getJSONArray("champs");
        champs.put(5, new JSONArray());
        assertFalse(RemoteData.validate(o));
    }

    @Test public void nullTierTwoFails() throws Exception {
        // optJSONArray returns null when slot holds a non-array value
        JSONObject o = valid();
        JSONArray champs = o.getJSONArray("champs");
        champs.put(2, "not-an-array");
        assertFalse(RemoteData.validate(o));
    }

    // ---- extra fields are tolerated ----

    @Test public void extraFieldsIgnored() throws Exception {
        JSONObject o = valid();
        o.put("gods", new JSONArray());
        o.put("patch", "15.10");
        o.put("version", 42);
        assertTrue(RemoteData.validate(o));
    }
}
