package com.maru.musiclive;

import java.util.List;

/**
 * Visual AI reply policy.
 * Entry notifications are events, not chat, and never generate an AI reply.
 * AI reply cards stay on screen for at most two seconds.
 */
public final class AutoReplyPolicy {
    public static final long MAX_VISUAL_REPLY_MS = 2_000L;

    private AutoReplyPolicy() {}

    public static boolean shouldAutoReply(ChatMessage chat) {
        if (chat == null) return false;
        String raw = chat.rawText == null ? "" : chat.rawText.trim();
        String message = chat.message == null ? "" : chat.message.trim();
        if (raw.isEmpty() && message.isEmpty()) return false;
        return !containsJoinNotification(raw) && !containsJoinNotification(message);
    }

    public static boolean containsJoinNotification(String text) {
        if (text == null || text.trim().isEmpty()) return false;

        BigoJoinParser joinParser = new BigoJoinParser();
        if (!joinParser.parseAll(text).isEmpty()) return true;

        BigoEventParser eventParser = new BigoEventParser();
        List<LiveEvent> events = eventParser.parseAll(text);
        for (LiveEvent event : events) {
            if (event != null && event.type == EventType.JOIN) return true;
        }
        return false;
    }
}
