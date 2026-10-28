package com.maru.musiclive;

import static org.junit.Assert.*;
import org.junit.Test;

public class ReleaseVersionLogicTest {
    @Test public void comparesNumericVersionsNotStrings() {
        assertTrue(ReleaseVersionLogic.newerThan("3.2.17", "3.2.16"));
        assertTrue(ReleaseVersionLogic.newerThan("3.10.1", "3.9.99"));
        assertFalse(ReleaseVersionLogic.newerThan("3.2.16", "3.2.16"));
        assertFalse(ReleaseVersionLogic.newerThan("3.2.15", "3.2.16"));
        assertFalse(ReleaseVersionLogic.newerThan("v3.2.18", "3.2.16"));
        assertFalse(ReleaseVersionLogic.newerThan("bad", "3.2.16"));
        assertFalse(ReleaseVersionLogic.newerThan("999999999999.2.17", "3.2.16"));
    }

    @Test public void onlyAllowsExpectedGithubApkLocation() {
        assertTrue(ReleaseVersionLogic.trustedApk(
                "3.2.18",
                "https://github.com/skytins3-png/maru-music-live-v3/releases/download/v3.2.18/MARU-RADIO.apk"));
        assertFalse(ReleaseVersionLogic.trustedApk("3.2.18", "https://evil.example/MARU-RADIO.apk"));
        assertFalse(ReleaseVersionLogic.trustedApk(
                "3.2.18",
                "https://github.com/skytins3-png/maru-music-live-v3/releases/download/v3.2.17/MARU-RADIO.apk"));
        assertFalse(ReleaseVersionLogic.trustedApk("oops", "https://github.com"));
    }
}
