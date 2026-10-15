package com.prowriter.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
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
 * Opens when the user selects text in any app and taps "Make Professional".
 * Shows the professional version and lets the user replace the original text or copy it.
 */
public class ProcessTextActivity extends Activity {

    private String original;
    private boolean readOnly;

    private ProgressBar progress;
    private TextView status;
    private EditText result;
    private Button replaceBtn;
    private Button copyBtn;
    private Button retryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.no_text, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        original = text.toString();
        readOnly = getIntent().getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false);

        setContentView(R.layout.activity_process);
        setFinishOnTouchOutside(false);
        makeDialogWide();

        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);
        result = findViewById(R.id.result);
        replaceBtn = findViewById(R.id.btn_replace);
        copyBtn = findViewById(R.id.btn_copy);
        retryBtn = findViewById(R.id.btn_retry);

        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        replaceBtn.setOnClickListener(v -> replace());
        copyBtn.setOnClickListener(v -> copy());

        start();
    }

    private void start() {
        showLoading();
        final Rewriter.Provider provider = Prefs.getProvider(this);
        final String key = Prefs.getApiKey(this, provider);
        final boolean english = Prefs.forceEnglish(this);

        new Thread(() -> {
            try {
                String rewritten = Rewriter.rewrite(provider, key, original, english,
                        soFar -> runOnUiThread(() -> showPartial(soFar)));
                runOnUiThread(() -> showResult(rewritten));
            } catch (Rewriter.RewriteException e) {
                runOnUiThread(() -> showError(e.getMessage(), e.missingKey));
            }
        }).start();
    }

    private boolean gone() {
        return isFinishing() || isDestroyed();
    }

    private void showLoading() {
        progress.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText(R.string.working);
        result.setVisibility(View.GONE);
        replaceBtn.setVisibility(View.GONE);
        copyBtn.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
    }

    /** Shows the answer while it is still being written. */
    private void showPartial(String soFar) {
        if (gone() || soFar.isEmpty()) return;
        status.setText(R.string.writing);
        result.setVisibility(View.VISIBLE);
        result.setText(soFar);
    }

    private void showResult(String text) {
        if (gone()) return;
        progress.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        result.setVisibility(View.VISIBLE);
        result.setText(text);
        copyBtn.setVisibility(View.VISIBLE);
        // Some apps (e.g. a web page) only let you read text, so "Replace" is hidden there.
        replaceBtn.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        retryBtn.setVisibility(View.GONE);
    }

    private void showError(String message, boolean missingKey) {
        if (gone()) return;
        progress.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        result.setVisibility(View.GONE);
        replaceBtn.setVisibility(View.GONE);
        copyBtn.setVisibility(View.GONE);
        retryBtn.setVisibility(View.VISIBLE);
        if (missingKey) {
            retryBtn.setText(R.string.open_settings);
            retryBtn.setOnClickListener(v -> {
                startActivity(new Intent(this, MainActivity.class));
                finish();
            });
        } else {
            retryBtn.setText(R.string.retry);
            retryBtn.setOnClickListener(v -> start());
        }
    }

    private void replace() {
        Intent data = new Intent();
        data.putExtra(Intent.EXTRA_PROCESS_TEXT, result.getText().toString());
        setResult(RESULT_OK, data);
        finish();
    }

    private void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Professional message", result.getText()));
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void makeDialogWide() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = (int) Math.min(dm.widthPixels * 0.92f, 560 * dm.density);
        getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
