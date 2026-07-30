package com.maru.musiclive;

/** Pure text formatter for the small one-line mobile event banner. */
public final class EventOverlayText {
    private EventOverlayText() {}

    public static String format(LiveEvent event) {
        if (event == null) return "감사합니다";
        String name = event.nickname == null ? "" : event.nickname.trim();
        String prefix = name.isEmpty() ? "" : name + "님, ";
        switch (event.type) {
            case JOIN:
                return prefix + "어서 오세요";
            case LIKE:
                return prefix + "좋아요 감사합니다";
            case GIFT:
                return prefix + "선물 정말 감사합니다";
            case FOLLOW:
                return prefix + "팔로우 감사합니다";
            case CHAT:
                return event.detail == null ? "" : event.detail.trim();
            default:
                return prefix + "감사합니다";
        }
    }
}
