package com.maru.radio;

/** Pure-java layout checks for a visible BIGO mixer "microphone volume = 1".
 * The numeral 1 anywhere else (room viewer count, other effect slider, etc.)
 * must never qualify as a microphone level. No private text or screenshots stored.
 */
public final class BigoMicLayoutPolicy {
    private BigoMicLayoutPolicy() {}

    public static boolean isNearMic(int screenWidth, int screenHeight,
            int micLeft, int micTop, int micRight, int micBottom,
            int digitLeft, int digitTop, int digitRight, int digitBottom) {
        if (screenWidth < 360 || screenHeight < 640 || screenWidth >= screenHeight
                || micRight <= micLeft || micBottom <= micTop
                || digitRight <= digitLeft || digitBottom <= digitTop)
            return false;
        if (micLeft < 0 || micTop < 0 || digitLeft < 0 || digitTop < 0
                || micRight > screenWidth || micBottom > screenHeight
                || digitRight > screenWidth || digitBottom > screenHeight)
            return false;
        // User's mixer screenshot: "마이크 볼륨" left aligned near y=460,
        // level "1" right aligned near y=530. BIGO may change layouts, so
        // require relative adjacency but never manufacture a guess if absent.
        float micY = (micTop + micBottom) * .5f;
        float digitY = (digitTop + digitBottom) * .5f;
        boolean toRight = digitLeft >= micRight && digitLeft >= screenWidth * .62f;
        boolean nearby = digitY >= micY - screenHeight * .025f
                && digitY <= micY + screenHeight * .100f;
        return toRight && nearby;
    }
}
