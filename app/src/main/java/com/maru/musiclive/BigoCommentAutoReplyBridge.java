package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small, process-safe handoff between OCR conversation analysis and the
 * user-enabled BIGO accessibility service. Only ordinary viewer comments are
 * queued. Join, like, follow and gift system events are deliberately excluded.
 */
public final class BigoCommentAutoReplyBridge {
    private static final String PREFS = "bigo_comment_auto_reply";
    private static final String KEY_TEXT = "pending_text";
    private static final String KEY_NICKNAME = "pending_nickname";
    private static final String KEY_CREATED_AT = "pending_created_at";
    private static final String KEY_LAST_SENT_AT = "last_sent_at";
    private static final long EXPIRE_MS = 25_000L;
    private static final long MIN_SEND_INTERVAL_MS = 6_000L;
    private static final int MAX_LENGTH = 180;

    private BigoCommentAutoReplyBridge() {}

    public static boolean enqueue(Context context, String nickname, String text) {
        String clean = sanitize(text);
        long now = System.currentTimeMillis();
        SharedPreferences prefs = prefs(context);
        if (clean.isEmpty()) return false;
        if (now - prefs.getLong(KEY_LAST_SENT_AT, 0L) < MIN_SEND_INTERVAL_MS) {
            return false;
        }
        prefs.edit()
                .putString(KEY_TEXT, clean)
                .putString(KEY_NICKNAME, nickname == null ? "" : nickname.trim())
                .putLong(KEY_CREATED_AT, now)
                .apply();
        return true;
    }

    public static Pending peek(Context context) {
        SharedPreferences prefs = prefs(context);
        String text = prefs.getString(KEY_TEXT, "");
        long createdAt = prefs.getLong(KEY_CREATED_AT, 0L);
        if (text == null || text.trim().isEmpty() || createdAt <= 0L) return null;
        if (System.currentTimeMillis() - createdAt > EXPIRE_MS) {
            clear(context);
            return null;
        }
        return new Pending(
                prefs.getString(KEY_NICKNAME, ""),
                text.trim(),
                createdAt);
    }

    public static void markSent(Context context) {
        prefs(context).edit()
                .remove(KEY_TEXT)
                .remove(KEY_NICKNAME)
                .remove(KEY_CREATED_AT)
                .putLong(KEY_LAST_SENT_AT, System.currentTimeMillis())
                .apply();
    }

    public static void clear(Context context) {
        prefs(context).edit()
                .remove(KEY_TEXT)
                .remove(KEY_NICKNAME)
                .remove(KEY_CREATED_AT)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        clean = clean.replaceAll("\\s{2,}", " ");
        if (clean.length() > MAX_LENGTH) clean = clean.substring(0, MAX_LENGTH).trim();
        return clean;
    }

    public static final class Pending {
        public final String nickname;
        public final String text;
        public final long createdAt;

        Pending(String nickname, String text, long createdAt) {
            this.nickname = nickname == null ? "" : nickname;
            this.text = text == null ? "" : text;
            this.createdAt = createdAt;
        }
    }
}
