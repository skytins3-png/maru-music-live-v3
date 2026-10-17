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
    "own package": "applicationId 'com.maru.radio.ahwan'" in gradle,
    "only pinned audited Apache2 RootEncoder SDK": "com.github.pedroSG94.RootEncoder:library:2.8.1" in gradle and "JitPack" not in gradle,
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
    "no audio capture or media read permissions beyond opt-in microphone": all(word not in manifest for word in (
        "MEDIA_PROJECTION", "READ_MEDIA_AUDIO", "READ_EXTERNAL_STORAGE"))
        and 'android.permission.RECORD_AUDIO' in manifest,
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
    "V1.7.2 separate app ID protects prior music": "applicationId 'com.maru.radio.ahwan'" in gradle
        and "applicationId 'com.maru.radio.safev16'" not in gradle
        and "applicationId 'com.maru.radio.safev17'" not in gradle
        and "applicationId 'com.maru.radio.safev171'" not in gradle
        and "applicationId 'com.maru.radio.safev173'" not in gradle,
    "V1.9.14 host mic prerecorded announcement version": "versionCode 28" in gradle
        and "versionName '1.9.14-ahwan-mic-voice-clips'" in gradle
        and "MARU RADIO V1.9.14 아환 음성녹음" in (root / "radio/src/main/res/values/strings.xml").read_text(encoding="utf-8"),
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
    "V1.8 no overlay or background capture permission": "SYSTEM_ALERT_WINDOW" not in manifest
        and "android.permission.RECORD_AUDIO" in manifest
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
    "V1.9 system TTS and separate user-started mic recording, no voice clone":
        "android.speech.tts.TextToSpeech" in dj_voice
        and "getPersistedUriPermissions" not in dj_voice
        and "android.permission.RECORD_AUDIO" in manifest
        and "INTERNET" in manifest,
    "V1.9 user recorded greeting only": all(x in dj_activity for x in (
        "Intent.ACTION_OPEN_DOCUMENT", "takePersistableUriPermission",
        "RadioDjSettings.setVoiceClip(this, uri)", "형의 실제 목소리"))
        and "voice().greeting(" in service
        and "ACTION_DJ_TEST_RECORDED" in dj_activity,
    "V1.9 intersong DJ announcement before actual music": all(x in service for x in (
        "RadioDjSettings.interSong(this)", "RadioDjLanguagePolicy.songIntro(t.title",
        "voice().sayLines(", "p.start()", "deadlineReached()",
        "djHoldingTrack = true;", "djHoldingTrack = false;")),
    "V1.9 requested originals can play without changing genre":
        "ACTION_DJ_REQUEST" in service
        and "queue.add(0, candidate)" in service
        and "RadioProgram.select(this,0)" not in service,
    "V1.9 manual typed comments preserved despite separate optional BIGO listener":
        "comment.getText().toString()" in dj_activity
        and "RadioDjBrain.respond(input, titles," in dj_activity
        and "BigoControlAssist" not in dj_activity[
            dj_activity.index("private void replyToComment()"):
            dj_activity.index("private void requestSong()")],
    "V1.9 explicit local-only limited DJ learning": all(x in dj_settings for x in (
        "isMemoryEnabled(Context ctx)", "rememberSong(Context ctx",
        "host_rules", "known.length() >= 20", "clearMemory(Context ctx)"))
        and 'getSharedPreferences(STORE, Context.MODE_PRIVATE)' in dj_settings
        and "context.getContentResolver()" not in dj_settings,
    "V1.9 explicit external speech input is optional alongside foreground microphone recording": all(x in dj_activity for x in (
        "RecognizerIntent.ACTION_RECOGNIZE_SPEECH",
        "REQUEST_SPOKEN_QUERY",
        "startActivityForResult(speech, REQUEST_SPOKEN_QUERY)",
        "comment.setText(phrases.get(0))"))
        and "android.permission.RECORD_AUDIO" in manifest,
    "V1.9 fail-fast unavailable system TTS": all(x in dj_voice for x in (
        "private boolean initializationFailed;",
        "else if (initializationFailed) complete(ticket)",
        "initializationFailed = !ready;")),
    "V1.9 five-language TTS": all(x in dj_voice for x in (
        "Locale.SIMPLIFIED_CHINESE", "Locale.JAPANESE",
        'new Locale("ru", "RU")', "pendingLanguage")),
})

# The announcer now speaks only genres' requested languages:
# KR=Korean+English, CN=Chinese+English, Western/Instrumental=English.
# Start/end always English, gifts sender language with English fallback.
language_policy = (root / "radio/src/main/java/com/maru/radio/RadioDjLanguagePolicy.java").read_text(encoding="utf-8")
checks.update({
    "V1.9.2 event-based short announcer": all(x in language_policy for x in (
        "public static List<Line> songIntro(", "case 1:", "case 2:",
        "new Line(KOREAN", "new Line(CHINESE", "new Line(ENGLISH",
        "public static List<Line> opening()", "public static List<Line> closing()")),
    "V1.9.2 TTS changes language per segment": all(x in dj_voice for x in (
        "public void sayLines(", "private void speakLine(",
        "pendingLanguage = Math.max(", "tts.setLanguage(voices[index])")),
    "V1.9.2 gift recipient language unknown defaults English": all(x in language_policy for x in (
        "giftLanguage(String comment, int manualHint)",
        "return ENGLISH; // unknown/default", "public static List<Line> gift(")),
    "V1.9.2 broadcast start and closing English":
        "RadioDjLanguagePolicy.opening()" in service
        and "RadioDjLanguagePolicy.closing()" in service
        and "ACTION_DJ_CLOSING" in dj_activity,
    "V1.9.2 no five-language fixed rotation on air":
        "RadioDjSettings.language(this)" not in service
        and "RadioDjLanguagePolicy.songIntro(" in service,
    "V1.9.2 gift sender language can be overridden":
        "RadioDjSettings.giftLanguageHint(this)" in dj_activity
        and "RadioDjSettings.setGiftLanguageHint(" in dj_activity
        and "ACTION_DJ_GIFT" in dj_activity,
})

# Store release is NOT an internet-sideload APK; keep accessibility documented,
# narrow and deterministic, with explicit user consent and proper Play review.
access_xml = (root / "radio/src/main/res/xml/bigo_control_config.xml").read_text(encoding="utf-8")
checks.update({
    "V1.9.4 Android 16 Play target":
        "targetSdk 36" in gradle,
    "V1.9.4 review requires owner-managed upload signing":
        'System.getenv("MARU_UPLOAD_KEYSTORE_PATH")' in gradle
        and 'System.getenv("MARU_UPLOAD_STORE_PASSWORD")' in gradle
        and 'System.getenv("MARU_UPLOAD_KEY_ALIAS")' in gradle
        and 'System.getenv("MARU_UPLOAD_KEY_PASSWORD")' in gradle,
    "V1.9.4 narrow BIGO package only":
        'android:packageNames="sg.bigo.live"' in access_xml
        and 'android:isAccessibilityTool="false"' in access_xml,
    "V1.9.4 explicit accessibility informed consent":
        'BigoControlAssist.approve(this, true)' in automation
        and 'BigoControlAssist.approve(this, false)' in automation,
    "V1.9.4 Android App Bundle smoke build":
        ":radio:bundleRelease" in (root / ".github/workflows/build-radio.yml").read_text(encoding="utf-8"),
})
checks.update({
    "V1.9.5 recorded voice flag is explicit and off by default":
        "public static boolean recordedOpening(Context ctx)" in dj_settings
        and 'getBoolean("recorded_opening", false)' in dj_settings
        and "setRecordedOpening(Context ctx, boolean enabled)" in dj_settings,
    "V1.9.5 opening uses voice recording with English TTS fallback":
        "private void playBroadcastOpening(Runnable after)" in service
        and "RadioDjSettings.recordedOpening(this)" in service
        and "voice().greeting(RadioDjLanguagePolicy.opening().get(0).text, after)" in service
        and "playBroadcastOpening(next)" in service
        and "playBroadcastOpening(afterSpeech)" in service,
    "V1.9.5 owner explicitly authorizes language of chosen clip":
        "영어 녹음 · 자동 시작에 사용" in dj_activity
        and "setRecordedOpening(this, true)" in dj_activity
        and "useOpening.setChecked(true)" in dj_activity,
    "V1.9.5 local recorded clip status and preview":
        "updateGreetingStatus()" in dj_activity
        and "녹음 인사가 아직 등록되지 않았어" in dj_activity
        and "Intent.ACTION_OPEN_DOCUMENT" in dj_activity,
    "V1.9.5 recorded clip media failure falls back to TTS":
        "fallbackRecorded(ticket, fallback, language, after)" in dj_voice
        and "say(fallback, language, after)" in dj_voice,
})
checks.update({
    "V1.9.6 balanced audio preset": 'getInt("leveling", RadioLevelPolicy.EVEN)' in program,
    "V1.9.6 attach processing each song": 'leveler.attach(p, RadioProgram.leveling(this))' in service,
    "V1.9.6 release processing on song change": "leveler.release()" in service,
    "V1.9.6 stereo compressor with limiter": all(x in
        (root / "radio/src/main/java/com/maru/radio/RadioLeveler.java").read_text(encoding="utf-8")
        for x in ("DynamicsProcessing", "getAudioSessionId()", "setMbcBandAllChannelsTo",
                  "setLimiterAllChannelsTo", "dsp.release()")),
    "V1.9.6 user profile and viewer caveat": "selectPlaybackLevel" in activity
        and "RadioProgram.setLeveling(" in activity,
})
chat_policy = (root / "radio/src/main/java/com/maru/radio/BigoChatRule.java").read_text(encoding="utf-8")
checks.update({
    "V1.9.7 scoped BIGO comment read-only opt-in":
        "commentReplies(Context ctx)" in assist
        and "setCommentReplies(Context ctx, boolean enabled)" in assist
        and 'getBoolean("comment_reply_opt_in", false)' in assist
        and "BigoAutomationPolicy.isHostLive(allLabels(root))" in assist
        and "BIGO.contentEquals(root.getPackageName())" in assist,
    "V1.9.7 limits chat rate and discards old events":
        "chatPrimed" in assist and "chatSeen.size() > 80" in assist
        and "15000L" in assist and "chatSeen.clear()" in assist,
    "V1.9.7 no hidden AI chat upload":
        "HttpURLConnection" not in chat_policy
        and "SharedPreferences" not in chat_policy
        and "ACTION_DJ_AUTO_COMMENT" in service,
    "V1.9.7 no private chat storage, fixed status only":
        'putString("chat_state", message)' in assist
        and "transientFingerprint" in chat_policy,
    "V1.9.7 local Ahwan TTS for BIGO greeting/gift":
        "ACTION_DJ_AUTO_COMMENT" in service
        and "RadioDjLanguagePolicy.gift(" in chat_policy
        and "BigoChatRule.match(" in assist,
    "V1.9.7 separate explicit user agreement":
        "BIGO 댓글 화면 읽기 별도 동의" in dj_activity
        and "BigoControlAssist.setCommentReplies(this, true)" in dj_activity
        and "BigoControlAssist.setCommentReplies(this, false)" in dj_activity,
    "V1.9.7 Android audio capturable but not microphone injection":
        'android:allowAudioPlaybackCapture="true"' in manifest
        and "setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)" in service
        and "setAllowedCapturePolicy" in dj_voice,
    "V1.9.7 viewer sound checklist disclosure":
        "BIGO 청취자 실제 소리 경로 점검" in dj_activity
        and "자동 입력되지 않습니다" in dj_activity,
})
rtmp_service = (root / "radio/src/main/java/com/maru/radio/RadioDirectRtmpService.java").read_text(encoding="utf-8")
rtmp_activity = (root / "radio/src/main/java/com/maru/radio/RadioDirectRtmpActivity.java").read_text(encoding="utf-8")
checks.update({
    "V1.9.8 user-supplied BIGO RTMP credentials remain private in memory":
        "EXTRA_SERVER" in rtmp_service
        and "EXTRA_KEY" in rtmp_service
        and "intent.removeExtra(EXTRA_KEY)" in rtmp_service
        and "key.setText(\"\")" in rtmp_activity
        and "FLAG_SECURE" in rtmp_activity,
    "V1.9.8 RTMP stream uses file decoder only, no mic/camera source":
        "AudioFileSource" in rtmp_service
        and "BitmapSource" in rtmp_service
        and "playAudioDevice();" not in rtmp_service
        and "RadioRtmpLevelAudioSource" in rtmp_service
        and "MicrophoneSource" not in rtmp_service
        and "android.permission.CAMERA" not in manifest,
    "V1.9.8 foreground service with direct transport":
        'android:name=".RadioDirectRtmpService"' in manifest
        and "android.permission.INTERNET" in manifest
        and "FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK" in rtmp_service
        and "new GenericStream(" in rtmp_service
        and "stream.startStream(endpoint)" in rtmp_service
        and "audioFile" not in rtmp_service,
    "V1.9.8 fails closed without own stream key":
        "if (!valid(server, key))" in rtmp_service
        and "BIGO 공식" in rtmp_activity
        and "getHost()" in rtmp_service,
    "V1.9.8 honest receiver audio status":
        "실제 BIGO 청취 수신은 별도 기기로 확인" in rtmp_activity
        and "청취자 확인 필요" in rtmp_service
        and "new RadioRtmpTtsBridge" in rtmp_service
        and "RadioRtmpPcmVoiceMixer" in (root / "radio/src/main/java/com/maru/radio/RadioRtmpLevelAudioSource.java").read_text(encoding="utf-8"),
})
# Entire imported music catalog screen: no broadcast-genre filter by default.
all_songs = (root / "radio/src/main/java/com/maru/radio/RadioAllSongsActivity.java").read_text(encoding="utf-8")
checks.update({
    "All registered originals are listed independently from radio program filter":
        'all.addAll(Originals.list(this))' in all_songs
        and 'private int genre = 0' in all_songs
        and 'RadioProgram.active(this)' not in all_songs
        and 'visible.add(item)' in all_songs
        and 'getCount() { return visible.size(); }' in all_songs,
    "Full song titles, count, search, category, sorting, playback":
        'title.setMaxLines(Integer.MAX_VALUE)' in all_songs
        and 'all.size()' in all_songs
        and 'private void clearFilter()' in all_songs
        and 'private void chooseGenre()' in all_songs
        and 'private void chooseSort()' in all_songs
        and 'RadioPlaybackService.ACTION_PLAY' in all_songs,
    "Song library screen reachable and locally scoped":
        '.RadioAllSongsActivity' in manifest
        and 'android:exported="false"' in manifest
        and 'this::openAllSongs' in activity
        and 'new Intent(this, RadioAllSongsActivity.class)' in activity,
    "Ahwan presenter is visible in all-songs screen and in main":
        '🎙 AI 아나운서 아환' in all_songs
        and 'new Intent(this, RadioDjActivity.class)' in all_songs
        and 'AI 아나운서 아환 설정 열기' in activity
        and '.RadioDjActivity' in manifest,
})

# The main list must never silently shrink when the broadcast program is
# changed: a user had 152 stored originals but only 34 shown in "Western".
feature_menu = (root / "radio/src/main/java/com/maru/radio/RadioFeatureMenuActivity.java").read_text(encoding="utf-8")
checks.update({
    "All 152-style imported songs visible regardless of active broadcast category":
        'allTracks.addAll(Originals.list(this))' in activity
        and 'for (Originals.Track t : sorted)' in activity
        and 'RadioProgram.category(this, t) != activeCategory' not in activity,
    "Complete menu entry and routing from main, library and Ahwan":
        '.RadioFeatureMenuActivity' in manifest
        and 'this, RadioFeatureMenuActivity.class' in activity
        and 'this, RadioFeatureMenuActivity.class' in all_songs
        and 'this, RadioFeatureMenuActivity.class' in dj_activity
        and 'EXTRA_MENU_ACTION' in activity
        and 'private void routeMenuAction(Intent intent)' in activity,
    "Complete menu retains all dedicated activity destinations":
        'RadioAllSongsActivity.class' in feature_menu
        and 'RadioDjActivity.class' in feature_menu
        and 'RadioAutomationActivity.class' in feature_menu
        and 'RadioDirectRtmpActivity.class' in feature_menu,
    "Complete menu routes all basic music, DJ, broadcast actions":
        all(s in feature_menu for s in (
            'mainAction("player")', 'mainAction("download")',
            'mainAction("folder")', 'mainAction("song_manage")',
            'mainAction("genre")', 'mainAction("timer")',
            'mainAction("cancel_timer")', 'mainAction("level")',
            'mainAction("program")', 'mainAction("bigo_start")',
            'mainAction("bigo_end")', 'mainAction("bigo_open")',
            'mainAction("sound_test")', 'mainAction("suno")')),
    "No hidden automatic go-live from feature directory":
        'RadioDirectRtmpService.ACTION_START' not in feature_menu
        and 'startService(' not in feature_menu,
})

# Broadcast original music PCM must not be affected by phone volume OR
# by an unintended loudness processor. TTS voice ducking is a separate
# intentional exception during actual Ahwan announcements.
network_source = (root / "radio/src/main/java/com/maru/radio/RadioRtmpLevelAudioSource.java").read_text(encoding="utf-8")
checks.update({
    "RTMP music PCM bypasses all auto-volume and device speaker gain":
        'new AudioFileSource(context, source, false, onFinish)' in network_source
        and 'receive.inputPCMData(frame)' in network_source
        and 'musicLeveler' not in network_source
        and 'RadioRtmpPcmLeveler' not in network_source
        and 'setVolume(' not in network_source
        and 'AudioManager' not in network_source,
    "RTMP Ahwan announcements remain optional network PCM overlays":
        'voiceMixer.mix(' in network_source
        and 'queueAnnouncement(short[] monoPcm)' in network_source,
    "Ahwan chat status differentiates consent, accessibility, TTS and stream":
        'boolean consent = BigoControlAssist.commentReplies(this)' in dj_activity
        and 'boolean connected = BigoControlAssist.connected(this)' in dj_activity
        and 'boolean voice = RadioDjSettings.isVoiceEnabled(this)' in dj_activity
        and 'boolean direct = RadioDirectRtmpService.isActive()' in dj_activity,
})

# No microphone/dialog button is necessary for hands-free comment replies.
# Manual text input and owner-training tools remain available in a hidden
# diagnostic drawer, but the opt-in LIVE comment service is independent of it.
checks.update({
    "Ahwan main screen starts in automatic-broadcast-first layout":
        'LinearLayout manualTools = new LinearLayout(this)' in dj_activity
        and 'manualTools.setVisibility(View.GONE)' in dj_activity
        and 'manualTools.addView(comment)' in dj_activity
        and 'manualTools.addView(teachTrigger)' in dj_activity
        and 'manualTools.addView(teachAnswer)' in dj_activity
        and 'manualTools.addView(button("✨ 대화 답변하고 읽어주기"' in dj_activity
        and '방송 중 자동 댓글답변에는 이 화면의 입력/버튼이 필요 없습니다.' in dj_activity,
    "Automatic replies process only explicitly tagged host LIVE rows":
        'BigoAutomationPolicy.isHostLive(allLabels(root))' in assist
        and 'boolean taggedChat = BigoChatRule.isChatWidget(' in assist
        and 'Math.min(8, n.getChildCount())' in assist
        and 'BigoChatRule.match(n.getViewIdResourceName()' in assist,
    "Automatic replies require consent and voice and run without new background FGS":
        'commentReplies(this)' in assist
        and 'RadioDjSettings.isVoiceEnabled(this)' in assist
        and 'handler.postDelayed(chatWork, 550L)' in assist
        and 'RadioDirectRtmpService.ACTION_SAY' in assist
        and 'sayLocalComment(match.reply, match.language)' in assist
        and 'new RadioDjVoice(this)' in assist
        and 'releaseCommentVoice()' in assist
        and 'RadioPlaybackService.ACTION_DJ_AUTO_COMMENT' not in assist,
})

# Simulated evidence is NOT a live listener. The user must explicitly check
# separate-device output when using ordinary keyless BIGO Audio LIVE.
no_key = (root / "radio/src/main/java/com/maru/radio/BigoNoKeyEvidence.java").read_text(encoding="utf-8")
no_key_ui = (root / "radio/src/main/java/com/maru/radio/RadioBigoNoKeyTestActivity.java").read_text(encoding="utf-8")
menu = (root / "radio/src/main/java/com/maru/radio/RadioFeatureMenuActivity.java").read_text(encoding="utf-8")
dj = (root / "radio/src/main/java/com/maru/radio/RadioDjActivity.java").read_text(encoding="utf-8")
checks.update({
    "No-key BIGO route checks actual second-device music at normal AND zero phone volume":
        's.bigoMusicSelected' in no_key
        and 's.heardAtNormal && s.heardAtMuted' in no_key
        and 's.bigoCommentArrived && s.ahwanHeardRemotely' in no_key,
    "No-key test page defaults to unverified and claims no automatic receiver access":
        'c.setChecked(false)' in no_key_ui
        and '실제 청취자' in no_key_ui
        and 'BigoNoKeyEvidence.complete(x)' in no_key_ui
        and '스트림 키 없는' in no_key_ui,
    "No-key page accessible from main, menu, Ahwan; original data stays unchanged":
        '.RadioBigoNoKeyTestActivity' in manifest
        and 'new Intent(this, RadioBigoNoKeyTestActivity.class)' in activity
        and 'RadioBigoNoKeyTestActivity.class' in menu
        and 'RadioBigoNoKeyTestActivity.class' in dj
        and 'Originals.list(' not in no_key_ui
        and 'deleteFile(' not in no_key_ui,
    "No-key UI never sends RTMP keys nor fabricates BIGO audio integration":
        'EXTRA_KEY' not in no_key_ui
        and 'RadioDirectRtmpService.ACTION_START' not in no_key_ui
        and 'startService(' not in no_key_ui,
})

# The explicit host microphone only records finite preset clips while the
# Ahwan recording Activity is foreground; there is NO new always-on microphone,
# voice cloning, background permission or authenticated BIGO stream connection.
mic = (root / "radio/src/main/java/com/maru/radio/RadioAhwanRecorderActivity.java").read_text(encoding="utf-8")
slots = (root / "radio/src/main/java/com/maru/radio/RadioAhwanClipSlot.java").read_text(encoding="utf-8")
checks.update({
    "Ahwan microphone room is opt-in and reachable from main, DJ and full menu":
        '.RadioAhwanRecorderActivity' in manifest
        and 'RadioAhwanRecorderActivity.class' in activity
        and 'RadioAhwanRecorderActivity.class' in dj_activity
        and 'RadioAhwanRecorderActivity.class' in menu,
    "Ahwan mic requires explicit foreground user permission and record/stop":
        'requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}' in mic
        and 'recorder.setAudioSource(MediaRecorder.AudioSource.MIC)' in mic
        and 'recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)' in mic
        and 'finishRecording(true)' in mic
        and 'onPause()' in mic
        and 'abandonRecording()' in mic,
    "Ahwan four independent clips with safe backups and explicit activation":
        all(k in slots for k in ('OPENING', 'GIFT', 'FOLLOW', 'CLOSING'))
        and 'backup' in mic
        and 'setRecordedClipUri(this, slot, Uri.fromFile(destination))' in mic
        and 'RadioDjSettings.setClipEnabled(this, slot, enabled)' in mic
        and 'ACTION_CREATE_DOCUMENT' in mic,
    "Ahwan prerecorded clips really dispatch on gift follow closing; opening preserved":
        'voice().playClip(RadioAhwanClipSlot.GIFT' in service
        and 'voice().playClip(RadioAhwanClipSlot.FOLLOW' in service
        and 'voice().playClip(RadioAhwanClipSlot.CLOSING' in service
        and 'voice().greeting(' in service
        and 'RadioDjSettings.recordedOpening(this)' in service
        and 'playStored(' in dj_voice,
    "Ahwan microphone recordings are never claimed as arbitrary AI voice clones":
        'voice clone' in mic or '목소리로 만들어' in mic,
})

# Short-circuit the actual Android 12+ service-launch failure while BIGO is
# foreground: use the already bound public chat service only with explicit
# opt-in; never misstate local TTS as remote BIGO mic ingestion.
checks.update({
    "Scoped Ahwan avoids Android background start failure for BIGO auto-comments":
        'private void sayLocalComment(String reply, int language)' in assist
        and 'new RadioDjVoice(this)' in assist
        and 'scopedCommentVoice.say(reply, language' in assist
        and 'startService(spoken)' not in assist
        and 'RadioPlaybackService.ACTION_DJ_AUTO_COMMENT' not in assist,
    "BIGO auto-comment voice resets on revoke/unbind and reports LOCAL-only":
        'running.releaseCommentVoice()' in assist
        and 'service.releaseCommentVoice()' in assist
        and 'BIGO 청취자 음성은' in assist
        and 'Android 접근성에서 MARU BIGO 연결 확인' in dj_activity,
})

for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("New MARU RADIO contract failed")
print("Clean app contract: ALL PASS")
