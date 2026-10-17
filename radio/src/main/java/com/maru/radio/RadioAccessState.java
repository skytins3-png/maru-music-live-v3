package com.maru.radio;

/** Public Android automation diagnostics. No permission bypass possible. */
public final class RadioAccessState {
    private RadioAccessState() {}
    public static final String READY = "READY";
    public static final String NO_CONSENT = "NO_CONSENT";
    public static final String NOT_CONNECTED = "NOT_CONNECTED";

    public static String status(boolean consent, boolean active) {
        if (!consent) return NO_CONSENT;
        if (!active) return NOT_CONNECTED;
        return READY;
    }

    public static String description(boolean consent, boolean active) {
        switch (status(consent, active)) {
            case READY:
                return "접근성 연결과 앱 내부 동의가 확인됐습니다. BIGO 실방송 자동 실행은 별도 확인해야 합니다.";
            case NO_CONSENT:
                return "MARU 내부에서 BIGO 자동 조작 동의가 꺼져 있습니다. 동의 없이 화면을 읽거나 누르지 않습니다.";
            default:
                return "MARU의 BIGO 접근성 서비스가 연결되지 않았습니다. 삼성의 제한된 설정일 수 있지만 이 상태만으로 차단 원인을 단정할 수 없습니다.";
        }
    }
}
