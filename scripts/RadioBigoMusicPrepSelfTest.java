package com.maru.radio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 152 virtual Suno originals + fault simulations. No REAL BIGO receiver,
 * user music data, stream keys or phone microphone are part of this test.
 */
public final class RadioBigoMusicPrepSelfTest {
    private static void require(boolean good, String desc) {
        if (!good) throw new AssertionError(desc);
    }
    private static String hash(byte[] b) throws Exception {
        return RadioBigoCopyEngine.hex(MessageDigest.getInstance("SHA-256").digest(b));
    }
    private static void fails(byte[] original, String expectedHash, AtomicBoolean cancelled,
            String name) throws Exception {
        boolean rejected = false;
        try {
            RadioBigoCopyEngine.copyAndVerify(new ByteArrayInputStream(original),
                    new ByteArrayOutputStream(), expectedHash, cancelled);
        } catch (Exception expected) { rejected = true; }
        require(rejected, name);
    }
    public static void main(String[] args) throws Exception {
        Set<String> exportedNames = new HashSet<>();
        Set<String> store = new HashSet<>();
        int copies = 0, duplicates = 0;
        String[] extensions = {".mp3", ".wav", ".m4a", ".flac", ".ogg", ".aac"};
        for (int index = 0; index < 152; index++) {
            byte[] original = new byte[8192 + (index % 17) * 307];
            java.util.Random random = new java.util.Random(1000L + index);
            random.nextBytes(original);
            String digest = hash(original);
            String name = RadioBigoExportNaming.fileName(digest,
                    "가을곡_" + index + extensions[index % extensions.length]);
            require(exportedNames.add(name), "152 recordings must have distinct exported names");
            require(name.startsWith("MARU-" + digest.substring(0,16)), "digest-prefix guard");
            require(name.endsWith(extensions[index % extensions.length]), "audio format preserved");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            long count = RadioBigoCopyEngine.copyAndVerify(
                    new ByteArrayInputStream(original), bytes, digest, new AtomicBoolean(false));
            require(count == original.length && Arrays.equals(original, bytes.toByteArray()),
                    "lossless original transfer");
            require(store.add(name), "first MusicStore virtual insert");
            copies++;
        }
        for (String name: exportedNames) {
            if (!store.add(name)) duplicates++;
        }
        require(copies == 152 && duplicates == 152,
                "152 originals exactly once, virtual re-run skips all 152 duplicates");
        byte[] test = "Suno source".getBytes(StandardCharsets.UTF_8);
        byte[] bad = "Corrupted source".getBytes(StandardCharsets.UTF_8);
        fails(bad, hash(test), new AtomicBoolean(false), "corrupted original rejected");
        fails(new byte[0], hash(new byte[0]), new AtomicBoolean(false), "empty audio rejected");
        fails(test, "not-a-sha256", new AtomicBoolean(false), "malformed stored hash rejected");
        fails(test, hash(test), new AtomicBoolean(true), "user cancellation interrupts transfer");
        String escaped = RadioBigoExportNaming.fileName(hash(test), "../私密/오디오:<bad>?.mp3");
        require(!escaped.contains("/") && !escaped.contains("..") && !escaped.contains(":")
                && !escaped.contains("<") && !escaped.contains("?"), "filename is safe from path traversal");
        require(RadioBigoExportNaming.mimeType("one.wav").equals("audio/wav"), "WAV is WAV");
        require(RadioBigoExportNaming.mimeType("one.m4a").equals("audio/mp4"), "M4A is audio/mp4");
        require(RadioBigoExportNaming.mimeType("one.mp3").equals("audio/mpeg"), "MP3 is audio/mpeg");
        System.out.println("VIRTUAL ONLY: 152 byte-identical Suno files, 152 dedup replays, "
                + "corruption/empty/cancel/path-safety/mime checks PASS. "
                + "This DOES NOT test BIGO Music import, live audio receiver, "
                + "speaker mute=0, or Ahwan voice delivered to BIGO listeners.");
    }
}
