package com.maru.radio;

import android.content.Context;
import android.content.SharedPreferences;

/** Independent opt-in. Does not overwrite legacy DJ or original music settings. */
public final class RadioEnsembleSettings {
    private static final String PREF = "maru_radio_ensemble_v1";
    private RadioEnsembleSettings() {}
    private static SharedPreferences pref(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    public static boolean enabled(Context ctx) {
        return pref(ctx).getBoolean("enabled", false);
    }
    public static void setEnabled(Context ctx, boolean value) {
        pref(ctx).edit().putBoolean("enabled", value).apply();
    }
    public static int lines(Context ctx) {
        return RadioEnsembleScript.clampLines(
                pref(ctx).getInt("lines", RadioEnsembleScript.DEFAULT_LINES));
    }
    public static void setLines(Context ctx, int value) {
        pref(ctx).edit().putInt("lines", RadioEnsembleScript.clampLines(value)).apply();
    }
    public static int every(Context ctx) {
        return RadioEnsembleScript.clampEvery(pref(ctx).getInt("every", 1));
    }
    public static void setEvery(Context ctx, int value) {
        pref(ctx).edit().putInt("every", RadioEnsembleScript.clampEvery(value)).apply();
    }
}
