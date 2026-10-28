package com.maru.musiclive;

/**
 * Original, offline radio conversation scripts.
 *
 * These are fictional presenter lines, not transcripts, impersonations or live
 * viewer events. They never claim a guest, listener or song backstory exists.
 */
public final class RadioConversationScript {
    public enum Speaker { AHWAN, YUNSOL }

    public static final class Line {
        public final Speaker speaker;
        public final String text;

        public Line(Speaker speaker, String text) {
            this.speaker = speaker;
            this.text = text;
        }
    }

    private RadioConversationScript() {}

    public static Line[] beforeSong(String title, int songIndex) {
        String safeTitle = cleanTitle(title);
        switch (Math.floorMod(songIndex, 4)) {
            case 0:
                return new Line[]{
                        new Line(Speaker.AHWAN,
                                "안녕하세요. 아환의 음악다방입니다. 잠시 쉬어가는 음악 시간, 함께하시죠."),
                        new Line(Speaker.YUNSOL,
                                "아환 DJ님, 오늘은 어떤 음악을 준비하셨나요?"),
                        new Line(Speaker.AHWAN,
                                "이번에 들려드릴 곡은 " + safeTitle + "입니다. 편안하게 귀 기울여 보세요."),
                        new Line(Speaker.YUNSOL,
                                "어떤 추억이 떠오를지 기대되네요."),
                        new Line(Speaker.AHWAN, "그럼 음악으로 이어가겠습니다.")
                };
            case 1:
                return new Line[]{
                        new Line(Speaker.AHWAN, "아환의 음악다방, 노래 사이에 잠깐 인사드립니다."),
                        new Line(Speaker.YUNSOL, "한 곡이 끝나면 마음에 어떤 장면이 남을까요?"),
                        new Line(Speaker.AHWAN,
                                "그건 듣는 분마다 다르겠지요. 다음 곡은 " + safeTitle + "입니다."),
                        new Line(Speaker.YUNSOL, "잠시 말을 줄이고, 음악을 느껴볼까요?"),
                        new Line(Speaker.AHWAN, "함께 듣겠습니다.")
                };
            case 2:
                return new Line[]{
                        new Line(Speaker.AHWAN, "마루 라디오, 아환의 음악다방입니다."),
                        new Line(Speaker.YUNSOL, "아환 DJ님, 좋은 음악은 왜 오래 기억에 남을까요?"),
                        new Line(Speaker.AHWAN,
                                "우리 마음과 닮은 순간이 있기 때문일 겁니다. 다음은 " + safeTitle + "입니다."),
                        new Line(Speaker.YUNSOL, "지금 이 순간에도 그런 노래가 될 수 있겠네요."),
                        new Line(Speaker.AHWAN, "다음 곡, 바로 들려드리겠습니다.")
                };
            default:
                return new Line[]{
                        new Line(Speaker.AHWAN, "음악이 이어지는 이곳, 아환의 음악다방입니다."),
                        new Line(Speaker.YUNSOL, "노래 한 곡으로도 여행을 떠날 수 있을까요?"),
                        new Line(Speaker.AHWAN,
                                "눈을 감고 들으면 어디든 갈 수 있죠. 이번 곡은 " + safeTitle + "입니다."),
                        new Line(Speaker.YUNSOL, "그럼 음악이 들려주는 풍경을 만나보겠습니다."),
                        new Line(Speaker.AHWAN, "편안하게 들어주세요.")
                };
        }
    }

    /** Limit unintended text-to-speech from externally named audio files. */
    public static String cleanTitle(String value) {
        if (value == null) return "다음 음악";
        String cleaned = value.replaceAll("[\\p{Cntrl}\\r\\n\\t]", " ")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) return "다음 음악";
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}
