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

    static boolean forceEnglish(Context c) {
        return sp(c).getBoolean(KEY_ENGLISH, false);
    }

    static void setForceEnglish(Context c, boolean value) {
        sp(c).edit().putBoolean(KEY_ENGLISH, value).apply();
    }
}
