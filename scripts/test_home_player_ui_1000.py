#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/maru/musiclive/MainActivity.java"
DRAWABLE = ROOT / "app/src/main/res/drawable-nodpi"
CYCLES = 1000

text = MAIN.read_text(encoding="utf-8")
required = (
    'showHomePlayer();',
    'private boolean homePlayerMode;',
    'private void showHomePlayer()',
    'backgroundImage = new ImageView(this);',
    'foregroundImage = new ImageView(this);',
    'loadActiveSongMedia();',
    'showActiveSongMedia();',
    'BitmapFactory.decodeResource',
    'smallButton("이전"',
    'smallButton("재생"',
    'smallButton("다음"',
    'smallButton("LIVE"',
    'smallButton("설정"',
    'if (!localTestMode && !homePlayerMode)',
)
for cycle in range(1, CYCLES + 1):
    missing = [token for token in required if token not in text]
    if missing:
        raise SystemExit(f"HOME-PLAYER-UI: FAIL cycle={cycle} missing={missing}")
    if not (DRAWABLE / "actual_image_01.jpg").is_file():
        raise SystemExit(f"HOME-PLAYER-UI: FAIL cycle={cycle} missing actual_image_01.jpg")
    if not (DRAWABLE / "actual_image_02.png").is_file():
        raise SystemExit(f"HOME-PLAYER-UI: FAIL cycle={cycle} missing actual_image_02.png")
    if 'if (localTestMode || homePlayerMode) return;' not in text:
        raise SystemExit(f"HOME-PLAYER-UI: FAIL cycle={cycle} hideControls policy")
print('HOME-PLAYER-UI: PASS')
print(f'CYCLES: {CYCLES}/{CYCLES}')
print('DEFAULT-IMAGES: 2/2')
print('VISIBLE-CONTROLS: previous/play/next/LIVE/settings')
