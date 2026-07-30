package com.maru.musiclive;

import java.util.ArrayList;
import java.util.List;

/**
 * 곡과 곡 사이에만 재생하는 통합 안내 문구.
 * 노래 재생 중에는 이 문구를 호출하지 않는다.
 */
public final class IntermissionAnnouncementText {
    private static final String[] ORDERED_LANGUAGES = {
            GreetingLanguage.KOREAN,
            GreetingLanguage.ENGLISH,
            GreetingLanguage.CHINESE,
            GreetingLanguage.JAPANESE,
            GreetingLanguage.RUSSIAN
    };

    private IntermissionAnnouncementText() {}

    public static String[] orderedLanguages() {
        return ORDERED_LANGUAGES.clone();
    }

    public static String build(
            String nextTitle,
            String languageHint,
            List<String> giftNames,
            List<String> followNames) {
        String title = clean(nextTitle, "music");
        List<String> gifts = cleanNames(giftNames);
        List<String> follows = cleanNames(followNames);
        String language = GreetingLanguage.normalize(languageHint);

        switch (language) {
            case GreetingLanguage.KOREAN:
                return korean(title, gifts, follows);
            case GreetingLanguage.CHINESE:
                return chinese(title, gifts, follows);
            case GreetingLanguage.JAPANESE:
                return japanese(title, gifts, follows);
            case GreetingLanguage.RUSSIAN:
                return russian(title, gifts, follows);
            default:
                return english(title, gifts, follows);
        }
    }

    private static String korean(String title, List<String> gifts, List<String> follows) {
        StringBuilder out = new StringBuilder();
        out.append("방송에 오신 분들 모두 환영합니다. 좋아요 한 번 눌러 주세요.");
        if (!gifts.isEmpty()) {
            out.append(' ').append(koreanNames(gifts)).append(" 선물 감사합니다.");
        }
        if (!follows.isEmpty()) {
            out.append(' ').append(koreanNames(follows)).append(" 팔로우 감사합니다.");
        }
        out.append(" 다음 노래는 ").append(title).append("입니다. 함께 듣겠습니다.");
        return out.toString();
    }

    private static String english(String title, List<String> gifts, List<String> follows) {
        StringBuilder out = new StringBuilder();
        out.append("Welcome, everyone. Please tap the like button once.");
        if (!gifts.isEmpty()) {
            out.append(' ').append(englishNames(gifts)).append(", thank you for the gifts.");
        }
        if (!follows.isEmpty()) {
            out.append(' ').append(englishNames(follows)).append(", thank you for following.");
        }
        out.append(" The next song is ").append(title).append(". Let's listen together.");
        return out.toString();
    }

    private static String chinese(String title, List<String> gifts, List<String> follows) {
        StringBuilder out = new StringBuilder();
        out.append("欢迎来到直播间。请点一下赞。");
        if (!gifts.isEmpty()) {
            out.append("感谢").append(chineseNames(gifts)).append("送来的礼物。");
        }
        if (!follows.isEmpty()) {
            out.append("感谢").append(chineseNames(follows)).append("的关注。");
        }
        out.append("下一首歌是《").append(title).append("》。让我们一起欣赏。");
        return out.toString();
    }

    private static String japanese(String title, List<String> gifts, List<String> follows) {
        StringBuilder out = new StringBuilder();
        out.append("配信に来てくださった皆さん、ようこそ。いいねを一回お願いします。");
        if (!gifts.isEmpty()) {
            out.append(japaneseNames(gifts)).append("、ギフトありがとうございます。");
        }
        if (!follows.isEmpty()) {
            out.append(japaneseNames(follows)).append("、フォローありがとうございます。");
        }
        out.append("次の曲は「").append(title).append("」です。一緒に聴きましょう。");
        return out.toString();
    }

    private static String russian(String title, List<String> gifts, List<String> follows) {
        StringBuilder out = new StringBuilder();
        out.append("Добро пожаловать на трансляцию. Пожалуйста, поставьте лайк.");
        if (!gifts.isEmpty()) {
            out.append(' ').append(russianNames(gifts)).append(", спасибо за подарки.");
        }
        if (!follows.isEmpty()) {
            out.append(' ').append(russianNames(follows)).append(", спасибо за подписку.");
        }
        out.append(" Следующая песня — «").append(title)
                .append("». Давайте послушаем вместе.");
        return out.toString();
    }

    private static List<String> cleanNames(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input == null) return out;
        for (String value : input) {
            String name = clean(value, "");
            if (name.isEmpty() || out.contains(name)) continue;
            out.add(name);
            if (out.size() >= 4) break;
        }
        return out;
    }

    private static String koreanNames(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) out.add(name + "님");
        return String.join(", ", out);
    }

    private static String englishNames(List<String> names) {
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return String.join(", ", names.subList(0, names.size() - 1))
                + ", and " + names.get(names.size() - 1);
    }

    private static String chineseNames(List<String> names) {
        return String.join("、", names);
    }

    private static String japaneseNames(List<String> names) {
        List<String> out = new ArrayList<>();
        for (String name : names) out.add(name + "さん");
        return String.join("、", out);
    }

    private static String russianNames(List<String> names) {
        return String.join(", ", names);
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String clean = value.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (clean.isEmpty()) return fallback;
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }
}
