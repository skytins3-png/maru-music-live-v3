package com.maru.musiclive;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaruOneUpdateCheckerTest {
    private static final String HASH="ab".repeat(32);

    @Test public void acceptsOnlyExactOnePackageAndExpectedArtifactPath() {
        assertTrue(MaruOneUpdateChecker.trustedUrl("3.2.25",
                "https://github.com/skytins3-png/maru-music-live-v3/releases/download/v3.2.25-one/MARU-ONE-v3.2.25.apk"));
        assertFalse(MaruOneUpdateChecker.trustedUrl("3.2.25",
                "https://github.com/skytins3-png/maru-music-live-v3/releases/download/v3.2.25-one/MARU-RADIO.apk"));
        assertFalse(MaruOneUpdateChecker.trustedUrl("3.2.25",
                "https://evil.example/maru.apk"));
        assertFalse(MaruOneUpdateChecker.trustedUrl("3.2.25",
                "http://github.com/skytins3-png/maru-music-live-v3/releases/download/v3.2.25-one/MARU-ONE-v3.2.25.apk"));
        assertFalse(MaruOneUpdateChecker.trustedUrl("3.2.25",
                "https://github.com/skytins3-png/maru-music-live-v3/releases/download/v3.2.24-one/MARU-ONE-v3.2.24.apk"));
    }
    @Test public void releaseMetadataRequires64DigitChecksumAndSigningFingerprint() {
        String notes="PACKAGE=com.maru.musiclive.one\nVERSION_CODE=3044\n"
                +"APK_SHA256="+HASH+"\nSIGNER_SHA256="+HASH+"\n";
        assertEquals("com.maru.musiclive.one",MaruOneUpdateChecker.metadataValue(notes,"PACKAGE"));
        assertEquals("3044",MaruOneUpdateChecker.metadataValue(notes,"VERSION_CODE"));
        assertEquals(HASH,MaruOneUpdateChecker.metadataValue(notes,"APK_SHA256"));
        assertTrue(MaruOneUpdateChecker.isHex256(HASH));
        assertFalse(MaruOneUpdateChecker.isHex256("not-a-hash"));
        assertFalse(MaruOneUpdateChecker.isHex256(""));
        assertFalse(MaruOneUpdateChecker.isHex256(null));
    }
    @Test public void comparesSemanticVersionsNotLexicographic() {
        assertTrue(MaruOneUpdateChecker.newerVersion("3.2.25","3.2.24"));
        assertTrue(MaruOneUpdateChecker.newerVersion("3.10.0","3.9.99"));
        assertFalse(MaruOneUpdateChecker.newerVersion("3.2.24","3.2.24"));
        assertFalse(MaruOneUpdateChecker.newerVersion("3.2.23","3.2.24"));
    }
}
