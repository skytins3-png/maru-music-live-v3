package com.maru.musiclive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure update-policy functions; never silently installs an APK. */
public final class ReleaseVersionLogic {
    private static final Pattern VERSION =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final String BASE =
            "https://github.com/skytins3-png/maru-music-live-v3/releases/download/v";

    private static final Pattern RELEASE_SIGNER =
            Pattern.compile("(?m)^SIGNER_SHA256=([0-9a-fA-F]{64})$");

    public static boolean sameReleaseSigner(String releaseNotes, String installedSigner) {
        if (releaseNotes == null || installedSigner == null) return false;
        Matcher match = RELEASE_SIGNER.matcher(releaseNotes);
        return match.find() && installedSigner.equalsIgnoreCase(match.group(1));
    }

    private ReleaseVersionLogic() {}

    public static boolean newerThan(String remote, String installed) {
        int[] a = parse(remote);
        int[] b = parse(installed);
        if (a == null || b == null) return false;
        for (int i = 0; i < 3; i++) {
            if (a[i] > b[i]) return true;
            if (a[i] < b[i]) return false;
        }
        return false;
    }

    public static boolean trustedApk(String version, String downloadUrl) {
        return parse(version) != null
                && (BASE + version + "/MARU-RADIO.apk").equals(downloadUrl);
    }

    private static int[] parse(String value) {
        if (value == null) return null;
        Matcher matcher = VERSION.matcher(value);
        if (!matcher.matches()) return null;
        try {
            return new int[] {
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
