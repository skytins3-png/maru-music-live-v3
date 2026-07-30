package com.maru.musiclive;

import java.util.Arrays;
import java.util.List;

public final class QuickReplies {
    public static final class Entry {
        private final String language;
        private final String text;

        public Entry(String language, String text) {
            this.language = language;
            this.text = text;
        }

        public String language() {
            return language;
        }

        public String text() {
            return text;
        }
    }

    private QuickReplies() {}

    public static List<Entry> all() {
        return Arrays.asList(
                new Entry("한국어", "어서 오세요. 편안하게 음악 듣고 가세요."),
                new Entry("English", "Welcome. Please relax and enjoy the music."),
                new Entry("中文", "欢迎光临，请轻松欣赏音乐。"),
                new Entry("日本語", "いらっしゃいませ。ゆっくり音楽を楽しんでください。"),
                new Entry("Русский", "Добро пожаловать. Расслабьтесь и наслаждайтесь музыкой.")
        );
    }
}
