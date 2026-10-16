package com.maru.radio;

import java.util.Set;

/** Safe, no-account simulator for the BIGO Audio LIVE screenshot's text anchors. */
public final class BigoAutomationPolicySelfTest {
    static void ok(boolean value, String label) {
        if (!value) throw new AssertionError("FAIL: " + label);
        System.out.println("PASS: " + label);
    }

    public static void main(String[] args) {
        Set<String> screenshot = Set.of(
                "잠시 휴식을 위해", "채팅", "친목", "게임", "관심사",
                "믹서", "방송하기", "오디오 라이브", "자리", "테마");
        Set<String> mixer = Set.of(
                "믹서", "마이크 볼륨", "1", "볼륨 재설정",
                "미리 보기", "지능형 오디오 효과", "음악 효과",
                "Original", "리버브", "Live Concert", "KTV", "이퀄라이저");
        Set<String> feed = Set.of("추천", "인기", "발견", "팔로우", "라이브");
        Set<String> host = Set.of("시청자 3", "자리", "댓글", "선물", "방송 시간");
        Set<String> confirm = Set.of("방송을 종료하시겠습니까?", "라이브 종료하기");
        ok(BigoAutomationPolicy.isSetup(screenshot), "actual composer screenshot recognized");
        ok(BigoAutomationPolicy.isMixer(mixer), "actual mic/mixer screenshot recognized");
        ok(BigoAutomationPolicy.isMicOneShown(mixer), "mic value already 1 skips unnecessary adjustment");
        ok(!BigoAutomationPolicy.isMicOneShown(screenshot), "composer must not pretend mixer is at level 1");
        ok(!BigoAutomationPolicy.isReadyToStart(mixer), "cannot press start under mixer overlay");
        ok(BigoAutomationPolicy.isReadyToStart(screenshot), "start permitted only on composer with mixer closed");
        ok(!BigoAutomationPolicy.isReadyToStart(host), "cannot start from a live host room");
        ok(!BigoAutomationPolicy.isHostLive(screenshot), "composer is not live room");
        ok(!BigoAutomationPolicy.isHostLive(mixer), "mixer is not live room");
        ok(!BigoAutomationPolicy.isSetup(feed), "feed is not composer");
        ok(BigoAutomationPolicy.isHostLive(host), "verified host requires anchors");
        ok(BigoAutomationPolicy.isEndConfirmation(confirm), "end confirmation recognized");
        ok(!BigoAutomationPolicy.isEndConfirmation(screenshot), "composer never triggers end");
        ok(BigoAutomationPolicy.startNext(BigoAutomationPolicy.Phase.OPEN_COMPOSER, screenshot)
                        == BigoAutomationPolicy.Phase.SELECT_AUDIO,
                "start composer navigation");
        ok(BigoAutomationPolicy.startNext(BigoAutomationPolicy.Phase.OPEN_MIXER, mixer)
                        == BigoAutomationPolicy.Phase.SET_MIC_ONE,
                "mixer progress candidate phase");
        ok(BigoAutomationPolicy.stopNext(BigoAutomationPolicy.Phase.OPEN_END_DIALOG, confirm)
                        == BigoAutomationPolicy.Phase.CONFIRM_END,
                "end confirmation is deliberate");
        ok(BigoAutomationPolicy.stopNext(BigoAutomationPolicy.Phase.OPEN_END_DIALOG, screenshot)
                        == BigoAutomationPolicy.Phase.OPEN_END_DIALOG,
                "never end from composer");
        System.out.println("BIGO screenshot-state unit checks PASS; NOT a live-device test.");
    }
}
