#!/usr/bin/env python3
"""Regression test for post-build DEX text verification.

This specifically guards against javac folding adjacent guide-string literals
into one longer DEX string. A required sentence fragment must be accepted when
it occurs inside that longer string, while standalone button labels remain
exact-match requirements.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKER_PATH = ROOT / "scripts" / "check_built_apk.py"

spec = importlib.util.spec_from_file_location(
    "check_built_apk",
    CHECKER_PATH,
)
if spec is None or spec.loader is None:
    raise SystemExit("cannot import check_built_apk.py")
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)

FULL_GUIDE = (
    "기존 청취자가 쉽게 찾아오는 일반 LIVE 또는 오디오 LIVE를 사용합니다. "
    "MARU는 뒤에서 자작곡을 재생하고 BIGO 화면에서 댓글·입장·선물·팔로우를 직접 확인합니다. "
    "BIGO의 방송하기 버튼은 직접 눌러야 합니다. "
    "음성은 휴대폰 TTS를 사용하며 성별은 고정하지 않고 곡 사이 안내에만 사용합니다. "
    "입장은 AI 자동답변에서 제외하고, AI 댓글 답변은 최대 2초 동안 글로만 표시합니다."
)

base_strings = set(checker.REQUIRED_EXACT_DEX_STRINGS)
base_strings.add(FULL_GUIDE)

for _ in range(1000):
    missing = checker.missing_required_dex_texts(base_strings)
    if missing:
        raise AssertionError(
            "compile-time folded guide string was rejected: "
            + " | ".join(missing)
        )

missing_button = set(base_strings)
missing_button.remove("완전 종료")
if "완전 종료" not in checker.missing_required_dex_texts(missing_button):
    raise AssertionError("standalone button label no longer requires exact match")

missing_guide = set(checker.REQUIRED_EXACT_DEX_STRINGS)
missing = checker.missing_required_dex_texts(missing_guide)
for expected in checker.REQUIRED_DEX_TEXT_FRAGMENTS:
    if expected not in missing:
        raise AssertionError("missing guide fragment was not detected: " + expected)

partial_guide = set(checker.REQUIRED_EXACT_DEX_STRINGS)
partial_guide.add("BIGO의 방송하기 버튼은 직접 눌러야 합니다.")
missing = checker.missing_required_dex_texts(partial_guide)
if "BIGO의 방송하기 버튼은 직접 눌러야 합니다." in missing:
    raise AssertionError("present guide fragment was rejected")
if (
    "입장은 AI 자동답변에서 제외하고, AI 댓글 답변은 최대 2초 동안 글로만 표시합니다."
    not in missing
):
    raise AssertionError("absent guide fragment was not detected")

print("BUILT-APK-TEXT-MATCHING-TEST: PASS")
print("CYCLES: 1000/1000")
print("EXACT LABELS:", len(checker.REQUIRED_EXACT_DEX_STRINGS))
print("GUIDE FRAGMENTS:", len(checker.REQUIRED_DEX_TEXT_FRAGMENTS))
