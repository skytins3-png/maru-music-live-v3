package com.maru.musiclive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AutoReplyPolicyTest {
    @Test public void joinNotificationsNeverAutoReply() {
        assertFalse("Korean join must not auto-reply",
                AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                        "민지",
                        "님이 입장했습니다",
                        "민지 님이 입장했습니다",
                        GreetingLanguage.KOREAN)));
        assertTrue("English join must be recognized as a join notification",
                AutoReplyPolicy.containsJoinNotification("Blue Moon joined the live"));
    }

    @Test public void multilingualJoinNotificationsAreExcluded() {
        assertTrue(AutoReplyPolicy.containsJoinNotification("민지 님이 입장했습니다"));
        assertTrue(AutoReplyPolicy.containsJoinNotification("Blue Moon joined the live"));
        assertTrue(AutoReplyPolicy.containsJoinNotification("Blue Moon\njoined the live"));
        assertTrue(AutoReplyPolicy.containsJoinNotification("小雨进入了你的直播间"));
        assertTrue(AutoReplyPolicy.containsJoinNotification("さくらさんが入室しました"));
        assertTrue(AutoReplyPolicy.containsJoinNotification("Анна присоединилась к эфиру"));
    }

    @Test public void ordinaryCommentsStillAutoReply() {
        assertTrue(AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                "민지",
                "노래가 좋아요",
                "민지: 노래가 좋아요",
                GreetingLanguage.KOREAN)));
        assertTrue(AutoReplyPolicy.shouldAutoReply(new ChatMessage(
                "Blue Moon",
                "I joined yesterday and this song is great",
                "Blue Moon: I joined yesterday and this song is great",
                GreetingLanguage.ENGLISH)));
        assertFalse(AutoReplyPolicy.containsJoinNotification(
                "Blue Moon: I joined yesterday and this song is great"));
    }

    @Test public void visualReplyNeverExceedsTwoSeconds() {
        assertEquals(2_000L, AutoReplyPolicy.MAX_VISUAL_REPLY_MS);
        assertEquals(2_000L, EventType.CHAT.overlayDurationMs);
    }
}
