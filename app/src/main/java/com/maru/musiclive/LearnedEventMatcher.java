package com.maru.musiclive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LearnedEventMatcher {
    private LearnedEventMatcher() {}

    public static List<LiveEvent> match(String raw, List<LearnedRule> rules) {
        List<LiveEvent> out = new ArrayList<>();
        if (raw == null || rules == null || rules.isEmpty()) return out;
        String normalized = TextNormalizer.normalize(raw);
        for (String line : normalized.split("\\n")) {
            String lower = line.toLowerCase(Locale.ROOT);
            for (LearnedRule rule : rules) {
                if (rule.phrase.length() < 2) continue;
                String phrase = rule.phrase.toLowerCase(Locale.ROOT);
                if (!lower.contains(phrase)) continue;
                String nickname = BigoEventParser.guessNicknameByTrigger(line, rule.phrase);
                out.add(new LiveEvent(
                        rule.type,
                        nickname,
                        "",
                        line,
                        rule.language,
                        System.currentTimeMillis()));
                break;
            }
        }
        return out;
    }
}
