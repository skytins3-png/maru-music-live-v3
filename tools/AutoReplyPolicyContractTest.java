import com.maru.musiclive.AutoReplyPolicy;
import com.maru.musiclive.ChatMessage;
import com.maru.musiclive.EventType;
import com.maru.musiclive.GreetingLanguage;

public final class AutoReplyPolicyContractTest {
    public static void main(String[] args) {
        check(!AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                "민지",
                "님이 입장했습니다",
                "민지 님이 입장했습니다",
                GreetingLanguage.KOREAN)),
                "Korean join must not auto-reply");

        check(AutoReplyPolicy.containsJoinNotification(
                "Blue Moon joined the live"),
                "English join detector must return true");

        check(!AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                "Blue Moon",
                "joined the live",
                "Blue Moon joined the live",
                GreetingLanguage.ENGLISH)),
                "English join must not auto-reply");

        check(AutoReplyPolicy.containsJoinNotification(
                "Blue Moon\njoined the live"),
                "split OCR English join must be detected");

        check(AutoReplyPolicy.containsJoinNotification(
                "小雨进入了你的直播间"),
                "Chinese join must be detected");

        check(AutoReplyPolicy.containsJoinNotification(
                "さくらさんが入室しました"),
                "Japanese join must be detected");

        check(AutoReplyPolicy.containsJoinNotification(
                "Анна присоединилась к эфиру"),
                "Russian join must be detected");

        check(AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                "민지",
                "노래가 좋아요",
                "민지: 노래가 좋아요",
                GreetingLanguage.KOREAN)),
                "ordinary comment must remain eligible");

        check(!AutoReplyPolicy.containsJoinNotification(
                "Blue Moon: I joined yesterday and this song is great"),
                "ordinary chat must not be treated as a join notification");

        check(AutoReplyPolicy.MAX_VISUAL_REPLY_MS == 2_000L,
                "reply policy must stay at two seconds");
        check(EventType.CHAT.overlayDurationMs == 2_000L,
                "chat overlay must use the two-second policy");

        System.out.println("AUTO-REPLY-POLICY-CONTRACT: PASS (11/11)");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
