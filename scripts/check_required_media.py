#!/usr/bin/env python3
"""Verify the committed Android raw media without copying or generating files."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app/src/main/res/raw"
EXPECTED = {
    "actual_music.mp3": {
        "sha256": "0675b96d48ec97cec56303b620e7652dc3408c0d27df03803653086af723e0b3",
        "min_bytes": 1_000_000,
    },
    "actual_lyrics.lrc": {
        "sha256": "dc11c908183a232e5c00e691da9f8895ac1022279cd07dc04e157ab1d8950564",
        "min_bytes": 1,
    },
}


def looks_like_mp3(data: bytes) -> bool:
    if data.startswith(b"ID3"):
        return True
    return len(data) >= 2 and data[0] == 0xFF and (data[1] & 0xE0) == 0xE0


failures = []
for name, rule in EXPECTED.items():
    path = RAW / name
    rel = path.relative_to(ROOT).as_posix()
    if not path.is_file():
        failures.append(f"missing committed resource: {rel}")
        continue

    data = path.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if len(data) < rule["min_bytes"]:
        failures.append(f"resource too small: {rel} ({len(data)} bytes)")
    if digest != rule["sha256"]:
        failures.append(f"resource hash mismatch: {rel} (sha256={digest})")

    if name.endswith(".mp3") and not looks_like_mp3(data):
        failures.append(f"resource is not an MP3 stream: {rel}")
    if name.endswith(".lrc"):
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError as exc:
            failures.append(f"resource is not UTF-8 LRC: {rel} ({exc})")
        else:
            if not text.strip():
                failures.append(f"resource LRC is empty: {rel}")

    print(f"RAW-MEDIA: {rel} | {len(data)} bytes | {digest}")

if failures:
    print("RAW-MEDIA-CHECK: FAIL")
    for failure in failures:
        print(" -", failure)
    raise SystemExit(1)

print(f"RAW-MEDIA-CHECK: PASS ({len(EXPECTED)}/{len(EXPECTED)})")
