package com.maru.musiclive;

/** Preset no-keyboard closing message for screen-shared broadcasts. */
public final class BroadcastClosingText {
    private BroadcastClosingText() {}

    public static String build(String languageHint) {
        switch (GreetingLanguage.normalize(languageHint)) {
            case GreetingLanguage.KOREAN:
                return "오늘 음악 방송은 여기까지입니다. 함께해 주셔서 감사합니다. 다음 방송에서 다시 만나요.";
            case GreetingLanguage.CHINESE:
                return "今天的音乐直播到这里结束。感谢大家的陪伴，我们下次直播再见。";
            case GreetingLanguage.JAPANESE:
                return "今日の音楽配信はここまでです。ご視聴ありがとうございました。また次の配信でお会いしましょう。";
            case GreetingLanguage.RUSSIAN:
                return "На этом сегодняшняя музыкальная трансляция заканчивается. Спасибо, что были с нами. До встречи в следующем эфире.";
            default:
                return "That is all for today's music live. Thank you for joining us. See you again in the next broadcast.";
        }
    }
}
