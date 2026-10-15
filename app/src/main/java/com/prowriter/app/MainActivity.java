package com.prowriter.app;

import android.app.Activity;
import android.content.ComponentName;
import android.app.StatusBarManager;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/** Settings screen: choose the AI provider, save its key, choose language, and try the rewrite. */
public class MainActivity extends Activity {

    private Rewriter.Provider provider;

    private TextView keyLabel;
    private EditText keyInput;
    private TextView keyLink;
    private EditText tryInput;
    private TextView tryResult;
    private ProgressBar tryProgress;
    private Button tryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        keyLabel = findViewById(R.id.key_label);
        keyInput = findViewById(R.id.api_key);
        keyLink = findViewById(R.id.key_link);
        tryInput = findViewById(R.id.try_input);
        tryResult = findViewById(R.id.try_result);
        tryProgress = findViewById(R.id.try_progress);
        tryBtn = findViewById(R.id.btn_try);

        keyLink.setMovementMethod(LinkMovementMethod.getInstance());

        // One radio button per provider.
        RadioGroup group = findViewById(R.id.provider_group);
        provider = Prefs.getProvider(this);
        for (Rewriter.Provider p : Rewriter.Provider.values()) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(p.label);
            rb.setTag(p);
            rb.setPadding(rb.getPaddingLeft(), 8, rb.getPaddingRight(), 8);
            group.addView(rb);
            if (p == provider) rb.setChecked(true);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            RadioButton rb = g.findViewById(checkedId);
            if (rb == null) return;
            provider = (Rewriter.Provider) rb.getTag();
            Prefs.setProvider(this, provider);
            showProvider();
        });
        showProvider();

        CheckBox english = findViewById(R.id.force_english);
        english.setChecked(Prefs.forceEnglish(this));
        english.setOnCheckedChangeListener((b, checked) -> Prefs.setForceEnglish(this, checked));

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();
            Prefs.setApiKey(this, provider, key);
            hideKeyboard();
            Toast.makeText(this, key.isEmpty() ? R.string.key_cleared : R.string.key_saved,
                    Toast.LENGTH_SHORT).show();
        });

        tryBtn.setOnClickListener(v -> tryIt());

        findViewById(R.id.btn_tile).setOnClickListener(v -> addTile());
    }

    /** Asks Android to add the Pro Writer tile (Android 13+); older phones get instructions. */
    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            if (sbm != null) {
                try {
                    sbm.requestAddTileService(
                            new ComponentName(this, ProTileService.class),
                            getString(R.string.tile_label),
                            Icon.createWithResource(this, R.drawable.ic_tile),
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

        // Use whatever is in the key box, even if the user forgot to tap Save.
        final Rewriter.Provider p = provider;
        String key = keyInput.getText().toString().trim();
        if (!key.isEmpty()) Prefs.setApiKey(this, p, key);
        boolean english = Prefs.forceEnglish(this);

        tryBtn.setEnabled(false);
        tryProgress.setVisibility(View.VISIBLE);
        tryResult.setText("");

        new Thread(() -> {
            String output;
            try {
                output = Rewriter.rewrite(p, key, text, english, soFar -> runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) tryResult.setText(soFar);
                }));
            } catch (Rewriter.RewriteException e) {
                output = "⚠ " + e.getMessage();
            }
            final String shown = output;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                tryBtn.setEnabled(true);
                tryProgress.setVisibility(View.GONE);
                tryResult.setText(shown);
            });
        }).start();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (focus != null && imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
