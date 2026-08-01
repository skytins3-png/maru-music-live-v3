package com.maru.musiclive;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BroadcastVoicePolicyTest {
    @Test public void usesExactFiveLanguageOrder() {
        assertArrayEquals(new String[] {
                GreetingLanguage.KOREAN,
                GreetingLanguage.ENGLISH,
                GreetingLanguage.CHINESE,
                GreetingLanguage.JAPANESE,
                GreetingLanguage.RUSSIAN
        }, BroadcastVoicePolicy.orderedLanguages());
        assertTrue(BroadcastVoicePolicy.isExpectedFiveLanguageOrder(
                BroadcastVoicePolicy.orderedLanguages()));
    }

    @Test public void onlyApprovedWindowsCanSpeak() {
        assertTrue(BroadcastVoicePolicy.isSpeechAllowed(
                BroadcastVoicePolicy.WINDOW_INTERMISSION));
        assertTrue(BroadcastVoicePolicy.isSpeechAllowed(
                BroadcastVoicePolicy.WINDOW_MANUAL_TEST));
        assertTrue(BroadcastVoicePolicy.isSpeechAllowed(
                BroadcastVoicePolicy.WINDOW_CLOSING));
        assertFalse(BroadcastVoicePolicy.isSpeechAllowed(
                BroadcastVoicePolicy.WINDOW_COMMENT));
        assertFalse(BroadcastVoicePolicy.isSpeechAllowed(
                BroadcastVoicePolicy.WINDOW_EVENT_DURING_SONG));
        assertFalse(BroadcastVoicePolicy.isSpeechAllowed("unknown"));
    }

    @Test public void doesNotForceVoiceGender() {
        assertFalse(BroadcastVoicePolicy.FORCE_GENDER);
    }
}
