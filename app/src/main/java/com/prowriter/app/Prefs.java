package com.prowriter.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the user's settings privately on the phone. */
final class Prefs {
    private static final String FILE = "settings";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_ENGLISH = "force_english";
    private static final String KEY_OLD_API = "api_key"; // version 1.0 stored the Claude key here

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static Rewriter.Provider getProvider(Context c) {
        String name = sp(c).getString(KEY_PROVIDER, Rewriter.Provider.GEMINI.name());
        try {
            return Rewriter.Provider.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Rewriter.Provider.GEMINI;
        }
    }

    static void setProvider(Context c, Rewriter.Provider p) {
        sp(c).edit().putString(KEY_PROVIDER, p.name()).apply();
    }

    static String getApiKey(Context c, Rewriter.Provider p) {
        String key = sp(c).getString("api_key_" + p.name(), null);
        if (key == null && p == Rewriter.Provider.CLAUDE) {
            key = sp(c).getString(KEY_OLD_API, "");
        }
        return key == null ? "" : key;
    }

    static void setApiKey(Context c, Rewriter.Provider p, String key) {
        sp(c).edit().putString("api_key_" + p.name(), key == null ? "" : key.trim()).apply();
    }

    static Rewriter.Target getLastTarget(Context c) {
        String name = sp(c).getString("last_target", null);
        if (name == null) {
            // Version 1.4 had an "Always write in English" switch.
            return sp(c).getBoolean(KEY_ENGLISH, false) ? Rewriter.Target.ENGLISH : Rewriter.Target.SAME;
        }
        try {
            return Rewriter.Target.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Rewriter.Target.SAME;
        }
    }

    static void setLastTarget(Context c, Rewriter.Target t) {
        sp(c).edit().putString("last_target", t.name()).apply();
    }

    static Rewriter.Speed getSpeed(Context c) {
        try {
            return Rewriter.Speed.valueOf(sp(c).getString("speed", Rewriter.Speed.BALANCED.name()));
        } catch (IllegalArgumentException e) {
            return Rewriter.Speed.BALANCED;
        }
    }

    static void setSpeed(Context c, Rewriter.Speed s) {
        sp(c).edit().putString("speed", s.name()).apply();
    }

    /**
     * Remembers texts ProChat has already worked on, and results it copied, so opening the tile
     * again doesn't redo old clipboard text. Only short fingerprints are stored, not the text.
     */
    static void markClipUsed(Context c, String text) {
        if (text == null || text.trim().isEmpty()) return;
        String fp = fingerprint(text);
        String old = sp(c).getString("used_clips", "");
        StringBuilder out = new StringBuilder(fp);
        int kept = 0;
        for (String f : old.split(",")) {
            if (f.isEmpty() || f.equals(fp)) continue;
            if (++kept > 20) break;
            out.append(',').append(f);
        }
        sp(c).edit().putString("used_clips", out.toString()).apply();
    }

    static boolean isClipUsed(Context c, String text) {
        if (text == null) return false;
        String fp = fingerprint(text);
        for (String f : sp(c).getString("used_clips", "").split(",")) {
            if (f.equals(fp)) return true;
        }
        return false;
    }

    private static String fingerprint(String text) {
        String t = text.trim();
        return Integer.toHexString(t.hashCode()) + "-" + t.length();
    }

    static boolean forceEnglish(Context c) {
        return sp(c).getBoolean(KEY_ENGLISH, false);
    }

    static void setForceEnglish(Context c, boolean value) {
        sp(c).edit().putBoolean(KEY_ENGLISH, value).apply();
    }
}
