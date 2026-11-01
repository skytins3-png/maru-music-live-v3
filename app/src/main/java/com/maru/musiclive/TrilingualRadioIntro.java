package com.maru.musiclive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Short, original DJ introductions in English -> Korean -> Mandarin.
 * Approved listener stories are read once in the ORIGINAL language only:
 * never pretend a literal translation was generated offline.
 */
public final class TrilingualRadioIntro {
    private TrilingualRadioIntro() {}

    public static final String[] ORDER = {
            GreetingLanguage.ENGLISH,
            GreetingLanguage.KOREAN,
            GreetingLanguage.CHINESE
    };

    public static final class Segment {
        public final String language;
        public final String text;
        public final boolean originalStory;
        public Segment(String language, String text, boolean originalStory) {
            this.language = language;
            this.text = text;
            this.originalStory = originalStory;
        }
    }

    /** Whether the local three-language announcer can pronounce the approved story. */
    public static boolean canReadStory(String story) {
        return !clean(story, 350).isEmpty() && !storyLanguage(story).isEmpty();
    }

    /** Content-script detection is only for reading the original, not translation. */
    public static String storyLanguage(String text) {
        String body = clean(text, 350);
        if (body.isEmpty()) return "";
        boolean latin = false;
        for (int offset = 0; offset < body.length();) {
            int codepoint = body.codePointAt(offset);
            offset += Character.charCount(codepoint);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(codepoint);
            if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO
                    || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                return GreetingLanguage.KOREAN;
            }
            if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.CYRILLIC) return "";
            if (Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN) {
                return GreetingLanguage.CHINESE;
            }
            if (Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.LATIN) latin = true;
        }
        return latin ? GreetingLanguage.ENGLISH : "";
    }

    public static Segment[] beforeSong(String title, int episode,
            String approvedStoryName, String approvedStory, boolean requested) {
        String safeTitle = clean(title, 60);
        if (safeTitle.isEmpty()) safeTitle = "음악";
        boolean story = canReadStory(approvedStory);
        String originalLanguage = story ? storyLanguage(approvedStory) : "";
        List<Segment> out = new ArrayList<>();
        int variant = Math.floorMod(episode, 3);
        for (String language : ORDER) {
            String line;
            boolean useTitle = language.equals(storyLanguage(safeTitle));
            switch (language) {
                case GreetingLanguage.ENGLISH:
                    line = variant == 0 ? "Welcome back to Ahwan Radio. "
                            : variant == 1 ? "Glad you're here with us on Ahwan Radio. "
                            : "You're listening to Ahwan, thanks for being here. ";
                    line += story ? "We've received a listener's story. Thank you for sharing it with us. "
                            : "";
                    line += requested ? "A listener's song request is coming up. "
                            : "Let's enjoy another song together. ";
                    if (useTitle) line += "It's called " + safeTitle + ". ";
                    line += "Stay with us.";
                    break;
                case GreetingLanguage.KOREAN:
                    line = variant == 0 ? "아환 라디오에 다시 오신 여러분, 반갑습니다. "
                            : variant == 1 ? "잠시 음악 사이에 인사드릴게요. 아환입니다. "
                            : "함께하고 계신 여러분, 오늘도 고맙습니다. ";
                    if (story) line += "먼저 청취자 한 분이 보내주신 사연을 함께 나눠볼게요. ";
                    line += requested ? "신청해 주신 노래를 이어서 들려드릴게요. "
                            : "다음 곡도 마음 편히 들어주세요. ";
                    if (useTitle) line += "제목은 " + safeTitle + "입니다. ";
                    line += "자, 함께 들어볼까요?";
                    break;
                default:
                    line = variant == 0 ? "欢迎回到阿焕音乐电台。"
                            : variant == 1 ? "感谢大家继续陪伴阿焕。"
                            : "这里是阿焕的音乐时间。";
                    if (story) line += "有位听众给我们分享了一段故事，谢谢您的信任。";
                    line += requested ? "接下来播放一首听众点播的歌曲。"
                            : "接下来为大家带来一首好听的歌。";
                    if (useTitle) line += "歌名是《" + safeTitle + "》。";
                    line += "请慢慢欣赏。";
                    break;
            }
            out.add(new Segment(language, line, false));
            if (story && language.equals(originalLanguage)) {
                String name = clean(approvedStoryName, 24);
                String opener;
                if (GreetingLanguage.KOREAN.equals(language)) {
                    opener = name.isEmpty() ? "보내주신 사연입니다. "
                            : name + "님이 보내주신 사연입니다. ";
                } else if (GreetingLanguage.CHINESE.equals(language)) {
                    opener = name.isEmpty() ? "下面读一下听众的来信。"
                            : "下面是" + name + "分享的故事。";
                } else {
                    opener = name.isEmpty() ? "Here's the original listener message. "
                            : "Here's the original message from " + name + ". ";
                }
                out.add(new Segment(language, opener, false));
                for (String chunk : splitForTts(clean(approvedStory, 350), 95)) {
                    out.add(new Segment(language, chunk, true));
                }
            }
        }
        return out.toArray(new Segment[0]);
    }

    private static List<String> splitForTts(String value, int max) {
        List<String> parts = new ArrayList<>();
        String remaining = value;
        while (!remaining.isEmpty()) {
            if (remaining.length() <= max) {
                parts.add(remaining);
                break;
            }
            int cut = max;
            for (int i = max; i > max / 2; i--) {
                char c = remaining.charAt(i - 1);
                if (Character.isWhitespace(c) || c == '.' || c == '。' || c == '!' || c == '?'
                        || c == '，' || c == ',') {
                    cut = i;
                    break;
                }
            }
            parts.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        return parts;
    }

    private static String clean(String input, int max) {
        if (input == null) return "";
        String s = input.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        if (s.length() > max) s = s.substring(0, max).trim();
        return s;
    }
}
