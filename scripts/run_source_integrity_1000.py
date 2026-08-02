#!/usr/bin/env python3
"""Run 1,000 deterministic source-integrity validation cycles.

This is intentionally independent from Gradle. It checks the repository that is
about to be built: version/workflow consistency, forbidden legacy files, direct
raw-media hashes, Gradle/manifest essentials, XML validity, UTF-8 text files,
and absence of generated build output in the upload package.
"""
from __future__ import annotations

from pathlib import Path
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app"
MAIN = APP / "src" / "main"
WORKFLOW = ROOT / ".github" / "workflows" / "build-apk.yml"
APP_GRADLE = APP / "build.gradle"
ROOT_GRADLE = ROOT / "build.gradle"
MANIFEST = MAIN / "AndroidManifest.xml"

CYCLES = 1000
EXPECTED_VERSION_CODE = "3023"
EXPECTED_VERSION_NAME = "3.2.3"
EXPECTED_MEDIA = {
    MAIN / "res" / "raw" / "actual_music.mp3":
        "0675b96d48ec97cec56303b620e7652dc3408c0d27df03803653086af723e0b3",
    MAIN / "res" / "raw" / "actual_lyrics.lrc":
        "dc11c908183a232e5c00e691da9f8895ac1022279cd07dc04e157ab1d8950564",
}
FORBIDDEN_EXACT = {
    MAIN / "res" / "raw" / "default_male_greeting.mp3",
    MAIN / "res" / "raw" / "default_male_greeting_en.mp3",
    MAIN / "res" / "raw" / "default_male_greeting_zh.mp3",
    MAIN / "res" / "xml" / "accessibility_service_config.xml",
    MAIN / "java" / "com" / "maru" / "musiclive" / "BigoAccessibilityService.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "AccessibilityEventRelay.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "AutoHostAccessibilityService.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "GreetingAudioResolver.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "NodeTextCollector.java",
    ROOT / "required_media",
    ROOT / "scripts" / "media_payload",
    ROOT / "scripts" / "restore_required_media.py",
}
REQUIRED_WORKFLOW_TOKENS = (
    "name: Build MARU MUSIC LIVE V3.2.3 APK",
    "gradle-version: '8.13'",
    "java-version: '17'",
    "python3 scripts/check_required_media.py",
    "python3 scripts/check_maru_clean.py",
    "python3 scripts/check_voice_policy.py",
    "python3 scripts/check_playback_ui_regression.py",
    "python3 scripts/test_home_player_ui_1000.py",
    "find . -maxdepth 1 -type f -name '*.java' -print -delete",
    "rm -rf build",
    "python3 scripts/run_source_integrity_1000.py",
    "bash scripts/run_core_self_test.sh",
    "bash scripts/run_auto_reply_policy_checked_compile.sh",
    ":app:testDebugUnitTest",
    ":app:lintDebug",
    ":app:assembleDebug",
    ":app:assembleRelease",
    "apksigner\" verify --verbose",
    "python3 scripts/check_built_apk.py",
    "python3 scripts/test_built_apk_text_matching.py",
    "MARU-MUSIC-LIVE-V3.2.3-DEBUG.apk",
    "MARU-MUSIC-LIVE-V3.2.3-MUSIC-RELEASE.apk",
)
TEXT_SUFFIXES = {
    ".java", ".xml", ".gradle", ".properties", ".yml", ".yaml",
    ".py", ".sh", ".md", ".txt", ".lrc", ".pro",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def balanced(text: str, opening: str, closing: str) -> bool:
    level = 0
    quote = None
    escaped = False
    for char in text:
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if quote:
            if char == quote:
                quote = None
            continue
        if char in ("'", '"'):
            quote = char
            continue
        if char == opening:
            level += 1
        elif char == closing:
            level -= 1
            if level < 0:
                return False
    return level == 0 and quote is None


def validate_static_once() -> list[str]:
    errors: list[str] = []

    for path in (WORKFLOW, APP_GRADLE, ROOT_GRADLE, MANIFEST):
        if not path.is_file():
            errors.append(f"missing required file: {path.relative_to(ROOT)}")

    built_apk_checker = ROOT / "scripts" / "check_built_apk.py"
    built_apk_text_test = ROOT / "scripts" / "test_built_apk_text_matching.py"
    if not built_apk_checker.is_file() or not built_apk_text_test.is_file():
        errors.append("built APK text-matching checker/test is missing")
    else:
        checker_text = built_apk_checker.read_text(encoding="utf-8")
        test_text = built_apk_text_test.read_text(encoding="utf-8")
        if "REQUIRED_EXACT_DEX_STRINGS" not in checker_text:
            errors.append("built APK checker exact-label policy missing")
        if "REQUIRED_DEX_TEXT_FRAGMENTS" not in checker_text:
            errors.append("built APK checker folded-fragment policy missing")
        if "if not any(fragment in value for value in values)" not in checker_text:
            errors.append("built APK checker does not use substring matching for folded guide text")
        if "for _ in range(1000):" not in test_text:
            errors.append("built APK text matching regression is not repeated 1000 times")

    if errors:
        return errors

    app_gradle = APP_GRADLE.read_text(encoding="utf-8")
    root_gradle = ROOT_GRADLE.read_text(encoding="utf-8")
    workflow = WORKFLOW.read_text(encoding="utf-8")
    manifest = MANIFEST.read_text(encoding="utf-8")

    version_code = re.search(r"\bversionCode\s+(\d+)", app_gradle)
    version_name = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", app_gradle)
    if not version_code or version_code.group(1) != EXPECTED_VERSION_CODE:
        errors.append("app/build.gradle versionCode is not 3023")
    if not version_name or version_name.group(1) != EXPECTED_VERSION_NAME:
        errors.append("app/build.gradle versionName is not 3.2.3")

    main_activity = (MAIN / "java" / "com" / "maru" / "musiclive" / "MainActivity.java")
    one_click_plan = (MAIN / "java" / "com" / "maru" / "musiclive" / "OneClickBroadcastPlan.java")
    if not main_activity.is_file() or not one_click_plan.is_file():
        errors.append("one-click BIGO source files are missing")
    else:
        main_text = main_activity.read_text(encoding="utf-8")
        plan_text = one_click_plan.read_text(encoding="utf-8")
        for token in (
            "일반 LIVE 음악방송 시작",
            "startCommunityLive",
            "OneClickBroadcastPlan.BIGO_PACKAGE",
            "오디오 LIVE 음악방송 시작",
            "전체화면 이미지 플레이어",
            "startMusicForBroadcast();",
            "BIGO의 방송하기 버튼은 직접 눌러야 합니다",
            "openBigoChat();",
            'smallButton("이전"',
            'smallButton("재생"',
            'smallButton("다음"',
            'showHomePlayer();',
            'BitmapFactory.decodeResource',
            'smallButton("LIVE"',
            'smallButton("설정"',
            '전체화면 이미지 플레이어 열기',
        ):
            if token not in main_text:
                errors.append(f"missing one-click source token: {token}")
        for token in (
            'BIGO_PACKAGE = "sg.bigo.live"',
            'BROADCAST_MODE = BroadcastMode.PORTRAIT_9_16',
            'REQUIRES_MARU_SCREEN_CAPTURE = false',
            'CONTROLS_EXTERNAL_APP_UI = false',
            'USES_EXISTING_PLAYBACK_UI = true',
            'USES_EXISTING_PLAYBACK_UI = true',
            'CONTROLS_EXTERNAL_APP_UI = false',
        ):
            if token not in plan_text:
                errors.append(f"missing one-click plan token: {token}")
        one_click_body = main_text[
            main_text.find("private void startOneClickBigoBroadcast"):
            main_text.find("private void requestScreenCapture")
        ]
        if "createScreenCaptureIntent()" in one_click_body:
            errors.append("one-click must not start a competing MARU MediaProjection")
        if "pendingCaptureMode = ScreenOcrGreetingService.MODE_AUTO_GREETING" in one_click_body:
            errors.append("one-click must not start MARU OCR during BIGO Game Live")
        playback_text = (MAIN / "java" / "com" / "maru" / "musiclive" / "PlaybackService.java").read_text(encoding="utf-8")
        visual_text = (MAIN / "java" / "com" / "maru" / "musiclive" / "BroadcastVisualProfile.java").read_text(encoding="utf-8")
        if "ACTION_PREPARE_FOR_BROADCAST" in playback_text:
            errors.append("V3.1.8 experimental service action remains")
        if "scheduleOneClickPlaybackRecovery" in main_text:
            errors.append("V3.1.8 repeated playback recovery remains")
        if "PLAYBACK_CONTROL_BOTTOM_MARGIN_DP" in visual_text:
            errors.append("V3.1.8 oversized custom control placement remains")
        if 'column.addView(button("완전 종료", v -> stopAllBroadcastNow()));' not in main_text:
            errors.append("complete stop button is not connected to stopAllBroadcastNow")
        if "performImmediateFullStop()" in main_text:
            errors.append("undefined performImmediateFullStop call remains")
        if 'private boolean homePlayerMode;' not in main_text:
            errors.append("home player mode flag missing")
        if 'if (!localTestMode && !homePlayerMode)' not in main_text:
            errors.append("home player controls can still auto-hide")
        if 'loadActiveSongMedia();' not in main_text or 'showActiveSongMedia();' not in main_text:
            errors.append("home player image loading path missing")

        for forbidden in (
            'BIND_ACCESSIBILITY_SERVICE',
            'dispatchGesture',
            'UiAutomator',
            'InstrumentationRegistry',
        ):
            if forbidden in main_text or forbidden in plan_text or forbidden in manifest:
                errors.append(f"forbidden external UI automation token: {forbidden}")

    gradle_tokens = (
        "compileSdk 36", "minSdk 26", "targetSdk 36",
        "sourceCompatibility JavaVersion.VERSION_17",
        "targetCompatibility JavaVersion.VERSION_17",
        "signingConfig signingConfigs.debug",
    )
    for token in gradle_tokens:
        if token not in app_gradle:
            errors.append(f"missing app Gradle token: {token}")
    if "version '8.10.1'" not in root_gradle:
        errors.append("Android Gradle Plugin is not pinned to 8.10.1")
    if not balanced(app_gradle, "{", "}") or not balanced(root_gradle, "{", "}"):
        errors.append("unbalanced Gradle braces or quotes")

    for token in REQUIRED_WORKFLOW_TOKENS:
        if token not in workflow:
            errors.append(f"missing workflow token: {token}")
    if 'purge_repository_leftovers.py' in workflow:
        errors.append('workflow depends on obsolete purge helper')

    # Presence alone is not enough. Cleanup must happen before the project and
    # 1,000-cycle integrity checks; otherwise stale tracked files fail CI first.
    source_check_pos = workflow.find('run: python3 scripts/check_maru_clean.py')
    integrity_check_pos = workflow.find(
        'run: python3 scripts/run_source_integrity_1000.py'
    )
    cleanup_tokens = (
        "find . -maxdepth 1 -type f -name '*.java' -print -delete",
        'rm -rf build',
    )
    for token in cleanup_tokens:
        pos = workflow.find(token)
        if pos < 0:
            continue
        if source_check_pos < 0 or integrity_check_pos < 0:
            errors.append('workflow project/integrity check step is missing')
            break
        if pos >= source_check_pos or pos >= integrity_check_pos:
            errors.append(
                f'workflow cleanup occurs too late: {token} must be before checks'
            )

    first_line = workflow.splitlines()[0] if workflow.splitlines() else ""
    if first_line != "name: Build MARU MUSIC LIVE V3.2.3 APK":
        errors.append(f"wrong workflow name: {first_line!r}")
    if "V3.1.1 APK" in workflow or "V3.1.1-" in workflow:
        errors.append("stale V3.1.1 workflow/APK token remains")

    self_test = (ROOT / "scripts" / "run_core_self_test.sh").read_text(encoding="utf-8")
    for token in (
        'SRC_DIR="build/core-self-test-src"',
        '-sourcepath "$SRC_DIR"',
        '@"$SRC_DIR/sources.txt"',
    ):
        if token not in self_test:
            errors.append(f"core self-test is not source-isolated: missing {token}")

    for path in FORBIDDEN_EXACT:
        if path.exists():
            errors.append(f"forbidden legacy path exists: {path.relative_to(ROOT)}")
    for path in sorted(ROOT.glob("*.java")):
        if path.is_file():
            errors.append(f"forbidden repository-root Java source exists: {path.name}")
    raw_dir = MAIN / "res" / "raw"
    if raw_dir.is_dir():
        for path in raw_dir.glob("default_male_greeting*.mp3"):
            errors.append(f"forbidden legacy raw exists: {path.relative_to(ROOT)}")

    for path, expected in EXPECTED_MEDIA.items():
        if not path.is_file():
            errors.append(f"missing direct raw resource: {path.relative_to(ROOT)}")
        else:
            actual = sha256(path)
            if actual != expected:
                errors.append(
                    f"raw hash mismatch: {path.relative_to(ROOT)} ({actual})"
                )

    if "BIND_ACCESSIBILITY_SERVICE" in manifest or "AccessibilityService" in manifest:
        errors.append("legacy Android accessibility service remains in manifest")
    if "FOREGROUND_SERVICE_MEDIA_PROJECTION" not in manifest:
        errors.append("media-projection foreground permission missing")
    if 'android:name=".ScreenOcrGreetingService"' not in manifest:
        errors.append("screen OCR greeting service missing")
    try:
        ET.fromstring(manifest)
    except Exception as exc:
        errors.append(f"manifest XML invalid: {exc}")

    for path in sorted((MAIN / "res").rglob("*.xml")):
        try:
            ET.parse(path)
        except Exception as exc:
            errors.append(f"resource XML invalid: {path.relative_to(ROOT)}: {exc}")

    for path in sorted(ROOT.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT)
        if rel.parts and rel.parts[0] in {".git", "build"}:
            continue
        if path.suffix.lower() in TEXT_SUFFIXES or path.name in {"gradlew", "gradlew.bat"}:
            try:
                path.read_text(encoding="utf-8")
            except UnicodeDecodeError as exc:
                errors.append(f"non-UTF-8 text file: {rel}: {exc}")

    generated_roots = [ROOT / "build", APP / "build", ROOT / ".gradle"]
    for path in generated_roots:
        if path.exists():
            errors.append(f"generated directory included in source package: {path.relative_to(ROOT)}")

    ocr_path = MAIN / "java" / "com" / "maru" / "musiclive" / "ScreenOcrGreetingService.java"
    intermission_path = MAIN / "java" / "com" / "maru" / "musiclive" / "IntermissionAnnouncementText.java"
    ocr_text = ocr_path.read_text(encoding="utf-8") if ocr_path.is_file() else ""
    intermission_text = intermission_path.read_text(encoding="utf-8") if intermission_path.is_file() else ""
    auto_reply_path = MAIN / "java" / "com" / "maru" / "musiclive" / "AutoReplyPolicy.java"
    event_type_path = MAIN / "java" / "com" / "maru" / "musiclive" / "EventType.java"
    if not auto_reply_path.is_file():
        errors.append("auto reply policy missing")
    else:
        auto_reply_text = auto_reply_path.read_text(encoding="utf-8")
        event_type_text = event_type_path.read_text(encoding="utf-8") if event_type_path.is_file() else ""
        if "MAX_VISUAL_REPLY_MS = 2_000L" not in auto_reply_text:
            errors.append("AI visual reply is not limited to two seconds")
        if "AutoReplyPolicy.MAX_VISUAL_REPLY_MS" not in event_type_text:
            errors.append("CHAT overlay does not use the two-second policy")
        if "if (!AutoReplyPolicy.shouldAutoReply(chat)) continue;" not in ocr_text:
            errors.append("join notifications are not explicitly excluded from AI replies")
        auto_reply_test_path = ROOT / "app" / "src" / "test" / "java" / "com" / "maru" / "musiclive" / "AutoReplyPolicyTest.java"
        if not auto_reply_test_path.is_file():
            errors.append("AutoReplyPolicyTest.java is missing")
        else:
            auto_reply_test_text = auto_reply_test_path.read_text(encoding="utf-8")
            if 'assertTrue("English join must be recognized as a join notification"' not in auto_reply_test_text:
                errors.append("English join detector test has the wrong expected value")
            if 'assertFalse(AutoReplyPolicy.containsJoinNotification("Blue Moon joined the live"))' in auto_reply_test_text:
                errors.append("stale false expectation remains for English join detector")
        contract_script = ROOT / "scripts" / "run_auto_reply_policy_checked_compile.sh"
        contract_tool = ROOT / "tools" / "AutoReplyPolicyContractTest.java"
        if not contract_script.is_file() or not contract_tool.is_file():
            errors.append("auto-reply contract compile files are missing")
        if "방송에 오신 분들 모두 환영합니다" not in intermission_text or "다음 노래는" not in intermission_text:
            errors.append("between-song welcome or next-song guide was removed")

    voice_policy_path = MAIN / "java" / "com" / "maru" / "musiclive" / "BroadcastVoicePolicy.java"
    voice_test_path = ROOT / "tools" / "VoicePolicyStressSelfTest.java"
    voice_script_path = ROOT / "scripts" / "check_voice_policy.py"
    if not voice_policy_path.is_file():
        errors.append("central broadcast voice policy missing")
    else:
        voice_policy_text = voice_policy_path.read_text(encoding="utf-8")
        for token in (
            "GreetingLanguage.KOREAN", "GreetingLanguage.ENGLISH",
            "GreetingLanguage.CHINESE", "GreetingLanguage.JAPANESE",
            "GreetingLanguage.RUSSIAN", "FORCE_GENDER = false",
            "SPEAK_COMMENTS = false", "SPEAK_EVENTS_DURING_SONG = false",
            "SPEECH_RATE = 0.94f", "PITCH = 1.00f", "VOLUME = 1.00f",
        ):
            if token not in voice_policy_text:
                errors.append(f"missing voice policy token: {token}")
    if not voice_test_path.is_file():
        errors.append("voice policy stress test missing")
    if not voice_script_path.is_file():
        errors.append("voice policy checker missing")

    conversation_text = (MAIN / "java" / "com" / "maru" / "musiclive" / "ConversationEngine.java").read_text(encoding="utf-8")
    adaptive_text = (MAIN / "java" / "com" / "maru" / "musiclive" / "AdaptiveAiStore.java").read_text(encoding="utf-8")
    ocr_text = (MAIN / "java" / "com" / "maru" / "musiclive" / "ScreenOcrGreetingService.java").read_text(encoding="utf-8")
    if "songRequestRefusal" not in conversation_text or "신청곡은 받지 않습니다" not in conversation_text:
        errors.append("adaptive original-song request refusal missing")
    if "recordSongRequest" not in adaptive_text or "isLearnedSongRequest" not in adaptive_text:
        errors.append("song request learning store missing")
    if "intent == ConversationIntent.SONG_REQUEST || learnedSongRequest" not in ocr_text:
        errors.append("fixed request refusal flow missing")

    return errors


def source_fingerprint() -> str:
    digest = hashlib.sha256()
    ignored_roots = {".git", "build", ".gradle"}
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(ROOT)
        if rel.parts and rel.parts[0] in ignored_roots:
            continue
        if len(rel.parts) >= 2 and rel.parts[:2] == ("app", "build"):
            continue
        digest.update(rel.as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def main() -> None:
    baseline = source_fingerprint()
    for cycle in range(1, CYCLES + 1):
        errors = validate_static_once()
        current = source_fingerprint()
        if current != baseline:
            errors.append(
                f"source mutated during validation cycle {cycle}: "
                f"{baseline} -> {current}"
            )
        if errors:
            print(f"SOURCE-INTEGRITY-1000: FAIL at cycle {cycle}")
            for error in errors:
                print(" -", error)
            raise SystemExit(1)
    print(f"SOURCE-INTEGRITY-1000: PASS ({CYCLES}/{CYCLES})")
    print("SOURCE-FINGERPRINT-SHA256:", baseline)


if __name__ == "__main__":
    main()
