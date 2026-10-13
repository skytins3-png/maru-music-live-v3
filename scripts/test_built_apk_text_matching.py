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
    "일반 LIVE 또는 오디오 LIVE를 누르면 MARU가 음악을 재생한 뒤 BIGO 앱을 엽니다. "
    "개인정보 보호를 위해 다른 앱 화면 자동 조작과 접근성 권한 요청 기능은 제거했습니다. "
    "BIGO 화면에서 방송 종류를 선택하고 실제 방송 시작 버튼은 직접 누릅니다. "
    "음성은 휴대폰 TTS를 사용하며 성별은 고정하지 않고 곡 사이 안내에만 사용합니다. "
    "BIGO 자체 입장·팔로우·선물 인사를 사용합니다. 댓글 자동 입력 대신 직접 입력하거나 복사한 답변을 붙여넣으세요."

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
partial_guide.add("BIGO 화면에서 방송 종류를 선택하고 실제 방송 시작 버튼은 직접 누릅니다.")
missing = checker.missing_required_dex_texts(partial_guide)
if "BIGO 화면에서 방송 종류를 선택하고 실제 방송 시작 버튼은 직접 누릅니다." in missing:
    raise AssertionError("present guide fragment was rejected")
if (
    "BIGO 자체 입장·팔로우·선물 인사를 사용합니다. 댓글 자동 입력 대신 직접 입력하거나 복사한 답변을 붙여넣으세요."
    not in missing
):
    raise AssertionError("absent guide fragment was not detected")

print("BUILT-APK-TEXT-MATCHING-TEST: PASS")
print("CYCLES: 1000/1000")
print("EXACT LABELS:", len(checker.REQUIRED_EXACT_DEX_STRINGS))
print("GUIDE FRAGMENTS:", len(checker.REQUIRED_DEX_TEXT_FRAGMENTS))
