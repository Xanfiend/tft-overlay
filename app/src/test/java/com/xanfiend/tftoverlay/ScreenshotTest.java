package com.xanfiend.tftoverlay;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.robolectric.Shadows.shadowOf;

/*
 * Renders the real overlay tabs and the launch screen to PNG for the README.
 * Off by default: runs only with SCREENSHOTS=1 (the Screenshots workflow).
 * Output goes to app/build/screenshots; a failed render is written to
 * errors.txt instead of failing the build.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33)
public class ScreenshotTest {

    private static final int BG = 0xFF0B0709;
    private final File out = new File("build/screenshots");
    private final StringBuilder errors = new StringBuilder();

    @Before public void gate(){
        Assume.assumeTrue("1".equals(System.getenv("SCREENSHOTS")));
        out.mkdirs();
    }

    private void seed(){
        Pool p = new Pool(ApplicationProvider.getApplicationContext());
        p.setPrivacySeen(true);
        p.setGold(42); p.setLevel(7); p.setXp(12, 48); p.setStreak(3);
        p.setHp(68); p.setStageRoundNums(4, 2); p.setWins(5); p.setLosses(3);
        p.add("Ahri", 4); p.add("Morgana", 2); p.add("Sett", 3); p.add("Karma", 5);
        p.add("Yorick", 6); p.add("Teemo", 3); p.add("Kayle", 2); p.add("Cassiopeia", 3);
        p.addOpp("Ahri", 2); p.addOpp("Morgana", 1); p.addOpp("Karma", 1);
        p.setPinned("Ahri");
        p.learnTraits("Ahri", Arrays.asList("Blossom", "Spellweaver"));
        p.learnTraits("Karma", Arrays.asList("Blossom", "Spellweaver"));
        p.learnTraits("Sett", Arrays.asList("Blossom", "Brawler"));
        p.learnTraits("Morgana", Arrays.asList("Coven", "Invoker"));
        p.learnTraits("Yorick", Arrays.asList("Blossom", "Juggernaut", "Summoner"));
    }

    private static void render(View v, int w, int h, File f) throws Exception {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(BG);
        v.draw(c);
        try (FileOutputStream fos = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, fos); }
        b.recycle();
    }

    private void fail(String what, Throwable t){
        StringWriter sw = new StringWriter(); t.printStackTrace(new PrintWriter(sw));
        errors.append("== ").append(what).append('\n').append(sw).append('\n');
    }

    private void flushErrors() throws Exception {
        if (errors.length() == 0) return;
        try (FileOutputStream fos = new FileOutputStream(new File(out, "errors.txt"), true)) {
            fos.write(errors.toString().getBytes("UTF-8"));
        }
    }

    // landscape phone, 2x density → 1746 x 786 px
    @Config(qualifiers = "w873dp-h393dp-land-xhdpi")
    @Test public void overlayTabs() throws Exception {
        seed();
        try {
            OverlayService svc = Robolectric.buildService(OverlayService.class).create().get();
            Field mode = OverlayService.class.getDeclaredField("mode"); mode.setAccessible(true);
            Field panel = OverlayService.class.getDeclaredField("panel"); panel.setAccessible(true);
            Field panelLp = OverlayService.class.getDeclaredField("panelLp"); panelLp.setAccessible(true);
            Method show = OverlayService.class.getDeclaredMethod("showPanel"); show.setAccessible(true);
            String[] names = {"pool", "odds", "guide", "gold", "setup", "builds"};
            for (int m = 0; m < names.length; m++) {
                try {
                    mode.setInt(svc, m);
                    show.invoke(svc);
                    shadowOf(Looper.getMainLooper()).idle();
                    View v = (View) panel.get(svc);
                    android.view.WindowManager.LayoutParams lp =
                        (android.view.WindowManager.LayoutParams) panelLp.get(svc);
                    render(v, lp.width, lp.height, new File(out, "tab-" + names[m] + ".png"));
                } catch (Throwable t) { fail("tab " + names[m], t); }
            }
        } catch (Throwable t) { fail("service", t); }
        flushErrors();
    }

    // portrait phone, 2x density → 786 x 1746 px
    @Config(qualifiers = "w393dp-h873dp-port-xhdpi")
    @Test public void launchScreen() throws Exception {
        seed();
        try {
            Activity a = Robolectric.buildActivity(MainActivity.class).setup().get();
            shadowOf(Looper.getMainLooper()).idle();
            View d = a.getWindow().getDecorView();
            Context ctx = ApplicationProvider.getApplicationContext();
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            render(d, dm.widthPixels, dm.heightPixels, new File(out, "launch.png"));
        } catch (Throwable t) { fail("launch", t); }
        flushErrors();
    }
}
