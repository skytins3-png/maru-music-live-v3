package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 곡 재생 중 감지된 감사 대상을 모아 다음 곡 직전에 한 번만 안내한다. */
public final class IntermissionStore {
    private static final String PREFS = "maru_intermission";
    private static final String KEY_GIFT_NAMES = "gift_names";
    private static final String KEY_FOLLOW_NAMES = "follow_names";
    private static final String SEP = "\u001F";
    private static final int MAX_NAMES = 8;

    private IntermissionStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void recordEvent(Context context, LiveEvent event) {
        if (context == null || event == null) return;
        if (event.type == EventType.GIFT) {
            appendUnique(context, KEY_GIFT_NAMES, event.nickname);
        } else if (event.type == EventType.FOLLOW) {
            appendUnique(context, KEY_FOLLOW_NAMES, event.nickname);
        }
    }

    public static synchronized Snapshot takeSnapshot(Context context) {
        SharedPreferences preferences = prefs(context);
        List<String> gifts = decode(preferences.getString(KEY_GIFT_NAMES, ""));
        List<String> follows = decode(preferences.getString(KEY_FOLLOW_NAMES, ""));
        preferences.edit()
                .remove(KEY_GIFT_NAMES)
                .remove(KEY_FOLLOW_NAMES)
                .apply();
        return new Snapshot(gifts, follows);
    }

    /** 매 곡 사이 안내에서 다섯 언어를 모두 같은 순서로 재생한다. */
    public static String[] announcementLanguages() {
        return IntermissionAnnouncementText.orderedLanguages();
    }

    public static synchronized void resetSession(Context context) {
        prefs(context).edit()
                .remove(KEY_GIFT_NAMES)
                .remove(KEY_FOLLOW_NAMES)
                .apply();
    }

    private static void appendUnique(Context context, String key, String nickname) {
        String clean = cleanName(nickname);
        if (clean.isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        List<String> names = decode(preferences.getString(key, ""));
        String folded = clean.toLowerCase(Locale.ROOT);
        for (String old : names) {
            if (old.toLowerCase(Locale.ROOT).equals(folded)) return;
        }
        names.add(clean);
        while (names.size() > MAX_NAMES) names.remove(0);
        preferences.edit().putString(key, String.join(SEP, names)).apply();
    }

    private static List<String> decode(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.isEmpty()) return out;
        for (String part : value.split(SEP, -1)) {
            String clean = cleanName(part);
            if (!clean.isEmpty()) out.add(clean);
        }
        return out;
    }

    private static String cleanName(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace(SEP, " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.length() > 40) clean = clean.substring(0, 40);
        return clean;
    }

    public static final class Snapshot {
        public final List<String> giftNames;
        public final List<String> followNames;

        Snapshot(List<String> giftNames, List<String> followNames) {
            this.giftNames = Collections.unmodifiableList(new ArrayList<>(giftNames));
            this.followNames = Collections.unmodifiableList(new ArrayList<>(followNames));
        }
    }
}
