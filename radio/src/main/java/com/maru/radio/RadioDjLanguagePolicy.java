package com.maru.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One announcer segment per language. TTS must switch locale for each segment.
 * Automatic announcements NEVER rotate through all five languages.
 */
public final class RadioDjLanguagePolicy {
    public static final int KOREAN = 0, CHINESE = 1, ENGLISH = 2, JAPANESE = 3, RUSSIAN = 4;
    public static final int AUTO = -1;

    public static final class Line {
        public final int language;
        public final String text;
        public Line(int language, String text) {
            this.language = language;
            this.text = text;
        }
    }

    private RadioDjLanguagePolicy() {}

    public static List<Line> opening() {
        return Collections.singletonList(new Line(ENGLISH,
                "Hello and welcome to Maru Radio! I'm your host Ahwan. Enjoy the music."));
    }

    public static List<Line> closing() {
        return Collections.singletonList(new Line(ENGLISH,
                "This is Ahwan, signing off from Maru Radio. Thank you and goodbye!"));
    }

    /** group 1 KR=KO,EN / 2 CN=ZH,EN / 3 Western=EN / 4 instrumental=EN */
    public static List<Line> songIntro(String title, int group) {
        String name = title == null ? "" : title.trim().replaceAll("[\\p{Cntrl}\\p{Cf}]+"," ");
        if (name.length() > 52) name = name.substring(0,52);
        if (name.isEmpty()) name = "the next track";
        List<Line> lines = new ArrayList<>();
        switch (group) {
            case 1:
                lines.add(new Line(KOREAN, "아환입니다. 다음 곡은 " + name + "입니다."));
                lines.add(new Line(ENGLISH, "Up next: " + name + ". Enjoy."));
                break;
            case 2:
                lines.add(new Line(CHINESE, "我是阿欢。下一首歌曲是《" + name + "》。"));
                lines.add(new Line(ENGLISH, "Up next: " + name + ". Enjoy."));
                break;
            default:
                // Western, instrumentals and unknown: safe English-only default.
                lines.add(new Line(ENGLISH, "Ahwan here. Up next: " + name + ". Enjoy."));
                break;
        }
        return Collections.unmodifiableList(lines);
    }

    /** Detect a supplied viewer's message only. No access to BIGO user profile. */
    public static int giftLanguage(String comment, int manualHint) {
        if (manualHint >= KOREAN && manualHint <= RUSSIAN) return manualHint;
        if (comment == null || comment.trim().isEmpty()) return ENGLISH;
        boolean han = false;
        for (int offset=0;offset<comment.length();) {
            int cp = comment.codePointAt(offset);
            offset += Character.charCount(cp);
            if ((cp >= 0xAC00 && cp <= 0xD7A3) || (cp >= 0x1100 && cp <= 0x11FF))
                return KOREAN;
            if ((cp >= 0x3040 && cp <= 0x30FF) || (cp >= 0x31F0 && cp <= 0x31FF))
                return JAPANESE;
            if ((cp >= 0x0400 && cp <= 0x052F)) return RUSSIAN;
            if ((cp >= 0x3400 && cp <= 0x9FFF)) han = true;
        }
        if (han) return CHINESE;
        return ENGLISH; // unknown/default
    }

    public static boolean isGiftMessage(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("선물") || lower.contains("장미")
                || lower.contains("gift") || lower.contains("rose")
                || lower.contains("礼物") || lower.contains("玫瑰")
                || lower.contains("プレゼント") || lower.contains("ギフト");
    }

    public static List<Line> gift(String suppliedComment, int manualHint) {
        int language = giftLanguage(suppliedComment, manualHint);
        switch (language) {
            case KOREAN: return Collections.singletonList(new Line(KOREAN, "선물 감사합니다! 아환 라디오에 큰 힘이 됩니다."));
            case CHINESE: return Collections.singletonList(new Line(CHINESE, "谢谢您的礼物！阿欢非常感谢您。"));
            case JAPANESE: return Collections.singletonList(new Line(JAPANESE, "プレゼントありがとうございます！アファンです。"));
            case RUSSIAN: return Collections.singletonList(new Line(RUSSIAN, "Большое спасибо за подарок! С вами Ахван."));
            default: return Collections.singletonList(new Line(ENGLISH, "Thank you for your gift! Ahwan appreciates you."));
        }
    }
}
