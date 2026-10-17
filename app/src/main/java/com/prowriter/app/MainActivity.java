package com.prowriter.app;

import android.Manifest;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** The ProChat app: Home (try it), Guide (what it is and how to use it) and Settings tabs. */
public class MainActivity extends Activity {

    private static final String STATE_TAB = "tab";
    private static final int REQ_MIC = 8;

    private Rewriter.Provider provider;

    private TextView[] tabs;
    private View[] pages;
    private int currentTab;

    private TextView keyLabel;
    private EditText keyInput;
    private TextView keyLink;
    private View setupBanner;

    private EditText tryInput;
    private View resultBox;
    private TextView tryResult;
    private TextView tryPinyin;
    private ProgressBar tryProgress;
    private Button tryBtn;
    private ModeChips modeChips;
    private String lastResultMessage = "";

    private Button micBtn;
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
        setContentView(R.layout.activity_main);

        // ---- tabs
        tabs = new TextView[] {findViewById(R.id.tab_home), findViewById(R.id.tab_guide),
                findViewById(R.id.tab_settings)};
        pages = new View[] {findViewById(R.id.page_home), findViewById(R.id.page_guide),
                findViewById(R.id.page_settings)};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> showTab(index));
        }

        // ---- settings: provider and key
        keyLabel = findViewById(R.id.key_label);
        keyInput = findViewById(R.id.api_key);
        keyLink = findViewById(R.id.key_link);
        keyLink.setMovementMethod(LinkMovementMethod.getInstance());

        RadioGroup group = findViewById(R.id.provider_group);
        provider = Prefs.getProvider(this);
        for (Rewriter.Provider p : Rewriter.Provider.values()) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(p.label);
            rb.setTag(p);
            rb.setTextColor(getColor(R.color.text_primary));
            rb.setPadding(rb.getPaddingLeft(), 12, rb.getPaddingRight(), 12);
            group.addView(rb);
            if (p == provider) rb.setChecked(true);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            RadioButton rb = g.findViewById(checkedId);
            if (rb == null) return;
            provider = (Rewriter.Provider) rb.getTag();
            Prefs.setProvider(this, provider);
            showProvider();
            updateSetupBanner();
        });
        showProvider();

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();
            Prefs.setApiKey(this, provider, key);
            hideKeyboard();
            updateSetupBanner();
            Toast.makeText(this, key.isEmpty() ? R.string.key_cleared : R.string.key_saved,
                    Toast.LENGTH_SHORT).show();
            if (!key.isEmpty()) showTab(0);
        });

        // ---- settings: speed
        RadioGroup speedGroup = findViewById(R.id.speed_group);
        switch (Prefs.getSpeed(this)) {
            case FAST:
                speedGroup.check(R.id.speed_fast);
                break;
            case BEST:
                speedGroup.check(R.id.speed_best);
                break;
            default:
                speedGroup.check(R.id.speed_balanced);
                break;
        }
        speedGroup.setOnCheckedChangeListener((g, id) -> {
            Rewriter.Speed s = id == R.id.speed_fast ? Rewriter.Speed.FAST
                    : id == R.id.speed_best ? Rewriter.Speed.BEST : Rewriter.Speed.BALANCED;
            Prefs.setSpeed(this, s);
        });

        // ---- settings: tile and version
        findViewById(R.id.btn_tile).setOnClickListener(v -> addTile(false));
        findViewById(R.id.btn_tile_guide).setOnClickListener(v -> addTile(false));
        findViewById(R.id.btn_tile_voice).setOnClickListener(v -> addTile(true));
        findViewById(R.id.btn_tile_voice_guide).setOnClickListener(v -> addTile(true));
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            // leave blank
        }
        ((TextView) findViewById(R.id.version_text)).setText(getString(R.string.version_body, version));

        // ---- home
        setupBanner = findViewById(R.id.setup_banner);
        findViewById(R.id.btn_setup).setOnClickListener(v -> showTab(2));
        findViewById(R.id.btn_open_guide).setOnClickListener(v -> showTab(1));

        tryInput = findViewById(R.id.try_input);
        resultBox = findViewById(R.id.result_box);
        tryResult = findViewById(R.id.try_result);
        tryPinyin = findViewById(R.id.try_pinyin);
        tryProgress = findViewById(R.id.try_progress);
        tryBtn = findViewById(R.id.btn_try);
        modeChips = new ModeChips(findViewById(R.id.mode_chips), Prefs.getLastTarget(this),
                t -> Prefs.setLastTarget(this, t));
        tryBtn.setOnClickListener(v -> tryIt());

        recorder = new VoiceRecorder(this);
        micBtn = findViewById(R.id.btn_mic_home);
        micBtn.setOnClickListener(v -> {
            if (recorder.isRecording()) {
                stopListening();
            } else {
                startListening();
            }
        });
        findViewById(R.id.btn_copy_result).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && !lastResultMessage.isEmpty()) {
                cm.setPrimaryClip(ClipData.newPlainText("ProChat", lastResultMessage));
                Prefs.markClipUsed(this, lastResultMessage);
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            }
        });

        updateSetupBanner();
        int start = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, 0)
                : (Prefs.getApiKey(this, provider).isEmpty() ? 2 : 0);
        showTab(start);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    private void showTab(int index) {
        currentTab = index;
        hideKeyboard();
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == index;
            tabs[i].setBackgroundResource(on ? R.drawable.tab_on : 0);
            tabs[i].setTextColor(on ? getColor(R.color.brand) : 0xFFFFFFFF);
            pages[i].setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSetupBanner() {
        boolean noKey = Prefs.getApiKey(this, provider).isEmpty();
        setupBanner.setVisibility(noKey ? View.VISIBLE : View.GONE);
    }

    /** Asks Android to add a ProChat tile (Android 13+); older phones get instructions. */
    private void addTile(boolean voice) {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            if (sbm != null) {
                try {
                    sbm.requestAddTileService(
                            new ComponentName(this, voice ? ProVoiceTileService.class : ProTileService.class),
                            getString(voice ? R.string.tile_voice_label : R.string.tile_label),
                            Icon.createWithResource(this, voice ? R.drawable.ic_tile_voice : R.drawable.ic_tile),
                            getMainExecutor(),
                            result -> {
                                int msg;
                                if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                    msg = R.string.tile_added;
                                } else if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                                    msg = R.string.tile_already;
                                } else {
                                    msg = R.string.tile_manual;
                                }
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            });
                    return;
                } catch (Exception ignored) {
                    // Some phones don't support this request; fall through to instructions.
                }
            }
        }
        Toast.makeText(this, R.string.tile_manual, Toast.LENGTH_LONG).show();
    }

    /** Updates the key box, its label and the "get a key" link for the selected provider. */
    private void showProvider() {
        keyLabel.setText(provider.keyLabel);
        keyInput.setHint(provider.keyHint);
        keyInput.setText(Prefs.getApiKey(this, provider));
        keyLink.setText(Html.fromHtml("<a href=\"" + provider.keyUrl + "\">"
                + provider.keyLinkText + "</a>", Html.FROM_HTML_MODE_LEGACY));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Rewriter.warmUp(provider);
    }

    private void tryIt() {
        String text = tryInput.getText().toString();
        if (text.trim().isEmpty()) {
            tryInput.setError(getString(R.string.type_something));
            return;
        }
        hideKeyboard();

        final Rewriter.Provider p = provider;
        final String key = Prefs.getApiKey(this, p);
        final Rewriter.Target target = modeChips.get();
        final Rewriter.Speed speed = Prefs.getSpeed(this);

        tryBtn.setEnabled(false);
        tryProgress.setVisibility(View.VISIBLE);
        showResult("", null, null);

        new Thread(() -> {
            String raw = null;
            String error = null;
            try {
                raw = Rewriter.rewrite(p, key, text, target, speed, soFar -> runOnUiThread(() -> {
                    String[] parts = Rewriter.split(soFar, target);
                    showResult(parts[0], parts[1], parts[2]);
                }));
            } catch (Rewriter.RewriteException e) {
                error = e.getMessage();
            }
            final String r = raw;
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                tryBtn.setEnabled(true);
                tryProgress.setVisibility(View.GONE);
                if (r != null) {
                    String[] parts = Rewriter.split(r, target);
                    showResult(parts[0], parts[1], parts[2]);
                } else {
                    lastResultMessage = "";
                    resultBox.setVisibility(View.VISIBLE);
                    tryResult.setText("⚠ " + err);
                    tryPinyin.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void showResult(String message, String extra, String kind) {
        if (isFinishing() || isDestroyed()) return;
        lastResultMessage = message == null ? "" : message;
        resultBox.setVisibility(lastResultMessage.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.btn_copy_result).setVisibility(View.VISIBLE);
        tryResult.setText(lastResultMessage);
        ExtraLine.show(tryPinyin, extra, kind);
    }

    // ------------------------------------------------------------------ voice (Home tab)

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (modeChips.get() == Rewriter.Target.REPLY) {
            modeChips.select(Rewriter.Target.SAME);
        }
        hideKeyboard();
        // Connect to Gemini while you're speaking, so sending starts the moment you tap Stop.
        Rewriter.warmUp(Rewriter.Provider.GEMINI, true);
        if (!recorder.start(() -> runOnUiThread(this::stopListening))) {
            showMessage(getString(R.string.mic_unavailable));
            return;
        }
        tryBtn.setEnabled(false);
        showMessage(getString(R.string.mic_listening));
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    private void stopListening() {
        ticker.removeCallbacks(tick);
        byte[] audio = recorder.stop();
        micBtn.setText(R.string.mic_start);
        tryBtn.setEnabled(true);
        if (audio == null) {
            showMessage(getString(R.string.mic_too_short));
            return;
        }
        final String key = Prefs.getApiKey(this, Rewriter.Provider.GEMINI);
        final Rewriter.Target target = modeChips.get();
        final Rewriter.Speed speed = Prefs.getSpeed(this);
        tryBtn.setEnabled(false);
        micBtn.setEnabled(false);
        tryProgress.setVisibility(View.VISIBLE);
        showMessage(getString(R.string.mic_working));

        new Thread(() -> {
            String raw = null;
            String error = null;
            try {
                raw = Rewriter.rewriteVoice(key, audio, VoiceRecorder.MIME_TYPE, target, speed,
                        soFar -> runOnUiThread(() -> {
                            String[] parts = Rewriter.split(soFar, target);
                            if (!parts[0].isEmpty()) showResult(parts[0], parts[1], parts[2]);
                        }));
            } catch (Rewriter.RewriteException e) {
                error = e.getMessage();
            }
            final String r = raw;
            final String err = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                tryBtn.setEnabled(true);
                micBtn.setEnabled(true);
                tryProgress.setVisibility(View.GONE);
                if (r != null) {
                    String[] parts = Rewriter.split(r, target);
                    showResult(parts[0], parts[1], parts[2]);
                } else {
                    showMessage("⚠ " + err);
                }
            });
        }).start();
    }

    /** Shows a status or error line in the result box (nothing to copy). */
    private void showMessage(String text) {
        lastResultMessage = "";
        resultBox.setVisibility(View.VISIBLE);
        findViewById(R.id.btn_copy_result).setVisibility(View.GONE);
        tryResult.setText(text);
        tryPinyin.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            showMessage(getString(R.string.mic_denied));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (recorder != null && recorder.isRecording()) {
            ticker.removeCallbacks(tick);
            recorder.cancel();
            micBtn.setText(R.string.mic_start);
            tryBtn.setEnabled(true);
            showMessage(getString(R.string.mic_cancelled));
        }
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (focus != null && imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
