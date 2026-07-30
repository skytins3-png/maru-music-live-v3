package com.maru.musiclive;

import java.util.Locale;
import java.util.Objects;

public final class JoinEvent {
    public final String nickname;
    public final String rawText;
    public final String languageHint;

    public JoinEvent(String nickname, String rawText, String languageHint) {
        this.nickname = Objects.requireNonNull(nickname, "nickname").trim();
        this.rawText = rawText == null ? "" : rawText.trim();
        this.languageHint = languageHint == null ? "ko" : languageHint;
    }

    public String fingerprint() {
        return nickname.toLowerCase(Locale.ROOT) + "|" + rawText.toLowerCase(Locale.ROOT);
    }
}
