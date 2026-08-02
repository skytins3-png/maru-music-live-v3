package com.maru.musiclive;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class AutoReplyPolicyTest {
    @Test public void joinNotificationsNeverAutoReply() {
        assertFalse(AutoReplyPolicy.shouldAutoReply(
                new ChatMessage("민지", "님이 입장했습니다", "민지 님이 입장했습니다", GreetingLanguage.KOREAN)));
        assertFalse(AutoReplyPolicy.containsJoinNotification("Blue Moon joined the live"));
    }

    @Test public void ordinaryCommentsStillAutoReply() {
        assertTrue(AutoReplyPolicy.shouldAutoReply(
                new ChatMessage("민지", "노래가 좋아요", "민지: 노래가 좋아요", GreetingLanguage.KOREAN)));
    }

    @Test public void visualReplyNeverExceedsTwoSeconds() {
        assertEquals(2_000L, AutoReplyPolicy.MAX_VISUAL_REPLY_MS);
        assertEquals(2_000L, EventType.CHAT.overlayDurationMs);
    }
}
