package com.maru.radio;

/** Short prerecorded on-air prompts; never represent voice cloning. */
public final class RadioAhwanClipSlot {
    public static final String OPENING = "opening";
    public static final String GIFT = "gift";
    public static final String FOLLOW = "follow";
    public static final String CLOSING = "closing";
    private RadioAhwanClipSlot() {}

    public static boolean valid(String slot) {
        return OPENING.equals(slot) || GIFT.equals(slot)
                || FOLLOW.equals(slot) || CLOSING.equals(slot);
    }

    public static String title(String slot) {
        if (OPENING.equals(slot)) return "방송 시작 인사";
        if (GIFT.equals(slot)) return "선물 감사 인사";
        if (FOLLOW.equals(slot)) return "팔로우 감사 인사";
        if (CLOSING.equals(slot)) return "방송 종료 인사";
        throw new IllegalArgumentException("Unsupported Ahwan clip slot");
    }

    public static String filename(String slot) {
        if (!valid(slot)) throw new IllegalArgumentException("Unsupported Ahwan clip slot");
        return "ahwan_" + slot + ".m4a";
    }
}
