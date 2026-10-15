package com.prowriter.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Stores the user's settings privately on the phone. */
final class Prefs {
    private static final String FILE = "settings";
    private static final String KEY_API = "api_key";
    private static final String KEY_ENGLISH = "force_english";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String getApiKey(Context c) {
        return sp(c).getString(KEY_API, "");
    }

    static void setApiKey(Context c, String key) {
        sp(c).edit().putString(KEY_API, key == null ? "" : key.trim()).apply();
    }

    static boolean forceEnglish(Context c) {
        return sp(c).getBoolean(KEY_ENGLISH, false);
    }

    static void setForceEnglish(Context c, boolean value) {
        sp(c).edit().putBoolean(KEY_ENGLISH, value).apply();
    }
}
