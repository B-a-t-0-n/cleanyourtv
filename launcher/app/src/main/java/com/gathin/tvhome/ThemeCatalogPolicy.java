package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;

/** Only appearance belongs to a catalogue theme; personal shortcuts, actions and motion do not. */
final class ThemeCatalogPolicy {
    private ThemeCatalogPolicy() { }

    static boolean id(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9_-]{0,47}");
    }

    static boolean profile(String value) {
        return "before".equals(value) || value != null && value.matches("p[0-9a-f]{1,32}");
    }

    static boolean visual(String key) {
        return (" accent_color ambient_glow app_glow background_color cinematic_home cinematic_art cinematic_scrim"
                + " cinematic_dots cinematic_white_focus clock_wallpaper clock_wallpaper_dim columns dock_band"
                + " dock_band_all dock_even_padding dock_glass dock_opacity dock_row dock_title fade_rows festival"
                + " festival_until glass_style glow_style header_white home_idle_clock_minutes icon_only_buttons"
                + " icon_only_inputs rows_below_dock seasonal_skin show_greeting single_row sky_intensity"
                + " uniform_tiles wallpaper wallpaper_captions wallpaper_dim wallpaper_moods wallpaper_order"
                + " wallpaper_rotation_minutes wallpaper_true_color wifi_status_color ").contains(" " + key + " ");
    }

    /** Reject symlinks escaping the parent as well as lexical path traversal. */
    static File child(File parent, String name) throws IOException {
        File file = new File(parent, name).getCanonicalFile();
        if (!file.getPath().startsWith(parent.getCanonicalPath() + File.separator))
            throw new IOException("Theme path escapes its folder");
        return file;
    }
}
