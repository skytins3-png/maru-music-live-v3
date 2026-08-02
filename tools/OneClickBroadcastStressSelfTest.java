import com.maru.musiclive.OneClickBroadcastPlan;

public final class OneClickBroadcastStressSelfTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int i = 0; i < 1000; i++) {
            int songCount = i % 7;
            boolean bigoInstalled = (i % 3) != 0;
            boolean alreadyStarting = (i % 5) == 0;
            boolean expected = songCount > 0 && bigoInstalled && !alreadyStarting;
            boolean actual = OneClickBroadcastPlan.canStart(
                    songCount, bigoInstalled, alreadyStarting);
            if (actual != expected) {
                throw new AssertionError(
                        "one-click mismatch at " + i
                                + ": expected=" + expected
                                + ", actual=" + actual);
            }
            checks++;
        }
        if (checks != 1000) {
            throw new AssertionError("expected 1000 checks, got " + checks);
        }
        if (OneClickBroadcastPlan.REQUIRES_MARU_SCREEN_CAPTURE) {
            throw new AssertionError("MARU OCR projection must stay off during BIGO Game Live");
        }
        if (!OneClickBroadcastPlan.REQUIRES_BIGO_SCREEN_CAPTURE_CONSENT) {
            throw new AssertionError("BIGO screen capture consent must remain required");
        }
        if (!OneClickBroadcastPlan.USES_BIGO_NATIVE_TOOLBAR) {
            throw new AssertionError("BIGO native toolbar must remain enabled");
        }
        if (!OneClickBroadcastPlan.USES_EXISTING_PLAYBACK_UI) {
            throw new AssertionError("V3.1.6 existing playback controls must remain enabled");
        }
        if (!OneClickBroadcastPlan.CONTROLS_EXTERNAL_APP_UI) {
            throw new AssertionError("BIGO broadcast-screen navigation must be enabled");
        }
        if (!OneClickBroadcastPlan.USES_USER_ENABLED_ACCESSIBILITY_NAVIGATOR) {
            throw new AssertionError("user-enabled accessibility navigator must be required");
        }
        if (!OneClickBroadcastPlan.STOPS_BEFORE_FINAL_BROADCAST_START) {
            throw new AssertionError("navigator must stop before the final public-broadcast action");
        }
        System.out.println("ONE-CLICK-BROADCAST-STRESS: 1000/1000 PASS + BIGO PREPARATION-SCREEN NAVIGATION PASS");
    }
}
