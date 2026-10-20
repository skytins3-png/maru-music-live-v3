package com.maru.radio;

/**
 * Manually recorded listener checks, never a BIGO network/API status.
 * All flags require explicit user input from a separate receiving device.
 */
public final class BigoReceptionChecklist {
    public static final int CHECKS = 5;
    private BigoReceptionChecklist() {}
    public static final int LISTENER_ON_DIFFERENT_DEVICE = 0;
    public static final int BIGO_ROOM_STARTED_BY_USER = 1;
    public static final int ANNOUNCER_HEARD = 2;
    public static final int SONG_HEARD = 3;
    public static final int NEXT_SONG_HEARD = 4;

    public static boolean acceptable(boolean[] flags) {
        if (flags == null || flags.length != CHECKS) return false;
        for (boolean flag : flags) if (!flag) return false;
        return true;
    }
    public static int completed(boolean[] flags) {
        if (flags == null || flags.length != CHECKS) return 0;
        int count = 0;
        for (boolean flag : flags) if (flag) count++;
        return count;
    }
    public static String diagnostic(boolean[] flags) {
        if (flags == null || flags.length != CHECKS)
            return "아직 청취자 수신 테스트 기록이 없습니다.";
        if (!flags[LISTENER_ON_DIFFERENT_DEVICE])
            return "두 번째 기기에서 BIGO 청취자로 입장하여 확인해 주세요.";
        if (!flags[BIGO_ROOM_STARTED_BY_USER])
            return "BIGO 오디오라이브 실제 방송을 사용자가 시작했는지 확인해 주세요.";
        if (!flags[ANNOUNCER_HEARD] && !flags[SONG_HEARD])
            return "진행자와 노래가 모두 들리지 않음: BIGO 마이크 입력·믹서·오디오 라우팅을 확인해야 합니다. MARU 내부 재생만으로 송출되지 않습니다.";
        if (!flags[ANNOUNCER_HEARD])
            return "노래만 들림: 진행자 TTS 지원 언어·음성 엔진·BIGO 음성 믹서 경로를 점검하세요.";
        if (!flags[SONG_HEARD])
            return "진행자만 들림: BIGO의 음악 입력 경로·권한과 원곡 재생을 확인하세요.";
        if (!flags[NEXT_SONG_HEARD])
            return "첫 곡은 들리지만 전환 확인이 없습니다: MARU 다음곡 버튼, BIGO 오디오 포커스·연속 재생을 검사하세요.";
        return "사용자가 별도 청취 기기로 진행자·원곡·다음곡 수신을 모두 확인했다고 기록했습니다. 외부 서버 인증은 아닙니다.";
    }
}

