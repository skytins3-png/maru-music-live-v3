package com.maru.radio;

import java.util.Set;

/** Screenshot-driven, offline-only regression for BIGO public chat host guards. */
public final class BigoHostChatGateSelfTest {
    private static void check(boolean valid, String msg) {
        if (!valid) throw new AssertionError(msg);
        System.out.println("PASS: " + msg);
    }
    public static void main(String[] args) {
        // These strings came from the owner's 2026-10-18 real screenshots
        // but test cannot verify Android Accessibility node accessibility.
        Set<String> realHostTools = Set.of("라이브 툴", "믹서", "사운드",
                "HQ", "프로그램 목록", "화면 공유하기", "Youtube",
                "노래방", "토픽", "관리", "실시간 지원", "녹화");
        Set<String> realHostRoom = Set.of("컨트리송 시간", "마이크...", "아환",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
        Set<String> originalHost = Set.of("시청자 9", "자리", "댓글", "방송 시간");
        Set<String> composer = Set.of("오디오 라이브", "방송하기",
                "마이크 볼륨", "1", "2", "3");
        Set<String> viewer = Set.of("라이브", "시청자 9", "채팅", "선물");
        Set<String> privateChat = Set.of("마이크", "DM", "1", "2", "3");
        Set<String> noMic = Set.of("컨트리송 시간", "1", "2", "3");
        long observedAt = 1_000_000L;
        check(BigoAutomationPolicy.isHostToolsSheet(realHostTools),
                "owner real LIVE tools is distinct from audio sound emoji tool");
        check(!BigoAutomationPolicy.isHostToolsSheet(realHostRoom),
                "seat screen cannot impersonate owner host tools");
        check(!BigoAutomationPolicy.isHostToolsSheet(viewer),
                "viewer feed cannot impersonate owner host tools");
        check(!BigoAutomationPolicy.isHostToolsSheet(composer),
                "pre-live composer cannot impersonate on-air tools");
        check(BigoAutomationPolicy.isOnAirSeatLayout(realHostRoom),
                "owner real on-air microphone + numbered seats recognized");
        check(!BigoAutomationPolicy.isOnAirSeatLayout(noMic),
                "numbered seats without host mic are insufficient");
        check(!BigoAutomationPolicy.isOnAirSeatLayout(composer),
                "pre-live composer never qualifies as host room");
        check(!BigoAutomationPolicy.isOnAirSeatLayout(privateChat),
                "private chat with only 3 digits and mic does not qualify with zero proof");
        check(!BigoHostChatGate.mayReadPublicChat(realHostRoom, 0, observedAt+10L),
                "on-air UI alone does not bypass proof requirement");
        check(BigoHostChatGate.mayReadPublicChat(realHostRoom, observedAt, observedAt+10L),
                "prior real owner host-tools proof gates public-chat read");
        check(BigoHostChatGate.mayReadPublicChat(realHostRoom, observedAt,
                        observedAt+BigoHostChatGate.HOST_TOOL_PROOF_MS),
                "bounded token usable for its stated window");
        check(!BigoHostChatGate.mayReadPublicChat(realHostRoom, observedAt,
                        observedAt+BigoHostChatGate.HOST_TOOL_PROOF_MS+1),
                "owner host-tools proof eventually expires");
        check(!BigoHostChatGate.mayReadPublicChat(realHostRoom, observedAt, observedAt-1),
                "future-dated proof rejected");
        check(!BigoHostChatGate.mayReadPublicChat(composer, observedAt, observedAt+5),
                "cannot read comments from start composer even with proof");
        check(!BigoHostChatGate.mayReadPublicChat(viewer, observedAt, observedAt+5),
                "cannot read comments from ordinary viewer feed");
        check(!BigoHostChatGate.mayReadPublicChat(privateChat, 0, observedAt+5),
                "never read private messages without proven on-air host scope");
        check(BigoHostChatGate.mayReadPublicChat(originalHost, 0, observedAt+5),
                "previous strict live host UI remains functional");
        check(!BigoHostChatGate.mayReadPublicChat(null, observedAt, observedAt+5),
                "null screen denied");
        // Exhaustive bool combinations: an unverified weak layout must never pass.
        int denied = 0;
        for (int mask = 0; mask < 128; mask++) {
            java.util.HashSet<String> labels = new java.util.HashSet<>();
            if ((mask & 1) != 0) labels.add("마이크");
            if ((mask & 2) != 0) labels.add("1");
            if ((mask & 4) != 0) labels.add("2");
            if ((mask & 8) != 0) labels.add("3");
            if ((mask & 16) != 0) labels.add("컨트리송 시간");
            if ((mask & 32) != 0) labels.add("아환");
            if ((mask & 64) != 0) labels.add("방송하기");
            check(!BigoHostChatGate.mayReadPublicChat(labels, 0L, observedAt+10L),
                    "unverified subset rejects mask " + mask);
            denied++;
        }
        System.out.println("PASS: " + denied +
                " unverified on-air variants safely rejected. "
                + "THIS DOES NOT VERIFY BIGO COMMENTS OR LISTENER AUDIO.");
    }
}
