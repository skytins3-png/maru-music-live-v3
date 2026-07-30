package com.maru.musiclive;

public final class LearnedRule {
    public final String phrase;
    public final EventType type;
    public final String language;

    public LearnedRule(String phrase, EventType type, String language) {
        this.phrase = phrase == null ? "" : phrase.trim();
        this.type = type == null ? EventType.UNKNOWN : type;
        this.language = GreetingLanguage.normalize(language);
    }
}
