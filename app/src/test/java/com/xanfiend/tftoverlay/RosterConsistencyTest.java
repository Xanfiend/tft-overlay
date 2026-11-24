package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** Per-champion tables must only name champions in the current SetData roster,
 *  so a set refresh can't leave stale names behind. */
public class RosterConsistencyTest {

    private static Set<String> roster(){
        Set<String> r = new HashSet<>();
        for(String[] tier : SetData.CHAMPS) for(String c : tier) r.add(c);
        return r;
    }

    private static void assertSubsetOfRoster(String table, Set<String> names){
        Set<String> stale = new HashSet<>(names);
        stale.removeAll(roster());
        assertTrue(table+" names champions not in SetData.CHAMPS: "+stale, stale.isEmpty());
    }

    @Test public void champItemDataNamesAreInRoster(){ assertSubsetOfRoster("ChampItemData", ChampItemData.names()); }

    @Test public void threatDataNamesAreInRoster(){ assertSubsetOfRoster("ThreatData", ThreatData.names()); }
}
