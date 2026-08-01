import com.maru.musiclive.BroadcastVoicePolicy;
import com.maru.musiclive.GreetingLanguage;

public final class VoicePolicyStressSelfTest {
    public static void main(String[] args) {
        int checks = 0;
        String[] expected = {
                GreetingLanguage.KOREAN,
                GreetingLanguage.ENGLISH,
                GreetingLanguage.CHINESE,
                GreetingLanguage.JAPANESE,
                GreetingLanguage.RUSSIAN
        };
        for (int i = 0; i < 1000; i++) {
            String[] actual = BroadcastVoicePolicy.orderedLanguages();
            if (!BroadcastVoicePolicy.isExpectedFiveLanguageOrder(actual)) {
                throw new AssertionError("language order changed at cycle " + i);
            }
            for (int j = 0; j < expected.length; j++) {
                if (!expected[j].equals(actual[j])) {
                    throw new AssertionError("unexpected language at " + j);
                }
            }
            if (BroadcastVoicePolicy.isSpeechAllowed(
                    BroadcastVoicePolicy.WINDOW_COMMENT)) {
                throw new AssertionError("comment TTS must stay disabled");
            }
            if (BroadcastVoicePolicy.isSpeechAllowed(
                    BroadcastVoicePolicy.WINDOW_EVENT_DURING_SONG)) {
                throw new AssertionError("event TTS during song must stay disabled");
            }
            if (BroadcastVoicePolicy.FORCE_GENDER) {
                throw new AssertionError("voice gender must not be forced");
            }
            checks++;
        }
        if (checks != 1000) throw new AssertionError("expected 1000 checks");
        System.out.println("VOICE-POLICY-STRESS: 1000/1000 PASS");
    }
}
