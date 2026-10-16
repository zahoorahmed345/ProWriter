package com.prowriter.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Opened from the ProChat quick-settings tiles (or the app-icon shortcuts).
 * Takes the text you just copied, or a voice message you speak, shows the professional (or
 * translated) version and copies it, so you only need to paste it. The app only sees your
 * clipboard at the moment you open this, and only uses the microphone while you're speaking.
 */
public class ClipboardActivity extends Activity {

    /** Set by the "ProChat Voice" tile/shortcut: start listening straight away. */
    static final String EXTRA_VOICE = "com.prowriter.app.VOICE";
    private static final int REQ_MIC = 7;

    private boolean started;
    private String original;      // copied text, or null when using voice
    private byte[] audio;         // voice recording, or null when using copied text
    private Rewriter.Target target;
    /** Results by mode, plus "SAME>ENGLISH" etc. for Pro applied to a translation. */
    private final Map<String, String> results = new HashMap<>();
    /** The last translation shown (ENG, 中文 or Urdu). Pro then polishes that text. */
    private Rewriter.Target lastTranslation;
    private int requestId;

    private ProgressBar progress;
    private TextView status;
    private EditText result;
    private TextView pinyin;
    private Button doneBtn;
    private Button copyBtn;
    private Button retryBtn;
    private Button micBtn;
    private ModeChips chips;

    private VoiceRecorder recorder;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (recorder == null || !recorder.isRecording()) return;
            long s = recorder.elapsedMs() / 1000;
            micBtn.setText(getString(R.string.mic_stop, s / 60, s % 60));
            ticker.postDelayed(this, 500);
        }
    };

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
        micBtn = findViewById(R.id.btn_mic);
        findViewById(R.id.btn_replace).setVisibility(View.GONE);
        recorder = new VoiceRecorder(this);

        // Opens in the mode you used last time.
        target = Prefs.getLastTarget(this);
        chips = new ModeChips(findViewById(R.id.mode_chips), target, t -> {
            target = t;
            Prefs.setLastTarget(this, t);
            if (recorder.isRecording()) return;
            if (original != null || audio != null) start();
        });

        doneBtn.setOnClickListener(v -> finish());
        copyBtn.setOnClickListener(v -> {
            copyToClipboard(result.getText().toString());
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            finish();
        });
        retryBtn.setOnClickListener(v -> start());

        micBtn.setVisibility(View.VISIBLE);
        micBtn.setOnClickListener(v -> {
            if (recorder.isRecording()) {
                stopListening();
            } else {
                startListening();
            }
        });

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

        if (wantsVoice()) {
            startListening();
            return;
        }

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
        if (Prefs.isClipUsed(this, text.toString())) {
            // Old text (already done, or a result ProChat copied): wait for something new.
            showNotNew(text.toString());
            return;
        }
        original = text.toString();
        start();
    }

    /** The clipboard holds text ProChat has already handled: offer voice, or use it anyway. */
    private void showNotNew(String oldText) {
        showError(getString(R.string.clip_not_new), true);
        retryBtn.setText(R.string.clip_use_anyway);
        retryBtn.setOnClickListener(v -> {
            retryBtn.setText(R.string.retry);
            retryBtn.setOnClickListener(x -> start());
            original = oldText;
            start();
        });
    }

    // ------------------------------------------------------------------ voice

    /** True when opened from the voice tile or shortcut (the shortcut may pass "true" as text). */
    private boolean wantsVoice() {
        Bundle extras = getIntent().getExtras();
        Object v = extras == null ? null : extras.get(EXTRA_VOICE);
        return Boolean.TRUE.equals(v) || "true".equals(v);
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        ++requestId; // ignore any answer still on its way
        retryBtn.setText(R.string.retry);
        retryBtn.setOnClickListener(v -> start());
        original = null;
        audio = null;
        results.clear();
        lastTranslation = null;

        // Reply doesn't make sense for a message you speak yourself.
        chips.setEnabled(Rewriter.Target.REPLY, false);
        if (target == Rewriter.Target.REPLY) {
            target = Rewriter.Target.SAME;
            chips.select(target);
        }

        if (!recorder.start(() -> runOnUiThread(this::stopListening))) {
            showError(getString(R.string.mic_unavailable), false);
            return;
        }
        progress.setVisibility(View.GONE);
        status.setText(R.string.mic_listening);
        result.setVisibility(View.GONE);
        pinyin.setVisibility(View.GONE);
        copyBtn.setVisibility(View.GONE);
        retryBtn.setVisibility(View.GONE);
        doneBtn.setText(R.string.cancel);
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    private void stopListening() {
        ticker.removeCallbacks(tick);
        byte[] recorded = recorder.stop();
        micBtn.setText(R.string.mic_again);
        if (recorded == null) {
            showError(getString(R.string.mic_too_short), false);
            return;
        }
        audio = recorded;
        start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            showError(getString(R.string.mic_denied), false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Never keep the microphone on when the pop-up isn't in front.
        if (recorder.isRecording()) {
            ticker.removeCallbacks(tick);
            recorder.cancel();
            micBtn.setText(R.string.mic_start);
            showError(getString(R.string.mic_cancelled), false);
        }
    }

    @Override
    protected void onDestroy() {
        ticker.removeCallbacks(tick);
        recorder.cancel();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ rewriting

    private void start() {
        final int id = ++requestId;
        final Rewriter.Target t = target;

        // What to work on: normally your original text or recording. But Pro right after a
        // translation polishes that translation (e.g. 中文 → Pro improves the Chinese).
        final String cacheKey;
        final String text;
        final byte[] voice;
        String translated = t == Rewriter.Target.SAME && lastTranslation != null
                ? results.get(lastTranslation.name()) : null;
        if (translated != null) {
            cacheKey = "SAME>" + lastTranslation.name();
            text = Rewriter.split(translated)[0];
            voice = null;
        } else {
            cacheKey = t.name();
            text = original;
            voice = audio;
        }
        if (Rewriter.isTranslation(t)) lastTranslation = t;

        String cached = results.get(cacheKey);
        if (cached != null) {
            showResult(cached);
            return;
        }
        showLoading();
        final Rewriter.Speed speed = Prefs.getSpeed(this);
        final Rewriter.Provider provider = Prefs.getProvider(this);
        final String key = voice != null
                ? Prefs.getApiKey(this, Rewriter.Provider.GEMINI)
                : Prefs.getApiKey(this, provider);

        if (text != null && translated == null) Prefs.markClipUsed(this, text);

        new Thread(() -> {
            try {
                Rewriter.Partial partial = soFar -> runOnUiThread(() -> {
                    if (id == requestId) showPartial(soFar);
                });
                String rewritten = voice != null
                        ? Rewriter.rewriteVoice(key, voice, VoiceRecorder.MIME_TYPE, t, speed, partial)
                        : Rewriter.rewrite(provider, key, text, t, speed, partial);
                runOnUiThread(() -> {
                    results.put(cacheKey, rewritten);
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
        status.setText(audio != null ? R.string.mic_working : R.string.working);
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
        Prefs.markClipUsed(this, text); // don't redo our own result next time
    }

    private void makeDialogWide() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int width = (int) Math.min(dm.widthPixels * 0.92f, 560 * dm.density);
        getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
