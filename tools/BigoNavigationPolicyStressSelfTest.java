import com.maru.musiclive.BigoNavigationPolicy;

public final class BigoNavigationPolicyStressSelfTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int i = 0; i < 1000; i++) {
            String mode = (i % 2 == 0) ? "일반 LIVE" : "오디오 LIVE";
            String expected = (i % 2 == 0)
                    ? BigoNavigationPolicy.MODE_REGULAR
                    : BigoNavigationPolicy.MODE_AUDIO;
            String actual = BigoNavigationPolicy.normalizeMode(mode);
            if (!expected.equals(actual)) {
                throw new AssertionError("mode mismatch at " + i);
            }
            if (BigoNavigationPolicy.isModeLabel(
                    "방송 시작", BigoNavigationPolicy.MODE_REGULAR)) {
                throw new AssertionError("final start button must never be a mode selector");
            }
            checks++;
        }
        if (checks != 1000) {
            throw new AssertionError("expected 1000 checks, got " + checks);
        }
        System.out.println("BIGO-NAVIGATION-POLICY-STRESS: 1000/1000 PASS");
    }
}
