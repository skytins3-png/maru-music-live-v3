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
    "own package": "applicationId 'com.maru.radio.safev172'" in gradle,
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
        "RECORD_AUDIO", "MEDIA_PROJECTION", "BIND_ACCESSIBILITY_SERVICE",
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
    "no accessibility service or permission": all(s not in manifest for s in
        ("AccessibilityService", "BIND_ACCESSIBILITY_SERVICE", "BigoControlAssist",
         "@xml/bigo_control_config")),
    "inert accessibility source": all(term not in
        (root / "radio/src/main/java/com/maru/radio/BigoControlAssist.java").read_text(encoding="utf-8")
        for term in ("extends AccessibilityService", "dispatchGesture", "onAccessibilityEvent",
                     "import android.accessibilityservice")),
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
    "V1.7.2 separate app ID protects prior music": "applicationId 'com.maru.radio.safev172'" in gradle
        and "applicationId 'com.maru.radio.safev16'" not in gradle
        and "applicationId 'com.maru.radio.safev17'" not in gradle
        and "applicationId 'com.maru.radio.safev171'" not in gradle,
    "V1.7.2 correct Android version": "versionCode 10" in gradle
        and "versionName '1.7.2'" in gradle
        and "MARU RADIO V1.7.2" in (root / "radio/src/main/res/values/strings.xml").read_text(encoding="utf-8"),
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
    "V1.7.2 end only MARU, no BIGO UI automation": all(x not in manifest for x in
        ("BIND_ACCESSIBILITY_SERVICE", "SYSTEM_ALERT_WINDOW", "RECORD_AUDIO"))
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
        and "ACTION_PLAY.equals(action) || ACTION_NEXT.equals(action)" in service,
    "V1.7 prevent next track after deadline": "private void advanceOnCompletion()" in service
        and "if (deadlineReached())" in service
        and "private void rescheduleTimerCheck()" in service,
    "V1.7 next-day time chooser": "chosen.add(Calendar.DAY_OF_MONTH, 1)" in automation
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
        and "BIND_ACCESSIBILITY_SERVICE" not in manifest
        and "SYSTEM_ALERT_WINDOW" not in manifest,
    "BIGO end notification has open shortcut": "BigoBroadcastGuide.endBigoShortcut(context)" in
        (root / "radio/src/main/java/com/maru/radio/RadioSchedule.java").read_text(encoding="utf-8"),
    "V1.7 no new BIGO accessibility controls": "BIND_ACCESSIBILITY_SERVICE" not in manifest
        and "dispatchGesture" not in service and "dispatchGesture" not in automation,
})
for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("New MARU RADIO contract failed")
print("Clean app contract: ALL PASS")
