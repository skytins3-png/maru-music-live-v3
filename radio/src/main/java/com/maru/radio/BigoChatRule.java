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

    private static boolean knownChatWidget(String resource, String description) {
        String id = resource == null ? "" : resource.toLowerCase(Locale.ROOT);
        String desc = description == null ? "" : description.toLowerCase(Locale.ROOT);
        return id.contains("chat") || id.contains("comment")
                || id.contains("danmu") || id.contains("barrage")
                || desc.startsWith("chat:") || desc.startsWith("comment:")
                || desc.startsWith("댓글:") || desc.startsWith("聊天:")
                || desc.startsWith("채팅:");
    }

    /**
     * Only user-name-prefixed text in labeled BIGO chat widgets; no broad
     * accessibility tree scraping, unknown UI, or private message content.
     */
    public static Match match(String resource, String description, String rowText) {
        if (!knownChatWidget(resource, description)) return null;
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
        } else if (lang == RadioDjLanguagePolicy.CHINESE) {
            if (low.contains("你好") || low.contains("欢迎")) answer = "你好！欢迎来到阿欢音乐电台。";
            else if (low.contains("谢谢") || low.contains("感谢")) answer = "谢谢你！祝你听歌愉快。";
        } else if (lang == RadioDjLanguagePolicy.JAPANESE) {
            if (low.contains("こんにちは") || low.contains("こんばんは")) answer = "こんにちは！アファンラジオへようこそ。";
            else if (low.contains("ありがとう")) answer = "ありがとうございます！音楽を楽しんでください。";
        } else if (lang == RadioDjLanguagePolicy.RUSSIAN) {
            if (low.contains("привет") || low.contains("здравств")) answer = "Привет! Добро пожаловать на радио Ахван.";
            else if (low.contains("спасибо")) answer = "Спасибо вам! Приятного прослушивания.";
        } else {
            if (low.matches(".*\\b(hello|hi|hey|welcome)\\b.*")) answer = "Hello and welcome to Maru Radio! This is Ahwan.";
            else if (low.matches(".*\\b(thanks|thank you)\\b.*")) answer = "Thank you for listening! Enjoy the music.";
        }
        return answer == null ? null : new Match(lang, answer, row);
    }
}
