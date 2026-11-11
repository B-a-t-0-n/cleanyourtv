package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LauncherScaleTest {
    @Test
    public void scaleIsOptInAndAcceptsOnlyWholeNumbersInRange() {
        assertEquals(100, LauncherScale.percent(null));
        assertEquals(100, LauncherScale.percent("160"));
        assertEquals(100, LauncherScale.percent(true));
        assertEquals(100, LauncherScale.percent(99));
        assertEquals(100, LauncherScale.percent(201));
        assertEquals(100, LauncherScale.percent(160.5));
        assertEquals(100, LauncherScale.percent(Double.NaN));
        assertEquals(100, LauncherScale.percent(Double.POSITIVE_INFINITY));
        assertEquals(100, LauncherScale.percent(100));
        assertEquals(160, LauncherScale.percent(160));
        assertEquals(160, LauncherScale.percent(160.0));
        assertEquals(200, LauncherScale.percent(200L));
    }

    @Test
    public void stationScaleKeepsTheFullHdPhysicalWindow() {
        int density = LauncherScale.densityDpi(160, 160);
        assertEquals(256, density);
        assertEquals(1200, LauncherScale.screenDp(1920, 160, density));
        assertEquals(675, LauncherScale.screenDp(1080, 160, density));
        assertEquals(288, Math.round(180 * density / 160f));
    }

    @Test
    public void roundedDensityIsAlsoUsedForDpDimensions() {
        int density = LauncherScale.densityDpi(213, 160);
        assertEquals(341, density);
        assertEquals(600, LauncherScale.screenDp(960, 213, density));
        assertEquals(213, LauncherScale.densityDpi(213, 100));
        assertEquals(1000, LauncherScale.screenDp(1000, 213, 213));
        assertEquals(0, LauncherScale.screenDp(0, 213, density));
    }
}
