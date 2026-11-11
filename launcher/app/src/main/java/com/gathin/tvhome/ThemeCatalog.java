package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Offline, externally installed theme packs. A complete immutable profile is published by one pointer. */
final class ThemeCatalog {
    private ThemeCatalog() { }

    static final class Entry {
        final String id, name;
        final File dir;
        final boolean current;

        Entry(String id, String name, File dir, boolean current) {
            this.id = id;
            this.name = name;
            this.dir = dir;
            this.current = current;
        }

        File file(String name) throws IOException { return ThemeCatalogPolicy.child(dir, name); }
    }

    private static File catalog(Context ctx) throws IOException {
        File root = ctx.getExternalFilesDir(null);
        if (root == null) throw new IOException("Storage unavailable");
        return ThemeCatalogPolicy.child(root, "themes");
    }

    private static synchronized String[] selection(File themes) throws IOException {
        AtomicFile pointer = new AtomicFile(ThemeCatalogPolicy.child(themes, "active"));
        try (FileInputStream in = pointer.openRead()) {
            return new String(read(in, 128), StandardCharsets.UTF_8).split("\n", -1);
        }
    }

    /** An explicit legacy provider upload selects the root theme, never edits a published profile. */
    static synchronized void externalTheme(Context ctx) throws IOException {
        new AtomicFile(ThemeCatalogPolicy.child(catalog(ctx), "active")).delete();
    }

    /** No Config.load here: Config.dir and the locale/scale wrapper both call this. */
    static File activeDir(Context ctx, File fallback) {
        try {
            File themes = catalog(ctx);
            String[] value = selection(themes);
            if (ThemeCatalogPolicy.profile(value[0])) {
                File profiles = ThemeCatalogPolicy.child(themes, ".profiles");
                File dir = ThemeCatalogPolicy.child(profiles, value[0]);
                if (ThemeCatalogPolicy.child(dir, "config.json").isFile()) return dir;
            }
        } catch (IOException | RuntimeException ignored) { }
        return fallback;
    }

    private static byte[] read(FileInputStream in, int limit) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (bytes.size() + count > limit) throw new IOException("Theme file is too large");
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    private static String text(File file, int limit) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            String value = new String(read(in, limit), StandardCharsets.UTF_8);
            return value.startsWith("\uFEFF") ? value.substring(1) : value;
        }
    }

    private static JSONObject config(File dir) throws Exception {
        File file = ThemeCatalogPolicy.child(dir, "config.json");
        return file.isFile() ? new JSONObject(text(file, 65536)) : new JSONObject();
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
            out.getFD().sync();
        }
    }

    private static void copy(File from, File to) throws IOException {
        if (from.isFile()) try (FileInputStream in = new FileInputStream(from)) {
            write(to, read(in, 8 * 1024 * 1024));
        }
    }

    /** Only known theme assets; never traverses unrelated user files or the catalogue itself. */
    private static void media(File from, File to) throws IOException {
        for (String name : new String[]{"wallpaper.jpg", "screenshot.jpg", "CREDITS.md"})
            copy(ThemeCatalogPolicy.child(from, name), ThemeCatalogPolicy.child(to, name));
        File source = ThemeCatalogPolicy.child(from, "wallpapers");
        File[] files = source.listFiles();
        if (files == null) return;
        if (files.length > 129) throw new IOException("Too many wallpapers");
        File target = ThemeCatalogPolicy.child(to, "wallpapers");
        if (!target.mkdir() && !target.isDirectory()) throw new IOException("Cannot create wallpaper folder");
        long total = 0;
        for (File file : files) {
            String name = file.getName();
            if (!ThemeFiles.collectionImage(name) && !"captions.txt".equals(name)) continue;
            File safe = ThemeCatalogPolicy.child(source, name);
            total += safe.length();
            if (total > 64 * 1024 * 1024) throw new IOException("Theme collection is too large");
            copy(safe, ThemeCatalogPolicy.child(target, name));
        }
    }

    private static void overlay(JSONObject target, JSONObject source) throws Exception {
        for (Iterator<String> keys = source.keys(); keys.hasNext();) {
            String key = keys.next();
            if (ThemeCatalogPolicy.visual(key)) target.put(key, source.get(key));
        }
    }

    private static void effective(JSONObject json, Config cfg) throws Exception {
        json.put("columns", cfg.columns).put("rows_below_dock", cfg.rowsBelowDock)
                .put("dock_opacity", cfg.dockOpacity).put("clock_format", cfg.clockFormat);
        motion(json, cfg);
    }

    private static void motion(JSONObject json, Config cfg) throws Exception {
        json.put("animations", cfg.animations).put("reduced_motion", cfg.reducedMotion)
                .put("advanced_effects", cfg.advancedEffects).put("focus_overshoot", cfg.focusOvershoot);
    }

    private static void profile(File from, File profiles, String name, JSONObject json) throws Exception {
        File stage = ThemeCatalogPolicy.child(profiles, ".stage-" + name + Long.toHexString(System.nanoTime()));
        File ready = ThemeCatalogPolicy.child(profiles, name);
        if (!stage.mkdir()) throw new IOException("Cannot stage theme");
        media(from, stage);
        write(new File(stage, "config.json"), json.toString().getBytes(StandardCharsets.UTF_8));
        write(new File(stage, "theme_revision"), name.getBytes(StandardCharsets.US_ASCII));
        if (!stage.renameTo(ready)) throw new IOException("Cannot publish theme folder");
    }

    static void apply(Context ctx, String id) throws Exception {
        File themes = catalog(ctx);
        File profiles = ThemeCatalogPolicy.child(themes, ".profiles");
        if (!profiles.mkdirs() && !profiles.isDirectory()) throw new IOException("Cannot create theme profiles");
        File current = Config.dir(ctx);
        Config cfg = Config.load(ctx);
        JSONObject live = config(current);
        File before = ThemeCatalogPolicy.child(profiles, "before");
        if (!before.isDirectory()) {
            JSONObject original = new JSONObject(live.toString());
            effective(original, cfg);
            SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
            if (choices.contains(Config.WALLPAPER_CHOICE_KEY))
                original.put("_catalog_wallpaper_choice", choices.getString(Config.WALLPAPER_CHOICE_KEY, null));
            profile(current, profiles, "before", original);
        }
        File source = id.isEmpty() ? before : ThemeCatalogPolicy.child(themes, id);
        File themeFile = ThemeCatalogPolicy.child(source, "config.json");
        if (!themeFile.isFile()) throw new IOException("Theme configuration is missing");
        JSONObject theme = config(source);
        JSONObject merged = new JSONObject(live.toString());
        List<String> remove = new ArrayList<>();
        for (Iterator<String> keys = merged.keys(); keys.hasNext();) {
            String key = keys.next();
            if (ThemeCatalogPolicy.visual(key)) remove.add(key);
        }
        for (String key : remove) merged.remove(key);
        overlay(merged, config(before));
        overlay(merged, theme);
        if (!theme.has("columns")) merged.put("columns", cfg.columns);
        merged.put("clock_format", cfg.clockFormat);
        motion(merged, cfg);
        String name = "p" + Long.toHexString(System.nanoTime());
        profile(source, profiles, name, merged);
        synchronized (ThemeCatalog.class) {
            AtomicFile pointer = new AtomicFile(ThemeCatalogPolicy.child(themes, "active"));
            FileOutputStream out = pointer.startWrite();
            try {
                out.write((name + "\n" + id + "\n").getBytes(StandardCharsets.UTF_8));
                pointer.finishWrite(out);
            } catch (IOException | RuntimeException e) {
                pointer.failWrite(out);
                throw e;
            }
        }
        // Config's external-theme revision clears on-TV overrides. Keep effective motion choices
        // after that sync, while letting the selected theme supply layout and wallpaper choices.
        Config.load(ctx);
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = choices.edit().putBoolean("animations", cfg.animations)
                .putBoolean("advanced_effects", cfg.advancedEffects)
                .putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1);
        if (id.isEmpty()) {
            if (theme.has("_catalog_wallpaper_choice")) edit.putString(Config.WALLPAPER_CHOICE_KEY,
                    theme.getString("_catalog_wallpaper_choice"));
            else edit.remove(Config.WALLPAPER_CHOICE_KEY);
        }
        edit.commit();
    }

    /** Read only; callers perform disk work off the UI thread. */
    static List<Entry> entries(Context ctx) throws Exception {
        File themes = catalog(ctx);
        JSONArray index = new JSONObject(text(ThemeCatalogPolicy.child(themes, "index.json"), 32768))
                .getJSONArray("themes");
        if (index.length() > 64) throw new IOException("Too many themes");
        String current;
        try { current = selection(themes)[1]; } catch (IOException | RuntimeException e) { current = null; }
        List<Entry> entries = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        File before = ThemeCatalogPolicy.child(themes, ".profiles/before");
        if (ThemeCatalogPolicy.child(before, "config.json").isFile())
            entries.add(new Entry("", ctx.getString(R.string.themes_restore), before, "".equals(current)));
        for (int i = 0; i < index.length(); i++) {
            JSONObject item = index.getJSONObject(i);
            String id = item.getString("id"), label = item.getString("name");
            if (!ThemeCatalogPolicy.id(id) || label.isEmpty() || label.length() > 80 || ids.contains(id)) continue;
            File dir = ThemeCatalogPolicy.child(themes, id);
            if (!ThemeCatalogPolicy.child(dir, "config.json").isFile()) continue;
            ids.add(id);
            entries.add(new Entry(id, label, dir, id.equals(current)));
        }
        if (entries.isEmpty()) throw new IOException("No installed themes");
        return entries;
    }

    static String credits(Entry entry) throws IOException {
        return text(entry.file("CREDITS.md"), 65536);
    }
}
