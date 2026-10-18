package com.maru.radio;

import java.util.Set;

/** Public-room chat guard. No credential, raw comments or private messages. */
public final class BigoHostChatGate {
    public static final long HOST_TOOL_PROOF_MS = 60L * 60L * 1000L;
    private BigoHostChatGate() {}

    /**
     * Legacy host anchors stay valid. The fallback is permitted only while
     * the previously observed host-exclusive tools sheet proof is fresh.
     * This gate cannot inject any sound into BIGO's broadcaster microphone.
     */
    public static boolean mayReadPublicChat(Set<String> labels,
            long observedOwnerToolsAt, long now) {
        if (labels == null || BigoAutomationPolicy.isSetup(labels)
                || BigoAutomationPolicy.isEndConfirmation(labels))
            return false;
        if (BigoAutomationPolicy.isHostLive(labels)) return true;
        if (observedOwnerToolsAt <= 0 || now < observedOwnerToolsAt
                || now - observedOwnerToolsAt > HOST_TOOL_PROOF_MS) return false;
        return BigoAutomationPolicy.isOnAirSeatLayout(labels);
    }
}
