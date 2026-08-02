package com.maru.musiclive;

public enum EventType {
    JOIN("입장", 40, 3_000L),
    LIKE("좋아요", 10, 2_500L),
    GIFT("선물", 100, 4_000L),
    FOLLOW("팔로우", 70, 3_000L),
    CHAT("대화", 30, AutoReplyPolicy.MAX_VISUAL_REPLY_MS),
    UNKNOWN("알 수 없음", 0, 3_000L);

    public final String koreanLabel;
    public final int priority;
    public final long overlayDurationMs;

    EventType(String koreanLabel, int priority, long overlayDurationMs) {
        this.koreanLabel = koreanLabel;
        this.priority = priority;
        this.overlayDurationMs = overlayDurationMs;
    }

    public static EventType fromStored(String value) {
        if (value == null) return UNKNOWN;
        try {
            return EventType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
