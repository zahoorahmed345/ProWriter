package com.prowriter.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The line under a result: pinyin for Chinese (for reading only) or the Urdu-script version of
 * a Roman Urdu result (tap it to copy the Urdu script instead).
 */
final class ExtraLine {

    private ExtraLine() {}

    static void show(TextView view, String text, String kind) {
        if (text == null) {
            view.setVisibility(View.GONE);
            view.setText("");
            view.setOnClickListener(null);
            view.setClickable(false);
            return;
        }
        view.setVisibility(View.VISIBLE);
        if (Rewriter.KIND_URDU.equals(kind)) {
            Context c = view.getContext();
            SpannableStringBuilder sb = new SpannableStringBuilder(text);
            sb.setSpan(new RelativeSizeSpan(1.2f), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int start = sb.length();
            sb.append("\n").append(c.getString(R.string.urdu_tap_copy));
            sb.setSpan(new RelativeSizeSpan(0.8f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            view.setTextIsSelectable(false);
            view.setAlpha(1f);
            view.setText(sb);
            view.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Urdu", text));
                    Prefs.markClipUsed(c, text);
                    Toast.makeText(c, R.string.urdu_copied, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            view.setOnClickListener(null);
            view.setClickable(false);
            view.setAlpha(0.65f);
            view.setText(text);
            view.setTextIsSelectable(true);
        }
    }
}
