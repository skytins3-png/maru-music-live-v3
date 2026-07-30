package com.maru.musiclive;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatMessageParser {
    private static final Pattern COLON = Pattern.compile("^(.{1,40}?)[：:]\\s*(.{1,120})$");
    private final BigoEventParser eventParser = new BigoEventParser();

    public List<ChatMessage> parseAll(String raw) {
        List<ChatMessage> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return out;
        Set<String> seen = new HashSet<>();
        String normalized = TextNormalizer.normalize(raw);
        for (String rawLine : normalized.split("\\n")) {
            String line = rawLine.trim();
            if (line.length() < 3 || line.length() > 170) continue;
            if (eventParser.parseLine(line) != null) continue;
            Matcher matcher = COLON.matcher(line);
            if (!matcher.matches()) continue;
            String nickname = matcher.group(1).trim();
            String message = matcher.group(2).trim();
            if (!valid(nickname, message)) continue;
            ChatMessage chat = new ChatMessage(
                    nickname,
                    message,
                    line,
                    GreetingLanguage.detectFromText(message));
            if (seen.add(chat.fingerprint())) out.add(chat);
        }
        return out;
    }

    private static boolean valid(String nickname, String message) {
        if (nickname.isEmpty() || message.length() < 1) return false;
        String lower = nickname.toLowerCase();
        if (lower.contains("bigo") || lower.equals("system") || lower.equals("live")) return false;
        return !message.matches("^[0-9:./\\-]+$");
    }
}
