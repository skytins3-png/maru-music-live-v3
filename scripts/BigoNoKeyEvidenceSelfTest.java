package com.maru.radio;

/**
 * Completely synthetic truth-table: no BIGO access, hardware, RTMP key,
 * receiver PCM, viewer accounts, microphone or automation privileges.
 */
public final class BigoNoKeyEvidenceSelfTest {
    private static void require(boolean yes, String description) {
        if (!yes) throw new AssertionError("FAIL: " + description);
        System.out.println("PASS: " + description);
    }
    public static void main(String[] args) {
        int accepted = 0;
        for (int mask = 0; mask < 32; mask++) {
            BigoNoKeyEvidence.Snapshot x = new BigoNoKeyEvidence.Snapshot(
                    (mask & 1) != 0, (mask & 2) != 0,
                    (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0);
            boolean expectedMusic = (mask & 7) == 7;
            boolean expectedChat = (mask & 24) == 24;
            boolean expectedFull = mask == 31;
            require(BigoNoKeyEvidence.musicVerified(x) == expectedMusic,
                    "music route checked only with internal file + two receiver observations mask " + mask);
            require(BigoNoKeyEvidence.commentVerified(x) == expectedChat,
                    "comment checked only with seen chat + remote Ahwan voice mask " + mask);
            require(BigoNoKeyEvidence.complete(x) == expectedFull,
                    "no false BIGO full success for mask " + mask);
            if (BigoNoKeyEvidence.complete(x)) accepted++;
        }
        require(accepted == 1, "only explicit all-five listener evidence can pass");
        BigoNoKeyEvidence.Snapshot mutedFailed =
                new BigoNoKeyEvidence.Snapshot(true, true, false, true, true);
        require(BigoNoKeyEvidence.musicStatus(mutedFailed).contains("음량 0"),
                "mute failure must be plainly identified even when comment path passes");
        BigoNoKeyEvidence.Snapshot replyFailed =
                new BigoNoKeyEvidence.Snapshot(true, true, true, true, false);
        require(BigoNoKeyEvidence.commentStatus(replyFailed).contains("음성 미확인"),
                "no false success from chat detection without listener hearing Ahwan");
        require(!BigoNoKeyEvidence.complete(null), "unknown result never passes");
        System.out.println("SIMULATION ONLY: no-key BIGO acceptance gate passed 32 truth-table states; actual listener NOT tested.");
    }
}
