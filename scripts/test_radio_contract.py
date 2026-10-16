#!/usr/bin/env python3
"""Check that the fresh radio module is NOT the reskinned legacy MARU app."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / "radio/build.gradle").read_text(encoding="utf-8")
manifest = (root / "radio/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
activity = (root / "radio/src/main/java/com/maru/radio/RadioActivity.java").read_text(encoding="utf-8")
store = (root / "radio/src/main/java/com/maru/radio/Originals.java").read_text(encoding="utf-8")
settings = (root / "settings.gradle").read_text(encoding="utf-8")

checks = {
    "own radio module": "include ':radio'" in settings,
    "own package": "applicationId 'com.maru.radio.ahwanv191'" in gradle,
    "no external SDKs": "dependencies {" not in gradle,
    "file picker": "Intent.ACTION_OPEN_DOCUMENT" in activity,
    "export picker": "Intent.ACTION_CREATE_DOCUMENT" in activity,
    "original file copying": "getContentResolver().openOutputStream(destination)" in activity,
    "BIGO app handoff": "getLaunchIntentForPackage(\"sg.bigo.live\")" in activity,
    "download-first picker": "DocumentsContract.EXTRA_INITIAL_URI" in activity and "primary:Download" in activity,
    "multi-file selection": "Intent.EXTRA_ALLOW_MULTIPLE" in activity,
    "searchable virtual list": "new BaseAdapter()" in activity and "songAdapter.notifyDataSetChanged()" in activity,
    "confirmed bulk delete only of app copies": "Originals.removeMany(" in activity and "new HashSet<>(selected)" in activity,
    "192+ batch import with progress": "Originals.importBatch" in activity and "onProgress" in store,
    "automatic SHA batch dedupe": "hashes.contains(digest)" in store and "save(ctx, tracks)" in store,
    "foreground media service": ".RadioPlaybackService" in manifest and "foregroundServiceType=\"mediaPlayback\"" in manifest,
    "visible player buttons": '"▶ 재생"' in activity and '"⏸ 일시정지"' in activity and "ACTION_NEXT" in activity and "ACTION_PREVIOUS" in activity,
    "play across app transitions": "Do NOT stop foreground music" in activity and "startForegroundService(i)" in activity,
    "seek control": "radioService.seekTo" in activity,
    "picker uses read-only permission": "takePersistableUriPermission" in store and "Intent.FLAG_GRANT_READ_URI_PERMISSION" in store,
    "no 192 downloads copy by default": "retainDownloadDocument" in store and "isDownloadReference()" in activity,
    "fallback preserves provider compatibility": "return copyOriginal(ctx, uri, tracks, hashes)" in store,
    "removal never deletes source downloads": "if (item.isDownloadReference())" in store and "releasePersistableUriPermission" in store,
    "imported bytes hashed": 'MessageDigest.getInstance("SHA-256")' in store,
    "no transcoding": "MediaCodec" not in activity and "MediaCodec" not in store,
    "no recording or capture permissions": all(word not in manifest for word in (
        "RECORD_AUDIO", "MEDIA_PROJECTION",
        "READ_MEDIA_AUDIO", "READ_EXTERNAL_STORAGE")),
    "four song groups": all(n in (root / "radio/src/main/java/com/maru/radio/RadioProgram.java").read_text(encoding="utf-8") for n in ("한국노래", "중국노래", "서양노래", "연주곡")),
    "genre-filtered queue": "RadioProgram.category(this, track) == category" in (root / "radio/src/main/java/com/maru/radio/RadioPlaybackService.java").read_text(encoding="utf-8"),
    "music auto-end service timer": "SystemClock.elapsedRealtime()" in (root / "radio/src/main/java/com/maru/radio/RadioPlaybackService.java").read_text(encoding="utf-8") and "ACTION_SET_TIMER" in activity,
    "music timer only": "RadioProgram.durationMinutes(this)" in activity and
        "EXTRA_END_BIGO" not in activity,
    "one-time clock reminder": "RadioSchedule" in manifest and "POST_NOTIFICATIONS" in manifest and
        "new Intent(this, RadioAutomationActivity.class)" in activity,
    "schedule confirms live start": all(w in (root / "radio/src/main/java/com/maru/radio/RadioAutomationActivity.java").read_text(encoding="utf-8") for w in (
        "handleStartIntent", "beginBroadcast()", "BigoBroadcastGuide.openBigo(this, false)", "RadioSchedule.set(this, pendingStartAt)")),
    "five-language copy-only replies": "RadioReplies.message(language, event)" in (root / "radio/src/main/java/com/maru/radio/RadioAutomationActivity.java").read_text(encoding="utf-8"),
    "experimental BIGO access service only after consent": all(s in manifest for s in
        ("AccessibilityService", "BIND_ACCESSIBILITY_SERVICE", "BigoControlAssist",
         "@xml/bigo_control_config")),
    "accessibility only BIGO package": 'android:packageNames="sg.bigo.live"' in
        (root / "radio/src/main/res/xml/bigo_control_config.xml").read_text(encoding="utf-8"),
    "BIGO open-only buttons": "requestBigoStart()" in activity and
        "requestBigoEnd()" in activity and "openBigo()" in activity and
        "BigoControlAssist" not in activity,
    "no imported legacy modules": all(word not in activity and word not in store for word in (
        "AutoGreetingService", "ScreenOcrGreetingService", "AdaptiveAiStore",
        "OcrTestActivity", "YouTubePlayerActivity")),
}

# V1.7 regression guards: opt-in only, independent timers, safe app handoff.
service = (root / "radio/src/main/java/com/maru/radio/RadioPlaybackService.java").read_text(encoding="utf-8")
automation = (root / "radio/src/main/java/com/maru/radio/RadioAutomationActivity.java").read_text(encoding="utf-8")
program = (root / "radio/src/main/java/com/maru/radio/RadioProgram.java").read_text(encoding="utf-8")
checks.update({
    "V1.7.2 separate app ID protects prior music": "applicationId 'com.maru.radio.ahwanv191'" in gradle
        and "applicationId 'com.maru.radio.safev16'" not in gradle
        and "applicationId 'com.maru.radio.safev17'" not in gradle
        and "applicationId 'com.maru.radio.safev171'" not in gradle
        and "applicationId 'com.maru.radio.safev173'" not in gradle,
    "V1.7.2 correct Android version": "versionCode 14" in gradle
        and "versionName '1.9.1-ahwan-preview'" in gradle
        and "MARU RADIO V1.9.1 아환 아나운서" in (root / "radio/src/main/res/values/strings.xml").read_text(encoding="utf-8"),
    "V1.7.2 exact alarm is explicit user-controlled": "SCHEDULE_EXACT_ALARM" in manifest
        and "canScheduleExactAlarms()" in
        (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8")
        and "Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM" in automation,
    "V1.7.2 background start limited to MARU music": all(
        word in (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8")
        for word in ("setExactAndAllowWhileIdle(", "startForegroundService(play)",
                     "RadioProgram.active(context)", "EXTRA_AUTO_READY", "end_when")),
    "V1.7.2 explicit auto/manual selection": all(word in automation for word in
        ("chooseClock(true)", "chooseClock(false)", "RadioSchedule.setAutomatic(this, pendingStartAt)",
         "RadioSchedule.set(this, pendingStartAt)", "잠겨 있어도")),
    "V1.7.2 reboot rearm only": "RECEIVE_BOOT_COMPLETED" in manifest
        and "android.intent.action.BOOT_COMPLETED" in manifest
        and "restore(context)" in
        (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8"),
    "V1.8 no recording or overlay permission": all(x not in manifest for x in
        ("SYSTEM_ALERT_WINDOW", "RECORD_AUDIO"))
        and "context.stopService(new Intent(context, RadioPlaybackService.class))" in
        (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8"),
    "V1.7 no hidden one-hour default": 'getInt("duration", 0)' in program
        and 'picker.setValue(currentMinutes == 0 ? 5 : currentMinutes)' in activity,
    "V1.7 timer only after explicit choice": 'RadioProgram.setDurationMinutes(this, chosen)' in activity
        and '타이머는 시작하지 않았어' in activity,
    "V1.7 independent deadline fields": "private long minutesDeadline;" in service
        and "private long wallEndAt;" in service
        and "Math.min(durationRemaining, clockRemaining)" in service,
    "V1.7 cancel duration without canceling clock": "minutesDeadline = 0L; // Do not cancel" in service
        and "wallEndAt = 0L; // Do not cancel" in service
        and ".setAction(RadioPlaybackService.ACTION_CANCEL_WALL_END)" in automation,
    "V1.7 clock applies normal playback": "syncWallEndFromSchedule();" in service
        and "ACTION_PLAY.equals(action) || ACTION_DJ_REQUEST.equals(action)" in service
        and "ACTION_NEXT.equals(action)" in service,
    "V1.7 prevent next track after deadline": "private void advanceOnCompletion()" in service
        and "if (deadlineReached())" in service
        and "private void rescheduleTimerCheck()" in service,
    "V1.7 next-day time chooser": "RadioClock.endAfter(now, start, hour, minute)" in automation
        and "RadioSchedule.setEnd(this, pendingEndAt)" in automation,
    "BIGO guided start/end based on user screenshots": all(
        text in (root / "radio/src/main/java/com/maru/radio/BigoBroadcastGuide.java")
                 .read_text(encoding="utf-8") for text in (
        "'믹서'", "마이크 볼륨 1", "Original(원본)", "방송하기",
        "라이브 종료하기", "stopService(new Intent(host, RadioPlaybackService.class))")),
    "BIGO navigation is safe launch-only": all(
        text in (root / "radio/src/main/java/com/maru/radio/BigoBroadcastGuide.java")
                 .read_text(encoding="utf-8") for text in (
        'getLaunchIntentForPackage(BIGO_PACKAGE)', 'host.startActivity(launch)'))
        and "SYSTEM_ALERT_WINDOW" not in manifest,
    "BIGO end notification has open shortcut": "BigoBroadcastGuide.endBigoShortcut(context)" in
        (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8"),
    "V1.8 no privileged media/overlay controls": "SYSTEM_ALERT_WINDOW" not in manifest
        and "dispatchGesture" not in service and "dispatchGesture" not in automation,
})
# V1.7.3 joint scheduling should neither overwrite a selected stop clock nor
# advertise automatic control over BIGO's third-party live session.
clock = (root / "radio/src/main/java/com/maru/radio/RadioClock.java").read_text(encoding="utf-8")
schedule = (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8")
checks.update({
    "V1.7.3 paired clock UI": all(value in automation for value in (
        "choosePair()", "savePair()", "cancelPair()", "ASK_PAIR_NOTIFICATION",
        "시작·종료 한번에 자동 예약", "RadioClock.endAfter(now, pendingStartAt",
        "RadioSchedule.setPair(this, pendingStartAt, pendingEndAt)")),
    "V1.7.3 pair time atomic preferences": all(value in schedule for value in (
        "public static void setPair(", "finishAt <= startAt", ".putLong(\"when\", startAt)",
        ".putLong(\"end_when\", finishAt)", ".putBoolean(\"auto_music\", true)",
        "manager.cancel(alarm(context, false))", "manager.cancel(alarm(context, true))")),
    "V1.7.3 end alarm restored despite start failure":
        schedule.find("if (end > now) arm(context, end, true, false)")
        < schedule.find("if (start > now) arm(context, start, false,"),
    "V1.7.3 calendar calculations pure Java": all(value in clock for value in (
        "nextOccurrence(Calendar now", "endAfter(Calendar now", "Calendar.DAY_OF_MONTH",
        "while (end.getTimeInMillis() <= startAt)")),
    "V1.8 experimental BIGO purpose disclosed": "BIND_ACCESSIBILITY_SERVICE" in manifest
        and "SYSTEM_ALERT_WINDOW" not in manifest
        and "consentBigo()" in automation
        and "BigoBroadcastGuide.showStart(this)" in automation,
})

# Experimental automation is explicitly opt-in and must never silently
# exercise gestures against arbitrary apps or collect chat/private content.
assist = (root / "radio/src/main/java/com/maru/radio/BigoControlAssist.java").read_text(encoding="utf-8")
checks.update({
    "V1.8 bounded consented actions": all(x in assist for x in (
        'getBoolean("approved", false)', '"sg.bigo.live"',
        'DEADLINE_MS = 90_000L', 'return false;',
        'getRootInActiveWindow()', 'performAction(', 'dispatchGesture(')),
    "V1.8 reads BIGO only": 'BIGO.contentEquals(root.getPackageName())' in assist
        and 'BIGO.contentEquals(e.getPackageName())' in assist,
    "V1.8 positive consent and revocation": all(x in automation for x in (
        'private void consentBigo()', 'private void disableBigo()',
        'BigoControlAssist.approve(this, true)',
        'BigoControlAssist.approve(this, false)')),
    "V1.8 scheduler only if opted in": "BigoControlAssist.queue(context, true)" in schedule
        and "BigoControlAssist.queue(context, false)" in schedule,
    "V1.8 requires UI state proof before public start": 'BigoAutomationPolicy.isSetup(labels)' in assist
        and 'BigoAutomationPolicy.isHostLive(labels)' in assist
        and 'BigoAutomationPolicy.isEndConfirmation(labels)' in assist,
})
# Runtime guardrails and audio coexistence must be explicit, never imply that
# media playback is injected into BIGO's microphone or that Android unlocks.
checks.update({
    "V1.8 only stores a whitelist of UI labels": all(x in assist for x in (
        "KNOWN_UI_ANCHORS", "private static void addControlLabel(",
        "addControlLabel(out, p.getText())",
        "addControlLabel(out, p.getContentDescription())",
        'recognized.add("시청자")'))
        and "out.add(p.getText().toString())" not in assist
        and "out.add(p.getContentDescription().toString())" not in assist,
    "V1.8 watchdog when no BIGO window event arrives": all(x in assist for x in (
        "private final Runnable expiryWatchdog = this::checkExpiration;",
        "running.handler.postDelayed(running.expiryWatchdog, DEADLINE_MS + 250L)",
        "private void checkExpiration()", 'getLong("expires", 0L)',
        "abort(\"90초 안에 BIGO 화면 자동화가 완료되지 않았어요.",
        "running.handler.postDelayed(running.expiryWatchdog, PREFLIGHT_MS + 250L)",
        'removeCallbacks(expiryWatchdog)',
        'contains("op")) return false')),
    "V1.8 error and shutdown notifications": all(x in assist for x in (
        "notifyResult(operation, message)", "NotificationManager.IMPORTANCE_HIGH",
        "BIGO 방송 종료 상태 확인 필요",
        "BIGO 방송 시작 상태 확인 필요",
        "POST_NOTIFICATIONS", "manager.notify(STATUS_NOTIFICATION, note)")),
    "V1.8 stop callbacks immediately on revoke": all(x in assist for x in (
        "if (!value) edit.remove(\"op\")",
        "service.handler.removeCallbacks(service.work)")),
    "V1.8 read-only internal BIGO BGM route probing": all(
        phrase in assist for phrase in (
            '"BGM", "배경 음악", "내 음악", "음악 추가"',
            'boolean bigoBgm = BigoAutomationPolicy.has(labels, "BGM")',
            '" / BIGO 내부 BGM: "', '"메뉴 감지" : "메뉴 미감지"')),
    "V1.8 read-only preflight BIGO diagnostics": all(x in assist for x in (
        "public static boolean requestPreflight(Context ctx)",
        "private void runPreflight()", "PREFLIGHT_MS = 60_000L",
        'remove("preflight_expires")', "countRangedSeekbars(root)",
        "Raw labels, room name, messages, viewers and contact info are discarded",
        'BIGO.contentEquals(root.getPackageName())')),
    "V1.8 preflight never triggers broadcast touches": all(x not in
        assist[assist.index("private void runPreflight()"):
               assist.index("private int countRangedSeekbars(")] for x in (
        "dispatchGesture(", "performAction(", "tapXY(", "doStart(", "doEnd(", "queue(")),
    "V1.8 preflight is user opt-in and visible": all(x in automation for x in (
        "private void diagnoseBigo()", "BigoControlAssist.requestPreflight(this)",
        "BIGO 화면 안전 진단", "BigoBroadcastGuide.openBigo(this, false)")),
    "V1.8 preserve chosen Original effects": "isOriginalAlreadySelected(root)" in assist
        and "기존 Original 효과 활성화 확인" in assist,
    "V1.8 avoid stale service connectivity":
        "return connectedService.get() != null;" in assist,
    "V1.8 event flood is coalesced":
        "handler.hasCallbacks(work)" in assist
        and "handler.postDelayed(work, 250L)" in assist,
    "V1.8 alarm never claims confirmed live":
        "bigo_ui_attempted" in automation
        and "bigo_ui_attempted" in schedule
        and "실제 송출 여부" in automation,

    "V1.8 fail closed on screen lock": "km.isKeyguardLocked()" in assist
        and "휴대폰 잠금 상태" in assist,
    "V1.8 avoids mistaken mic=1 from unrelated digit":
        "micLevelOneVerified(root, labels)" in assist
        and "BigoMicLayoutPolicy.isNearMic(" in assist
        and "matched == 1" in assist
        and "isReadyToStart(labels)" in assist,
    "V1.8 Java regression tests for spatial mic confirmation": all(
        x in (root / "radio/src/main/java/com/maru/radio/BigoMicLayoutPolicy.java").read_text(encoding="utf-8")
        for x in ("isNearMic(", "digitLeft >= micRight", "micY + screenHeight"))
        and "BigoMicLayoutPolicy.java" in
        (root / ".github/workflows/build-radio.yml").read_text(encoding="utf-8"),
    "V1.8 safe anchored BIGO label tap":
        "n.getBoundsInScreen(b)" in assist
        and "n.isVisibleToUser()" in assist
        and 'BIGO.contentEquals(root.getPackageName())' in assist,
    "V1.8 opt-in simultaneous audio focus mode":
        'getBoolean("concurrent_audio", false)' in program
        and 'RadioProgram.concurrentAudio(this)' in service
        and 'ACTION_APPLY_AUDIO_COEXIST' in service
        and 'RadioProgram.setConcurrentAudio(this, checked)' in activity
        and "다른 앱으로 소리를 직접 전달하지는 않으며" in activity,
})

# Offline host assistant is an opt-in Android system TTS and explicit
# listener-text input, not an undocumented BIGO chat/voice-cloning integration.
dj_activity = (root / "radio/src/main/java/com/maru/radio/RadioDjActivity.java").read_text(encoding="utf-8")
dj_brain = (root / "radio/src/main/java/com/maru/radio/RadioDjBrain.java").read_text(encoding="utf-8")
dj_voice = (root / "radio/src/main/java/com/maru/radio/RadioDjVoice.java").read_text(encoding="utf-8")
dj_settings = (root / "radio/src/main/java/com/maru/radio/RadioDjSettings.java").read_text(encoding="utf-8")
checks.update({
    "V1.9 offline announcer screen linked": "new Intent(this, RadioDjActivity.class)" in activity
        and ".RadioDjActivity" in manifest
        and "ACTION_DJ_SAY" in service,
    "V1.9 system TTS, no voice clone or microphone recording":
        "android.speech.tts.TextToSpeech" in dj_voice
        and "getPersistedUriPermissions" not in dj_voice
        and "RECORD_AUDIO" not in manifest
        and "INTERNET" not in manifest,
    "V1.9 user recorded greeting only": all(x in dj_activity for x in (
        "Intent.ACTION_OPEN_DOCUMENT", "takePersistableUriPermission",
        "RadioDjSettings.setVoiceClip(this, uri)", "형의 실제 목소리"))
        and "voice().greeting(" in service,
    "V1.9 intersong DJ announcement before actual music": all(x in service for x in (
        "RadioDjSettings.interSong(this)", "RadioDjBrain.nextSong(t.title",
        "voice().say(", "p.start()", "deadlineReached()",
        "djHoldingTrack = true;", "djHoldingTrack = false;")),
    "V1.9 requested originals can play without changing genre":
        "ACTION_DJ_REQUEST" in service
        and "queue.add(0, candidate)" in service
        and "RadioProgram.select(this,0)" not in service,
    "V1.9 user-typed comment only":
        "comment.getText().toString()" in dj_activity
        and "RadioDjBrain.respond(input, titles," in dj_activity
        and "BigoControlAssist" not in dj_activity,
    "V1.9 explicit local-only limited DJ learning": all(x in dj_settings for x in (
        "isMemoryEnabled(Context ctx)", "rememberSong(Context ctx",
        "host_rules", "known.length() >= 20", "clearMemory(Context ctx)"))
        and 'getSharedPreferences(STORE, Context.MODE_PRIVATE)' in dj_settings
        and "context.getContentResolver()" not in dj_settings,
    "V1.9 explicit user speech input with no app microphone permission": all(x in dj_activity for x in (
        "RecognizerIntent.ACTION_RECOGNIZE_SPEECH",
        "REQUEST_SPOKEN_QUERY",
        "startActivityForResult(speech, REQUEST_SPOKEN_QUERY)",
        "comment.setText(phrases.get(0))"))
        and "RECORD_AUDIO" not in manifest,
    "V1.9 fail-fast unavailable system TTS": all(x in dj_voice for x in (
        "private boolean initializationFailed;",
        "else if (initializationFailed) complete(ticket)",
        "initializationFailed = !ready;")),
    "V1.9 five-language TTS": all(x in dj_voice for x in (
        "Locale.SIMPLIFIED_CHINESE", "Locale.JAPANESE",
        'new Locale("ru", "RU")', "RadioDjSettings.language(context)")),
})

for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("New MARU RADIO contract failed")
print("Clean app contract: ALL PASS")
