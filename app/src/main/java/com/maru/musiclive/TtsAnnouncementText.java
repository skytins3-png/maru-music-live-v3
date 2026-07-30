package com.maru.musiclive;

public final class TtsAnnouncementText {
    private TtsAnnouncementText() {}

    public static String greeting(String nickname, String languageHint) {
        return event(EventType.JOIN, nickname, "", languageHint);
    }

    public static String event(
            EventType type,
            String nickname,
            String detail,
            String languageHint) {
        EventType eventType = type == null ? EventType.UNKNOWN : type;
        String name = clean(nickname, viewerWord(languageHint));
        String extra = clean(detail, "");
        String language = GreetingLanguage.normalize(languageHint);

        switch (language) {
            case GreetingLanguage.KOREAN:
                return korean(eventType, name, extra);
            case GreetingLanguage.CHINESE:
                return chinese(eventType, name, extra);
            case GreetingLanguage.JAPANESE:
                return japanese(eventType, name, extra);
            case GreetingLanguage.SPANISH:
                return spanish(eventType, name, extra);
            case GreetingLanguage.FRENCH:
                return french(eventType, name, extra);
            case GreetingLanguage.GERMAN:
                return german(eventType, name, extra);
            case GreetingLanguage.ITALIAN:
                return italian(eventType, name, extra);
            case GreetingLanguage.PORTUGUESE:
                return portuguese(eventType, name, extra);
            case GreetingLanguage.RUSSIAN:
                return russian(eventType, name, extra);
            default:
                return english(eventType, name, extra);
        }
    }

    public static String songTitle(String title, String languageHint) {
        String song = clean(title, "music");
        switch (GreetingLanguage.normalize(languageHint)) {
            case GreetingLanguage.KOREAN: return "다음 곡은 " + song + "입니다.";
            case GreetingLanguage.CHINESE: return "下一首歌曲是，" + song + "。";
            case GreetingLanguage.JAPANESE: return "次の曲は、" + song + "です。";
            case GreetingLanguage.SPANISH: return "La siguiente canción es, " + song + ".";
            case GreetingLanguage.FRENCH: return "La prochaine chanson est, " + song + ".";
            case GreetingLanguage.GERMAN: return "Der nächste Titel ist, " + song + ".";
            case GreetingLanguage.ITALIAN: return "La prossima canzone è, " + song + ".";
            case GreetingLanguage.PORTUGUESE: return "A próxima música é, " + song + ".";
            case GreetingLanguage.RUSSIAN: return "Следующая песня — " + song + ".";
            default: return "Next song, " + song + ".";
        }
    }

    private static String korean(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + "님, 어서 오세요. 좋은 음악과 함께 편안하게 머물다 가세요.";
            case LIKE: return name + "님, 좋아요 감사합니다.";
            case GIFT: return name + "님, 소중한 선물 정말 감사합니다." + detailSuffix(detail, " 선물 " + detail + "도 고맙습니다.");
            case FOLLOW: return name + "님, 팔로우해 주셔서 감사합니다.";
            case CHAT: return detail;
            default: return name + "님, 감사합니다.";
        }
    }

    private static String english(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", welcome to the live. Please relax and enjoy the music.";
            case LIKE: return name + ", thank you for the like.";
            case GIFT: return name + ", thank you so much for the gift." + detailSuffix(detail, " Thank you for " + detail + ".");
            case FOLLOW: return name + ", thank you for following.";
            case CHAT: return detail;
            default: return name + ", thank you.";
        }
    }

    private static String chinese(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + "，欢迎来到直播间。请放松心情，享受音乐。";
            case LIKE: return name + "，感谢点赞。";
            case GIFT: return name + "，礼物真的非常感谢。";
            case FOLLOW: return name + "，感谢关注。";
            case CHAT: return detail;
            default: return name + "，谢谢你。";
        }
    }

    private static String japanese(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + "さん、いらっしゃいませ。どうぞ音楽を楽しみながら、ゆっくりお過ごしください。";
            case LIKE: return name + "さん、いいねをありがとうございます。";
            case GIFT: return name + "さん、ギフトを本当にありがとうございます。";
            case FOLLOW: return name + "さん、フォローありがとうございます。";
            case CHAT: return detail;
            default: return name + "さん、ありがとうございます。";
        }
    }

    private static String spanish(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", bienvenido al directo. Relájate y disfruta de la música.";
            case LIKE: return name + ", muchas gracias por el me gusta.";
            case GIFT: return name + ", muchas gracias por el regalo.";
            case FOLLOW: return name + ", gracias por seguirme.";
            case CHAT: return detail;
            default: return name + ", muchas gracias.";
        }
    }

    private static String french(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", bienvenue sur le direct. Détendez-vous et profitez de la musique.";
            case LIKE: return name + ", merci beaucoup pour le j'aime.";
            case GIFT: return name + ", merci beaucoup pour le cadeau.";
            case FOLLOW: return name + ", merci de vous être abonné.";
            case CHAT: return detail;
            default: return name + ", merci beaucoup.";
        }
    }

    private static String german(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", willkommen im Livestream. Entspann dich und genieße die Musik.";
            case LIKE: return name + ", vielen Dank für das Like.";
            case GIFT: return name + ", vielen Dank für das Geschenk.";
            case FOLLOW: return name + ", vielen Dank fürs Folgen.";
            case CHAT: return detail;
            default: return name + ", vielen Dank.";
        }
    }

    private static String italian(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", benvenuto nella live. Rilassati e goditi la musica.";
            case LIKE: return name + ", grazie mille per il mi piace.";
            case GIFT: return name + ", grazie mille per il regalo.";
            case FOLLOW: return name + ", grazie per avermi seguito.";
            case CHAT: return detail;
            default: return name + ", grazie mille.";
        }
    }

    private static String portuguese(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", bem-vindo à live. Relaxe e aproveite a música.";
            case LIKE: return name + ", muito obrigado pela curtida.";
            case GIFT: return name + ", muito obrigado pelo presente.";
            case FOLLOW: return name + ", obrigado por seguir.";
            case CHAT: return detail;
            default: return name + ", muito obrigado.";
        }
    }

    private static String russian(EventType type, String name, String detail) {
        switch (type) {
            case JOIN: return name + ", добро пожаловать на эфир. Расслабьтесь и наслаждайтесь музыкой.";
            case LIKE: return name + ", большое спасибо за лайк.";
            case GIFT: return name + ", большое спасибо за подарок.";
            case FOLLOW: return name + ", спасибо за подписку.";
            case CHAT: return detail;
            default: return name + ", большое спасибо.";
        }
    }

    private static String viewerWord(String languageHint) {
        switch (GreetingLanguage.normalize(languageHint)) {
            case GreetingLanguage.KOREAN: return "청취자";
            case GreetingLanguage.CHINESE: return "朋友";
            case GreetingLanguage.JAPANESE: return "リスナー";
            case GreetingLanguage.SPANISH: return "amigo";
            case GreetingLanguage.FRENCH: return "ami";
            case GreetingLanguage.GERMAN: return "Freund";
            case GreetingLanguage.ITALIAN: return "amico";
            case GreetingLanguage.PORTUGUESE: return "amigo";
            case GreetingLanguage.RUSSIAN: return "друг";
            default: return "friend";
        }
    }

    private static String detailSuffix(String detail, String value) {
        return detail == null || detail.trim().isEmpty() ? "" : value;
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String clean = value.replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        if (clean.isEmpty()) return fallback;
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }
}
