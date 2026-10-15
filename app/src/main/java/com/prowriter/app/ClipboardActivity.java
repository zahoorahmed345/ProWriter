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

/**
 * Opened from the Pro Writer quick-settings tile (or the app-icon shortcut).
 * Takes the text you just copied, shows the professional version and copies it back,
 * so you only need to paste it. Needs no special permission: the app only sees
 * your clipboard at the moment you open this.
 */
public class ClipboardActivity extends Activity {

    private boolean started;
    private String original;

    private ProgressBar progress;
    private TextView status;
    private EditText result;
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
        doneBtn = findViewById(R.id.btn_cancel);
        copyBtn = findViewById(R.id.btn_copy);
        retryBtn = findViewById(R.id.btn_retry);
        findViewById(R.id.btn_replace).setVisibility(View.GONE);

        doneBtn.setOnClickListener(v -> finish());
        copyBtn.setOnClickListener(v -> {
            copyToClipboard(result.getText().toString());
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            finish();
        });
        retryBtn.setOnClickListener(v -> start());

        status.setText(R.string.clip_reading);
        result.setVisibility(View.GONE);
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
        showLoading();
        final Rewriter.Provider provider = Prefs.getProvider(this);
        final String key = Prefs.getApiKey(this, provider);
        final boolean english = Prefs.forceEnglish(this);

        new Thread(() -> {
            try {
                String rewritten = Rewriter.rewrite(provider, key, original, english);
                runOnUiThread(() -> showResult(rewritten));
            } catch (Rewriter.RewriteException e) {
                runOnUiThread(() -> showError(e.getMessage(), true));
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
        copyBtn.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
        doneBtn.setText(R.string.cancel);
    }

    private void showResult(String text) {
        if (gone()) return;
        copyToClipboard(text); // ready to paste straight away
        progress.setVisibility(View.GONE);
        status.setText(R.string.clip_copied);
        result.setVisibility(View.VISIBLE);
        result.setText(text);
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
