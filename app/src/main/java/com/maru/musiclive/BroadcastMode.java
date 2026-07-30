package com.maru.musiclive;

public enum BroadcastMode {
    PORTRAIT_9_16("세로 9:16 전체화면"),
    LANDSCAPE_16_9("가로 16:9 음악방송 · BIGO 채팅용");

    public final String label;

    BroadcastMode(String label) {
        this.label = label;
    }

    public static BroadcastMode fromStored(String value) {
        try {
            return value == null ? PORTRAIT_9_16 : valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PORTRAIT_9_16;
        }
    }
}
