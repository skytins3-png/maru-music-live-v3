package com.maru.musiclive;

import java.util.Locale;

/** Pure-Java text rules used by the BIGO broadcast-screen navigator. */
public final class BigoNavigationPolicy {
    public static final String MODE_REGULAR = "regular";
    public static final String MODE_AUDIO = "audio";

    private BigoNavigationPolicy() {}

    public static String normalizeMode(String liveMode) {
        String value = normalize(liveMode);
        return value.contains("오디오")
                || value.contains("audio")
                || value.contains("voice")
                || value.contains("음성")
                ? MODE_AUDIO
                : MODE_REGULAR;
    }

    public static boolean isEntryLabel(String label) {
        String value = normalize(label);
        return equalsAny(value,
                "live", "golive", "라이브", "방송", "방송하기",
                "开播", "直播", "配信", "ライブ", "эфир")
                || value.contains("createbroadcast")
                || value.contains("startbroadcastmenu")
                || value.contains("라이브만들기")
                || value.contains("방송메뉴");
    }

    public static boolean isModeLabel(String label, String mode) {
        String value = normalize(label);
        if (isFinalStartLabel(value)) return false;
        if (MODE_AUDIO.equals(mode)) {
            return equalsAny(value,
                    "오디오live", "오디오라이브", "음성live", "음성라이브",
                    "audiolive", "voicelive", "audio", "voice",
                    "语音直播", "音频直播", "音聲配信", "オーディオライブ",
                    "аудиоэфир");
        }
        return equalsAny(value,
                "일반live", "일반라이브", "라이브", "live", "videolive",
                "normal live", "일반방송", "视频直播", "通常配信",
                "通常ライブ", "обычныйэфир");
    }

    public static boolean isFinalStartLabel(String label) {
        String value = normalize(label);
        return equalsAny(value,
                "방송시작", "라이브시작", "방송하기", "startlive",
                "startbroadcast", "golive", "开始直播", "立即开播", "开播",
                "配信開始", "ライブ配信を開始", "начатьэфир");
    }

    public static boolean isSetupEvidenceLabel(String label) {
        String value = normalize(label);
        return value.contains("방송제목")
                || value.equals("제목")
                || value.contains("라이브제목")
                || value.contains("covertitle")
                || value.contains("livetitle")
                || value.contains("category")
                || value.contains("카테고리")
                || value.contains("태그")
                || value.contains("beauty")
                || value.contains("뷰티")
                || value.contains("필터")
                || value.contains("camera")
                || value.contains("카메라")
                || value.contains("封面")
                || value.contains("标题")
                || value.contains("カテゴリ")
                || value.contains("タイトル");
    }

    public static boolean isBroadcastMenuEvidence(String label) {
        String value = normalize(label);
        return isModeLabel(value, MODE_REGULAR)
                || isModeLabel(value, MODE_AUDIO)
                || value.contains("gamelive")
                || value.contains("게임live")
                || value.contains("멀티게스트")
                || value.contains("multiguest")
                || value.contains("game直播")
                || value.contains("ゲーム配信");
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·・:：_\\-]+", "")
                .trim();
    }

    private static boolean equalsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.equals(normalize(candidate))) return true;
        }
        return false;
    }
}
