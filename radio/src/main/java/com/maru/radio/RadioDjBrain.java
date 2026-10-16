package com.maru.radio;

import java.util.List;
import java.util.Locale;

/**
 * On-device DJ conversation and request matching. No network, no copied comments
 * stored, no claim of a language model or voice cloning.
 */
public final class RadioDjBrain {
    /** The announcer's name selected by the owner. */
    public static final String PRESENTER_NAME = "아환";
    public static final class Reply {
        public final String speech;
        public final int songIndex;  // -1 if not a song request
        public Reply(String speech, int songIndex) {
            this.speech = speech;
            this.songIndex = songIndex;
        }
    }
    private RadioDjBrain() {}

    private static String clean(String text, int limit) {
        if (text == null) return "";
        String one = text.replaceAll("[\\p{Cntrl}\\p{Cf}]+", " ")
                .replaceAll("\\s+", " ").trim();
        return one.substring(0, Math.min(limit, one.length()));
    }

    private static String key(String t) {
        return clean(t, 150).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·・]+", "");
    }

    /** Exact titles are preferred, partial requests require a distinct candidate. */
    public static int findSong(String request, List<String> titles) {
        String query = key(request);
        if (query.length() < 2 || titles == null) return -1;
        int exact = -1;
        for (int i = 0; i < titles.size(); i++) {
            if (key(titles.get(i)).equals(query)) {
                if (exact >= 0) return -1;
                exact = i;
            }
        }
        if (exact >= 0) return exact;
        int match = -1;
        for (int i = 0; i < titles.size(); i++) {
            String title = key(titles.get(i));
            if (title.length() >= 2 && title.contains(query)) {
                if (match >= 0) return -1;
                match = i;
            }
        }
        return match;
    }

    public static String nextSong(String title, int count) {
        String safe = clean(title, 55);
        if (safe.isEmpty()) safe = "다음 곡";
        String[] prefixes = {
                "잠시 후 들려드릴 곡은 ",
                "아환 아나운서가 준비한 원곡, ",
                "계속해서 함께할 노래는 "
        };
        return prefixes[Math.floorMod(count, prefixes.length)]
                + safe + "입니다. 편안하게 들어주세요.";
    }

    /** Bilingual/multilingual next-track headlines in the installed TTS voice. */
    public static String nextSong(String title, int count, int language) {
        if (language == 0) return nextSong(title, count);
        String t = clean(title, 55);
        if (t.isEmpty()) t = "MARU";
        switch (language) {
            case 1: return "我是主持人 Ahwan。接下来请欣赏：" + t + "。";
            case 2: return "Ahwan here, on Maru Radio. Up next: " + t + ". Enjoy!";
            case 3: return "アナウンサーの Ahwan です。続いての曲は、" + t + "。";
            case 4: return "С вами ведущий Ahwan. Далее песня: " + t + ".";
            default: return nextSong(t, count);
        }
    }

    public static String opening(int count) {
        String[] scripts = {
            "안녕하세요! 마루 라디오 진행자 아환 아나운서입니다. "
                    + "좋은 음악과 따뜻한 이야기, 함께해요.",
            "어서 오세요! 마루 라디오의 아환입니다. "
                    + "오늘도 음악으로 이어지는 소중한 시간, 시작합니다.",
            "마루 라디오에 오신 여러분 반갑습니다. "
                    + "아환 아나운서와 함께 마음 편히 쉬어가세요."
        };
        return scripts[Math.floorMod(count, scripts.length)];
    }

    public static String opening(int count, int language) {
        if (language == 0) return opening(count);
        switch (language) {
            case 1: return "大家好，我是马루电台主持人 Ahwan。欢迎大家来听音乐。";
            case 2: return "Hello, this is announcer Ahwan on Maru Radio. Welcome!";
            case 3: return "こんにちは、マルラジオのアナウンサー Ahwan です。ようこそ。";
            case 4: return "Здравствуйте! С вами ведущий Ahwan на Мару Радио. Добро пожаловать!";
            default: return opening(count);
        }
    }

    public static String closing(int language) {
        switch (language) {
            case 1: return "我是 Ahwan。感谢您收听马루电台，下次再见。";
            case 2: return "This is Ahwan. Thanks for listening to Maru Radio. See you next time!";
            case 3: return "アナウンサーの Ahwan でした。マルラジオを聴いてくださり、ありがとうございました。";
            case 4: return "С вами был Ahwan. Спасибо, что слушали Мару Радио!";
            default: return closing();
        }
    }

    public static String closing() {
        return "지금까지 마루 라디오 아환 아나운서였습니다. "
                + "함께해 주셔서 고맙습니다. 편안한 시간 되세요.";
    }

    /**
     * No automatic BIGO comment access. Message comes only from user's input.
     * Language templates are safe; complex questions ask for operator review.
     */
    public static Reply respond(String raw, List<String> titles, int styleCount) {
        String query = clean(raw, 180);
        String norm = key(query);
        if (query.isEmpty()) return new Reply("환영합니다. 듣고 싶은 곡을 이야기해주세요.", -1);
        int pos = -1;
        if (norm.contains("틀어줘") || norm.contains("들려줘") || norm.contains("신청")
                || norm.contains("재생") || norm.contains("play") || norm.contains("노래")) {
            String title = query.replaceFirst(
                    "(?i)^(신청곡|노래|음악|request|play)\\s*[:：]?\\s*", "")
                    .replaceFirst("(?i)\\s*(틀어줘|들려줘|재생해줘|재생|신청해요|부탁해요|해주세요|please)$", "")
                    .trim();
            pos = findSong(title, titles);
            if (pos < 0) {
                // Some requests specify only the title plus ending words.
                pos = findSong(title.replaceAll("(좀|한번|노래)$", "").trim(), titles);
            }
            if (pos >= 0)
                return new Reply("신청 감사합니다! " + clean(titles.get(pos), 55)
                        + " 바로 이어서 들려드릴게요.", pos);
        }
        if (norm.contains("장미") || norm.contains("선물") || norm.contains("gift"))
            return new Reply("따뜻한 선물 정말 고맙습니다. 장미 한 송이에도 마음이 전해져요!", -1);
        if (norm.contains("팔로우") || norm.contains("follow"))
            return new Reply("팔로우 고맙습니다. 마루 라디오에 자주 놀러 오세요.", -1);
        if (norm.contains("안녕") || norm.contains("반가") || norm.contains("hello") || norm.contains("你好"))
            return new Reply("어서 오세요! 우리 함께 음악 들으면서 즐겁게 이야기해요.", -1);
        if (norm.contains("좋은노래") || norm.contains("좋아요") || norm.contains("좋다")
                || norm.contains("멋져") || norm.contains("좋아"))
            return new Reply("함께 즐겨 주셔서 고마워요. 여러분 덕분에 라디오가 더 따뜻해집니다.", -1);
        if (norm.contains("슬퍼") || norm.contains("힘들") || norm.contains("피곤"))
            return new Reply("오늘 힘든 하루였군요. 여기서 음악 들으며 잠깐 쉬어가세요.", -1);
        if (norm.contains("몇시") || norm.contains("날씨") || norm.contains("뉴스"))
            return new Reply("실시간 정보는 확인하지 못해요. 오늘은 좋은 음악으로 이야기 나눠요.", -1);
        String[] general = {
            "이야기 나눠주셔서 고맙습니다. 어떤 분위기의 곡을 듣고 싶으세요?",
            "댓글 고마워요. 지금 마음에 떠오르는 노래가 있을까요?",
            "오늘도 와 주셔서 반가워요. 좋아하는 음악을 알려 주세요."
        };
        return new Reply(general[Math.floorMod(styleCount, general.length)], -1);
    }
}
