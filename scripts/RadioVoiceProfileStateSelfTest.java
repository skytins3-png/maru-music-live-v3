package com.maru.radio;

public final class RadioVoiceProfileStateSelfTest {
    static void ok(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        System.out.println("PASS " + why);
    }
    public static void main(String[] args) {
        ok(RadioVoiceProfileState.valid("talk"), "owner speaking slot");
        ok(RadioVoiceProfileState.valid("sing"), "owner singing slot");
        ok(!RadioVoiceProfileState.valid("../outside"), "reject path traversal");
        ok("owner_speech.m4a".equals(RadioVoiceProfileState.filename("talk")),
                "fixed owner speaking filename");
        ok("owner_singing.m4a".equals(RadioVoiceProfileState.filename("sing")),
                "fixed owner singing filename");
        ok(RadioVoiceProfileState.clampPercent(-50)==0, "mix lower bound");
        ok(RadioVoiceProfileState.clampPercent(150)==100, "mix upper bound");
        ok(RadioVoiceProfileState.clampPercent(50)==50, "50/50 default mix");
        ok(RadioVoiceProfileState.blendDescription(50).contains("형 음색 50%"),
                "owner voice mix label");
        ok(RadioVoiceProfileState.blendDescription(50).contains("아직 음성 합성 적용 전"),
                "must not falsely claim clone training");
        ok(!RadioVoiceProfileState.readyForConsentBasedTraining(false,true,true),
                "no consent forbids training readiness");
        ok(!RadioVoiceProfileState.readyForConsentBasedTraining(true,true,false),
                "singing sample needed");
        ok(!RadioVoiceProfileState.readyForConsentBasedTraining(true,false,true),
                "speaking sample needed");
        ok(RadioVoiceProfileState.readyForConsentBasedTraining(true,true,true),
                "both clips and opt-in present (not training)");
        System.out.println("LOCAL REFERENCE ONLY. No clone model, audio synthesis, "
                + "voice transfer, cloud upload, or BIGO remote broadcast performed.");
    }
}
