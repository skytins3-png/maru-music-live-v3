package com.maru.musiclive;

/**
 * Mobile broadcast visual constants kept compatible with the compact V2.7.5 look.
 * This class has no Android dependency so the values can be stress-tested on the build host.
 */
public final class BroadcastVisualProfile {
    public static final float TITLE_SP = 15f;
    public static final float EVENT_SP = 14f;
    public static final float LYRIC_SP = 17f;
    public static final float TIME_SP = 12f;
    public static final float CONTROL_SP = 11f;

    public static final int TITLE_HEIGHT_DP = 48;
    public static final int TITLE_TOP_MARGIN_DP = 24;
    public static final int EVENT_MIN_WIDTH_DP = 220;
    public static final float EVENT_WIDTH_RATIO = 0.82f;
    public static final int EVENT_HEIGHT_DP = 42;
    public static final int EVENT_TOP_MARGIN_DP = 80;
    public static final int EVENT_CORNER_DP = 12;
    public static final int BIGO_NATIVE_TOOLBAR_SAFE_BOTTOM_DP = 220;
    public static final int LYRIC_BOTTOM_MARGIN_DP = 250;
    public static final int TIME_HEIGHT_DP = 34;
    public static final int TIME_BOTTOM_MARGIN_DP = 60;

    private BroadcastVisualProfile() {}

    public static int overlayWidthPx(int screenWidthPx, int minWidthPx) {
        if (screenWidthPx <= 0) return Math.max(0, minWidthPx);
        int width = Math.max(minWidthPx, Math.round(screenWidthPx * EVENT_WIDTH_RATIO));
        return Math.min(screenWidthPx, width);
    }

    public static boolean isCompactMobileProfile() {
        return EVENT_SP <= 14f
                && TITLE_SP <= 15f
                && LYRIC_SP <= 17f
                && TIME_SP <= 12f
                && EVENT_HEIGHT_DP <= 42
                && EVENT_WIDTH_RATIO <= 0.82f
                && BIGO_NATIVE_TOOLBAR_SAFE_BOTTOM_DP >= 200
                && LYRIC_BOTTOM_MARGIN_DP > BIGO_NATIVE_TOOLBAR_SAFE_BOTTOM_DP;
    }
}
