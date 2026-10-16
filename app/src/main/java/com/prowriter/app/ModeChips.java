package com.prowriter.app;

import android.view.View;
import android.widget.TextView;

/** The buttons "Pro | Reply | ENG | 中文 | اردو" used in the pop-ups and the app. */
final class ModeChips {

    interface Listener {
        void onModeChanged(Rewriter.Target target);
    }

    private final TextView[] chips;
    private final int offTextColor;
    private Rewriter.Target current;
    private final java.util.EnumSet<Rewriter.Target> disabled =
            java.util.EnumSet.noneOf(Rewriter.Target.class);

    ModeChips(View row, Rewriter.Target initial, Listener listener) {
        chips = new TextView[] {
                row.findViewById(R.id.chip_same),
                row.findViewById(R.id.chip_reply),
                row.findViewById(R.id.chip_english),
                row.findViewById(R.id.chip_chinese),
                row.findViewById(R.id.chip_urdu)
        };
        offTextColor = row.getContext().getColor(R.color.chip_text_off);
        Rewriter.Target[] targets = Rewriter.Target.values();
        for (int i = 0; i < chips.length; i++) {
            final Rewriter.Target t = targets[i];
            chips[i].setOnClickListener(v -> {
                if (t == current || disabled.contains(t)) return;
                select(t);
                if (listener != null) listener.onModeChanged(t);
            });
        }
        select(initial);
    }

    void select(Rewriter.Target target) {
        current = target;
        Rewriter.Target[] targets = Rewriter.Target.values();
        for (int i = 0; i < chips.length; i++) {
            boolean on = targets[i] == target;
            chips[i].setBackgroundResource(on ? R.drawable.chip_on : R.drawable.chip_off);
            chips[i].setTextColor(on ? 0xFFFFFFFF : offTextColor);
        }
    }

    /** Greys out a mode that can't be used right now (e.g. Reply while using voice). */
    void setEnabled(Rewriter.Target target, boolean enabled) {
        if (enabled) disabled.remove(target); else disabled.add(target);
        chips[target.ordinal()].setAlpha(enabled ? 1f : 0.35f);
    }

    Rewriter.Target get() {
        return current;
    }
}
