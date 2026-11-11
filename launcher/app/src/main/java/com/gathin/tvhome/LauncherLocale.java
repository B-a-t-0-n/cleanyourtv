package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Environment;
import android.util.DisplayMetrics;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * locale: the language the launcher shows itself in, instead of the TV's. A China-market Xiaomi offers Chinese and
 * English only, so a Vietnamese owner saw the launcher in Chinese although it is translated into Vietnamese.
 * Each activity wraps its base context here; an empty or malformed tag leaves the TV's language.
 */
final class LauncherLocale {
    // Subtags after the language, so "sr-Latn" and "ar-EG-u-nu-latn" (Arabic with Western digits) pass too.
    private static final Pattern TAG = Pattern.compile("[a-z]{2,3}(-[A-Za-z0-9]{1,8})*");

    private LauncherLocale() {
    }

    /** The Locale for a config tag such as "vi", "pt-BR" or "ar-EG-u-nu-latn", or null for "" or anything that is not a plain tag. */
    static Locale parse(String tag) {
        if (tag == null || !TAG.matcher(tag.trim()).matches()) return null;
        return Locale.forLanguageTag(tag.trim());
    }

    /** Activity-local language and optional UI scale; the TV and other apps keep their resources. */
    static Context wrap(Context base) {
        Locale locale = parse(Config.load(base).locale);
        int scale = readScale(base);
        if (locale == null && scale == LauncherScale.DEFAULT_PERCENT) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        if (locale != null) {
            // Formatters that take no Context (the clock's date, the lunar line) follow the default locale.
            Locale.setDefault(locale);
            c.setLocale(locale);
        }
        if (scale != LauncherScale.DEFAULT_PERCENT) {
            int density = c.densityDpi;
            if (density <= 0) density = base.getResources().getDisplayMetrics().densityDpi;
            if (density <= 0) density = DisplayMetrics.DENSITY_DEFAULT;
            int scaled = LauncherScale.densityDpi(density, scale);
            c.screenWidthDp = LauncherScale.screenDp(c.screenWidthDp, density, scaled);
            c.screenHeightDp = LauncherScale.screenDp(c.screenHeightDp, density, scaled);
            c.smallestScreenWidthDp = LauncherScale.screenDp(c.smallestScreenWidthDp, density, scaled);
            c.densityDpi = scaled;
            // Keep fontScale: Android derives scaledDensity from this density and the user's font size.
        }
        return base.createConfigurationContext(c);
    }

    /** Same overlay location, boot fallback, size limit and UTF-8 BOM handling as Config. Never writes. */
    private static int readScale(Context base) {
        File dir = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                ? Config.dir(base) : base.getFilesDir();
        File file = dir == null ? null : new File(dir, Config.FILE_NAME);
        if (file == null || !file.isFile() || file.length() > 64 * 1024) return LauncherScale.DEFAULT_PERCENT;
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = 0, read;
            while (n < bytes.length && (read = in.read(bytes, n, bytes.length - n)) > 0) n += read;
            String text = new String(bytes, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            return LauncherScale.percent(new JSONObject(text).opt("ui_scale_percent"));
        } catch (Exception ignored) {
            return LauncherScale.DEFAULT_PERCENT;
        }
    }
}
