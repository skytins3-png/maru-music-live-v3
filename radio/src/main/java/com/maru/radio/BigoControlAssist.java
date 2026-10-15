package com.maru.radio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * User-initiated, narrow BIGO-only accessibility assistant.
 *
 * This is NOT an official BIGO integration. It cannot attest that the
 * broadcaster is live/off-air. It never reads other apps or chat content,
 * logs events, handles login/payment, or runs without a fresh user command.
 * Unknown screens always fail closed instead of guessing.
 */
public final class BigoControlAssist extends AccessibilityService {
    private static final String PREF = "bigo_control_assist";
    private static final String BIGO = "sg.bigo.live";
    private static final String MODE_START = "start";
    private static final String MODE_STOP = "stop";
    private static final long TIMEOUT_MS = 60000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scheduledProcess = this::process;
    // Respond promptly to the user's explicit request even when BIGO's host
    // screen is otherwise idle (no new accessibility event at timer expiry).
    private final SharedPreferences.OnSharedPreferenceChangeListener pendingListener =
            (preferences, key) -> {
                if ("mode".equals(key) && connected) {
                    handler.removeCallbacks(scheduledProcess);
                    handler.postDelayed(scheduledProcess, 350);
                }
            };

    private String mode = "";
    private int step;
    private long expires;
    private long lastOperationAt;
    private boolean awaitingTap;
    private boolean connected;

    public static boolean isConnected(Context context) {
        return context.getSharedPreferences(PREF, MODE_PRIVATE)
                .getBoolean("connected", false);
    }

    public static String lastStatus(Context context) {
        return context.getSharedPreferences(PREF, MODE_PRIVATE)
                .getString("status", "BIGO 시작·종료 지원 준비");
    }

    public static void queue(Context context, boolean start) {
        context.getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString("mode", start ? MODE_START : MODE_STOP)
                .putLong("expires", System.currentTimeMillis() + TIMEOUT_MS)
                .putInt("step", 0)
                .putString("status", start
                        ? "BIGO 오디오 LIVE 시작 화면을 찾는 중"
                        : "BIGO 종료 화면을 찾는 중")
                .apply();
    }

    private SharedPreferences pref() {
        return getSharedPreferences(PREF, MODE_PRIVATE);
    }

    private void status(String message) {
        pref().edit().putString("status", message).apply();
    }

    private void close(String message) {
        pref().edit().remove("mode").remove("expires").remove("step")
                .putString("status", message).apply();
        mode = "";
        awaitingTap = false;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connected = true;
        pref().registerOnSharedPreferenceChangeListener(pendingListener);
        pref().edit().putBoolean("connected", true)
                .putString("status", "BIGO 버튼 지원 연결됨 · 요청 전에는 동작하지 않음")
                .apply();
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        connected = false;
        pref().unregisterOnSharedPreferenceChangeListener(pendingListener);
        handler.removeCallbacks(scheduledProcess);
        pref().edit().putBoolean("connected", false).remove("mode")
                .putString("status", "BIGO 시작·종료 지원 꺼짐").apply();
        return super.onUnbind(intent);
    }

    @Override public void onInterrupt() {
        close("BIGO 제어가 중단됐어요. 방송 상태를 직접 확인해 주세요.");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!connected || event == null || event.getPackageName() == null
                || !BIGO.contentEquals(event.getPackageName())) return;
        handler.removeCallbacks(scheduledProcess);
        handler.postDelayed(scheduledProcess, 350);
    }

    private void process() {
        if (!connected || awaitingTap) return;
        String pending = pref().getString("mode", "");
        if (pending.isEmpty()) return;
        long expiration = pref().getLong("expires", 0);
        if (System.currentTimeMillis() > expiration) {
            close("시간 초과: BIGO에서 방송 시작/종료 버튼을 직접 확인해 주세요.");
            return;
        }
        if (!pending.equals(mode)) {
            mode = pending;
            step = pref().getInt("step", 0);
            expires = expiration;
            lastOperationAt = 0;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !BIGO.contentEquals(root.getPackageName())) return;
        if (System.currentTimeMillis() - lastOperationAt < 900) {
            handler.postDelayed(scheduledProcess, 550);
            return;
        }
        if (MODE_START.equals(mode)) handleStart(root);
        else if (MODE_STOP.equals(mode)) handleStop(root);
    }

    private void saveStep(int next, String note) {
        step = next;
        lastOperationAt = System.currentTimeMillis();
        pref().edit().putInt("step", next).putString("status", note).apply();
        handler.postDelayed(scheduledProcess, 1300);
    }

    private void handleStart(AccessibilityNodeInfo root) {
        if (has(root, "라이브 종료하기") || has(root, "명 시청자") || has(root, "시청자")) {
            close("이미 BIGO 라이브 화면일 수 있어요. 재시작하지 않았습니다.");
            return;
        }

        if (has(root, "오디오 라이브") && has(root, "방송하기")) {
            if (step < 2) {
                if (tapExact(root, "오디오 라이브")) {
                    saveStep(2, "오디오 LIVE를 선택했어요. 방송 시작 버튼 확인 중");
                    return;
                }
                // '오디오 라이브' may be a selected text-only tab.
                saveStep(2, "오디오 LIVE 화면 확인 · 방송하기 버튼을 찾는 중");
                return;
            }
            if (tapExact(root, "방송하기")) {
                close("BIGO 방송하기를 눌렀어요. 실제 라이브 시작 여부를 BIGO 화면에서 확인해 주세요.");
                return;
            }
        }

        // BIGO browse page -> broadcast composer. Never tap coordinates unless
        // these exact navigation anchors identify the BIGO landing screen.
        if (step == 0 && has(root, "근처") && has(root, "인기")
                && has(root, "추천") && has(root, "발견")) {
            if (safeTap(0.50f, 0.904f, () -> saveStep(1,
                    "BIGO 방송 준비 화면 여는 중 · 필요하면 가운데 방송 버튼을 눌러 주세요."))) {
                return;
            }
        }
        if (has(root, "오디오 라이브") && step < 2) {
            if (tapExact(root, "오디오 라이브")) {
                saveStep(2, "BIGO 오디오 라이브 탭 선택");
                return;
            }
        }
        status("BIGO 오디오 LIVE 설정 → 방송하기 순서로 확인 중 · 찾지 못하면 직접 눌러 주세요.");
    }

    private void handleStop(AccessibilityNodeInfo root) {
        if (has(root, "방송을 종료하시겠습니까?")) {
            if (tapExact(root, "라이브 종료하기")) {
                close("BIGO 라이브 종료하기를 눌렀어요. 실제 종료 여부를 화면에서 확인해 주세요.");
                return;
            }
        }
        if (tapExact(root, "라이브 종료하기")) {
            close("BIGO 종료 버튼을 눌렀어요. 실제 종료 여부를 확인해 주세요.");
            return;
        }

        // Only interact with the close button when the recognized LIVE host
        // screen includes a viewer label; never click arbitrary unknown pages.
        if (step == 0 && isHostScreen(root)) {
            if (tapAnyExact(root, "종료", "닫기", "Close")) {
                saveStep(1, "종료 확인창 확인 중 · '라이브 종료하기'를 눌러 주세요.");
                return;
            }
            // BIGO's upper-right X often has no accessibility text.
            // Fallback to explicit on-screen X location on verified host screen.
            if (safeTap(0.922f, 0.086f, () -> saveStep(1,
                    "BIGO 종료 확인창이 열렸는지 확인 중"))) {
                return;
            }
        }
        status("BIGO 라이브 화면 오른쪽 위 X를 누르고 '라이브 종료하기'를 선택하세요.");
    }

    private boolean isHostScreen(AccessibilityNodeInfo root) {
        return has(root, "시청자")
                && (has(root, "쟁탈전") || has(root, "PK") || has(root, "자리"));
    }

    private boolean tapAnyExact(AccessibilityNodeInfo root, String... names) {
        for (String name : names) if (tapExact(root, name)) return true;
        return false;
    }

    private boolean tapExact(AccessibilityNodeInfo root, String expected) {
        AccessibilityNodeInfo node = find(root, expected, true);
        if (node == null) return false;
        for (AccessibilityNodeInfo p = node; p != null; p = p.getParent()) {
            if (p.isClickable() && p.isEnabled() && p.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return true;
        }
        return false;
    }

    private boolean has(AccessibilityNodeInfo root, String fragment) {
        return find(root, fragment, false) != null;
    }

    private AccessibilityNodeInfo find(AccessibilityNodeInfo root, String match, boolean exact) {
        if (root == null) return null;
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int seen = 0;
        while (!queue.isEmpty() && seen++ < 1400) {
            AccessibilityNodeInfo item = queue.removeFirst();
            String text = item.getText() == null ? "" : item.getText().toString().trim();
            String description = item.getContentDescription() == null
                    ? "" : item.getContentDescription().toString().trim();
            boolean found = exact ? text.equals(match) || description.equals(match)
                    : text.contains(match) || description.contains(match);
            if (found) return item;
            for (int i = 0; i < item.getChildCount(); i++) {
                AccessibilityNodeInfo child = item.getChild(i);
                if (child != null) queue.addLast(child);
            }
        }
        return null;
    }

    private boolean safeTap(float fx, float fy, Runnable done) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !BIGO.contentEquals(root.getPackageName())) return false;
        Rect bounds = new Rect();
        root.getBoundsInScreen(bounds);
        android.util.DisplayMetrics m = getResources().getDisplayMetrics();
        int width = m.widthPixels;
        int height = m.heightPixels;
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null) {
                Rect full = wm.getMaximumWindowMetrics().getBounds();
                width = full.width();
                height = full.height();
            }
        }
        if (width < 400 || height < 700 || width >= height) return false;
        Path path = new Path();
        float x = width * fx, y = height * fy;
        path.moveTo(x, y);
        path.lineTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(path, 0, 100));
        awaitingTap = true;
        boolean accepted = dispatchGesture(b.build(),
                new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription gesture) {
                        awaitingTap = false;
                        done.run();
                    }
                    @Override public void onCancelled(GestureDescription gesture) {
                        awaitingTap = false;
                        status("BIGO 자동 터치가 취소됐어요. 해당 버튼은 직접 눌러 주세요.");
                    }
                }, handler);
        if (!accepted) awaitingTap = false;
        return accepted;
    }
}
