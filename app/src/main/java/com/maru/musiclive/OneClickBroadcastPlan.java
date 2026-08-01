package com.maru.musiclive;

/** Pure-Java launch rules for the safe single-button BIGO broadcast flow. */
public final class OneClickBroadcastPlan {
    public static final String BIGO_PACKAGE = "sg.bigo.live";
    public static final long BIGO_OPEN_DELAY_MS = 900L;
    public static final BroadcastMode BROADCAST_MODE = BroadcastMode.PORTRAIT_9_16;
    public static final boolean REQUIRES_SCREEN_CAPTURE_CONSENT = true;
    public static final boolean CONTROLS_EXTERNAL_APP_UI = false;

    private OneClickBroadcastPlan() {}

    public static boolean canStart(
            int songCount,
            boolean bigoInstalled,
            boolean alreadyStarting) {
        return songCount > 0 && bigoInstalled && !alreadyStarting;
    }
}
