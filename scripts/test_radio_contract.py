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
    "own package": "applicationId 'com.maru.radio.bigo'" in gradle,
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
    "accessibility restricted to BIGO": "BigoControlAssist" in manifest and
        "android.permission.BIND_ACCESSIBILITY_SERVICE" in manifest and
        'android:exported="true"' in manifest and
        'android:resource="@xml/bigo_control_config"' in manifest,
    "user-initiated start/end": "requestBigoStart()" in activity and
        "BigoControlAssist.queue(this, true)" in activity and
        "BigoControlAssist.queue(this, false)" in activity,
    "no imported legacy modules": all(word not in activity and word not in store for word in (
        "AutoGreetingService", "ScreenOcrGreetingService", "AdaptiveAiStore",
        "OcrTestActivity", "YouTubePlayerActivity")),
}
for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("New MARU RADIO contract failed")
print("Clean app contract: ALL PASS")
