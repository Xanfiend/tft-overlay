package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** Positioning roles + archetype tags. Meta-stable, so worth locking down. */
public class ThreatDataTest {

    @Test public void knownRoles(){
        assertEquals(ThreatData.FRONT, ThreatData.roleOf("Aatrox"));
        assertEquals(ThreatData.FLANK, ThreatData.roleOf("Talon"));
    }

    @Test public void roleIsAlwaysOneOfThree(){
        Set<String> valid = new HashSet<>(Arrays.asList(
            ThreatData.FRONT, ThreatData.BACK, ThreatData.FLANK));
        // every champ on the live roster resolves to a real placement role
        for(int c = 1; c <= 5; c++)
            for(String champ : SetData.CHAMPS[c])
                assertTrue(champ + " has no valid role", valid.contains(ThreatData.roleOf(champ)));
        // even an unknown name falls back to a real role, never null/empty
        assertTrue(valid.contains(ThreatData.roleOf("__unknown__")));
    }

    @Test public void archetypeTags(){
        assertTrue(ThreatData.isHook("Blitzcrank"));
        assertFalse(ThreatData.isHook("Aatrox"));
        assertTrue(ThreatData.isAoe("AurelionSol"));
    }

    @Test public void damageTypeIsApAdOrEmpty(){
        for(String champ : new String[]{"Aatrox","Talon","AurelionSol","__x__"}){
            String dt = ThreatData.damageType(champ);
            assertTrue(dt.equals("AP") || dt.equals("AD") || dt.equals(""));
        }
    }
}
