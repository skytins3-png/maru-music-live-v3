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
        // Coordinate-only checks from the owner's 709 x 1536 BIGO mixer image.
        // Digit 1 on unrelated widgets or the viewer-count must NOT grant mic=1.
        ok(BigoMicLayoutPolicy.isNearMic(709, 1536, 42, 437, 290, 477,
                        645, 508, 662, 546),
                "owner mixer screenshot: 1 belongs to the microphone row");
        ok(!BigoMicLayoutPolicy.isNearMic(709, 1536, 42, 437, 290, 477,
                        642, 902, 663, 943),
                "a digit below music effects does not verify microphone");
        ok(!BigoMicLayoutPolicy.isNearMic(709, 1536, 42, 437, 290, 477,
                        170, 508, 189, 546),
                "unrelated left-side numeral is not a microphone value");
        ok(!BigoMicLayoutPolicy.isNearMic(709, 1536, 42, 437, 290, 477,
                        647, 39, 662, 57),
                "status bar digit does not verify microphone");
        ok(!BigoMicLayoutPolicy.isNearMic(1536, 709, 42, 437, 290, 477,
                        645, 508, 662, 546),
                "landscape UI fails safely; coordinates not assumed");
        ok(!BigoMicLayoutPolicy.isNearMic(709, 1536, 42, 437, 290, 477,
                        700, 508, 729, 546),
                "out-of-screen numeral fails safely");
        Set<String> realOnAir = Set.of("마이크", "1", "2", "3", "4", "5", "6",
                "7", "8", "9", "10", "11", "12");
        Set<String> preLive = Set.of("마이크", "1", "2", "3", "오디오 라이브",
                "방송하기");
        Set<String> mixerWithSeats = Set.of("믹서", "마이크 볼륨", "마이크",
                "Original", "1", "2", "3", "4", "5", "6");
        ok(BigoAutomationPolicy.isHostRoomAfterPress(realOnAir),
                "owner's real on-air mic + guest seats confirms visible host UI after broadcast press");
        ok(!BigoAutomationPolicy.isHostRoomAfterPress(preLive),
                "pre-live controls must not be mistaken for actual LIVE");
        ok(!BigoAutomationPolicy.isHostRoomAfterPress(mixerWithSeats),
                "mixer with 6 digits must not be mistaken for live stream");
        ok(BigoAutomationPolicy.isHostRoomAfterPress(host),
                "legacy host viewer-text anchors still work");
        ok(!BigoAutomationPolicy.isHostRoomAfterPress(screenshot),
                "setup with 방송하기 does not confirm LIVE");
        System.out.println("BIGO screenshot-state unit checks PASS; NOT a live-device test.");
    }
}
