package com.maru.radio;

import java.util.Locale;

/**
 * Privacy-bounded, deterministic BIGO host-room comment-to-voice rules.
 * Only text in a recognized chat widget is considered; nothing is persisted.
 * Does not look up identities, make decisions about sensitive content,
 * write to BIGO chat, or claim an official BIGO chat API.
 */
public final class BigoChatRule {
    private BigoChatRule() {}

    public static final class Match {
        public final int language;
        public final String reply;
        // Volatile dedupe only; never persisted or logged as a raw chat string.
        public final int transientFingerprint;
        Match(int lang, String speech, String original) {
            language = lang;
            reply = speech;
            transientFingerprint = original.hashCode();
        }
    }

    public static boolean isChatWidget(String resource, String description) {
        String id = resource == null ? "" : resource.toLowerCase(Locale.ROOT);
        String desc = description == null ? "" : description.toLowerCase(Locale.ROOT);
        return id.contains("chat") || id.contains("comment")
                || id.contains("danmu") || id.contains("barrage")
                || desc.startsWith("chat:") || desc.startsWith("comment:")
                || desc.startsWith("댓글:") || desc.startsWith("聊天:")
                || desc.startsWith("채팅:");
    }

    /**
     * Some BIGO builds split a public LIVE comment into separately labeled
     * name and message child views. Only merge two direct children of a
     * recognized CHAT container: never inspect general or private UI.
     * Raw text is neither persisted nor logged.
     */
    public static Match matchSplitChat(String resource, String description,
            String name, String message) {
        if (!isChatWidget(resource, description) || name == null || message == null)
            return null;
        String nick = name.trim();
        String body = message.trim();
        if (nick.length() == 0 || nick.length() > 30 || body.length() < 2
                || body.length() > 100 || nick.contains(":") || nick.contains("："))
            return null;
        return match(resource, description, nick + ": " + body);
    }

    /**
     * Only user-name-prefixed text in labeled BIGO chat widgets; no broad
     * accessibility tree scraping, unknown UI, or private message content.
     */
    public static Match match(String resource, String description, String rowText) {
        if (!isChatWidget(resource, description)) return null;
        String row = rowText == null ? "" : rowText.trim();
        if (row.isEmpty() && description != null) {
            row = description.replaceFirst("^(?i:chat|comment|댓글|聊天|채팅)\\s*[:：]\\s*", "");
        }
        if (row.length() < 4 || row.length() > 145
                || row.contains("http://") || row.contains("https://")
                || row.contains("www.") || row.contains("@")) return null;
        int colon = row.indexOf(':');
        if (colon < 0) colon = row.indexOf('：');
        if (colon <= 0 || colon > 30 || colon >= row.length() - 2) return null;
        String message = row.substring(colon + 1).trim();
        if (message.length() < 2 || message.length() > 100) return null;
        if (message.contains("://") || message.matches(".*\\d{9,}.*")) return null;
        String low = message.toLowerCase(Locale.ROOT);
        int lang = RadioDjLanguagePolicy.giftLanguage(message, RadioDjLanguagePolicy.AUTO);
        String answer = null;
        if (RadioDjLanguagePolicy.isGiftMessage(message)) {
            RadioDjLanguagePolicy.Line line = RadioDjLanguagePolicy.gift(message,
                    RadioDjLanguagePolicy.AUTO).get(0);
            lang = line.language; answer = line.text;
        } else if (lang == RadioDjLanguagePolicy.KOREAN) {
            if (low.contains("안녕") || low.contains("반가")) answer = "반갑습니다! 아환 라디오에 오신 걸 환영해요.";
            else if (low.contains("고마") || low.contains("감사")) answer = "함께해 주셔서 감사합니다. 좋은 음악 들려드릴게요.";
            else if (low.contains("누구") || low.contains("이름") || low.contains("아환이야"))
                answer = "저는 마루 라디오 진행자 아환입니다. 반가워요!";
            else if (low.contains("좋은 노래") || low.contains("좋아요") || low.contains("음악 좋")
                    || low.contains("잘 들려") || low.contains("잘들려"))
                answer = "함께 음악 들어주셔서 고마워요. 편안한 시간 되세요.";
            else if (low.contains("무슨 노래") || low.contains("신청곡") || low.contains("노래 추천"))
                answer = "듣고 싶은 곡 제목을 댓글로 남겨 주세요. 함께 좋은 음악 즐겨요.";
            else if (low.contains("피곤") || low.contains("힘들"))
                answer = "오늘도 수고 많았어요. 잠시 편하게 음악을 즐겨 주세요.";
        } else if (lang == RadioDjLanguagePolicy.CHINESE) {
            if (low.contains("你好") || low.contains("欢迎")) answer = "你好！欢迎来到阿欢音乐电台。";
            else if (low.contains("谢谢") || low.contains("感谢")) answer = "谢谢你！祝你听歌愉快。";
            else if (low.contains("叫什么") || low.contains("你是谁"))
                answer = "我是马루电台主持人阿欢，很高兴认识你！";
            else if (low.contains("歌曲") || low.contains("听歌") || low.contains("音乐很好"))
                answer = "感谢你来听音乐！希望今晚的歌曲能陪伴你。";
        } else if (lang == RadioDjLanguagePolicy.JAPANESE) {
            if (low.contains("こんにちは") || low.contains("こんばんは")) answer = "こんにちは！アファンラジオへようこそ。";
            else if (low.contains("ありがとう")) answer = "ありがとうございます！音楽を楽しんでください。";
            else if (low.contains("お名前") || low.contains("誰ですか"))
                answer = "マルラジオのアナウンサー、アファンです。よろしくお願いします。";
            else if (low.contains("音楽") || low.contains("曲が好き"))
                answer = "聴いてくれてありがとうございます。音楽を楽しんでください。";
        } else if (lang == RadioDjLanguagePolicy.RUSSIAN) {
            if (low.contains("привет") || low.contains("здравств")) answer = "Привет! Добро пожаловать на радио Ахван.";
            else if (low.contains("спасибо")) answer = "Спасибо вам! Приятного прослушивания.";
            else if (low.contains("как тебя зовут") || low.contains("кто ты"))
                answer = "Я Ахван, ведущий радио Мару. Рад знакомству!";
            else if (low.contains("музыка") || low.contains("хорошая песня"))
                answer = "Спасибо, что слушаете! Приятного музыкального вечера.";
        } else {
            if (low.matches(".*\\b(hello|hi|hey|welcome)\\b.*")) answer = "Hello and welcome to Maru Radio! This is Ahwan.";
            else if (low.matches(".*\\b(thanks|thank you)\\b.*")) answer = "Thank you for listening! Enjoy the music.";
            else if (low.matches(".*\\b(who are you|your name|what is your name)\\b.*"))
                answer = "I'm Ahwan, your announcer on Maru Radio. Welcome!";
            else if (low.matches(".*\\b(love the music|great song|nice music|good song)\\b.*"))
                answer = "Thanks for listening! Enjoy the next songs on Maru Radio.";
            else if (low.matches(".*\\b(song request|recommend a song)\\b.*"))
                answer = "Thank you! Feel free to share the title of a song you'd enjoy.";
        }
        return answer == null ? null : new Match(lang, answer, row);
    }
}
