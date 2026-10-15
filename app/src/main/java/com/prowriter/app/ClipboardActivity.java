package com.prowriter.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

/**
 * Opened from the Pro Writer quick-settings tile (or the app-icon shortcut).
 * Takes the text you just copied, shows the professional (or translated) version and copies
 * it back, so you only need to paste it. Needs no special permission: the app only sees your
 * clipboard at the moment you open this.
 */
public class ClipboardActivity extends Activity {

    private boolean started;
    private String original;
    private Rewriter.Target target;
    private final Map<Rewriter.Target, String> results = new EnumMap<>(Rewriter.Target.class);
    private int requestId;

    private ProgressBar progress;
    private TextView status;
    private EditText result;
    private TextView pinyin;
    private Button doneBtn;
    private Button copyBtn;
    private Button retryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_process);
        setFinishOnTouchOutside(false);
        makeDialogWide();

        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        pinyin = findViewById(R.id.pinyin);
        doneBtn = findViewById(R.id.btn_cancel);
        copyBtn = findViewById(R.id.btn_copy);
        retryBtn = findViewById(R.id.btn_retry);
        findViewById(R.id.btn_replace).setVisibility(View.GONE);

        // Opens in the mode you used last time.
        target = Prefs.getLastTarget(this);
        new ModeChips(findViewById(R.id.mode_chips), target, t -> {
            target = t;
            Prefs.setLastTarget(this, t);
            if (original != null) start();
        });

        doneBtn.setOnClickListener(v -> finish());
        copyBtn.setOnClickListener(v -> {
            copyToClipboard(result.getText().toString());
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            finish();
        });
        retryBtn.setOnClickListener(v -> start());

        status.setText(R.string.clip_reading);
        result.setVisibility(View.GONE);

        // Start connecting right away, while the clipboard is being read.
        Rewriter.warmUp(Prefs.getProvider(this));
    }

    /** Android only lets an app read the clipboard once its window is in front, so start here. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || started) return;
        started = true;

        CharSequence text = null;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                text = clip.getItemAt(0).coerceToText(this);
            }
        }
        if (text == null || text.toString().trim().isEmpty()) {
            showError(getString(R.string.clip_empty), false);
            return;
        }
        original = text.toString();
        start();
    }

    private void start() {
        final int id = ++requestId;
        final Rewriter.Target t = target;
        String cached = results.get(t);
        if (cached != null) {
            showResult(cached);
            return;
        }
        showLoading();
        final Rewriter.Provider provider = Prefs.getProvider(this);
        final String key = Prefs.getApiKey(this, provider);

        new Thread(() -> {
            try {
                String rewritten = Rewriter.rewrite(provider, key, original, t,
                        soFar -> runOnUiThread(() -> {
                            if (id == requestId) showPartial(soFar);
                        }));
                runOnUiThread(() -> {
                    results.put(t, rewritten);
                    if (id == requestId) showResult(rewritten);
                });
            } catch (Rewriter.RewriteException e) {
                runOnUiThread(() -> {
                    if (id == requestId) showError(e.getMessage(), true);
                });
            }
        }).start();
    }

    private boolean gone() {
        return isFinishing() || isDestroyed();
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        status.setText(R.string.working);
        result.setVisibility(View.GONE);
        pinyin.setVisibility(View.GONE);
        copyBtn.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
        doneBtn.setText(R.string.cancel);
    }

    /** Shows the answer while it is still being written. */
    private void showPartial(String soFar) {
        if (gone() || soFar.isEmpty()) return;
        String[] parts = Rewriter.split(soFar);
        if (parts[0].isEmpty()) return;
        status.setText(R.string.writing);
        result.setVisibility(View.VISIBLE);
        result.setText(parts[0]);
        showPinyin(parts[1]);
    }

    private void showPinyin(String text) {
        pinyin.setVisibility(text == null ? View.GONE : View.VISIBLE);
        pinyin.setText(text == null ? "" : text);
    }

    private void showResult(String raw) {
        if (gone()) return;
        String[] parts = Rewriter.split(raw);
        copyToClipboard(parts[0]); // only the message, never the pinyin; ready to paste
        progress.setVisibility(View.GONE);
        status.setText(R.string.clip_copied);
        result.setVisibility(View.VISIBLE);
        result.setText(parts[0]);
        showPinyin(parts[1]);
        copyBtn.setText(R.string.copy_edited);
        copyBtn.setVisibility(View.VISIBLE);
        retryBtn.setVisibility(View.GONE);
        doneBtn.setText(R.string.done);
    }

    private void showError(String message, boolean canRetry) {
        if (gone()) return;
        progress.setVisibility(View.GONE);
        status.setText(message);
        result.setVisibility(View.GONE);
        pinyin.setVisibility(View.GONE);
        copyBtn.setVisibility(View.GONE);
        retryBtn.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        doneBtn.setText(R.string.close);
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Professional message", text));
    }

    private void makeDialogWide() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = (int) Math.min(dm.widthPixels * 0.92f, 560 * dm.density);
        getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
