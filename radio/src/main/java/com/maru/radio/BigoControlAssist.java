package com.maru.radio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.lang.ref.WeakReference;

/**
 * EXPERIMENT ONLY — requires separate explicit consent and Android accessibility opt-in.
 * Operates ONLY inside the real BIGO package, on pre-declared static UI steps.
 * Never reads or stores viewer comments, passwords, messages or other app screens.
 * Never acts when the lock screen is shown or when BIGO is not foreground.
 * No unknown-screen taps, no hidden API, and no Play Protect bypass.
 *
 * This service has NOT been validated on a real BIGO Audio LIVE broadcast.
 */
public final class BigoControlAssist extends AccessibilityService {
    private static final String STORE = "bigo_control_assist";
    private static final String BIGO = "sg.bigo.live";
    private static final long DEADLINE_MS = 90_000L;
    private static final long STEP_GAP_MS = 950L;
    private static volatile WeakReference<BigoControlAssist> connectedService =
            new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable work = this::evaluate;
    private long lastActionAt;
    private boolean waitingGesture;
    private boolean actionInProgress;

    private SharedPreferences pref() { return getSharedPreferences(STORE, MODE_PRIVATE); }

    public static boolean approved(Context ctx) {
        return ctx.getSharedPreferences(STORE, MODE_PRIVATE).getBoolean("approved", false);
    }

    public static boolean connected(Context ctx) {
        return ctx.getSharedPreferences(STORE, MODE_PRIVATE).getBoolean("connected", false);
    }

    public static String status(Context ctx) {
        return ctx.getSharedPreferences(STORE, MODE_PRIVATE)
                .getString("status", "BIGO 자동 조작은 꺼져 있습니다.");
    }

    public static void approve(Context ctx, boolean value) {
        SharedPreferences.Editor edit = ctx.getSharedPreferences(STORE, MODE_PRIVATE).edit()
                .putBoolean("approved", value);
        if (!value) edit.remove("op").remove("expires").remove("phase");
        edit.apply();
    }

    /** Can only be queued by the user's selected MARU schedule after local consent. */
    public static boolean queue(Context ctx, boolean start) {
        if (!approved(ctx) || !connected(ctx)) return false;
        ctx.getSharedPreferences(STORE, MODE_PRIVATE).edit()
                .putString("op", start ? "start" : "stop")
                .putLong("expires", System.currentTimeMillis() + DEADLINE_MS)
                .putString("phase", start ? BigoAutomationPolicy.Phase.OPEN_COMPOSER.name()
                        : BigoAutomationPolicy.Phase.OPEN_END_DIALOG.name())
                .putString("status", start ? "BIGO 오디오 LIVE 화면 찾는 중"
                        : "BIGO 진행 방송 종료 화면 찾는 중")
                .apply();
        // An alarm can fire while BIGO is already foreground: a new window
        // event may not arrive, so wake the connected service on its own loop.
        BigoControlAssist running = connectedService.get();
        if (running != null) running.handler.postDelayed(running.work, 400L);
        return true;
    }

    private void mark(String message) {
        pref().edit().putString("status", message).apply();
    }

    private void advance(BigoAutomationPolicy.Phase next, String reason) {
        pref().edit().putString("phase", next.name()).putString("status", reason).apply();
        lastActionAt = System.currentTimeMillis();
        handler.removeCallbacks(work);
        handler.postDelayed(work, 1000L);
    }

    private void abort(String message) {
        pref().edit().remove("op").remove("expires").remove("phase")
                .putString("status", message).apply();
        handler.removeCallbacks(work);
        waitingGesture = false;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = new WeakReference<>(this);
        pref().edit().putBoolean("connected", true).apply();
        mark("접근성 기능 연결됨 (자동 조작은 별도 동의 후에만 허용)");
    }

    @Override public boolean onUnbind(Intent i) {
        connectedService = new WeakReference<>(null);
        pref().edit().putBoolean("connected", false)
                .remove("op").remove("expires").remove("phase")
                .putString("status", "접근성 서비스가 꺼졌어요.").apply();
        return super.onUnbind(i);
    }

    @Override public void onInterrupt() { abort("자동 조작 중단: BIGO 상태 확인 필요"); }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null
                || !BIGO.contentEquals(e.getPackageName()) || !approved(this)) return;
        if (!pref().contains("op")) return;
        handler.removeCallbacks(work);
        handler.postDelayed(work, 450);
    }

    private void evaluate() {
        if (!approved(this) || waitingGesture || actionInProgress) return;
        String op = pref().getString("op", "");
        if (!"start".equals(op) && !"stop".equals(op)) return;
        if (System.currentTimeMillis() >= pref().getLong("expires", 0L)) {
            abort("90초 초과: BIGO 자동 조작 실패. 실제 방송 상태를 확인해 주세요.");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !BIGO.contentEquals(root.getPackageName())) return;
        if (System.currentTimeMillis() - lastActionAt < STEP_GAP_MS) {
            handler.postDelayed(work, STEP_GAP_MS);
            return;
        }
        String phaseName = pref().getString("phase", "");
        BigoAutomationPolicy.Phase phase;
        try { phase = BigoAutomationPolicy.Phase.valueOf(phaseName); }
        catch (IllegalArgumentException err) { abort("잘못된 BIGO 자동화 상태"); return; }
        Set<String> labels = allLabels(root);
        if ("start".equals(op)) doStart(root, phase, labels);
        else doEnd(root, phase, labels);
    }

    private void doStart(AccessibilityNodeInfo root, BigoAutomationPolicy.Phase phase,
            Set<String> labels) {
        switch (phase) {
            case OPEN_COMPOSER:
                if (BigoAutomationPolicy.isSetup(labels)) {
                    advance(BigoAutomationPolicy.Phase.SELECT_AUDIO, "오디오 LIVE 준비 화면 인식");
                    return;
                }
                if (BigoAutomationPolicy.has(labels, "추천")
                        && BigoAutomationPolicy.has(labels, "인기")
                        && BigoAutomationPolicy.has(labels, "발견")) {
                    tapXY(root, .50f, .91f, () ->
                            advance(BigoAutomationPolicy.Phase.SELECT_AUDIO,
                                    "BIGO 방송 준비 화면 열기 시도"));
                }
                return;
            case SELECT_AUDIO:
                if (!BigoAutomationPolicy.isSetup(labels)) return;
                if (click(root, "오디오 라이브")) {
                    advance(BigoAutomationPolicy.Phase.OPEN_MIXER, "오디오 LIVE 선택");
                } else {
                    // If the mode tab has no click node, require clear proof of composer.
                    advance(BigoAutomationPolicy.Phase.OPEN_MIXER, "오디오 LIVE 화면 표시");
                }
                return;
            case OPEN_MIXER:
                if (BigoAutomationPolicy.isMixer(labels)) {
                    advance(BigoAutomationPolicy.Phase.SET_MIC_ONE, "믹서 화면 열림");
                    return;
                }
                if (BigoAutomationPolicy.isSetup(labels) && click(root, "믹서"))
                    advance(BigoAutomationPolicy.Phase.SET_MIC_ONE, "믹서 메뉴 선택");
                return;
            case SET_MIC_ONE:
                if (!BigoAutomationPolicy.isMixer(labels)) return;
                if (setOneOnMixerSeekbar(root))
                    advance(BigoAutomationPolicy.Phase.SELECT_ORIGINAL, "마이크 값 1 설정 시도");
                else
                    abort("마이크 슬라이더 1 설정 실패: BIGO 실제 UI 검증이 필요해요.");
                return;
            case SELECT_ORIGINAL:
                if (!BigoAutomationPolicy.isMixer(labels)) return;
                // Verify the actual rendered number from the user screenshot.
                // An accepted ACTION_SET_PROGRESS does not prove BIGO changed it.
                boolean micOneShown = labels.stream().anyMatch(v -> "1".equals(v.trim()));
                if (!micOneShown) {
                    abort("마이크 볼륨이 화면에 1로 표시되는지 확인하지 못했어요.");
                    return;
                }
                if (click(root, "Original") || click(root, "원본"))
                    advance(BigoAutomationPolicy.Phase.CLOSE_MIXER, "Original 효과 선택");
                else abort("Original 효과를 찾지 못해 방송을 시작하지 않았어요.");
                return;
            case CLOSE_MIXER:
                if (BigoAutomationPolicy.isSetup(labels)
                        && !BigoAutomationPolicy.isMixer(labels)) {
                    advance(BigoAutomationPolicy.Phase.START_STREAM, "방송하기 준비");
                    return;
                }
                if (!BigoAutomationPolicy.isMixer(labels)) return;
                if (click(root, "닫기") || click(root, "Close")) {
                    advance(BigoAutomationPolicy.Phase.START_STREAM, "믹서 창 닫음");
                    return;
                }
                // User's actual screenshot is 709x1536 portrait, X at 0.90 x 0.23;
                // use ONLY if the full Mixer/Mic labels are still visible in BIGO.
                tapXY(root, .904f, .233f, () ->
                        advance(BigoAutomationPolicy.Phase.START_STREAM, "믹서 닫기 시도"));
                return;
            case START_STREAM:
                if (!BigoAutomationPolicy.isSetup(labels)) {
                    if (BigoAutomationPolicy.isHostLive(labels))
                        advance(BigoAutomationPolicy.Phase.VERIFY_STREAM,
                                "호스트 라이브 화면으로 전환됨");
                    return;
                }
                if (click(root, "방송하기")) {
                    advance(BigoAutomationPolicy.Phase.VERIFY_STREAM,
                            "방송하기 누름 · 실제 LIVE 상태 검증 대기");
                } else abort("방송하기 버튼을 안전하게 찾지 못했어요.");
                return;
            case VERIFY_STREAM:
                if (BigoAutomationPolicy.isHostLive(labels))
                    abort("BIGO 라이브 화면 확인됨(참고: 서버 송출·청취자 음질은 별도 검증 필요)");
                return;
            default:
                abort("알 수 없는 방송 시작 상태"); return;
        }
    }

    private void doEnd(AccessibilityNodeInfo root, BigoAutomationPolicy.Phase phase,
            Set<String> labels) {
        switch (phase) {
            case OPEN_END_DIALOG:
                if (BigoAutomationPolicy.isEndConfirmation(labels)) {
                    advance(BigoAutomationPolicy.Phase.CONFIRM_END, "종료 확인창 표시");
                    return;
                }
                // No guesses on unknown screens or composer: host evidence required.
                if (!BigoAutomationPolicy.isHostLive(labels)) return;
                if (click(root, "종료") || click(root, "닫기")) {
                    advance(BigoAutomationPolicy.Phase.CONFIRM_END, "종료 버튼 선택");
                    return;
                }
                tapXY(root, .93f, .08f, () ->
                        advance(BigoAutomationPolicy.Phase.CONFIRM_END, "라이브 종료 X 선택"));
                return;
            case CONFIRM_END:
                if (BigoAutomationPolicy.isEndConfirmation(labels)
                        && click(root, "라이브 종료하기")) {
                    advance(BigoAutomationPolicy.Phase.VERIFY_END,
                            "라이브 종료하기 누름 · 실제 종료 검증 대기");
                }
                return;
            case VERIFY_END:
                if (BigoAutomationPolicy.isSetup(labels))
                    abort("BIGO 방송 준비 화면으로 복귀 · 종료 추정 (서버 확인 불가)");
                return;
            default:
                abort("알 수 없는 방송 종료 상태"); return;
        }
    }

    private boolean setOneOnMixerSeekbar(AccessibilityNodeInfo root) {
        // Do not blindly swipe other sliders: require the mixer, MIC label, exactly
        // one visible android.widget.SeekBar and an actual accessibility RangeInfo.
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        AccessibilityNodeInfo found = null;
        int count = 0;
        while (!q.isEmpty() && count++ < 1800) {
            AccessibilityNodeInfo p = q.poll();
            if (p == null) continue;
            if (p.getClassName() != null
                    && p.getClassName().toString().contains("SeekBar")
                    && p.getRangeInfo() != null && p.isEnabled()) {
                if (found != null) return false; // Ambiguous: don't touch the wrong slider.
                found = p;
            }
            for (int n = 0; n < p.getChildCount(); n++) {
                AccessibilityNodeInfo child = p.getChild(n);
                if (child != null) q.add(child);
            }
        }
        if (found == null) return false;
        AccessibilityNodeInfo.RangeInfo range = found.getRangeInfo();
        if (range.getMin() > 1f || range.getMax() < 1f) return false;
        Bundle b = new Bundle();
        b.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 1f);
        return found.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(), b);
    }

    private boolean click(AccessibilityNodeInfo root, String label) {
        AccessibilityNodeInfo n = find(root, label);
        if (n == null) return false;
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) {
            if (p.isClickable() && p.isEnabled()
                    && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String text) {
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int count = 0;
        String target = BigoAutomationPolicy.normalize(text);
        while (!queue.isEmpty() && count++ < 1800) {
            AccessibilityNodeInfo item = queue.poll();
            if (item == null) continue;
            String visible = item.getText() == null ? "" : item.getText().toString();
            String desc = item.getContentDescription() == null
                    ? "" : item.getContentDescription().toString();
            if (BigoAutomationPolicy.normalize(visible).equals(target)
                    || BigoAutomationPolicy.normalize(desc).equals(target)) return item;
            for (int i = 0; i < item.getChildCount(); i++) {
                AccessibilityNodeInfo child = item.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private Set<String> allLabels(AccessibilityNodeInfo root) {
        HashSet<String> out = new HashSet<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int count = 0;
        while (!q.isEmpty() && count++ < 1800) {
            AccessibilityNodeInfo p = q.poll();
            if (p == null) continue;
            if (p.getText() != null) out.add(p.getText().toString());
            if (p.getContentDescription() != null) out.add(p.getContentDescription().toString());
            for (int n = 0; n < p.getChildCount(); n++) {
                AccessibilityNodeInfo child = p.getChild(n);
                if (child != null) q.add(child);
            }
        }
        return out;
    }

    private void tapXY(AccessibilityNodeInfo root, float xFactor, float yFactor, Runnable done) {
        Rect screen = new Rect();
        root.getBoundsInScreen(screen);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        if (w < 400 || h < 700 || w >= h
                || root.getPackageName() == null
                || !BIGO.contentEquals(root.getPackageName())) {
            abort("알 수 없는 화면 비율이라 자동 좌표 누르기를 중단했어요.");
            return;
        }
        float x = w * xFactor, y = h * yFactor;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 100)).build();
        waitingGesture = true;
        boolean dispatched = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                waitingGesture = false;
                done.run();
            }
            @Override public void onCancelled(GestureDescription g) {
                waitingGesture = false;
                abort("BIGO 자동 누르기가 취소됐어요.");
            }
        }, handler);
        if (!dispatched) {
            waitingGesture = false;
            abort("BIGO 누르기 실행 거부됨");
        }
    }
}
