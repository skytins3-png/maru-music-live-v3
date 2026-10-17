package com.maru.radio;

/**
 * Evidence gate for ordinary BIGO Audio LIVE without RTMP keys.
 * Never confuses passing offline tests, local speaker playback, or a visible
 * BIGO mixer button with verified remote listener audio.
 * All evidence is from explicit observation on a second listener device.
 */
public final class BigoNoKeyEvidence {
    private BigoNoKeyEvidence() {}

    public static final class Snapshot {
        public final boolean bigoMusicSelected;
        public final boolean heardAtNormal;
        public final boolean heardAtMuted;
        public final boolean bigoCommentArrived;
        public final boolean ahwanHeardRemotely;

        public Snapshot(boolean selected, boolean normal, boolean muted,
                boolean comment, boolean ahwan) {
            bigoMusicSelected = selected;
            heardAtNormal = normal;
            heardAtMuted = muted;
            bigoCommentArrived = comment;
            ahwanHeardRemotely = ahwan;
        }
    }

    public static boolean musicVerified(Snapshot s) {
        return s != null && s.bigoMusicSelected
                && s.heardAtNormal && s.heardAtMuted;
    }

    public static boolean commentVerified(Snapshot s) {
        return s != null && s.bigoCommentArrived && s.ahwanHeardRemotely;
    }

    public static boolean complete(Snapshot s) {
        return musicVerified(s) && commentVerified(s);
    }

    public static String musicStatus(Snapshot s) {
        if (s == null) return "미점검";
        if (!s.bigoMusicSelected)
            return "미확인: BIGO 자체 믹서에서 로컬 원곡 선택 경로 확인 필요";
        if (!s.heardAtNormal)
            return "차단: 다른 청취자 휴대폰에서 음악 수신 미확인";
        if (!s.heardAtMuted)
            return "미달성: 방송 휴대폰 미디어 음량 0에서 음악 수신 미확인";
        return "청취자 확인: BIGO 원곡을 음량 0에서도 들음 (이 기기의 관찰 결과)";
    }

    public static String commentStatus(Snapshot s) {
        if (s == null || !s.bigoCommentArrived)
            return "미확인: BIGO 진행자 LIVE 화면에서 실제 새 댓글 인식 필요";
        if (!s.ahwanHeardRemotely)
            return "미달성: 새 댓글은 왔지만 다른 휴대폰에서 아환 음성 미확인";
        return "청취자 확인: 실댓글 응답 음성 수신됨 (이 기기의 관찰 결과)";
    }
}
