package com.prowriter.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
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

        // Floating button (accessibility service)
        findViewById(R.id.btn_float).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        CheckBox auto = findViewById(R.id.auto_suggest);
        auto.setChecked(Prefs.autoSuggest(this));
        auto.setOnCheckedChangeListener((b, checked) -> Prefs.setAutoSuggest(this, checked));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = floatingButtonEnabled();
        ((TextView) findViewById(R.id.float_status)).setText(on ? R.string.float_on : R.string.float_off);
        ((Button) findViewById(R.id.btn_float)).setText(on ? R.string.float_settings : R.string.float_turn_on);
        findViewById(R.id.float_help).setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private boolean floatingButtonEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, ProAssistService.class);
        for (String s : enabled.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    /** Updates the key box, its label and the "get a key" link for the selected provider. */
    private void showProvider() {
        keyLabel.setText(provider.keyLabel);
        keyInput.setHint(provider.keyHint);
        keyInput.setText(Prefs.getApiKey(this, provider));
        keyLink.setText(Html.fromHtml("<a href=\"" + provider.keyUrl + "\">"
                + provider.keyLinkText + "</a>", Html.FROM_HTML_MODE_LEGACY));
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
                output = Rewriter.rewrite(p, key, text, english);
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
