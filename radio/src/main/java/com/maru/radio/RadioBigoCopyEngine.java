package com.maru.radio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/** Platform-neutral SHA-256 checked lossless copy. Virtual-testable on JVM. */
public final class RadioBigoCopyEngine {
    private static final long MAX_BYTES = 300L * 1024L * 1024L;
    private RadioBigoCopyEngine() {}

    public static long copyAndVerify(InputStream in, OutputStream out, String expectedSha,
            AtomicBoolean stopped) throws Exception {
        if (in == null || out == null) throw new IOException("Audio stream missing");
        if (expectedSha == null || !expectedSha.matches("[0-9a-fA-F]{64}"))
            throw new IOException("Audio source hash invalid");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] data = new byte[64 * 1024];
        long written = 0;
        int n;
        while ((n = in.read(data)) != -1) {
            if (stopped != null && stopped.get())
                throw new InterruptedIOException("Cancelled copy");
            if (n == 0) continue;
            written += n;
            if (written > MAX_BYTES) throw new IOException("Original exceeds 300MB");
            digest.update(data, 0, n);
            out.write(data, 0, n);
        }
        out.flush();
        if (written == 0) throw new IOException("Empty original");
        if (!hex(digest.digest()).equalsIgnoreCase(expectedSha))
            throw new IOException("Original SHA-256 mismatch; incomplete or changed file");
        return written;
    }

    public static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i*2] = digits[(bytes[i] >>> 4) & 15];
            out[i*2+1] = digits[bytes[i] & 15];
        }
        return new String(out);
    }
}
