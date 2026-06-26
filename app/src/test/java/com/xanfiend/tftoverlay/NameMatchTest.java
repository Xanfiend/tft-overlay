package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;

/* Pure JVM tests for the fuzzy champion-name matcher. */
public class NameMatchTest {

    private static final List<String> ROSTER = Arrays.asList(
        "Jhin", "Vex", "Corki", "Samira", "Kai'Sa", "Bel'Veth", "AurelionSol", "TwistedFate");

    // ---- norm ----
    @Test public void normStripsToAz() {
        assertEquals("kaisa", NameMatch.norm("Kai'Sa"));
        assertEquals("belveth", NameMatch.norm("Bel'Veth"));
        assertEquals("twistedfate", NameMatch.norm("TwistedFate"));
        assertEquals("", NameMatch.norm(null));
        assertEquals("aurelionsol", NameMatch.norm("Aurelion Sol 2"));
    }

    // ---- editDistance ----
    @Test public void editDistanceBasics() {
        assertEquals(0, NameMatch.editDistance("jhin", "jhin"));
        assertEquals(1, NameMatch.editDistance("jhin", "jhln"));
        assertEquals(1, NameMatch.editDistance("samira", "samlra"));
        assertEquals(4, NameMatch.editDistance("", "vex".concat("x")));
    }

    // ---- tolerance ----
    @Test public void toleranceScalesWithLength() {
        assertEquals(0, NameMatch.tolerance(4));   // short = exact only
        assertEquals(1, NameMatch.tolerance(5));
        assertEquals(1, NameMatch.tolerance(7));
        assertEquals(2, NameMatch.tolerance(8));
        assertEquals(2, NameMatch.tolerance(11));
    }

    // ---- bestMatch: exact ----
    @Test public void exactMatches() {
        assertEquals("Jhin", NameMatch.bestMatch("Jhin", ROSTER));
        assertEquals("Vex", NameMatch.bestMatch("vex", ROSTER));
        assertEquals("Kai'Sa", NameMatch.bestMatch("KaiSa", ROSTER));   // apostrophe-insensitive
    }

    // ---- bestMatch: tolerates OCR slips on longer names ----
    @Test public void recoversSingleOcrError() {
        assertEquals("Corki", NameMatch.bestMatch("Corkl", ROSTER));        // i->l, len5 tol1
        assertEquals("Samira", NameMatch.bestMatch("Samlra", ROSTER));      // i->l, len6 tol1
        assertEquals("AurelionSol", NameMatch.bestMatch("AurelionSel", ROSTER)); // o->e, len11 tol2
    }

    // ---- bestMatch: short names stay exact-only ----
    @Test public void shortNamesNoFuzzy() {
        assertNull(NameMatch.bestMatch("Jhln", ROSTER));   // 4-char target, no fuzzy
        assertNull(NameMatch.bestMatch("Vox", ROSTER));    // would be Vex but exact-only
    }

    // ---- bestMatch: containment path (OCR ⊆ target or target ⊆ OCR, >=80% overlap) ----
    @Test public void containmentTargetContainsOcr() {
        // "twistedfat" is 10/11 chars of "TwistedFate" -> containment fires, d=1
        assertEquals("TwistedFate", NameMatch.bestMatch("TwistedFat",
                Arrays.asList("TwistedFate", "Samira")));
    }
    @Test public void containmentOcrContainsTarget() {
        // OCR has trailing junk: "SamiraQ" contains "samira" at 6/7 > 80%
        assertEquals("Samira", NameMatch.bestMatch("SamiraQ",
                Arrays.asList("Corki", "Samira")));
    }
    @Test public void containmentTooShortForEightyPctRule() {
        // "Twisted" (7) vs "TwistedFate" (11): 7/11 < 80% -> containment not triggered;
        // edit distance = 4, tolerance(11) = 2 -> no match
        assertNull(NameMatch.bestMatch("Twisted", Arrays.asList("TwistedFate")));
    }

    // ---- bestMatch: rejects junk and ambiguity ----
    @Test public void rejectsJunk() {
        assertNull(NameMatch.bestMatch("zzzzz", ROSTER));
        assertNull(NameMatch.bestMatch("ab", ROSTER));     // too short
    }
    @Test public void rejectsAmbiguousTie() {
        // both candidates are edit-distance 1 from "aaaaa" -> ambiguous -> null
        assertNull(NameMatch.bestMatch("aaaaa", Arrays.asList("aaaab", "aaaac")));
    }
}
