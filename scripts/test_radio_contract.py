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
    "own package": "applicationId 'com.maru.radio.original'" in gradle,
    "no external SDKs": "dependencies {" not in gradle,
    "file picker": "Intent.ACTION_OPEN_DOCUMENT" in activity,
    "export picker": "Intent.ACTION_CREATE_DOCUMENT" in activity,
    "original file copying": "getContentResolver().openOutputStream(destination)" in activity,
    "BIGO direct handoff only": "getLaunchIntentForPackage(\"sg.bigo.live\")" in activity,
    "imported bytes hashed": 'MessageDigest.getInstance("SHA-256")' in store,
    "no transcoding": "MediaCodec" not in activity and "MediaCodec" not in store,
    "no recording or capture permissions": all(word not in manifest for word in (
        "RECORD_AUDIO", "MEDIA_PROJECTION", "BIND_ACCESSIBILITY_SERVICE",
        "READ_MEDIA_AUDIO", "READ_EXTERNAL_STORAGE")),
    "no imported legacy modules": all(word not in activity and word not in store for word in (
        "AutoGreetingService", "ScreenOcrGreetingService", "AdaptiveAiStore",
        "OcrTestActivity", "YouTubePlayerActivity")),
}
for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("New MARU RADIO contract failed")
print("Clean app contract: ALL PASS")
