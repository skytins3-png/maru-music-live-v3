package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationEngineTest {
    @Test public void repliesInEnglish() {
        ConversationEngine.Reply reply = ConversationEngine.reply("John", "hello", "en");
        assertTrue(reply.shouldSpeak());
        assertTrue(reply.text.toLowerCase().contains("welcome"));
    }

    @Test public void unknownIsNotSpoken() {
        ConversationEngine.Reply reply = ConversationEngine.reply("John", "zxqv 123", "en");
        assertFalse(reply.shouldSpeak());
    }
}
