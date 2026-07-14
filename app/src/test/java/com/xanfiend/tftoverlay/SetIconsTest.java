package com.xanfiend.tftoverlay;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import androidx.test.core.app.ApplicationProvider;

/*
 * Loads the REAL bundled planner icons through the same code path the app uses
 * on device (AssetManager list + decode + roster name matching). A device
 * reported "set icons: 0 champions (0 images)" while the APK verifiably
 * contained all 122 PNGs — this pins the JVM-reachable part of the pipeline so
 * a regression in listing, name matching, or asset packaging fails CI.
 */
@RunWith(RobolectricTestRunner.class)
public class SetIconsTest {

    @Test public void bundledIconsLoadAndMatchTheRoster(){
        SetIcons.load(ApplicationProvider.getApplicationContext());
        // 61 champs have icons (MightyMech is a summon, no icon). Allow slack
        // for roster churn, but 0 = the pipeline is broken.
        assertTrue("expected >=55 champion icons, got " + SetIcons.champCount(),
                SetIcons.champCount() >= 55);
    }
}
