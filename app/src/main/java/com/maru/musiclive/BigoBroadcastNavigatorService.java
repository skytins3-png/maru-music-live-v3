package com.maru.musiclive;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * User-enabled navigator that moves from BIGO's home screen to its broadcast
 * preparation screen. It deliberately stops before the final public-broadcast
 * button so the host keeps control of the actual start.
 */
public final class BigoBroadcastNavigatorService extends AccessibilityService {
    private static final String PREFS = "bigo_broadcast_navigator";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_MODE = "mode";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_STAGE = "stage";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final long REQUEST_TIMEOUT_MS = 90_000L;
    private static final int MAX_ATTEMPTS = 28;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = this::navigateCurrentWindow;

    public static void arm(Context context, String liveMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, true)
                .putString(KEY_MODE, BigoNavigationPolicy.normalizeMode(liveMode))
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .putInt(KEY_STAGE, 0)
                .putInt(KEY_ATTEMPTS, 0)
                .apply();
    }

    public static void disarm(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACTIVE, false)
                .putInt(KEY_STAGE, 0)
                .putInt(KEY_ATTEMPTS, 0)
                .apply();
    }

    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName component = new ComponentName(
                context,
                BigoBroadcastNavigatorService.class);
        String full = component.flattenToString();
        String shortName = component.flattenToShortString();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            String item = splitter.next();
            if (full.equalsIgnoreCase(item) || shortName.equalsIgnoreCase(item)) {
                return true;
            }
        }
        return false;
    }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_CLICKED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                    | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.notificationTimeout = 80L;
            info.packageNames = new String[]{OneClickBroadcastPlan.BIGO_PACKAGE};
            setServiceInfo(info);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isArmed()) return;
        CharSequence packageName = event == null ? null : event.getPackageName();
        if (packageName != null
                && !OneClickBroadcastPlan.BIGO_PACKAGE.contentEquals(packageName)) {
            return;
        }
        scheduleNavigation(220L);
    }

    @Override public void onInterrupt() {
        handler.removeCallbacks(navigateRunnable);
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(navigateRunnable);
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean isArmed() {
        return prefs().getBoolean(KEY_ACTIVE, false);
    }

    private void scheduleNavigation(long delayMs) {
        handler.removeCallbacks(navigateRunnable);
        handler.postDelayed(navigateRunnable, delayMs);
    }

    private void navigateCurrentWindow() {
        if (!isArmed()) return;
        SharedPreferences preferences = prefs();
        long startedAt = preferences.getLong(KEY_STARTED_AT, 0L);
        if (startedAt <= 0L
                || System.currentTimeMillis() - startedAt > REQUEST_TIMEOUT_MS) {
            fail("BIGO 방송 화면 자동 이동 시간이 초과되었습니다.");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            retry(350L);
            return;
        }
        CharSequence packageName = root.getPackageName();
        if (packageName != null
                && !OneClickBroadcastPlan.BIGO_PACKAGE.contentEquals(packageName)) {
            retry(350L);
            return;
        }

        int stage = preferences.getInt(KEY_STAGE, 0);
        String requestedMode = preferences.getString(
                KEY_MODE,
                BigoNavigationPolicy.MODE_REGULAR);
        int attempts = preferences.getInt(KEY_ATTEMPTS, 0) + 1;
        preferences.edit().putInt(KEY_ATTEMPTS, attempts).apply();

        if (isBroadcastPreparationScreen(root, stage)) {
            complete();
            return;
        }

        // BIGO may reopen the previous preparation screen instead of its home.
        // Regular LIVE can safely keep the default selection; Audio LIVE still
        // has to select its own mode before completion.
        if (stage == 0 && hasBroadcastPreparationEvidence(root)) {
            setStage(BigoNavigationPolicy.MODE_AUDIO.equals(requestedMode) ? 1 : 2);
            scheduleNavigation(260L);
            return;
        }

        if (attempts > MAX_ATTEMPTS) {
            fail("BIGO 화면 구성이 달라 자동 이동을 완료하지 못했습니다.");
            return;
        }

        if (stage == 0) {
            AccessibilityNodeInfo entry = findBroadcastEntry(root);
            if (entry != null && clickNode(entry)) {
                setStage(1);
                scheduleNavigation(950L);
                return;
            }
            if (attempts >= 4) {
                tapBottomCenter();
                setStage(1);
                scheduleNavigation(1_050L);
                return;
            }
            retry(320L);
            return;
        }

        if (stage == 1) {
            String mode = requestedMode;
            AccessibilityNodeInfo modeNode = findModeNode(root, mode);
            if (modeNode != null && clickNode(modeNode)) {
                setStage(2);
                scheduleNavigation(1_050L);
                return;
            }

            if (BigoNavigationPolicy.MODE_AUDIO.equals(mode)
                    && (attempts == 8 || attempts == 13 || attempts == 18)) {
                swipeModeStripLeft();
                scheduleNavigation(800L);
                return;
            }

            if (BigoNavigationPolicy.MODE_REGULAR.equals(mode)
                    && hasBroadcastPreparationEvidence(root)) {
                // BIGO regular LIVE is commonly the default mode. Do not use a
                // coordinate tap here because that could hit the final public
                // broadcast button on a changed BIGO layout.
                setStage(2);
                scheduleNavigation(260L);
                return;
            }
            retry(350L);
            return;
        }

        if (stage >= 2) {
            if (attempts == 18) {
                setStage(1);
            }
            retry(420L);
        }
    }

    private void setStage(int stage) {
        prefs().edit().putInt(KEY_STAGE, stage).apply();
    }

    private void retry(long delayMs) {
        scheduleNavigation(delayMs);
    }

    private AccessibilityNodeInfo findBroadcastEntry(AccessibilityNodeInfo root) {
        DisplayMetrics display = getResources().getDisplayMetrics();
        int width = Math.max(display.widthPixels, 1);
        int height = Math.max(display.heightPixels, 1);
        AccessibilityNodeInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AccessibilityNodeInfo node : flatten(root)) {
            String label = nodeLabel(node);
            if (!BigoNavigationPolicy.isEntryLabel(label)) continue;
            AccessibilityNodeInfo clickable = clickableNode(node);
            if (clickable == null) continue;
            Rect bounds = new Rect();
            clickable.getBoundsInScreen(bounds);
            float cx = bounds.exactCenterX() / width;
            float cy = bounds.exactCenterY() / height;
            int score = 0;
            if (cy > 0.68f) score += 8;
            if (cx > 0.28f && cx < 0.72f) score += 8;
            if (cy > 0.82f) score += 5;
            String normalized = BigoNavigationPolicy.normalize(label);
            if (normalized.equals("live") || normalized.equals("golive")
                    || normalized.equals("라이브")) score += 4;
            if (score > bestScore) {
                bestScore = score;
                best = clickable;
            }
        }
        return bestScore >= 12 ? best : null;
    }

    private AccessibilityNodeInfo findModeNode(
            AccessibilityNodeInfo root,
            String mode) {
        DisplayMetrics display = getResources().getDisplayMetrics();
        int height = Math.max(display.heightPixels, 1);
        AccessibilityNodeInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AccessibilityNodeInfo node : flatten(root)) {
            String label = nodeLabel(node);
            if (!BigoNavigationPolicy.isModeLabel(label, mode)) continue;
            AccessibilityNodeInfo clickable = clickableNode(node);
            if (clickable == null) continue;
            Rect bounds = new Rect();
            clickable.getBoundsInScreen(bounds);
            float cy = bounds.exactCenterY() / height;
            int score = cy > 0.55f ? 8 : 1;
            if (cy > 0.76f) score += 5;
            if (node.isSelected() || node.isChecked()) score += 2;
            if (score > bestScore) {
                bestScore = score;
                best = clickable;
            }
        }
        return best;
    }

    private boolean isBroadcastPreparationScreen(
            AccessibilityNodeInfo root,
            int stage) {
        return stage >= 2 && hasBroadcastPreparationEvidence(root);
    }

    private boolean hasBroadcastPreparationEvidence(AccessibilityNodeInfo root) {
        int setupEvidence = 0;
        boolean finalStartAction = false;
        for (AccessibilityNodeInfo node : flatten(root)) {
            String label = nodeLabel(node);
            if (BigoNavigationPolicy.isSetupEvidenceLabel(label)) setupEvidence++;
            if (BigoNavigationPolicy.isFinalStartLabel(label)) {
                finalStartAction = true;
            }
        }
        return finalStartAction && setupEvidence >= 1;
    }

    private int broadcastMenuEvidenceCount(AccessibilityNodeInfo root) {
        int count = 0;
        for (AccessibilityNodeInfo node : flatten(root)) {
            if (BigoNavigationPolicy.isBroadcastMenuEvidence(nodeLabel(node))) {
                count++;
            }
        }
        return count;
    }

    private static List<AccessibilityNodeInfo> flatten(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < 900) {
            AccessibilityNodeInfo node = queue.removeFirst();
            nodes.add(node);
            visited++;
            for (int index = 0; index < node.getChildCount(); index++) {
                AccessibilityNodeInfo child = node.getChild(index);
                if (child != null) queue.addLast(child);
            }
        }
        return nodes;
    }

    private static String nodeLabel(AccessibilityNodeInfo node) {
        StringBuilder text = new StringBuilder();
        append(text, node.getText());
        append(text, node.getContentDescription());
        append(text, node.getViewIdResourceName());
        return text.toString();
    }

    private static void append(StringBuilder builder, CharSequence value) {
        if (value == null || value.length() == 0) return;
        if (builder.length() > 0) builder.append(' ');
        builder.append(value);
    }

    private static AccessibilityNodeInfo clickableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        for (int depth = 0; current != null && depth < 5; depth++) {
            if (current.isClickable() && current.isEnabled()) return current;
            current = current.getParent();
        }
        return null;
    }

    private static boolean clickNode(AccessibilityNodeInfo node) {
        try {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void tapBottomCenter() {
        DisplayMetrics display = getResources().getDisplayMetrics();
        float x = display.widthPixels * 0.50f;
        float y = display.heightPixels * 0.88f;
        dispatchTap(x, y);
    }

    private void dispatchTap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 80L))
                .build();
        try {
            dispatchGesture(gesture, null, null);
        } catch (RuntimeException ignored) {
            // A later accessibility event will retry without terminating MARU.
        }
    }

    private void swipeModeStripLeft() {
        DisplayMetrics display = getResources().getDisplayMetrics();
        float y = display.heightPixels * 0.84f;
        Path path = new Path();
        path.moveTo(display.widthPixels * 0.78f, y);
        path.lineTo(display.widthPixels * 0.24f, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0L, 360L))
                .build();
        try {
            dispatchGesture(gesture, null, null);
        } catch (RuntimeException ignored) {
            // Retried through the normal navigation loop.
        }
    }

    private void complete() {
        disarm(this);
        AutoGreetingStore.setStatus(
                this,
                "BIGO 방송 준비 화면 자동 이동 완료 · 마지막 방송 시작은 직접 확인");
        Toast.makeText(
                this,
                "BIGO 방송 준비 화면으로 이동했습니다. 마지막 방송 시작 버튼은 직접 눌러주세요.",
                Toast.LENGTH_LONG).show();
    }

    private void fail(String reason) {
        disarm(this);
        AutoGreetingStore.setStatus(this, reason);
        Toast.makeText(
                this,
                reason + " BIGO 가운데 LIVE 버튼을 한 번 눌러주세요.",
                Toast.LENGTH_LONG).show();
    }
}
