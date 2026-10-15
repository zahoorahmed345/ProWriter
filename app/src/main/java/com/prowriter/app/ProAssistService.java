package com.prowriter.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows a floating "✨ Pro" button above the keyboard in any app (WeChat included).
 * When you stop typing, it prepares the professional version and shows it in a card;
 * tapping "Use" replaces the text in the message box.
 */
public class ProAssistService extends AccessibilityService {

    /** How long you must stop typing before the professional version is prepared. */
    private static final long AUTO_DELAY_MS = 1500;
    /** Don't bother rewriting very short drafts. */
    private static final int MIN_WORDS = 3;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    /** Recent results, so the same text is never sent twice. */
    private final Map<String, String> cache = new LinkedHashMap<String, String>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 30;
        }
    };

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private View root;
    private View pill;
    private View card;
    private ProgressBar pillProgress;
    private TextView pillText;
    private TextView cardTitle;
    private TextView cardText;
    private View useBtn;

    private boolean attached;
    private boolean expanded;
    private Rect lastIme;

    private AccessibilityNodeInfo field;
    private String fieldText = "";
    private String shownSuggestion;
    private String lastApplied;
    private String dismissedFor;
    private int requestId;

    private final Runnable refreshRunnable = this::refresh;
    private final Runnable autoRunnable = () -> requestSuggestion(false);

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        Context themed = new ContextThemeWrapper(this, R.style.AppTheme);
        root = LayoutInflater.from(themed).inflate(R.layout.overlay, null);
        pill = root.findViewById(R.id.overlay_pill);
        card = root.findViewById(R.id.overlay_card);
        pillProgress = root.findViewById(R.id.overlay_progress);
        pillText = root.findViewById(R.id.overlay_pill_text);
        cardTitle = root.findViewById(R.id.overlay_title);
        cardText = root.findViewById(R.id.overlay_text);
        useBtn = root.findViewById(R.id.overlay_use);

        pill.setOnClickListener(v -> onPillTap());
        useBtn.setOnClickListener(v -> apply());
        root.findViewById(R.id.overlay_close).setOnClickListener(v -> {
            dismissedFor = fieldText;
            collapse();
        });

        lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || root == null) return;
        CharSequence pkg = event.getPackageName();
        // Ignore our own app and our own floating button.
        if (pkg != null && getPackageName().contentEquals(pkg)) return;
        // Many events arrive in bursts; handle them once.
        main.removeCallbacks(refreshRunnable);
        main.postDelayed(refreshRunnable, 60);
    }

    @Override
    public void onInterrupt() {
        hide();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        hide();
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ watching the text box

    private void refresh() {
        AccessibilityNodeInfo node;
        try {
            node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        } catch (Exception e) {
            node = null;
        }
        if (node == null || !node.isEditable() || node.isPassword()
                || (node.getPackageName() != null && getPackageName().contentEquals(node.getPackageName()))) {
            leaveField();
            return;
        }

        Rect ime = keyboardBounds();
        if (ime == null) { // keyboard closed: you're not typing
            leaveField();
            return;
        }

        String text = (node.isShowingHintText() || node.getText() == null) ? "" : node.getText().toString();
        field = node;

        if (!text.equals(fieldText)) {
            fieldText = text;
            main.removeCallbacks(autoRunnable);
            if (expanded) setExpanded(false);
            if (Prefs.autoSuggest(this) && wordCount(text) >= MIN_WORDS
                    && !text.equals(lastApplied) && !text.equals(dismissedFor)) {
                main.postDelayed(autoRunnable, AUTO_DELAY_MS);
            }
        }

        if (text.trim().isEmpty()) {
            hide();
            return;
        }
        lastIme = ime;
        show();
    }

    private void leaveField() {
        field = null;
        fieldText = "";
        main.removeCallbacks(autoRunnable);
        hide();
    }

    private Rect keyboardBounds() {
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (Exception e) {
            return null;
        }
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Rect r = new Rect();
                w.getBoundsInScreen(r);
                if (r.height() > 0) return r;
            }
        }
        return null;
    }

    private static int wordCount(String text) {
        String t = text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    // ------------------------------------------------------------------ getting the rewrite

    private void onPillTap() {
        if (fieldText.trim().isEmpty() || pillProgress.getVisibility() == View.VISIBLE) return;
        String cached = cache.get(fieldText);
        if (cached != null) {
            showSuggestion(fieldText, cached, true);
        } else {
            requestSuggestion(true);
        }
    }

    private void requestSuggestion(boolean manual) {
        final String text = fieldText;
        if (wordCount(text) == 0) return;

        String cached = cache.get(text);
        if (cached != null) {
            showSuggestion(text, cached, manual);
            return;
        }

        final int id = ++requestId;
        setLoading(true);
        final Rewriter.Provider provider = Prefs.getProvider(this);
        final String key = Prefs.getApiKey(this, provider);
        final boolean english = Prefs.forceEnglish(this);

        worker.execute(() -> {
            String result = null;
            String error = null;
            try {
                result = Rewriter.rewrite(provider, key, text, english);
            } catch (Rewriter.RewriteException e) {
                error = e.getMessage();
            }
            final String r = result;
            final String err = error;
            main.post(() -> {
                if (id != requestId) return; // a newer request replaced this one
                setLoading(false);
                if (r != null) {
                    cache.put(text, r);
                    if (text.equals(fieldText)) showSuggestion(text, r, manual);
                } else if (manual || err != null && err.startsWith("No ")) {
                    showError(err);
                }
            });
        });
    }

    private void showSuggestion(String original, String suggestion, boolean manual) {
        if (suggestion.trim().equals(original.trim())) {
            if (manual) Toast.makeText(this, R.string.already_good, Toast.LENGTH_SHORT).show();
            return;
        }
        shownSuggestion = suggestion;
        cardTitle.setText(R.string.overlay_title);
        cardText.setText(suggestion);
        useBtn.setVisibility(View.VISIBLE);
        setExpanded(true);
    }

    private void showError(String message) {
        shownSuggestion = null;
        cardTitle.setText(R.string.overlay_error_title);
        cardText.setText(message);
        useBtn.setVisibility(View.GONE);
        setExpanded(true);
    }

    // ------------------------------------------------------------------ replacing the text

    private void apply() {
        String s = shownSuggestion;
        AccessibilityNodeInfo node = field;
        setExpanded(false);
        if (s == null || node == null) return;

        lastApplied = s;
        node.refresh();
        if (!setFieldText(node, s)) {
            Toast.makeText(this, R.string.copied_paste, Toast.LENGTH_LONG).show();
        }
    }

    private boolean setFieldText(AccessibilityNodeInfo node, String text) {
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            Bundle sel = new Bundle();
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length());
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length());
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
            return true;
        }

        // Fallback for apps that don't allow direct text replacement: select all and paste.
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null) return false;
        cm.setPrimaryClip(ClipData.newPlainText("Professional message", text));
        Bundle sel = new Bundle();
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
        sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, fieldText.length());
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel);
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    }

    // ------------------------------------------------------------------ the floating view

    private void setLoading(boolean loading) {
        pillProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        pillText.setText(loading ? R.string.overlay_working : R.string.overlay_pill);
        if (attached) show();
    }

    private void setExpanded(boolean value) {
        expanded = value;
        card.setVisibility(value ? View.VISIBLE : View.GONE);
        pill.setVisibility(value ? View.GONE : View.VISIBLE);
        if (attached || value) show();
    }

    private void collapse() {
        setExpanded(false);
    }

    private void show() {
        if (lastIme == null || field == null) return;
        int margin = dp(8);
        int screenW = Math.max(lastIme.right, getResources().getDisplayMetrics().widthPixels);

        int widthSpec;
        if (expanded) {
            lp.width = screenW - 2 * margin;
            lp.x = margin;
            widthSpec = View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY);
        } else {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            widthSpec = View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST);
        }
        root.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        if (!expanded) lp.x = screenW - root.getMeasuredWidth() - margin;
        lp.y = Math.max(0, lastIme.top - root.getMeasuredHeight() - margin);

        try {
            if (attached) {
                wm.updateViewLayout(root, lp);
            } else {
                wm.addView(root, lp);
                attached = true;
            }
        } catch (Exception e) {
            attached = false;
        }
    }

    private void hide() {
        expanded = false;
        if (card != null) card.setVisibility(View.GONE);
        if (pill != null) pill.setVisibility(View.VISIBLE);
        if (attached) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
                // already removed
            }
            attached = false;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
