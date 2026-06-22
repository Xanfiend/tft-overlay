package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Well-formedness of the static reference tables (OPENER / AUGMENTS / TRAITS). */
public class StaticDataTest {

    @Test public void openerTablesAreWellFormed(){
        assertTrue(OpenerData.PHASES.length > 0);
        for(String[] row : OpenerData.PHASES) assertTrue(row.length > 0);
        assertTrue(OpenerData.SLAMS.length > 0);
        for(String[] row : OpenerData.SLAMS) assertTrue(row.length > 0);
        assertTrue(OpenerData.PRINCIPLES.length > 0);
        for(String p : OpenerData.PRINCIPLES) assertFalse(p.trim().isEmpty());
    }

    @Test public void augmentsHaveNameAndValidTier(){
        assertTrue(AugmentData.AUGMENTS.length > 0);
        for(AugmentData.AugmentEntry a : AugmentData.AUGMENTS){
            assertFalse("augment name must be set", a.name.trim().isEmpty());
            assertTrue("tier '"+a.tier+"' for "+a.name+" must be S/A/B/C",
                Arrays.asList("S","A","B","C").contains(a.tier));
            assertNotNull("comps must be non-null (may be empty)", a.comps);
        }
    }

    @Test public void traitRowsHaveNameBreakpointEffect(){
        assertTrue(TraitData.TRAITS.length > 0);
        for(String[] row : TraitData.TRAITS){
            assertEquals("each trait row is {name, breakpoints, effect}", 3, row.length);
            assertFalse("trait name must be set", row[0].trim().isEmpty());
            assertFalse("breakpoints must be set", row[1].trim().isEmpty());
        }
    }
}
