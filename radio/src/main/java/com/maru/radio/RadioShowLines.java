package com.maru.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * MARU RADIO original 'Ahwan's Music Letters' between-song script.
 * Inspired only by the general genre of music radio storytelling:
 * NO existing show's exact catchphrases, scripts, jingles or voice cloning.
 * TTS host labels are fictional, not independently trained AI singers.
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

    private static final String[] AHWAHN_OPENINGS = {
        "아환의 음악편지입니다. 오늘도 노래가 머무는 시간에 함께해 주세요.",
        "아환입니다. 지금부터 새로운 음악 한 장을 천천히 펼쳐볼까요?",
        "오늘의 마음에 어울리는 한 곡, 아환의 음악편지에서 준비했습니다.",
        "아환입니다. 잠시 쉬어 가는 동안 우리의 자작곡을 함께 들어요.",
        "멀리 계신 청취자 여러분도 반갑습니다. 아환의 음악편지가 이어집니다.",
        "노래에는 저마다 다른 풍경이 있지요. 아환과 다음 풍경으로 가 보겠습니다."
    };

    private static final String[] THIRD_LINES = {
        "이어서 들려드릴 자작곡은 %s입니다.",
        "다음 음악의 제목은 %s입니다. 편안하게 감상하세요.",
        "오늘의 또 다른 선율, %s를 만나보겠습니다.",
        "노래 한 곡의 여운을 이어 갈 시간입니다. 제목은 %s입니다."
    };

    public static List<Line> intro(int category, String rawTitle, int number) {
        String title = tidy(rawTitle);
        int rotation = Math.floorMod(number - 1, AHWAHN_OPENINGS.length);
        int verse = Math.floorMod(number - 1, 4);
        List<Line> result = new ArrayList<>();
        result.add(new Line("아환", Locale.KOREAN, AHWAHN_OPENINGS[rotation], .94f));

        // Every group has three fictional hosts. Language is chosen by genre;
        // if device TTS does not support it, the player will still play music.
        switch (category) {
            case 2: {
                String[] chinese = {
                    "欢迎来到马鲁音乐时间，愿这首原创歌曲陪伴你。",
                    "每一首歌都有故事，今天我们一起听一段新的旋律。",
                    "不管你在哪里，愿音乐带给你一点温暖。",
                    "接下来是一首原创中文歌曲，请慢慢欣赏。"
                };
                result.add(new Line("나린", Locale.SIMPLIFIED_CHINESE, chinese[verse], 1.07f));
                break;
            }
            case 3: {
                String[] english = {
                    "Welcome to our music corner. Here is an original song.",
                    "Every melody tells a different story. Enjoy this one.",
                    "Wherever you are listening, we are glad you are here.",
                    "Let's turn the page and hear another original track."
                };
                result.add(new Line("하온", Locale.ENGLISH, english[verse], 1.04f));
                break;
            }
            case 4: {
                String[] instrumentals = {
                    "이번에는 말보다 선율이 먼저 마음에 닿는 연주곡이에요.",
                    "피아노와 현악기의 울림을 잠시 따라가 보세요.",
                    "바람처럼 고요한 연주로 마음의 속도를 늦춰 보아요.",
                    "멀리 떠나는 기분으로, 가사가 없는 풍경을 들어볼까요?"
                };
                result.add(new Line("나린", Locale.KOREAN, instrumentals[verse], 1.10f));
                break;
            }
            default: {
                String[] korean = {
                    "오늘의 한국 노래는 작은 일상의 마음을 담았습니다.",
                    "익숙한 말 한마디가 오래 기억될 때가 있지요.",
                    "잠시 노래를 들으며 오늘의 하루를 돌아봐요.",
                    "새로운 자작곡 한 곡으로 다음 이야기를 열어볼게요."
                };
                result.add(new Line("나린", Locale.KOREAN, korean[verse], 1.08f));
                break;
            }
        }

        result.add(new Line("하온", Locale.KOREAN,
                String.format(Locale.KOREAN, THIRD_LINES[verse], title), 1.00f));
        return Collections.unmodifiableList(result);
    }

    public static String tidy(String raw) {
        if (raw == null) return "제목 없는 자작곡";
        String result = raw.replaceAll("(?i)\\\\.(mp3|m4a|wav|aac|ogg|flac)$", "")
                .replaceAll("[\\\\p{Cntrl}<>]", " ").trim();
        return result.isEmpty() ? "제목 없는 자작곡"
                : result.substring(0, Math.min(48, result.length()));
    }
}
