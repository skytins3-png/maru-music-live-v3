package com.maru.radio;

/** Offline names/safety contract. NOT a microphone/BIGO listener test. */
public final class RadioAhwanClipSlotSelfTest {
    private static void check(boolean good, String meaning) {
        if (!good) throw new AssertionError("FAIL: " + meaning);
        System.out.println("PASS: " + meaning);
    }
    public static void main(String[] args) {
        String[] keys = { RadioAhwanClipSlot.OPENING, RadioAhwanClipSlot.GIFT,
                RadioAhwanClipSlot.FOLLOW, RadioAhwanClipSlot.CLOSING };
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String slot : keys) {
            check(RadioAhwanClipSlot.valid(slot), "Known prerecorded Ahwan clip type " + slot);
            String file = RadioAhwanClipSlot.filename(slot);
            check(file.startsWith("ahwan_") && file.endsWith(".m4a")
                    && !file.contains("/") && !file.contains(".."),
                    "mic recording filename is a safe local basename: " + slot);
            check(!RadioAhwanClipSlot.title(slot).isBlank(), "user-facing label exists " + slot);
            check(names.add(file), "different event slots do not overwrite each other's audio");
        }
        check(!RadioAhwanClipSlot.valid("../outside"), "reject path traversal");
        check(!RadioAhwanClipSlot.valid("comment"), "viewer comments cannot become a recorded slot");
        check(!RadioAhwanClipSlot.valid(null), "null slot rejected");
        try {
            RadioAhwanClipSlot.filename("../../evil");
            throw new AssertionError("Unsafe slot not rejected");
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: illegal slots reject filename creation");
        }
        System.out.println("Ahwan clip slot safety passed. Actual MIC and BIGO receiver were not used.");
    }
}
