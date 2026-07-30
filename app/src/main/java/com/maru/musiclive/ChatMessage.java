package com.maru.musiclive;

public final class ChatMessage {
    public final String nickname;
    public final String message;
    public final String rawText;
    public final String languageHint;

    public ChatMessage(String nickname, String message, String rawText, String languageHint) {
        this.nickname = nickname == null ? "" : nickname.trim();
        this.message = message == null ? "" : message.trim();
        this.rawText = rawText == null ? "" : rawText.trim();
        this.languageHint = GreetingLanguage.normalize(languageHint);
    }

    public String fingerprint() {
        return "CHAT|" + nickname.toLowerCase() + "|" + message.toLowerCase();
    }
}
