package com.maru.musiclive;

import java.time.LocalDate;

/**
 * Offline, deterministic daily content rotation. No network, API key, app
 * update or personal data is needed to change the script at local midnight.
 *
 * These are original template conversations, not external generative AI.
 * Do not claim actual events, birthdays, weather or audience messages.
 */
public final class DailyRadioProgram {
    private static final String[] THEMES = {
            "오래된 사진", "가을바람", "다정한 안부", "새벽의 길", "작은 용기",
            "커피 한 잔", "서로 다른 하늘", "따뜻한 집", "산책의 즐거움", "별을 보는 밤",
            "손편지", "첫눈의 기억", "고향의 골목", "달빛 산책", "기다림의 미소",
            "새로운 출발", "그리운 목소리", "우연한 만남", "여행의 풍경", "바다의 노래",
            "소중한 친구", "고마운 사람", "창가의 풍경", "한 줄의 일기", "꿈꾸던 시절",
            "느린 걸음", "노을빛 하늘", "작은 약속", "마음의 쉼표", "먼 곳의 안부",
            "다시 만날 봄"
    };
    private static final String[] QUESTIONS = {
            "같은 노래를 들을 때마다 떠오르는 장면이 달라질까요?",
            "평범한 하루에도 잊지 못할 소리가 있겠죠?",
            "음악이 잠깐의 여행이 될 수도 있을까요?",
            "누군가를 떠올리게 하는 멜로디가 있나요?",
            "지난 시간과 오늘의 마음은 어떻게 이어질까요?",
            "마음에 남는 노래는 무엇이 다른 걸까요?",
            "조용히 듣고 싶은 순간은 언제 찾아올까요?",
            "음악은 말하지 못한 마음을 대신할 수 있을까요?"
    };
    private static final String[] ANSWERS = {
            "오늘의 이야기는 듣는 분의 기억에 맡겨두겠습니다.",
            "그 답은 저마다의 하루 속에 있을 것 같습니다.",
            "모두에게 같은 의미일 필요는 없겠지요.",
            "한 곡의 음악이 작은 쉼터가 되었으면 좋겠습니다.",
            "바쁜 일상에도 잠시 마음을 쉬어갈 수 있으니까요.",
            "서두르지 않아도 괜찮은 시간이었으면 합니다.",
            "노래는 가끔 긴 설명보다 따뜻한 위로가 되지요.",
            "저마다의 이야기를 가진 마음으로 들어보면 어떨까요?"
    };
    private static final String[] CLOSINGS = {
            "그러면 다음 음악으로 이어가겠습니다.",
            "오늘의 다음 곡, 함께 들어볼까요?",
            "이제 음악이 들려주는 이야기를 만나보죠.",
            "편안하게 다음 곡을 들어주세요.",
            "잠시 대화는 접고 음악에 귀를 기울이겠습니다.",
            "그럼 노래로 마음을 이어보겠습니다.",
            "우리의 음악 시간은 계속됩니다.",
            "함께 감상하겠습니다."
    };

    private DailyRadioProgram() {}

    public static RadioConversationScript.Line[] beforeSong(
            String title, int songIndex, LocalDate localDate) {
        if (localDate == null) throw new IllegalArgumentException("localDate required");
        long epochDay = localDate.toEpochDay();
        int topic = Math.floorMod(epochDay, THEMES.length);
        int rotate = Math.floorMod(epochDay + songIndex, QUESTIONS.length);
        // Song index drives a second independent rotation for less repetition
        // within a single day; epochDay makes tomorrow different.
        int replyIndex = Math.floorMod(epochDay * 3 + songIndex * 5L, ANSWERS.length);
        int closingIndex = Math.floorMod(epochDay * 7 + songIndex * 3L, CLOSINGS.length);
        String safeTitle = RadioConversationScript.cleanTitle(title);
        return new RadioConversationScript.Line[]{
                new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN,
                        "아환의 음악다방입니다. 오늘의 음악 이야기 주제는 " + THEMES[topic] + "입니다."),
                new RadioConversationScript.Line(RadioConversationScript.Speaker.YUNSOL,
                        "아환 DJ님, " + QUESTIONS[rotate]),
                new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN,
                        ANSWERS[replyIndex] + " 다음 곡은 " + safeTitle + "입니다."),
                new RadioConversationScript.Line(RadioConversationScript.Speaker.YUNSOL,
                        "듣는 분마다 마음에 남는 장면이 다르겠네요."),
                new RadioConversationScript.Line(RadioConversationScript.Speaker.AHWAN,
                        CLOSINGS[closingIndex])
        };
    }

    /** Acknowledgments are only included when concrete events were recorded. */
    public static RadioConversationScript.Line[] beforeSong(
            String title, int songIndex, LocalDate date,
            java.util.List<String> giftNames, java.util.List<String> followNames) {
        RadioConversationScript.Line[] base = beforeSong(title, songIndex, date);
        boolean gifts = giftNames != null && !giftNames.isEmpty();
        boolean follows = followNames != null && !followNames.isEmpty();
        if (!gifts && !follows) return base;
        String message = gifts && follows
                ? "선물과 팔로우로 응원해주신 여러분, 고맙습니다."
                : gifts ? "선물로 응원해주신 여러분, 고맙습니다."
                        : "팔로우로 함께해주신 여러분, 고맙습니다.";
        RadioConversationScript.Line[] extended =
                new RadioConversationScript.Line[base.length + 1];
        extended[0] = base[0];
        extended[1] = new RadioConversationScript.Line(
                RadioConversationScript.Speaker.AHWAN, message);
        System.arraycopy(base, 1, extended, 2, base.length - 1);
        return extended;
    }
}
