package com.maru.radio;

import java.util.Arrays;
import java.util.List;

public final class RadioDjBrainSelfTest {
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError("FAIL: " + label);
        System.out.println("PASS: " + label);
    }
    public static void main(String[] args) {
        List<String> catalog = Arrays.asList("가을 남자", "봄바람", "风中有你",
                "Autumn Sunset", "바람의 소리 1", "가을 남자 (연주)");
        check(RadioDjBrain.findSong("가을 남자", catalog) == 0, "exact request title");
        check(RadioDjBrain.findSong("가을", catalog) == -1, "ambiguous partial title rejected");
        check(RadioDjBrain.findSong("봄바람", catalog) == 1, "single title");
        check(RadioDjBrain.findSong("없는 노래", catalog) == -1, "unknown request");
        check(RadioDjBrain.findSong("", catalog) == -1, "empty request");
        check(RadioDjBrain.respond("봄바람 노래 틀어줘", catalog, 0).songIndex == 1,
                "spoken Korean request selects catalog source");
        check(RadioDjBrain.respond("Autumn Sunset 재생해줘", catalog, 0).songIndex == 3,
                "English title in Korean request");
        check(RadioDjBrain.respond("오늘 힘들어요", catalog, 0).speech.contains("쉬어"),
                "empathic radio reply");
        check(RadioDjBrain.respond("장미 선물", catalog, 0).speech.contains("고맙"),
                "gift thank-you");
        check(RadioDjBrain.respond("팔로우했습니다", catalog, 0).speech.contains("팔로우"),
                "follow thank-you");
        check(!RadioDjBrain.opening(1).equals(RadioDjBrain.opening(2)),
                "script variants rotate");
        check(RadioDjBrain.nextSong("바람의 소리", 1).contains("바람의 소리"),
                "next song title in DJ narration");
        String malicious = "악성\\u202e\n문장" + "X".repeat(300);
        check(RadioDjBrain.respond(malicious, catalog, 4).speech.length() < 220,
                "long or control-filled comment never echoed");
        check(RadioDjBrain.respond("안녕", catalog, 0).songIndex == -1,
                "ordinary greeting cannot start music");
        System.out.println("MARU DJ unit tests PASS; not proof of user voice cloning or BIGO audio routing.");
    }
}
