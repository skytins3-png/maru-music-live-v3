#!/usr/bin/env python3
"""MARU RADIO V1.9.3: protect music and remove sensitive BIGO UI automation."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "radio/src/main"
read = lambda relative: (root / relative).read_text(encoding="utf-8")
manifest = read("radio/src/main/AndroidManifest.xml")
gradle = read("radio/build.gradle")
strings = read("radio/src/main/res/values/strings.xml")
activity = read("radio/src/main/java/com/maru/radio/RadioActivity.java")
automation = read("radio/src/main/java/com/maru/radio/RadioAutomationActivity.java")
schedule = read("radio/src/main/java/com/maru/radio/RadioSchedule.java")
service = read("radio/src/main/java/com/maru/radio/RadioPlaybackService.java")
store = read("radio/src/main/java/com/maru/radio/Originals.java")
program = read("radio/src/main/java/com/maru/radio/RadioProgram.java")
dj_activity = read("radio/src/main/java/com/maru/radio/RadioDjActivity.java")
dj_voice = read("radio/src/main/java/com/maru/radio/RadioDjVoice.java")
dj_settings = read("radio/src/main/java/com/maru/radio/RadioDjSettings.java")
dj_policy = read("radio/src/main/java/com/maru/radio/RadioDjLanguagePolicy.java")
workflow = read(".github/workflows/build-radio.yml")

checks = {
    "separately installed V1.9.3 app":
        "applicationId 'com.maru.radio.ahwansafev193'" in gradle
        and "versionCode 16" in gradle
        and "versionName '1.9.3-ahwan-safe-preview'" in gradle
        and "MARU RADIO V1.9.3 아환 안전형" in strings,
    "NO accessibility service declaration or permission": all(x not in manifest for x in (
        "AccessibilityService", "BIND_ACCESSIBILITY_SERVICE",
        "BigoControlAssist", "bigo_control_config",
        "android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\"")),
    "NO sensitive accessibility source/config": all(not (src / x).exists() for x in (
        "java/com/maru/radio/BigoControlAssist.java",
        "java/com/maru/radio/BigoAutomationPolicy.java",
        "java/com/maru/radio/BigoMicLayoutPolicy.java",
        "res/xml/bigo_control_config.xml")),
    "NO BIGO automated screen tapping or privacy monitoring": all(
        x not in (service + schedule + activity + automation) for x in (
            "dispatchGesture(", "getRootInActiveWindow", "BigoControlAssist",
            "ACTION_ACCESSIBILITY_SETTINGS", "setOnAccessibilityEventListener")),
    "NO microphone or overlay capture": all(x not in manifest for x in (
        "RECORD_AUDIO", "SYSTEM_ALERT_WINDOW", "READ_MEDIA_AUDIO",
        "READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "INTERNET")),
    "preserve BIGO app launch-only": 'getLaunchIntentForPackage("sg.bigo.live")' in activity
        and ".BigoBroadcastGuide" not in manifest,
    "preserve Suno original import/dedupe": all(x in store for x in (
        "Originals", 'MessageDigest.getInstance("SHA-256")',
        "retainDownloadDocument", "isDownloadReference()")),
    "preserve four music genres": all(x in program for x in (
        "한국노래", "중국노래", "서양노래", "연주곡")),
    "preserve playback and timer": all(x in service for x in (
        "MediaPlayer", "SystemClock.elapsedRealtime()",
        "ACTION_SET_TIMER", "ACTION_SET_WALL_END", "deadlineReached()",
        "ACTION_NEXT", "ACTION_PREVIOUS")),
    "preserve start and stop pair scheduling": all(x in schedule for x in (
        "public static void setPair(", ".putLong(\"when\", startAt)",
        ".putLong(\"end_when\", finishAt)",
        "setExactAndAllowWhileIdle(",
        "startForegroundService(play)")),
    "end never claims BIGO livestream ended":
        "BIGO 라이브는 직접 종료" in schedule
        and "BIGO 실제 라이브는 직접 종료" in automation,
    "separate Android TTS announcer app screen":
        ".RadioDjActivity" in manifest and "RadioDjActivity.class" in activity
        and "TextToSpeech" in dj_voice,
    "own recorded greeting from user selection": all(x in dj_activity for x in (
        "Intent.ACTION_OPEN_DOCUMENT", "takePersistableUriPermission",
        "RadioDjSettings.setVoiceClip(this, uri)")),
    "manual-only viewer comment input":
        "comment.getText().toString()" in dj_activity
        and "BigoControlAssist" not in dj_activity,
    "local learning with wipe": all(x in dj_settings for x in (
        "host_rules", "clearMemory(Context ctx)",
        "clearHostRules(Context ctx)")),
    "brief genre-specific language lines": all(x in dj_policy for x in (
        "songIntro(String title, int group)", "new Line(KOREAN",
        "new Line(CHINESE", "new Line(ENGLISH",
        "giftLanguage(String comment, int manualHint)",
        "public static List<Line> opening()", "public static List<Line> closing()")),
    "English-only start and end TTS":
        "RadioDjLanguagePolicy.opening()" in service
        and "RadioDjLanguagePolicy.closing()" in service,
    "no outdated BIGO UI mock in CI":
        "BigoAutomationPolicySelfTest.java" not in workflow
        and "BigoControlAssist" not in workflow,
    "signed APK CI checks sensitive components":
        "apksigner" in workflow and "AAPT" in workflow
        and "bigo_control_config" in workflow,
}
for label, result in checks.items():
    print(("PASS" if result else "FAIL") + ": " + label)
if not all(checks.values()):
    raise SystemExit("Safe MARU V1.9.3 contract failed")
print("Safe MARU V1.9.3 contracts: ALL PASS")
