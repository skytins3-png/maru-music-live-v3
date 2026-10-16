package com.maru.radio;

/** Pure JVM tests: no Android, no BIGO network, no real room or sensitive data. */
public final class BigoChatRuleSelfTest {
    private static void check(boolean yes, String name) {
        if (!yes) throw new AssertionError("FAIL " + name);
        System.out.println("PASS " + name);
    }
    public static void main(String[] args) {
        check(BigoChatRule.match(null, null, "nickname: hello") == null,
                "ignore untagged app UI and private messages");
        check(BigoChatRule.match("sg.bigo.live:id/live_chat", null,
                "viewer: Hello Ahwan").language == RadioDjLanguagePolicy.ENGLISH,
                "tagged English greeting produces English spoken reply");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "粉丝: 你好阿欢").language == RadioDjLanguagePolicy.CHINESE,
                "Chinese greeting is Chinese voice");
        check(BigoChatRule.match("sg.bigo.live:id/danmu_msg", null,
                "청취자: 안녕하세요").language == RadioDjLanguagePolicy.KOREAN,
                "Korean greeting is Korean voice");
        check(BigoChatRule.match("sg.bigo.live:id/chat_message", null,
                "ゲスト: こんにちは").language == RadioDjLanguagePolicy.JAPANESE,
                "Japanese greeting gets Japanese voice");
        check(BigoChatRule.match("sg.bigo.live:id/chat_message", null,
                "гость: привет").language == RadioDjLanguagePolicy.RUSSIAN,
                "Russian greeting gets Russian voice");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "listener: Please tell me the weather") == null,
                "no unverified weather or arbitrary free-response");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "listener: https://example.com hi") == null,
                "ignore linked/suspicious comments");
        check(BigoChatRule.match("sg.bigo.live:id/chat_message", null,
                "listener: +821012345678") == null,
                "never auto-repeat phone numbers");
        check(BigoChatRule.match("sg.bigo.live:id/profile_name", null,
                "listener: hello") == null,
                "no replies to profile text");
        check(BigoChatRule.match("sg.bigo.live:id/chat_msg", null,
                "fan: thank you").reply.contains("Thank you"),
                "English appreciation reply");
        check(BigoChatRule.match("sg.bigo.live:id/chat_msg", null,
                "fan: gift rose").reply.contains("gift"),
                "gift uses same fixed language policy");
        check(BigoChatRule.match("sg.bigo.live:id/chat_msg", null,
                "guest: hallo") == null,
                "unsupported unknown comment remains silent");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "청취자: 어떤 노래 추천해요?").reply.contains("제목"),
                "Korean music question gets a short contextual suggestion without reading the comment aloud");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "청취자: 아환 이름이 뭐야?").reply.contains("아환"),
                "Korean DJ identity answered automatically");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "user: What is your name?").reply.contains("Ahwan"),
                "English identity answered automatically");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "粉丝: 你叫什么名字？").language == RadioDjLanguagePolicy.CHINESE,
                "Chinese DJ name answered in Chinese");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "ゲスト: お名前を教えて").language == RadioDjLanguagePolicy.JAPANESE,
                "Japanese identity answered in Japanese");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "гость: как тебя зовут?").language == RadioDjLanguagePolicy.RUSSIAN,
                "Russian identity answered in Russian");
        check(BigoChatRule.match("sg.bigo.live:id/comment_row", null,
                "guest: good song").reply != null,
                "common music comment gets a safe reply");
        BigoChatRule.Match a = BigoChatRule.match("sg.bigo.live:id/chat_msg", null,
                "guest: hello");
        BigoChatRule.Match b = BigoChatRule.match("sg.bigo.live:id/chat_msg", null,
                "guest: hello");
        check(a.transientFingerprint == b.transientFingerprint,
                "in-memory dedupe key remains stable for identical row");
        System.out.println("BIGO chat parser tests passed. NOT a live-BIGO acceptance test.");
    }
}
