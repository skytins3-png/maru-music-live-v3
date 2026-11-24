package com.maru.musiclive;

/** User-attested listener reception, never confused with a BIGO API receipt. */
public final class BigoListenerEvidence {
    public static final int USER_STARTED_BIGOLIVE=0;
    public static final int LISTENER_CONFIRMED_VOICE=1;
    public static final int LISTENER_CONFIRMED_SONG=2;
    public static final int LISTENER_CONFIRMED_NEXT_SONG=3;
    public static final int COUNT=4;

    private BigoListenerEvidence() {}

    public static boolean complete(boolean[] checks) {
        if (checks==null || checks.length!=COUNT)return false;
        for (boolean yes : checks) if (!yes)return false;
        return true;
    }

    public static String diagnosis(boolean[] checks) {
        if (checks==null || checks.length!=COUNT)
            return "청취 결과를 확인하지 못했습니다.";
        if (!checks[USER_STARTED_BIGOLIVE])
            return "BIGO 오디오 LIVE에서 방송 시작을 직접 선택하고 다시 검사하세요.";
        if (!checks[LISTENER_CONFIRMED_VOICE] && !checks[LISTENER_CONFIRMED_SONG])
            return "청취자에게 진행자와 음악이 모두 안 들립니다. BIGO의 마이크·믹서와 음악 입력을 확인해야 합니다.";
        if (!checks[LISTENER_CONFIRMED_VOICE])
            return "음악만 들립니다. BIGO 마이크가 켜져 있는지 확인하세요.";
        if (!checks[LISTENER_CONFIRMED_SONG])
            return "진행자만 들립니다. MARU 내부 재생은 BIGO 송출이 아닙니다. BIGO 안의 음악·믹서 기능을 확인하세요.";
        if (!checks[LISTENER_CONFIRMED_NEXT_SONG])
            return "첫 곡은 들렸지만 다음 곡 전환이 확인되지 않았습니다.";
        return "사용자가 청취자로부터 진행자·음악·다음곡 수신을 확인했다고 기록했습니다. BIGO 서버 인증은 아닙니다.";
    }
}
