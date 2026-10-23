package com.maru.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Original royalty-free scripted introductions for the user's own Suno music.
 * Host aliases are fictional; these are not independent singer voice models.
 */
public final class RadioShowLines {
    private RadioShowLines() {}
    public static final class Line {
        public final String speaker;
        public final Locale locale;
        public final String text;
        public final float pitch;
        Line(String speaker, Locale locale, String text, float pitch) {
            this.speaker = speaker;
            this.locale = locale;
            this.text = text;
            this.pitch = pitch;
        }
    }
    public static List<Line> intro(int category, String rawTitle, int number) {
        String title = tidy(rawTitle);
        List<Line> result = new ArrayList<>();
        result.add(new Line("마루", Locale.KOREAN,
                "마루 라디오입니다. 잠시 이야기를 나눈 뒤 자작곡을 들려드릴게요.", .94f));
        if (category == 2) {
            result.add(new Line("별하", Locale.SIMPLIFIED_CHINESE,
                    "大家好，欢迎来到马鲁音乐时间。请欣赏这首原创歌曲。", 1.07f));
        } else if (category == 4) {
            result.add(new Line("나린", Locale.KOREAN,
                    "바쁜 하루에 잠시 쉬어 가는 연주곡을 준비했어요.", 1.11f));
        } else if (category == 3) {
            result.add(new Line("하온", Locale.ENGLISH,
                    "Welcome to Maru Radio. Here comes our original music.", 1.04f));
        } else {
            result.add(new Line("채온", Locale.KOREAN,
                    "오늘도 찾아주셔서 고마워요. 함께 노래를 들어볼까요?", 1.08f));
        }
        result.add(new Line("가람", Locale.KOREAN,
                "지금 들려드릴 노래는 " + title + " 입니다.", 1.00f));
        return Collections.unmodifiableList(result);
    }
    public static List<Line> ahwanIntro(String title, String artist) {
        String singer = artist == null || artist.trim().isEmpty() || "미지정".equals(artist) ? "" : tidy(artist) + "의 ";
        return Collections.singletonList(new Line("아환", Locale.KOREAN,
                "마루 라디오 아환입니다. 이번 곡은 " + singer + tidy(title) + "입니다. 함께 들어주세요.", 1.0f));
    }
    public static String tidy(String raw) {
        if (raw == null) return "제목 없는 자작곡";
        String result = raw.replaceAll("(?i)\\.(mp3|m4a|wav|aac|ogg|flac)$", "")
                .replaceAll("[\\p{Cntrl}<>]", " ").trim();
        return result.isEmpty() ? "제목 없는 자작곡"
                : result.substring(0, Math.min(48, result.length()));
    }
}
