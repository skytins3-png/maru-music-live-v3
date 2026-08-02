import com.maru.musiclive.AutoReplyPolicy;
import com.maru.musiclive.ChatMessage;
import com.maru.musiclive.EventType;
import com.maru.musiclive.GreetingLanguage;
import com.maru.musiclive.IntermissionAnnouncementText;

import java.util.Collections;

public final class AutoReplyPolicyStressSelfTest {
    private static int checks;

    public static void main(String[] args) {
        for (int i = 0; i < 1_000; i++) {
            String name = "청취자" + i;
            String englishName = "Blue Moon " + i;

            ok(!AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                    name,
                    "님이 입장했습니다",
                    name + " 님이 입장했습니다",
                    GreetingLanguage.KOREAN)),
                    "Korean join excluded from AI reply");

            ok(AutoReplyPolicy.containsJoinNotification(
                    englishName + " joined the live"),
                    "English join recognized as a join notification");

            ok(!AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                    englishName,
                    "joined the live",
                    englishName + " joined the live",
                    GreetingLanguage.ENGLISH)),
                    "English join excluded from AI reply");

            ok(AutoReplyPolicy.containsJoinNotification(
                    englishName + "\njoined the live"),
                    "split English OCR join recognized");

            ok(AutoReplyPolicy.containsJoinNotification(
                    "小雨" + i + "进入了你的直播间"),
                    "Chinese join recognized");

            ok(AutoReplyPolicy.containsJoinNotification(
                    "さくら" + i + "さんが入室しました"),
                    "Japanese join recognized");

            ok(AutoReplyPolicy.containsJoinNotification(
                    "Анна" + i + " присоединилась к эфиру"),
                    "Russian join recognized");

            ok(AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                    name,
                    "자작곡이 좋아요",
                    name + ": 자작곡이 좋아요",
                    GreetingLanguage.KOREAN)),
                    "ordinary comment remains eligible");

            ok(!AutoReplyPolicy.containsJoinNotification(
                    englishName + ": I joined yesterday and this song is great"),
                    "ordinary English chat is not misclassified as a join");

            ok(EventType.CHAT.overlayDurationMs <= 2_000L,
                    "visual reply duration <= 2 seconds");

            String between = IntermissionAnnouncementText.build(
                    "다음 자작곡",
                    GreetingLanguage.KOREAN,
                    Collections.emptyList(),
                    Collections.emptyList());
            ok(between.contains("환영") && between.contains("다음 노래"),
                    "between-song welcome and song guide retained");
        }
        if (checks != 11_000) {
            throw new AssertionError("expected 11000 checks, got " + checks);
        }
        System.out.println(
                "AUTO-REPLY-POLICY-STRESS: PASS (1000/1000, "
                        + checks + " checks)");
    }

    private static void ok(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label + " at check " + checks);
    }
}
