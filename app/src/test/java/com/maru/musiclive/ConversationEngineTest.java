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
    @Test public void songRequestIsPolitelyRefusedForOriginalSongsOnly() {
        ConversationEngine.Reply reply = ConversationEngine.reply(
                "민지", "신청곡 틀어 주세요", "ko");
        assertEquals(ConversationIntent.SONG_REQUEST, reply.intent);
        assertTrue(reply.text.contains("자작곡"));
        assertTrue(reply.text.contains("신청곡은 받지 않습니다"));
        assertFalse(reply.text.contains("반영"));
    }

    @Test public void repeatedSongRequestGetsReminder() {
        ConversationEngine.Reply reply = ConversationEngine.songRequestRefusal(
                "민지", "ko", 2);
        assertTrue(reply.text.contains("앞서 안내드린 것처럼"));
        assertTrue(reply.text.contains("자작곡"));
    }

    @Test public void englishSongRequestIsRefused() {
        ConversationEngine.Reply reply = ConversationEngine.reply(
                "John", "please play my song request", "en");
        assertTrue(reply.text.toLowerCase().contains("original songs"));
        assertTrue(reply.text.toLowerCase().contains("not accepted"));
    }

}
