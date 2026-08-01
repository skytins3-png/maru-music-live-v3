#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/maru/musiclive"
policy = (JAVA / "BroadcastVoicePolicy.java").read_text(encoding="utf-8")
auto = (JAVA / "AutoGreetingService.java").read_text(encoding="utf-8")
intermission = (JAVA / "IntermissionAnnouncementText.java").read_text(encoding="utf-8")
main = (JAVA / "MainActivity.java").read_text(encoding="utf-8")

checks = {
    "five-language order": all(token in policy for token in (
        "GreetingLanguage.KOREAN", "GreetingLanguage.ENGLISH",
        "GreetingLanguage.CHINESE", "GreetingLanguage.JAPANESE",
        "GreetingLanguage.RUSSIAN")),
    "device TTS gender not forced": "FORCE_GENDER = false" in policy,
    "comments visual only": "SPEAK_COMMENTS = false" in policy,
    "events visual only during song": "SPEAK_EVENTS_DURING_SONG = false" in policy,
    "intermission allowed": "case WINDOW_INTERMISSION:" in policy,
    "manual voice test allowed": "case WINDOW_MANUAL_TEST:" in policy,
    "closing allowed": "case WINDOW_CLOSING:" in policy,
    "service uses centralized order": "BroadcastVoicePolicy.orderedLanguages()" in auto,
    "service uses centralized rate": "BroadcastVoicePolicy.SPEECH_RATE" in auto,
    "service uses centralized pitch": "BroadcastVoicePolicy.PITCH" in auto,
    "service uses centralized volume": "BroadcastVoicePolicy.VOLUME" in auto,
    "intermission uses centralized order": "BroadcastVoicePolicy.orderedLanguages()" in intermission,
    "UI explains device TTS": "음성은 휴대폰 TTS를 사용하며 성별은 고정하지 않고" in main,
    "UI explains visual-only replies": "AI 댓글 답변은 글로만 표시합니다" in main,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    print("VOICE-POLICY-CHECK: FAIL")
    for name in failed:
        print(" -", name)
    sys.exit(1)
print(f"VOICE-POLICY-CHECK: PASS ({len(checks)}/{len(checks)})")
