import com.maru.musiclive.*;

public final class SongRequestPolicyStressSelfTest {
    private static final String[][] CASES = {
            {"민지", "신청곡 틀어 주세요", "ko", "자작곡", "신청곡은 받지 않습니다"},
            {"John", "please play my song request", "en", "original songs", "not accepted"},
            {"小雨", "我想点歌，请播放一首", "zh", "原创歌曲", "不接受点歌"},
            {"さくら", "この曲をリクエストします", "ja", "オリジナル曲", "受け付けていません"},
            {"Анна", "включи мою песню", "ru", "авторские песни", "не принимаются"}
    };

    public static void main(String[] args) {
        int passed = 0;
        for (int i = 0; i < 1000; i++) {
            String[] c = CASES[i % CASES.length];
            ConversationEngine.Reply reply = ConversationEngine.reply(c[0], c[1], c[2]);
            require(reply.intent == ConversationIntent.SONG_REQUEST,
                    "request not classified: " + c[1]);
            require(reply.text.contains(c[3]), "original-song explanation missing: " + reply.text);
            require(reply.text.contains(c[4]), "polite refusal missing: " + reply.text);
            require(!reply.text.contains("방송에 반영")
                            && !reply.text.toLowerCase().contains("i will check it")
                            && !reply.text.contains("我会确认")
                            && !reply.text.contains("メッセージに残してください"),
                    "old acceptance wording remains: " + reply.text);
            passed++;
        }
        ConversationEngine.Reply repeated = ConversationEngine.songRequestRefusal(
                "민지", "ko", 2);
        require(repeated.text.contains("앞서 안내드린 것처럼"),
                "repeat-aware Korean reminder missing");
        System.out.println("SONG-REQUEST-POLICY-STRESS: " + passed + "/" + passed
                + " + FIXED REFUSAL PASS");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
