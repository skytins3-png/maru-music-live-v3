import com.maru.musiclive.*;

public final class VisualCompatibilityStressSelfTest {
    private static int checks;

    private static void ok(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name + " at check " + checks);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 250; i++) {
            int screen = 320 + (i % 8) * 90;
            int min = 180 + (i % 5) * 10;
            int width = BroadcastVisualProfile.overlayWidthPx(screen, min);
            ok(width <= screen && width >= Math.min(screen, min), "overlay fits mobile width");
        }
        for (int i = 0; i < 250; i++) {
            ok(BroadcastVisualProfile.isCompactMobileProfile(), "compact profile remains enabled");
        }
        for (int i = 0; i < 250; i++) {
            ok(BroadcastVisualProfile.EVENT_SP < BroadcastVisualProfile.LYRIC_SP
                    && BroadcastVisualProfile.TIME_SP < BroadcastVisualProfile.TITLE_SP,
                    "visual hierarchy");
        }
        for (int i = 0; i < 250; i++) {
            ok(BroadcastVisualProfile.EVENT_TOP_MARGIN_DP
                            > BroadcastVisualProfile.TITLE_TOP_MARGIN_DP
                    && BroadcastVisualProfile.EVENT_HEIGHT_DP <= 42,
                    "event banner stays below title and compact");
        }
        if (checks != 1000) throw new AssertionError("expected 1000 checks, got " + checks);
        System.out.println("VISUAL-COMPATIBILITY-STRESS-TEST: " + checks + "/" + checks);
    }
}
