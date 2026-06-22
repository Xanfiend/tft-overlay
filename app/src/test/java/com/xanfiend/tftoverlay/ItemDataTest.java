package com.xanfiend.tftoverlay;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/** Item combine matrix + the Phase-2 item-property tags. Pure. */
public class ItemDataTest {

    @Test public void combosAreSymmetric(){
        for(int i=1;i<ItemData.COMBOS.length;i++)
            for(int j=1;j<ItemData.COMBOS.length;j++)
                assertEquals("COMBOS["+i+"]["+j+"] must equal ["+j+"]["+i+"]",
                    ItemData.COMBOS[i][j], ItemData.COMBOS[j][i]);
    }

    @Test public void knownCombines(){
        assertEquals("Infinity Edge", ItemData.COMBOS[1][1]);   // BF + BF
        assertEquals("Dragon's Claw", ItemData.COMBOS[9][9]);   // Negatron + Negatron
    }

    @Test public void fullItemsDistinctNonEmpty(){
        List<String> items = ItemData.fullItems();
        assertFalse(items.isEmpty());
        assertEquals("fullItems must be de-duplicated",
            items.size(), new java.util.HashSet<>(items).size());
        assertTrue(items.contains("Infinity Edge"));
    }

    @Test public void itemPropertyTags(){
        assertTrue(ItemData.isAdItem("Infinity Edge"));
        assertTrue(ItemData.isApItem("Rabadon's Deathcap"));
        assertTrue(ItemData.isHealItem("Bloodthirster"));
        // a tank item is none of the damage/heal tags
        assertFalse(ItemData.isAdItem("Bramble Vest"));
        assertFalse(ItemData.isApItem("Bramble Vest"));
        assertFalse(ItemData.isHealItem("Bramble Vest"));
        // junk / null safe
        assertFalse(ItemData.isAdItem("___"));
        assertFalse(ItemData.isApItem(null));
    }
}
