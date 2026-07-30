import com.maru.musiclive.*;
import java.util.*;

public final class StressSelfTest {
    private static int checks;

    private static void ok(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name + " at check " + checks);
    }

    public static void main(String[] args) {
        BigoJoinParser joinParser = new BigoJoinParser();
        BigoEventParser eventParser = new BigoEventParser();

        for (int i = 0; i < 250; i++) {
            String name = "청취자" + i;
            List<JoinEvent> joins = joinParser.parseAll(name + "님이 입장했습니다");
            ok(joins.size() == 1 && name.equals(joins.get(0).nickname),
                    "strict join parse");
        }

        for (int i = 0; i < 250; i++) {
            String comment = (i % 2 == 0)
                    ? "user" + i + ": 게임 좋아하세요?"
                    : "user" + i + "\nI joined yesterday";
            boolean ignored = joinParser.parseAll(comment).isEmpty()
                    && eventParser.parseAll(comment).isEmpty();
            ok(ignored, "ordinary comment ignored");
        }

        for (int i = 0; i < 250; i++) {
            EventType expected;
            String line;
            int kind = i % 3;
            if (kind == 0) {
                expected = EventType.LIKE;
                line = "사용자" + i + "님이 좋아요를 눌렀습니다";
            } else if (kind == 1) {
                expected = EventType.GIFT;
                line = "사용자" + i + "님이 선물을 보냈습니다 Rose x1";
            } else {
                expected = EventType.FOLLOW;
                line = "사용자" + i + "님이 팔로우했습니다";
            }
            List<LiveEvent> events = eventParser.parseAll(line);
            ok(events.size() == 1 && events.get(0).type == expected,
                    "text-only event parse");
        }

        EventType[] types = {
                EventType.JOIN, EventType.LIKE, EventType.GIFT, EventType.FOLLOW
        };
        for (int i = 0; i < 250; i++) {
            EventType type = types[i % types.length];
            LiveEvent event = new LiveEvent(
                    type,
                    "긴닉네임테스트" + i,
                    type == EventType.GIFT ? "Rose x10" : "",
                    "",
                    "ko",
                    i);
            String text = EventOverlayText.format(event);
            ok(!text.isEmpty() && !text.contains("\n") && text.contains("님, "),
                    "single-line overlay text");
        }

        if (checks != 1000) throw new AssertionError("expected 1000 checks, got " + checks);
        System.out.println("STRESS-SELF-TEST: " + checks + "/" + checks);
    }
}
