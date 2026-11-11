package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

public class ThemeCatalogPolicyTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void onlyPlainCatalogueAndGeneratedProfileIdsAreAccepted() {
        assertTrue(ThemeCatalogPolicy.id("istanbul_v2"));
        assertTrue(ThemeCatalogPolicy.id("ambient-glass"));
        for (String id : new String[]{"", ".profiles", "../stockholm", "a/b", "a\\b", "Stockholm", "a\n"})
            assertFalse(id, ThemeCatalogPolicy.id(id));
        assertTrue(ThemeCatalogPolicy.profile("before"));
        assertTrue(ThemeCatalogPolicy.profile("p123abc"));
        assertFalse(ThemeCatalogPolicy.profile("p../a"));
        assertFalse(ThemeCatalogPolicy.profile("stockholm"));
    }

    @Test public void appearanceCanChangeWithoutReplacingPersonalOrMotionKeys() {
        for (String key : new String[]{"columns", "wallpaper_moods", "dock_glass", "wallpaper", "seasonal_skin", "wifi_status_color"})
            assertTrue(key, ThemeCatalogPolicy.visual(key));
        for (String key : new String[]{"dock_apps", "hidden_apps", "app_labels", "fill_dock", "app_keys",
                "library_apps", "app_library_selection", "launch_actions", "boot_to_live_tv", "input_action",
                "ui_scale_percent", "locale", "animations", "reduced_motion", "advanced_effects", "focus_overshoot"})
            assertFalse(key, ThemeCatalogPolicy.visual(key));
    }

    @Test public void nestedOwnedFilesAreAllowedButEscapesAreRejected() throws IOException {
        File root = temporary.newFolder("themes");
        assertEquals(new File(root, ".profiles/before/config.json").getCanonicalFile(),
                ThemeCatalogPolicy.child(root, ".profiles/before/config.json"));
        try {
            ThemeCatalogPolicy.child(root, "../other/config.json");
            fail("escaped the catalogue");
        } catch (IOException expected) { }
        try {
            ThemeCatalogPolicy.child(root, "../themes-other/config.json");
            fail("a shared prefix is not containment");
        } catch (IOException expected) { }
    }
}
