package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Browsing changes only this preview. Theme publication requires the separate Apply button. */
public final class ThemeGalleryActivity extends Activity {
    // Like the former picker, serialize applies even when Home closes/reopens an Activity mid-copy.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final List<ThemeCatalog.Entry> entries = new ArrayList<>();
    private RecyclerView list;
    private ImageView preview;
    private TextView name, position, fallback, hint, apply, credits, back;
    private int selected = -1, generation;
    private boolean busy, focusPending;
    private Future<?> previewJob;
    private Runnable pendingPreview;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_theme_gallery);
        name = findViewById(R.id.themeName);
        position = findViewById(R.id.themePosition);
        preview = findViewById(R.id.themePreview);
        fallback = findViewById(R.id.themePreviewFallback);
        hint = findViewById(R.id.themeHint);
        apply = findViewById(R.id.themeApply);
        credits = findViewById(R.id.themeCredits);
        back = findViewById(R.id.themeBack);
        list = findViewById(R.id.themeList);
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        list.setItemAnimator(null);
        list.setAdapter(new Themes());
        list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override public void onChildViewAttachedToWindow(View view) { list.post(ThemeGalleryActivity.this::takeListFocus); }
            @Override public void onChildViewDetachedFromWindow(View view) { }
        });
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        apply.setOnClickListener(v -> applySelected());
        credits.setOnClickListener(v -> showCredits());
        back.setOnClickListener(v -> finish());
        for (View button : new View[]{apply, credits, back}) {
            button.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_UP) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) focusSelected();
                return true;
            });
        }
        final String restored = state == null ? null : state.getString("theme");
        IO.execute(() -> {
            try {
                List<ThemeCatalog.Entry> loaded = ThemeCatalog.entries(ThemeGalleryActivity.this);
                runOnUiThread(() -> {
                    if (!alive()) return;
                    entries.addAll(loaded);
                    int initial = 0;
                    for (int i = 0; i < entries.size(); i++) {
                        ThemeCatalog.Entry entry = entries.get(i);
                        if (restored == null ? entry.current : entry.id.equals(restored)) initial = i;
                    }
                    list.getAdapter().notifyDataSetChanged();
                    select(initial);
                    focusSelected();
                });
            } catch (Exception e) { error(e); }
        });
    }

    private boolean alive() { return !isFinishing() && !isDestroyed(); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void select(int index) {
        if (busy || index == selected) return;
        selected = index;
        // Focus may move during RecyclerView layout: update attached cards without notifying it.
        for (int i = 0; i < list.getChildCount(); i++) {
            View card = list.getChildAt(i);
            card.setSelected(list.getChildAdapterPosition(card) == index);
        }
        ThemeCatalog.Entry entry = entries.get(index);
        name.setText(entry.name);
        position.setText((entry.current ? getString(R.string.themes_current) + "  ·  " : "")
                + (index + 1) + " / " + entries.size());
        hint.setText(entry.id.isEmpty() ? R.string.themes_restore_hint : R.string.themes_browse_hint);
        apply.setEnabled(!entry.current);
        credits.setEnabled(false);
        preview.setImageDrawable(null);
        preview.setContentDescription(entry.name);
        fallback.setText(entry.id.isEmpty() ? R.string.themes_restore_preview : R.string.themes_preview_missing);
        fallback.setVisibility(View.VISIBLE);
        int token = ++generation;
        if (pendingPreview != null) preview.removeCallbacks(pendingPreview);
        if (previewJob != null) previewJob.cancel(true);
        // Coalesce rapid D-pad repeats; never enqueue all 29 full-sized screenshots.
        pendingPreview = () -> {
            int width = Math.max(1, preview.getWidth()), height = Math.max(1, preview.getHeight());
            previewJob = IO.submit(() -> loadPreview(entry, width, height, token));
        };
        preview.postDelayed(pendingPreview, 100);
    }

    private void loadPreview(ThemeCatalog.Entry entry, int width, int height, int token) {
        Bitmap image = null;
        boolean hasCredits = false;
        try {
            hasCredits = entry.file("CREDITS.md").isFile();
            File file = entry.file("screenshot.jpg");
            if (file.isFile() && file.length() <= 8 * 1024 * 1024) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), bounds);
                if (WallpaperSize.accepted(bounds.outWidth, bounds.outHeight) && !Thread.currentThread().isInterrupted()) {
                    float fit = Math.min(1f, Math.min((float) width / bounds.outWidth, (float) height / bounds.outHeight));
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = WallpaperSize.sampleSize(bounds.outWidth, bounds.outHeight,
                            Math.max(1, Math.round(bounds.outWidth * fit)), Math.max(1, Math.round(bounds.outHeight * fit)));
                    image = BitmapFactory.decodeFile(file.getPath(), options);
                }
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.w("TvHome.Themes", "Preview unavailable", e);
        }
        final Bitmap result = image;
        final boolean showCredits = hasCredits;
        runOnUiThread(() -> {
            if (!alive() || token != generation) {
                if (result != null) result.recycle();
                return;
            }
            preview.setImageBitmap(result);
            fallback.setVisibility(result == null ? View.VISIBLE : View.GONE);
            credits.setEnabled(showCredits && !busy);
        });
    }

    private void focusSelected() {
        if (selected < 0) return;
        focusPending = true;
        list.scrollToPosition(selected);
        list.post(this::takeListFocus);
    }

    private void takeListFocus() {
        if (!focusPending || !alive()) return;
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(selected);
        if (holder == null) return; // The layout callback retries once scrolling creates the holder.
        focusPending = false;
        holder.itemView.requestFocus();
    }

    private void applySelected() {
        if (busy || selected < 0) return;
        ThemeCatalog.Entry entry = entries.get(selected);
        busy = true;
        apply.setEnabled(false);
        credits.setEnabled(false);
        back.setEnabled(false);
        hint.setText(R.string.themes_working);
        Context ctx = getApplicationContext();
        IO.execute(() -> {
            try {
                ThemeCatalog.apply(ctx, entry.id);
                runOnUiThread(() -> { if (alive()) { setResult(RESULT_OK); finish(); } });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    back.setEnabled(true);
                    int index = selected;
                    selected = -1;
                    if (alive()) select(index);
                });
                error(e);
            }
        });
    }

    private void showCredits() {
        if (busy || selected < 0) return;
        ThemeCatalog.Entry entry = entries.get(selected);
        IO.execute(() -> {
            try {
                String text = ThemeCatalog.credits(entry);
                runOnUiThread(() -> {
                    if (alive()) new AlertDialog.Builder(this).setTitle(R.string.themes_credits)
                            .setMessage(text).setPositiveButton(android.R.string.ok, null).show();
                });
            } catch (Exception e) { error(e); }
        });
    }

    private void error(Exception e) {
        Log.w("TvHome.Themes", "Offline theme operation failed", e);
        runOnUiThread(() -> {
            if (alive()) Toast.makeText(this, R.string.themes_failed, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (!busy) super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (selected >= 0) state.putString("theme", entries.get(selected).id);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        generation++;
        focusPending = false;
        if (pendingPreview != null) preview.removeCallbacks(pendingPreview);
        if (previewJob != null) previewJob.cancel(true);
        preview.setImageDrawable(null);
        super.onDestroy();
    }

    private final class Themes extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            TextView card = new TextView(ThemeGalleryActivity.this, null, 0, R.style.SettingsRow);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(dp(184), dp(56));
            params.setMarginEnd(dp(8));
            card.setLayoutParams(params);
            card.setGravity(Gravity.CENTER);
            card.setMaxLines(2);
            card.setEllipsize(TextUtils.TruncateAt.END);
            card.setTextSize(16);
            return new RecyclerView.ViewHolder(card) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int index) {
            TextView card = (TextView) holder.itemView;
            ThemeCatalog.Entry entry = entries.get(index);
            card.setText(entry.name + (entry.current ? " ✓" : ""));
            card.setSelected(index == selected);
            card.setOnFocusChangeListener((v, focused) -> {
                int at = holder.getBindingAdapterPosition();
                if (focused && at != RecyclerView.NO_POSITION) select(at);
            });
            card.setOnClickListener(v -> {
                int at = holder.getBindingAdapterPosition();
                if (at != RecyclerView.NO_POSITION) select(at);
            });
            card.setOnKeyListener((v, key, event) -> {
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        int at = holder.getBindingAdapterPosition();
                        if (at == RecyclerView.NO_POSITION) return true;
                        int step = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
                        if (list.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) step = -step;
                        int next = at + step;
                        if (next >= 0 && next < entries.size()) { select(next); focusSelected(); }
                    }
                    return true;
                }
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN)
                        (apply.isEnabled() ? apply : credits.isEnabled() ? credits : back).requestFocus();
                    return true;
                }
                return key == KeyEvent.KEYCODE_DPAD_UP;
            });
        }

        @Override
        public int getItemCount() { return entries.size(); }
    }
}
