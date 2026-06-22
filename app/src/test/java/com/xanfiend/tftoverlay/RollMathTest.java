package com.xanfiend.tftoverlay;

import org.junit.Test;
import static org.junit.Assert.*;

/** Roll hit-probability sanity: results stay in [0,1] and degenerate inputs are safe. */
public class RollMathTest {

    @Test public void probabilitiesStayInRange(){
        double[] p = RollMath.hitChances(8, 4, 9, 12, 50, 3);
        assertEquals(3, p.length);
        for(double x : p){ assertTrue(x >= 0.0); assertTrue(x <= 1.0); }
    }

    @Test public void zeroGoldZeroChance(){
        double[] p = RollMath.hitChances(8, 4, 9, 12, 0, 1);
        assertEquals(0.0, p[0], 1e-9);
    }

    @Test public void invalidLevelIsSafe(){
        double[] p = RollMath.hitChances(99, 4, 9, 12, 50, 1);
        assertEquals(0.0, p[0], 1e-9); // out-of-range level returns zeros, no crash
    }

    @Test public void moreGoldNeverLowersHitChance(){
        double low  = RollMath.hitChances(8, 4, 9, 12, 20, 1)[0];
        double high = RollMath.hitChances(8, 4, 9, 12, 60, 1)[0];
        assertTrue("more rolls should not reduce P(>=1 copy)", high >= low);
    }
}
