package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

public final class EventVisualStore {
    private static final String PREFS = "maru_event_visuals";

    private EventVisualStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String mediaUri(Context context, EventType type) {
        return prefs(context).getString(key(type), "");
    }

    public static void setMediaUri(Context context, EventType type, String uri) {
        prefs(context).edit()
                .putString(key(type), uri == null ? "" : uri)
                .apply();
    }

    public static void clear(Context context, EventType type) {
        prefs(context).edit().remove(key(type)).apply();
    }

    private static String key(EventType type) {
        EventType value = type == null ? EventType.UNKNOWN : type;
        return "visual_" + value.name().toLowerCase();
    }
}
