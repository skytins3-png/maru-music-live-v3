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
    "own package": "applicationId 'com.maru.radio.safev16'" in gradle,
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
        "handleStartIntent", "beginBroadcast()", "getLaunchIntentForPackage(\"sg.bigo.live\")", "RadioSchedule.set(this, pendingStartAt)")),
    "five-language copy-only replies": "RadioReplies.message(language, event)" in (root / "radio/src/main/java/com/maru/radio/RadioAutomationActivity.java").read_text(encoding="utf-8"),
    "no accessibility service or permission": all(s not in manifest for s in
        ("AccessibilityService", "BIND_ACCESSIBILITY_SERVICE", "BigoControlAssist",
         "@xml/bigo_control_config")),
    "inert accessibility source": "AccessibilityService" not in
        (root / "radio/src/main/java/com/maru/radio/BigoControlAssist.java").read_text(encoding="utf-8")
        .replace("AccessibilityService or", "") and "dispatchGesture" not in
        (root / "radio/src/main/java/com/maru/radio/BigoControlAssist.java").read_text(encoding="utf-8"),
    "BIGO open-only buttons": "requestBigoStart()" in activity and
        "requestBigoEnd()" in activity and "openBigo()" in activity and
        "BigoControlAssist" not in activity,
    "no imported legacy modules": all(word not in activity and word not in store for word in (
        "AutoGreetingService", "ScreenOcrGreetingService", "AdaptiveAiStore",
        "OcrTestActivity", "YouTubePlayerActivity")),
}
for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("New MARU RADIO contract failed")
print("Clean app contract: ALL PASS")
