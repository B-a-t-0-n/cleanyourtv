package com.gathin.tvhome;

/** Pure arithmetic for the opt-in config.json ui_scale_percent (100..200, default 100). */
final class LauncherScale {
    static final int DEFAULT_PERCENT = 100;

    private LauncherScale() {
    }

    /** Wrong types, fractions and out-of-range values keep the historical size. */
    static int percent(Object value) {
        if (!(value instanceof Number)) return DEFAULT_PERCENT;
        double number = ((Number) value).doubleValue();
        return number >= 100 && number <= 200 && number == Math.rint(number)
                ? (int) number : DEFAULT_PERCENT;
    }

    static int densityDpi(int original, int percent) {
        return (int) Math.round(original * (percent / 100.0));
    }

    /** Preserve the physical window and undefined (0) configuration dimensions. */
    static int screenDp(int original, int densityDpi, int scaledDensityDpi) {
        if (original <= 0 || densityDpi <= 0 || scaledDensityDpi <= 0) return original;
        return Math.max(1, (int) Math.round(original * (double) densityDpi / scaledDensityDpi));
    }
}
