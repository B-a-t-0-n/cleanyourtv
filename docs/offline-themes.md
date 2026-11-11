# Offline theme catalogue (Station fork)

Launcher settings → **Themes · offline** opens a local catalogue, marks the current
theme, offers its picture credits and applies it with OK. It never downloads files.
The small APK contains no wallpaper bundle or INTERNET permission.

Reproduce the separate pack from this checkout with Python (no Android tools):

```sh
python tools/package-offline-themes.py --output ../cleanyourtv-offline-pack
```

The output must not exist. It contains `themes/` with the same Stockholm-first
Russian index, unchanged theme payloads, their credits, the project licence and
`theme-package-manifest.json` with SHA-256 hashes. The manifest covers every
pack file except itself. No archive, download or device operation is needed.

Install the pack separately under the application's external files directory:

```text
themes/index.json
themes/stockholm/config.json
themes/stockholm/screenshot.jpg       optional; retained, not rendered by the compact picker
themes/stockholm/CREDITS.md           when the theme has picture credits
themes/stockholm/wallpaper.jpg        for a single picture, or
themes/stockholm/wallpapers/*.jpg     jpg, jpeg, png and webp are accepted
themes/stockholm/wallpapers/captions.txt
```

`index.json` is UTF-8, at most 32 KiB:

```json
{"themes":[{"id":"stockholm","name":"Stockholm"}]}
```

IDs match `[a-z0-9][a-z0-9_-]{0,47}`; names are at most 80 characters. There may be
up to 64 entries. The Station pack contains the repository's 29 themes, their
original configs, 124 pictures, previews and credits. Keep the attribution files
and their original licences; the project MIT licence does not replace them.

Only a defined set of appearance keys is imported. Personal dock/hidden/library
choices, app labels, remote/input/startup actions, locale and `ui_scale_percent`
stay current. The effective `animations`, `reduced_motion`, `advanced_effects`
and `focus_overshoot` are preserved, including on-TV motion overrides. A theme
with no `columns` keeps the current count; an explicit count belongs to that theme.
On-TV wallpaper and layout overrides are reset so the chosen appearance can show.

Before the first selection, **Before choosing a theme** saves the original
appearance and assets, including an on-TV choice of one wallpaper or "none".
It can be selected later while keeping current apps,
actions, scale and motion. It restores appearance, not old personal shortcuts.

Copying runs on one background executor. Only known assets are copied, with
canonical containment checks, file/count/collection limits. A complete staged
folder is renamed to `themes/.profiles/p<hex>/` before publication. `themes/active`
is an Android `AtomicFile`: its first UTF-8 line is the generated profile name,
its second is the catalogue ID, and both end with a newline. The saved original
is `themes/.profiles/before/`. Each profile includes `config.json` and a unique
`theme_revision`, which drives the existing settings revision mechanism.

`Config.dir()` resolves the current valid published profile without calling
`Config.load()`. Missing/broken pointers fall back to the original external
files directory. A failed copy or pointer write leaves the previous selection
active. The root config, original pictures, saved tile ordering and library
files are never deleted. Published/staging folders are retained for recovery;
there is no automatic cleanup of user storage in this version.

The permission-protected ThemeProvider always writes/deletes in the original
external files root and clears the catalogue pointer for an explicit external
theme upload. Published profiles remain immutable. A direct shell copy bypasses
the provider: remove `themes/active` and `themes/active.bak` explicitly to select
the root theme, then restart Home using the existing external theme tools.

The catalogue feature currently adds about 5 KiB beyond the upstream 576 KiB
APK budget. The upstream size gate and CI are unchanged, so this fork's feature
branch does not yet pass that size check. Launcher unit tests, lint and the
release build are checked separately; passing them does not imply the size
gate passes. Wallpapers remain outside the APK.
