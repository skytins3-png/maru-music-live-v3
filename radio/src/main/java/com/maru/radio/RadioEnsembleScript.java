package com.maru.radio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A privacy-preserving, predictable OFFLINE radio ensemble script.
 *
 * One lead host (Ahwan) and EIGHT other fictional radio personalities.
 * The actual phone speaks with installed Android system TTS, pitch/rate
 * adjustments only; this is not eight individually trained voice models,
 * a LLM, user-voice clone, karaoke singer, or BIGO listener transmission.
 * Between full ORIGINAL audio tracks (never over the middle of a song),
 * 2-4 short alternating lines are played in order.
 */
public final class RadioEnsembleScript {
    private RadioEnsembleScript() {}

    public static final class Role {
        public final String name;
        public final String desk;
        public final float pitch;
        public final float rate;
        private Role(String name, String desk, float pitch, float rate) {
            this.name = name;
            this.desk = desk;
            this.pitch = pitch;
            this.rate = rate;
        }
    }

    public static final Role[] CAST = {
        new Role("아환", "대표 진행자 · 중후한 남성 DJ", 0.90f, 0.92f),
        new Role("루나", "감성 발라드 · 따뜻한 여성", 1.14f, 0.93f),
        new Role("준호", "트로트 · 유쾌한 남성", 0.94f, 1.08f),
        new Role("메이", "중국 음악 · 차분한 여성", 1.07f, 0.89f),
        new Role("알렉스", "팝·R&B · 편안한 남성", 0.97f, 1.02f),
        new Role("소라", "사연·위로 · 잔잔한 여성", 1.18f, 0.86f),
        new Role("민재", "음악 이야기 · 지적인 남성", 0.84f, 0.99f),
        new Role("지나", "청취자 소통 · 밝은 여성", 1.23f, 1.05f),
        new Role("레온", "연주곡·악기 · 깊은 남성", 0.87f, 0.88f)
    };

    public static final class Line {
        public final int roleIndex;
        public final String text;
        public Line(int who, String say) {
            if (who < 0 || who >= CAST.length)
                throw new IllegalArgumentException("Unknown speaker index");
            roleIndex = who;
            text = say;
        }
        public Role role() { return CAST[roleIndex]; }
    }

    public static final int DEFAULT_LINES = 4;
    public static int clampLines(int lines) { return Math.max(2, Math.min(4, lines)); }
    public static int clampEvery(int every) { return Math.max(1, Math.min(4, every)); }

    /** Human-readable local title is never treated as model instructions. */
    private static String title(String input) {
        if (input == null || input.trim().isEmpty()) return "다음 음악";
        String clean = input.replaceAll("[\\p{Cntrl}\\n\\r\\t]+", " ")
                .replaceAll("[<>\\[\\]{}]", "").trim();
        return clean.isEmpty() ? "다음 음악"
                : clean.substring(0, Math.min(clean.length(), 55));
    }

    /**
     * Zero-based ordinal counts songs since the service started.
     * Every two song windows cover all eight guest voices at least once.
     * The lead alternates with two guests in each full 4-line scene.
     * No segment can delay actual song playback indefinitely: caller
     * separately applies guarded audio completion/timeouts.
     */
    public static List<Line> scene(int ordinal, String upcomingTitle, int requestedLines) {
        int step = Math.max(0, ordinal);
        int first = 1 + (step * 2) % 8;
        int second = 1 + ((step * 2 + 1) % 8);
        String track = title(upcomingTitle);
        int variation = step % 4;
        String[] openings = {
            "자, 음악 사이에 잠깐 이야기 나눠 볼까요? 오늘 같은 날은 어떤 노래가 떠오르세요?",
            "여기는 마루 라디오입니다. 방금 들은 음악의 여운, 다들 느끼셨나요?",
            "이번에는 우리 진행자들에게 음악이 주는 힘을 물어볼게요.",
            "잠깐 쉬어 가는 라디오 시간입니다. 오늘 기분은 어떠세요?"
        };
        String[] answers = {
            "전 따뜻한 멜로디를 들으면 오래된 추억부터 떠올라요.",
            "좋은 음악은 끝나고도 마음속에서 조금 더 흐르는 것 같아요.",
            "말로 다 표현하지 못하는 감정을 노래가 대신해 주죠.",
            "조용한 한 소절이 때로는 긴 이야기보다 큰 위로가 돼요."
        };
        String[] replies = {
            "맞아요. 저도 그래서 다음 노래를 더 기대하게 되네요.",
            "공감합니다. 그럼 이번엔 새로운 분위기를 만나 볼까요?",
            "좋은 말이네요. 청취자 여러분도 편하게 함께해 주세요.",
            "이런 이야기가 있어서 음악이 더 특별해지는 것 같아요."
        };
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(0, openings[variation]));
        lines.add(new Line(first, answers[(variation + first) % answers.length]));
        lines.add(new Line(second, replies[(variation + second) % replies.length]));
        lines.add(new Line(0, "이야기 고맙습니다. 이어서 " + track
                + ", 원곡 그대로 들려드리겠습니다."));
        int keep = clampLines(requestedLines);
        if (keep == 2) {
            // Even the shortest segment MUST introduce the upcoming track.
            return Collections.unmodifiableList(new ArrayList<>(
                    java.util.Arrays.asList(lines.get(1), lines.get(3))));
        }
        if (keep == 3) {
            return Collections.unmodifiableList(new ArrayList<>(
                    java.util.Arrays.asList(lines.get(0), lines.get(1), lines.get(3))));
        }
        return Collections.unmodifiableList(lines);
    }

    public static boolean shouldRun(int ordinal, int every) {
        return ordinal >= 0 && ordinal % clampEvery(every) == 0;
    }
}
