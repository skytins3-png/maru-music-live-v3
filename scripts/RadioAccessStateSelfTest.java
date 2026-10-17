package com.maru.radio;

public final class RadioAccessStateSelfTest {
    static void require(boolean value, String what) {
        if (!value) throw new AssertionError("FAIL " + what);
        System.out.println("PASS " + what);
    }
    public static void main(String[] args) {
        require(RadioAccessState.NO_CONSENT.equals(RadioAccessState.status(false,false)),
                "no user consent means no automation");
        require(RadioAccessState.NO_CONSENT.equals(RadioAccessState.status(false,true)),
                "service present does not override owner's opt-out");
        require(RadioAccessState.NOT_CONNECTED.equals(RadioAccessState.status(true,false)),
                "owner opt-in without Android accessibility connection not ready");
        require(RadioAccessState.READY.equals(RadioAccessState.status(true,true)),
                "both actual service and app opt-in required");
        require(!RadioAccessState.description(true,false).contains("허용 완료"),
                "do not misstate Android restricted setting as enabled");
        System.out.println("OFFLINE ONLY: access gate passed; Android's restricted settings "
                + "were NOT bypassed or simulated with a real Samsung BIGO app.");
    }
}
