import com.maru.musiclive.*;
import java.util.*;

public final class IntermissionStressSelfTest {
    private static int checks;

    private static void ok(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name + " at check " + checks);
    }

    public static void main(String[] args) {
        String[] expected = {"ko", "en", "zh", "ja", "ru"};
        for (int i = 0; i < 1000; i++) {
            String[] languages = IntermissionAnnouncementText.orderedLanguages();
            boolean valid = Arrays.equals(expected, languages);
            languages[0] = "mutated";
            valid &= "ko".equals(IntermissionAnnouncementText.orderedLanguages()[0]);
            languages = IntermissionAnnouncementText.orderedLanguages();
            String title = "다음곡" + i;
            List<String> gifts = Arrays.asList("GiftUser" + i, "GiftFriend" + i);
            List<String> follows = Collections.singletonList("FollowUser" + i);
            for (String language : languages) {
                String text = IntermissionAnnouncementText.build(
                        title, language, gifts, follows);
                valid &= !text.isEmpty()
                        && text.contains(title)
                        && !text.contains("\n")
                        && text.length() < 600;
            }
            ok(valid, "five-language intermission sequence");
        }
        if (checks != 1000) {
            throw new AssertionError("expected 1000 checks, got " + checks);
        }
        System.out.println("INTERMISSION-STRESS-TEST: " + checks + "/" + checks);
    }
}
