package com.prowriter.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/** Settings screen: save the API key, choose language, and try the rewrite. */
public class MainActivity extends Activity {

    private EditText keyInput;
    private EditText tryInput;
    private TextView tryResult;
    private ProgressBar tryProgress;
    private Button tryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        keyInput = findViewById(R.id.api_key);
        tryInput = findViewById(R.id.try_input);
        tryResult = findViewById(R.id.try_result);
        tryProgress = findViewById(R.id.try_progress);
        tryBtn = findViewById(R.id.btn_try);

        ((TextView) findViewById(R.id.key_link)).setMovementMethod(LinkMovementMethod.getInstance());

        keyInput.setText(Prefs.getApiKey(this));

        CheckBox english = findViewById(R.id.force_english);
        english.setChecked(Prefs.forceEnglish(this));
        english.setOnCheckedChangeListener((b, checked) -> Prefs.setForceEnglish(this, checked));

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();
            Prefs.setApiKey(this, key);
            hideKeyboard();
            Toast.makeText(this, key.isEmpty() ? R.string.key_cleared : R.string.key_saved,
                    Toast.LENGTH_SHORT).show();
        });

        tryBtn.setOnClickListener(v -> tryIt());
    }

    private void tryIt() {
        String text = tryInput.getText().toString();
        if (text.trim().isEmpty()) {
            tryInput.setError(getString(R.string.type_something));
            return;
        }
        hideKeyboard();

        // Use whatever is in the key box, even if the user forgot to tap Save.
        String key = keyInput.getText().toString().trim();
        if (!key.isEmpty()) Prefs.setApiKey(this, key);
        boolean english = Prefs.forceEnglish(this);

        tryBtn.setEnabled(false);
        tryProgress.setVisibility(View.VISIBLE);
        tryResult.setText("");

        new Thread(() -> {
            String output;
            try {
                output = Rewriter.rewrite(key, text, english);
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
