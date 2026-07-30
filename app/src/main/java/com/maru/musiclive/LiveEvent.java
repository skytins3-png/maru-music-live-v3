package com.maru.musiclive;

import java.util.Locale;
import java.util.Objects;

public final class LiveEvent {
    public final EventType type;
    public final String nickname;
    public final String detail;
    public final String rawText;
    public final String languageHint;
    public final long timeMs;

    public LiveEvent(
            EventType type,
            String nickname,
            String detail,
            String rawText,
            String languageHint,
            long timeMs) {
        this.type = type == null ? EventType.UNKNOWN : type;
        this.nickname = clean(Objects.requireNonNull(nickname, "nickname"));
        this.detail = clean(detail);
        this.rawText = rawText == null ? "" : rawText.trim();
        this.languageHint = GreetingLanguage.normalize(languageHint);
        this.timeMs = timeMs;
    }

    public LiveEvent withLanguage(String language) {
        return new LiveEvent(
                type,
                nickname,
                detail,
                rawText,
                language,
                timeMs);
    }

    public String fingerprint() {
        String base = type.name()
                + "|"
                + nickname.toLowerCase(Locale.ROOT);
        if (type == EventType.GIFT && !detail.isEmpty()) {
            base += "|" + detail.toLowerCase(Locale.ROOT);
        }
        if (type == EventType.CHAT && !detail.isEmpty()) {
            base += "|" + detail.toLowerCase(Locale.ROOT);
        }
        return base;
    }

    public String displayLabel() {
        String value = nickname;
        if (!detail.isEmpty()) value += " · " + detail;
        return value;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
