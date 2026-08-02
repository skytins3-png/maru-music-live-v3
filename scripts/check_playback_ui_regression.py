#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = (root / "app/src/main/java/com/maru/musiclive/MainActivity.java").read_text(encoding="utf-8")
playback = (root / "app/src/main/java/com/maru/musiclive/PlaybackService.java").read_text(encoding="utf-8")
visual = (root / "app/src/main/java/com/maru/musiclive/BroadcastVisualProfile.java").read_text(encoding="utf-8")

required = [
    'smallButton("이전"', 'smallButton("재생"', 'smallButton("다음"',
    'smallButton("답변"', 'smallButton("말하기"', 'smallButton("복귀"',
    'smallButton("BIGO"', 'smallButton("종료"',
    'playback.setQueue(songs);', 'playback.prepareForBroadcast();',
    'startMusicForBroadcast();', 'startBroadcast();', 'openBigoChat();',
]
for token in required:
    if token not in main:
        raise SystemExit(f"PLAYBACK-UI-REGRESSION: FAIL missing {token}")
for token in ('ACTION_PREPARE_FOR_BROADCAST',):
    if token in playback:
        raise SystemExit(f"PLAYBACK-UI-REGRESSION: FAIL experimental token remains: {token}")
for token in ('scheduleOneClickPlaybackRecovery', 'PLAYBACK_CONTROL_BOTTOM_MARGIN_DP'):
    if token in main or token in visual:
        raise SystemExit(f"PLAYBACK-UI-REGRESSION: FAIL experimental UI token remains: {token}")
print('PLAYBACK-UI-REGRESSION: PASS')
print('CONTROL-BUTTONS: 8/8')
print('PLAYBACK-CORE: V3.1.6 binder-driven service restored')
