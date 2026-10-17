package com.maru.radio;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.lang.ref.WeakReference;

/**
 * EXPERIMENT ONLY — requires separate explicit consent and Android accessibility opt-in.
 * Operates ONLY inside the real BIGO package, on pre-declared static UI steps.
 * Optional separately-approved comment replies read only labeled chat widgets
 * within the verified BIGO host LIVE view; content is transient, never logged.
 * No usernames, raw comments, passwords, or contacts are persisted or sent out.
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
    private static final long PREFLIGHT_MS = 60_000L;
    private static final String STATUS_CHANNEL = "maru_bigo_action_status";
    private static final int STATUS_NOTIFICATION = 5908;
    private static volatile WeakReference<BigoControlAssist> connectedService =
            new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable work = this::evaluate;
    // Crucial when Android disallows background BIGO activity launch and NO
    // accessibility event arrives: a watchdog still reports the failure.
    private final Runnable expiryWatchdog = this::checkExpiration;
    private long lastActionAt;
    private boolean waitingGesture;
    private boolean actionInProgress;
    private final Runnable chatWork = this::checkChat;
    private final LinkedHashSet<Integer> chatSeen = new LinkedHashSet<>();
    private boolean chatPrimed;
    // Only an observed owner-exclusive live tools sheet can prime the weak
    // seat-layout fallback. This is transient, never persisted as viewer data.
    private long hostToolsObservedAt;

    // One ephemeral public BIGO chat message event, never written to disk.
    private BigoChatRule.Match pendingPublicChatEvent;
    private long chatLastHandledAt;
    // A bound AccessibilityService can talk locally without trying to launch
    // a new media foreground service from BIGO's background-app context.
    // This never injects audio into the BIGO microphone / remote stream.
    private RadioDjVoice scopedCommentVoice;

    private SharedPreferences pref() { return getSharedPreferences(STORE, MODE_PRIVATE); }

    public static boolean approved(Context ctx) {
        return ctx.getSharedPreferences(STORE, MODE_PRIVATE).getBoolean("approved", false);
    }

    public static boolean connected(Context ctx) {
        // A persistent SharedPreferences flag can survive process death. Only
        // a currently bound Android AccessibilityService is really connected.
        return connectedService.get() != null;
    }

    public static boolean commentReplies(Context ctx) {
        return approved(ctx) && ctx.getSharedPreferences(STORE, MODE_PRIVATE)
                .getBoolean("comment_reply_opt_in", false);
    }

    /** Separate informed opt-in for read-only LIVE chat; default OFF. */
    public static boolean setCommentReplies(Context ctx, boolean enabled) {
        if (enabled && !approved(ctx)) return false;
        ctx.getSharedPreferences(STORE, MODE_PRIVATE).edit()
                .putBoolean("comment_reply_opt_in", enabled)
                .putString("chat_state", enabled
                        ? "댓글 대기 · BIGO 호스트 LIVE 화면/접근성 연결 필요"
                        : "댓글 자동응답 꺼짐")
                .apply();
        BigoControlAssist running = connectedService.get();
        if (running != null) {
            running.handler.removeCallbacks(running.chatWork);
            running.pendingPublicChatEvent = null;
            running.chatSeen.clear();
            running.chatPrimed = false;
            running.hostToolsObservedAt = 0L;
            if (!enabled) running.releaseCommentVoice();
            else running.setChatState("댓글 감시 ON · BIGO에서 진행 중인 호스트 LIVE 화면이 보여야 합니다.");
        }
        return true;
    }

    public static String chatState(Context ctx) {
        return ctx.getSharedPreferences(STORE, MODE_PRIVATE).getString(
                "chat_state", "댓글 자동응답 꺼짐");
    }

    public static String status(Context ctx) {
        return ctx.getSharedPreferences(STORE, MODE_PRIVATE)
                .getString("status", "BIGO 자동 조작은 꺼져 있습니다.");
    }

    /**
     * Read-only capability inspection of the CURRENT BIGO window.
     * No audio broadcast, no taps, no raw chat text or screen content stored.
     */
    public static boolean requestPreflight(Context ctx) {
        if (!approved(ctx) || !connected(ctx)) return false;
        // A read-only diagnostic must not overlap scheduled live controls.
        if (ctx.getSharedPreferences(STORE, MODE_PRIVATE).contains("op")) return false;
        ctx.getSharedPreferences(STORE, MODE_PRIVATE).edit()
                .putLong("preflight_expires", System.currentTimeMillis() + PREFLIGHT_MS)
                .putString("status", "BIGO 화면 점검 대기 중 · 방송 버튼은 누르지 않음")
                .apply();
        BigoControlAssist running = connectedService.get();
        if (running != null) {
            running.handler.removeCallbacks(running.expiryWatchdog);
            running.handler.postDelayed(running.expiryWatchdog, PREFLIGHT_MS + 250L);
            running.handler.postDelayed(running.work, 350L);
        }
        return true;
    }

    public static void approve(Context ctx, boolean value) {
        SharedPreferences.Editor edit = ctx.getSharedPreferences(STORE, MODE_PRIVATE).edit()
                .putBoolean("approved", value);
        if (!value) edit.remove("op").remove("expires").remove("phase")
                .remove("preflight_expires").putBoolean("comment_reply_opt_in", false)
                .putString("chat_state", "접근성 동의 철회 · 댓글 자동응답 꺼짐")
                .putString("status", "BIGO 자동화 동의 철회됨");
        edit.apply();
        if (!value) {
            BigoControlAssist service = connectedService.get();
            if (service != null) {
                service.handler.removeCallbacks(service.work);
                service.handler.removeCallbacks(service.expiryWatchdog);
                service.handler.removeCallbacks(service.chatWork);
                service.chatSeen.clear();
                service.chatPrimed = false;
                service.releaseCommentVoice();
            }
        }
    }

    /** Can only be queued by the user's selected MARU schedule after local consent. */
    public static boolean queue(Context ctx, boolean start) {
        if (!approved(ctx) || !connected(ctx)) return false;
        ctx.getSharedPreferences(STORE, MODE_PRIVATE).edit()
                .remove("preflight_expires")
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
        if (running != null) {
            running.handler.removeCallbacks(running.expiryWatchdog);
            running.handler.postDelayed(running.expiryWatchdog, DEADLINE_MS + 250L);
            running.handler.postDelayed(running.work, 400L);
        }
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
        String operation = pref().getString("op", "");
        pref().edit().remove("op").remove("expires").remove("phase")
                .putString("status", message).apply();
        handler.removeCallbacks(work);
        handler.removeCallbacks(expiryWatchdog);
        waitingGesture = false;
        if (!operation.isEmpty() && approved(this)) notifyResult(operation, message);
    }

    private void complete(String message) {
        String operation = pref().getString("op", "");
        pref().edit().remove("op").remove("expires").remove("phase")
                .putString("status", message).apply();
        handler.removeCallbacks(work);
        handler.removeCallbacks(expiryWatchdog);
        waitingGesture = false;
        if (!operation.isEmpty() && approved(this)) notifyResult(operation, message);
    }

    private void notifyResult(String operation, String message) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(STATUS_CHANNEL,
                "MARU BIGO 자동 방송 결과 확인", NotificationManager.IMPORTANCE_HIGH));
        Intent show = new Intent(this, RadioAutomationActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, STATUS_NOTIFICATION,
                show, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String title = "start".equals(operation)
                ? "BIGO 방송 시작 상태 확인 필요"
                : "BIGO 방송 종료 상태 확인 필요";
        Notification note = new Notification.Builder(this, STATUS_CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message
                        + "\nBIGO의 실제 서버 상태와 시청자 음악 송출은 별도 확인이 필요합니다."))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ERROR)
                .build();
        manager.notify(STATUS_NOTIFICATION, note);
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        connectedService = new WeakReference<>(this);
        pref().edit().putBoolean("connected", true).apply();
        mark("접근성 기능 연결됨 (자동 조작은 별도 동의 후에만 허용)");
    }

    @Override public boolean onUnbind(Intent i) {
        handler.removeCallbacks(work);
        handler.removeCallbacks(expiryWatchdog);
        handler.removeCallbacks(chatWork);
        pendingPublicChatEvent = null;
        chatSeen.clear();
        chatPrimed = false;
        hostToolsObservedAt = 0L;
        releaseCommentVoice();
        connectedService = new WeakReference<>(null);
        pref().edit().putBoolean("connected", false)
                .remove("op").remove("expires").remove("phase")
                .putString("status", "접근성 서비스가 꺼졌어요.").apply();
        return super.onUnbind(i);
    }

    @Override public void onInterrupt() {
        handler.removeCallbacks(chatWork);
        pendingPublicChatEvent = null;
        chatSeen.clear();
        chatPrimed = false;
        releaseCommentVoice();
        abort("자동 조작 중단: BIGO 상태 확인 필요");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getPackageName() == null
                || !BIGO.contentEquals(e.getPackageName()) || !approved(this)) return;
        if (commentReplies(this) && RadioDjSettings.isVoiceEnabled(this)) {
            // Some BIGO builds expose an accessibility event on a visible
            // public comment row but omit its text from later tree scans.
            // Accept only labeled, non-editable chat widgets in the BIGO
            // package; defer host-LIVE verification to checkChat().
            AccessibilityNodeInfo source = e.getSource();
            if (source != null && source.isVisibleToUser()
                    && source.getPackageName() != null
                    && BIGO.contentEquals(source.getPackageName())
                    && source.getClassName() != null
                    && !source.getClassName().toString().contains("EditText")) {
                String resource = source.getViewIdResourceName();
                CharSequence description = source.getContentDescription();
                String labeled = description == null ? null : description.toString();
                if (BigoChatRule.isChatWidget(resource, labeled)
                        && e.getText() != null) {
                    for (CharSequence candidateText : e.getText()) {
                        if (candidateText == null) continue;
                        BigoChatRule.Match parsed = BigoChatRule.match(resource,
                                labeled, candidateText.toString());
                        if (parsed != null) pendingPublicChatEvent = parsed;
                    }
                }
            }
            if (Build.VERSION.SDK_INT < 29 || !handler.hasCallbacks(chatWork))
                handler.postDelayed(chatWork, 550L);
        }
        if (!pref().contains("op") && !pref().contains("preflight_expires")) return;
        // BIGO may generate frequent UI events (e.g. animation). Replacing the
        // delay on every event starves automation forever. Coalesce instead.
        if (android.os.Build.VERSION.SDK_INT >= 29 && handler.hasCallbacks(work)) return;
        handler.removeCallbacks(work);
        handler.postDelayed(work, 250L);
    }

    private void releaseCommentVoice() {
        if (scopedCommentVoice != null) {
            scopedCommentVoice.close();
            scopedCommentVoice = null;
        }
    }

    private void sayLocalComment(String reply, int language) {
        // A media foreground service launched from background can be refused
        // on Android 12+/One UI while BIGO is foreground. Speak directly from
        // this already-bound opt-in service instead. Local TTS != BIGO ingest.
        if (scopedCommentVoice == null) scopedCommentVoice = new RadioDjVoice(this);
        scopedCommentVoice.say(reply, language, () -> {
            // This is a LOCAL engine callback, not an acknowledgement from BIGO.
            if (!commentReplies(this)) return;
            String engine = scopedCommentVoice == null ? "음성 재생 상태 확인 불가"
                    : scopedCommentVoice.status();
            setChatState("아환 로컬 음성 처리 종료 · " + engine
                    + " · BIGO 청취자 음성은 별도 확인 필요");
        });
    }

    private void setChatState(String message) {
        // Keep only fixed status strings; never store a viewer's comment.
        pref().edit().putString("chat_state", message).apply();
    }

    private void checkChat() {
        if (!approved(this) || !commentReplies(this)) {
            setChatState("댓글 감시 OFF · BIGO 댓글 읽기 동의가 필요합니다.");
            return;
        }
        if (!RadioDjSettings.isVoiceEnabled(this)) {
            setChatState("아환 음성 OFF · 아나운서 음성 사용을 켜야 합니다.");
            return;
        }
        if (pref().contains("op") || pref().contains("preflight_expires")) {
            setChatState("BIGO 시작·종료 화면 조작 또는 점검 중 · 댓글 응답 잠시 보류");
            return;
        }
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            chatPrimed = false;
            chatSeen.clear();
            setChatState("화면 잠금 · BIGO 댓글을 읽을 수 없음");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !BIGO.contentEquals(root.getPackageName())) {
            chatPrimed = false;
            pendingPublicChatEvent = null;
            chatSeen.clear();
            setChatState("BIGO 화면의 공개 댓글 접근 불가 · BIGO 방송 화면이 전면에 표시되어야 합니다.");
            return;
        }
        java.util.Set<String> labels = allLabels(root);
        if (BigoAutomationPolicy.isHostToolsSheet(labels)) {
            hostToolsObservedAt = android.os.SystemClock.elapsedRealtime();
            chatPrimed = false;
            pendingPublicChatEvent = null;
            chatSeen.clear();
            setChatState("BIGO 실제 호스트 라이브 툴 확인 · 창을 닫으면 "
                    + "실방송 자리 화면의 공개 댓글을 감지합니다. 청취자 TTS 출력은 미연결");
            return;
        }
        if (BigoAutomationPolicy.isSetup(labels)
                || BigoAutomationPolicy.isEndConfirmation(labels)) {
            hostToolsObservedAt = 0L;
        }
        // Do NOT lower the test used to automate BIGO's broadcast start/end.
        // The weak layout is a chat-read hint only after a recent host-exclusive
        // controls sheet; no UI click, DM access or raw chat logging added.
        if (!BigoHostChatGate.mayReadPublicChat(labels, hostToolsObservedAt,
                android.os.SystemClock.elapsedRealtime())) {
            chatPrimed = false;
            pendingPublicChatEvent = null;
            chatSeen.clear();
            setChatState("BIGO 호스트 LIVE 확인 필요 · 먼저 방송 중 '라이브 툴'을 "
                    + "한 번 열었다 닫아 주세요. 댓글·사적인 대화는 저장하지 않습니다.");
            return;
        }
        // The first observed screen may contain old comments. Prime only and
        // reply to NEW matching rows on later accessibility content changes.
        java.util.ArrayList<BigoChatRule.Match> candidates = new java.util.ArrayList<>();
        // A public chat event is only trusted after verifying the current
        // host LIVE root; only the bounded reply + fingerprint survives here.
        BigoChatRule.Match eventMatch = pendingPublicChatEvent;
        pendingPublicChatEvent = null;
        if (eventMatch != null && !chatSeen.contains(eventMatch.transientFingerprint))
            candidates.add(eventMatch);
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        int scanned = 0;
        int taggedWidgets = 0;
        while (!queue.isEmpty() && scanned++ < 800) {
            AccessibilityNodeInfo n = queue.removeFirst();
            if (n == null) continue;
            if (n.isVisibleToUser() && n.getPackageName() != null
                    && BIGO.contentEquals(n.getPackageName())) {
                CharSequence desc = n.getContentDescription();
                CharSequence row = n.getText();
                boolean taggedChat = BigoChatRule.isChatWidget(
                        n.getViewIdResourceName(),
                        desc == null ? null : desc.toString());
                if (taggedChat) {
                    taggedWidgets++;
                    // Some BIGO versions label the row CONTAINER, while its
                    // text is on an immediate child with no view resource ID.
                    // Read only a directly labeled chat row and its immediate
                    // children. Never look into profiles, DMs or untagged trees.
                    BigoChatRule.Match reply = BigoChatRule.match(
                            n.getViewIdResourceName(),
                            desc == null ? null : desc.toString(),
                            row == null ? null : row.toString());
                    if (reply != null && !chatSeen.contains(reply.transientFingerprint))
                        candidates.add(reply);
                    if (reply == null) {
                        java.util.ArrayList<String> childTexts = new java.util.ArrayList<>();
                        for (int ci = 0; ci < Math.min(8, n.getChildCount()); ci++) {
                            AccessibilityNodeInfo child = n.getChild(ci);
                            if (child == null) continue;
                            if (!child.isVisibleToUser() || child.getPackageName() == null
                                    || !BIGO.contentEquals(child.getPackageName()))
                                continue;
                            CharSequence text = child.getText();
                            CharSequence childDesc = child.getContentDescription();
                            String part = text != null ? text.toString()
                                    : childDesc == null ? "" : childDesc.toString();
                            if (!part.trim().isEmpty()) childTexts.add(part.trim());
                            reply = BigoChatRule.match(n.getViewIdResourceName(),
                                    desc == null ? null : desc.toString(),
                                    text != null ? text.toString()
                                            : childDesc == null ? null : childDesc.toString());
                            if (reply != null && !chatSeen.contains(reply.transientFingerprint))
                                candidates.add(reply);
                        }
                        // A recognized chat container can have [nickname, message]
                        // as separate direct children. Never merge unrelated nodes.
                        if (childTexts.size() == 2) {
                            BigoChatRule.Match split = BigoChatRule.matchSplitChat(
                                    n.getViewIdResourceName(),
                                    desc == null ? null : desc.toString(),
                                    childTexts.get(0), childTexts.get(1));
                            if (split != null && !chatSeen.contains(split.transientFingerprint))
                                candidates.add(split);
                        }
                    }
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        for (BigoChatRule.Match c : candidates) {
            chatSeen.add(c.transientFingerprint);
            if (chatSeen.size() > 80) {
                Integer earliest = chatSeen.iterator().next();
                chatSeen.remove(earliest);
            }
        }
        if (!chatPrimed) {
            chatPrimed = true;
            setChatState(taggedWidgets == 0
                    ? "호스트 LIVE 확인 · 댓글 위젯 미노출 (실기기 확인 필요)"
                    : "BIGO 댓글 위젯 " + taggedWidgets
                        + "개 인식 · 새 댓글 자동 음성답변 대기");
            return;
        }
        // Max one short fixed-script reply per 15 seconds; drop extras
        // so music and listeners aren't overwhelmed by voice announcements.
        long now = System.currentTimeMillis();
        if (candidates.isEmpty()) {
            if (taggedWidgets == 0)
                setChatState("BIGO 호스트 화면은 확인됐지만 공개 댓글 UI 식별자 0개 · BIGO 버전/화면 접근성 제한 가능");
            return;
        }
        if (now - chatLastHandledAt < 15000L) {
            setChatState("댓글 형태 인식됨 · 답변 간격 15초 제한 중");
            return;
        }
        BigoChatRule.Match match = candidates.get(candidates.size() - 1);
        boolean directRtmp = RadioDirectRtmpService.isActive();
        try {
            if (directRtmp) {
                // Optional authorized RTMP can mix TTS PCM into the stream;
                // the owner's normal BIGO Audio LIVE has no provided RTMP key.
                startService(new Intent(this, RadioDirectRtmpService.class)
                        .setAction(RadioDirectRtmpService.ACTION_SAY)
                        .putExtra(RadioDirectRtmpService.EXTRA_SAY, match.reply)
                        .putExtra(RadioDirectRtmpService.EXTRA_LANGUAGE, match.language));
            } else {
                // Crucial repair: do not start RadioPlaybackService as an
                // Android 12+ background foreground service just for a reply.
                sayLocalComment(match.reply, match.language);
            }
            chatLastHandledAt = now;
            setChatState("새 댓글 인식 · 1개 응답 처리 / "
                    + (directRtmp ? "RTMP PCM 요청" : "휴대폰 로컬 TTS 요청")
                    + " · BIGO 실제 청취 음성 미검증");
        } catch (RuntimeException denied) {
            setChatState("댓글은 감지했지만 안드로이드가 음성 실행을 차단함 · "
                    + "녹음·앱 권한이 아니라 출력 실행 경로 오류");
        }
    }

    private void evaluate() {
        if (!approved(this) || waitingGesture || actionInProgress) return;
        if (pref().contains("preflight_expires") && !pref().contains("op")) {
            runPreflight();
            return;
        }
        String op = pref().getString("op", "");
        // Android/OneUI screen lock is not a legal unattended UI surface.
        // Stop rather than spoof, dismiss or bypass the keyguard.
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            if (!op.isEmpty()) abort("휴대폰 잠금 상태: BIGO 자동 시작/종료를 중단했어요.");
            return;
        }
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

    private void checkExpiration() {
        long now = System.currentTimeMillis();
        String operation = pref().getString("op", "");
        if (!operation.isEmpty() && now >= pref().getLong("expires", 0L)) {
            abort("90초 안에 BIGO 화면 자동화가 완료되지 않았어요. "
                    + "방송 실제 시작·종료 상태를 확인해 주세요.");
            return;
        }
        long preflight = pref().getLong("preflight_expires", 0L);
        if (preflight > 0L && now >= preflight) {
            pref().edit().remove("preflight_expires")
                    .putString("status", "BIGO 읽기 전용 화면 점검 60초 시간 초과").apply();
        }
    }

    private void runPreflight() {
        long expiry = pref().getLong("preflight_expires", 0L);
        if (expiry <= 0L) return;
        if (System.currentTimeMillis() > expiry) {
            pref().edit().remove("preflight_expires")
                    .putString("status", "BIGO 화면 점검 60초 시간 초과 (방송 조작 없음)")
                    .apply();
            return;
        }
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            pref().edit().remove("preflight_expires")
                    .putString("status", "잠금 상태: BIGO 화면 읽기를 진행하지 않았어요.")
                    .apply();
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !BIGO.contentEquals(root.getPackageName())) return;
        Set<String> labels = allLabels(root);
        int ranged = countRangedSeekbars(root);
        boolean startBtn = find(root, "방송하기") != null;
        boolean mixerBtn = find(root, "믹서") != null;
        boolean original = find(root, "Original") != null
                || find(root, "원본") != null;
        // BIGO's own music/BGM input is the only plausible phone-only
        // integration path until an official cross-app audio API is available.
        // Detection is read-only and DOES NOT load or play any music.
        boolean bigoBgm = BigoAutomationPolicy.has(labels, "BGM")
                || BigoAutomationPolicy.has(labels, "배경 음악")
                || BigoAutomationPolicy.has(labels, "내 음악")
                || BigoAutomationPolicy.has(labels, "음악 추가");
        // Only recognized booleans and slider COUNT are persisted.
        // Raw labels, room name, messages, viewers and contact info are discarded.
        String report = "BIGO 화면 안전 점검 (읽기 전용): "
                + (BigoAutomationPolicy.isSetup(labels) ? "방송 준비 인식" : "준비 화면 미확인")
                + " / " + (BigoAutomationPolicy.isMixer(labels) ? "믹서 인식" : "믹서 미확인")
                + " / " + (micLevelOneVerified(root, labels) ? "마이크 1 표시" : "마이크 1 미확인")
                + " / " + (mixerBtn ? "믹서 버튼 감지" : "믹서 버튼 미감지")
                + " / " + (startBtn ? "방송하기 감지" : "방송하기 미감지")
                + " / " + (original ? "Original 감지" : "Original 미감지")
                + " / 값 설정 가능한 슬라이더 " + ranged + "개"
                + " / BIGO 내부 BGM: " + (bigoBgm ? "메뉴 감지" : "메뉴 미감지")
                + " (BGM은 BIGO 자체 음원 입력이며 MARU 음악 연결은 검증 안 됨)"
                + " (실제 방송 시작·종료 및 송출은 검증 안 됨)";
        pref().edit().remove("preflight_expires").putString("status", report).apply();
        handler.removeCallbacks(expiryWatchdog);
    }

    private int countRangedSeekbars(AccessibilityNodeInfo root) {
        int n = 0, visited = 0;
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        while (!nodes.isEmpty() && visited++ < 1800) {
            AccessibilityNodeInfo node = nodes.removeFirst();
            if (node == null) continue;
            if (node.isVisibleToUser() && node.isEnabled()
                    && node.getClassName() != null
                    && node.getClassName().toString().contains("SeekBar")
                    && node.getRangeInfo() != null) n++;
            for (int k = 0; k < node.getChildCount(); k++) {
                AccessibilityNodeInfo child = node.getChild(k);
                if (child != null) nodes.addLast(child);
            }
        }
        return n;
    }

    /** Do not confuse some unrelated BIGO "1" with the MIC volume. */
    private boolean micLevelOneVerified(AccessibilityNodeInfo root, Set<String> labels) {
        if (!BigoAutomationPolicy.isMixer(labels)) return false;
        AccessibilityNodeInfo mic = find(root, "마이크 볼륨");
        if (mic == null || !mic.isVisibleToUser()) return false;
        Rect title = new Rect();
        mic.getBoundsInScreen(title);
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        int count = 0;
        int matched = 0;
        while (!nodes.isEmpty() && count++ < 1800) {
            AccessibilityNodeInfo node = nodes.removeFirst();
            if (node == null) continue;
            if (node.isVisibleToUser()
                    && (node.getText() != null
                            && "1".equals(BigoAutomationPolicy.normalize(node.getText().toString()))
                        || node.getContentDescription() != null
                            && "1".equals(BigoAutomationPolicy.normalize(
                                    node.getContentDescription().toString())))) {
                Rect digit = new Rect();
                node.getBoundsInScreen(digit);
                if (BigoMicLayoutPolicy.isNearMic(width, height,
                        title.left, title.top, title.right, title.bottom,
                        digit.left, digit.top, digit.right, digit.bottom)) {
                    matched++;
                    if (matched > 1) return false; // ambiguous mixer values
                }
            }
            for (int n = 0; n < node.getChildCount(); n++) {
                AccessibilityNodeInfo child = node.getChild(n);
                if (child != null) nodes.addLast(child);
            }
        }
        return matched == 1;
    }

    private boolean isOriginalAlreadySelected(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo selected = find(root, "Original");
        if (selected == null) selected = find(root, "원본");
        if (selected == null || !selected.isVisibleToUser()) return false;
        // The original audio effect is already active in the submitted screenshot.
        // Require BIGO to expose actual selected-state, not visual guesswork.
        for (AccessibilityNodeInfo p = selected; p != null; p = p.getParent()) {
            if (p.isSelected() || p.isChecked()) return true;
        }
        return false;
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
                // The owner's submitted BIGO mixer screenshot ALREADY shows "1".
                // Avoid changing the slider if it is at the requested value.
                if (micLevelOneVerified(root, labels)) {
                    advance(BigoAutomationPolicy.Phase.SELECT_ORIGINAL,
                            "화면의 마이크 볼륨 1 확인 · 슬라이더 변경 불필요");
                } else if (setOneOnMixerSeekbar(root)) {
                    advance(BigoAutomationPolicy.Phase.SELECT_ORIGINAL, "마이크 값 1 설정 시도");
                } else {
                    abort("마이크 슬라이더를 찾지 못했어요. BIGO 실제 UI 검증 필요.");
                }
                return;
            case SELECT_ORIGINAL:
                if (!BigoAutomationPolicy.isMixer(labels)) return;
                // Verify the actual rendered number from the user screenshot.
                // An accepted ACTION_SET_PROGRESS does not prove BIGO changed it.
                if (!micLevelOneVerified(root, labels)) {
                    abort("마이크 볼륨이 화면에 1로 표시되는지 확인하지 못했어요.");
                    return;
                }
                if (isOriginalAlreadySelected(root)) {
                    advance(BigoAutomationPolicy.Phase.CLOSE_MIXER, "기존 Original 효과 활성화 확인");
                } else if (click(root, "Original") || click(root, "원본")) {
                    advance(BigoAutomationPolicy.Phase.CLOSE_MIXER, "Original 효과 선택");
                } else {
                    abort("Original 활성 상태 또는 버튼을 확인할 수 없어 방송 시작 중단");
                }
                return;
            case CLOSE_MIXER:
                if (BigoAutomationPolicy.isReadyToStart(labels)) {
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
                // Do not press public START while mixer settings are still open.
                if (BigoAutomationPolicy.isMixer(labels)) return;
                if (!BigoAutomationPolicy.isReadyToStart(labels)) {
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
                if (BigoAutomationPolicy.isHostRoomAfterPress(labels))
                    complete("BIGO 방송하기 누른 뒤 호스트 LIVE 화면 전환 확인 · "
                            + "실제 서버 수신·청취자 음악·아환 음성 송출은 미검증");
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
        if (root == null || !BIGO.contentEquals(root.getPackageName())) return false;
        AccessibilityNodeInfo n = find(root, label);
        if (n == null || !n.isVisibleToUser() || !n.isEnabled()) return false;
        // Prefer semantic accessibility actions. BIGO sometimes draws a visual
        // label in a custom view with no clickable Android parent; in that case
        // tap ONLY the exact visible label bounds instead of guessed coordinates.
        for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) {
            if (p.isClickable() && p.isEnabled()
                    && p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
        }
        Rect b = new Rect();
        n.getBoundsInScreen(b);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        if (b.width() < 12 || b.height() < 12 || b.left < 0 || b.top < 0
                || b.right > w || b.bottom > h || w < 400 || h < 700 || w >= h)
            return false;
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        Path touch = new Path();
        touch.moveTo(cx, cy);
        touch.lineTo(cx, cy);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(touch, 0, 90)).build();
        waitingGesture = true;
        boolean requested = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription actual) {
                waitingGesture = false;
            }
            @Override public void onCancelled(GestureDescription actual) {
                waitingGesture = false;
                abort("BIGO " + label + " 버튼 제스처가 취소됐어요.");
            }
        }, handler);
        if (!requested) waitingGesture = false;
        return requested;
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

    // Keep only STATIC BUTTON/SCREEN ANCHORS. Never retain the original text
    // of room names, comments, user names, chat messages or descriptions.
    private static final String[] KNOWN_UI_ANCHORS = {
            "오디오 라이브", "방송하기", "믹서", "마이크 볼륨",
            "1", "Original", "원본", "닫기", "Close", "종료",
            "라이브 종료하기", "추천", "인기", "발견", "홈", "라이브",
            "자리", "시청자", "명 시청", "방송 시간",
            "BGM", "배경 음악", "내 음악", "음악 추가",
            "라이브 툴", "프로그램 목록", "실시간 지원", "관리", "녹화",
            "마이크", "2", "3", "4", "5", "6", "7",
            "8", "9", "10", "11", "12"
    };

    private static void addControlLabel(HashSet<String> recognized, CharSequence text) {
        if (text == null) return;
        String normalized = BigoAutomationPolicy.normalize(text.toString());
        if (normalized.isEmpty()) return;
        for (String anchor : KNOWN_UI_ANCHORS) {
            if (BigoAutomationPolicy.normalize(anchor).equals(normalized)) {
                recognized.add(anchor);
                return;
            }
        }
        if (normalized.equals("마이크") || normalized.equals("mic")
                || normalized.equals("마이크...") || normalized.equals("마이크…")) {
            recognized.add("마이크");
        } else if (normalized.equals("오디오live") || normalized.equals("audiolive")) {
            recognized.add("오디오 라이브");
        } else if (normalized.startsWith("시청자")
                && normalized.length() < 16
                && normalized.substring("시청자".length()).matches("[0-9]{1,6}")) {
            // Store only the generic recognized label, never viewer numbers.
            recognized.add("시청자");
        } else if (normalized.matches("[0-9]{1,6}명시청")) {
            recognized.add("명 시청");
        } else if (normalized.startsWith("방송을종료")
                && normalized.length() <= 28) {
            recognized.add("방송을 종료");
        }
    }

    private Set<String> allLabels(AccessibilityNodeInfo root) {
        HashSet<String> out = new HashSet<>();
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int count = 0;
        while (!q.isEmpty() && count++ < 1800) {
            AccessibilityNodeInfo p = q.poll();
            if (p == null) continue;
            addControlLabel(out, p.getText());
            addControlLabel(out, p.getContentDescription());
            for (int n = 0; n < p.getChildCount(); n++) {
                AccessibilityNodeInfo child = p.getChild(n);
                if (child != null) q.addLast(child);
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
