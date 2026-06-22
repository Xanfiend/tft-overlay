package com.xanfiend.tftoverlay;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure static econ + cost helpers on Pool. No SharedPreferences touched. */
public class PoolMathTest {

    @Test public void interestCapsAtFive(){
        assertEquals(0, Pool.interest(0));
        assertEquals(3, Pool.interest(39));
        assertEquals(5, Pool.interest(50));
        assertEquals(5, Pool.interest(120));   // never exceeds 5
    }

    @Test public void toNextBracket(){
        assertEquals(10, Pool.toNextBracket(0));
        assertEquals(3,  Pool.toNextBracket(47));
        assertEquals(10, Pool.toNextBracket(50)); // next 10-bracket from a round number
    }

    @Test public void streakBonusTiersAreAsymmetric(){
        // win streak: +1 @3, +2 @5, +3 @6
        assertEquals(0, Pool.streakBonus(0));
        assertEquals(0, Pool.streakBonus(1));
        assertEquals(1, Pool.streakBonus(3));
        assertEquals(2, Pool.streakBonus(5));
        assertEquals(3, Pool.streakBonus(6));
        // loss streak pays earlier: +1 @2, +2 @4, +3 @5
        assertEquals(1, Pool.streakBonus(-2));
        assertEquals(2, Pool.streakBonus(-4));
        assertEquals(3, Pool.streakBonus(-5));
    }

    @Test public void expectedIncomeIncludesWinRoundBonus(){
        // 5 base + interest(50)=5 + streakBonus(4)=1 + win-round +1 -> 12
        assertEquals(12, Pool.expectedIncome(50, 4));
        // neutral: 5 base + 0 interest + 0 streak + no win bonus -> 5
        assertEquals(5, Pool.expectedIncome(0, 0));
        // a loss streak gets the streak bonus but NOT the win-round +1
        assertEquals(5 + Pool.interest(20) + Pool.streakBonus(-4), Pool.expectedIncome(20, -4));
    }

    @Test public void costOfKnownAndUnknown(){
        // unknown name -> 0
        assertEquals(0, Pool.costOf("NotAChampion__"));
        // first 1-cost in the live set roster resolves to cost 1
        String oneCost = SetData.CHAMPS[1].length > 0 ? SetData.CHAMPS[1][0] : null;
        if(oneCost != null) assertEquals(1, Pool.costOf(oneCost));
    }
}
