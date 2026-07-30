#!/usr/bin/env python3
from pathlib import Path
import hashlib
import struct
import sys
import zipfile

FORBIDDEN_ENTRIES = {
    "res/raw/default_male_greeting.mp3",
    "res/raw/default_male_greeting_en.mp3",
    "res/raw/default_male_greeting_zh.mp3",
}
FORBIDDEN_DEX_STRINGS = {
    "Lcom/maru/musiclive/GreetingAudioResolver;",
    "Lcom/maru/musiclive/NodeTextCollector;",
    "기본 남성 인사 음성이 자동으로 재생됩니다.",
    "감지 성공\n남성 자동 인사 재생 확인",
    "감지 성공\n여성 TTS 자동 인사 재생 확인",
    "한국어 여성",
    "영어 여성",
}
REQUIRED_DEX_STRINGS = {
    "V3.1.2 · 상단 이미지 꽉 채움 · 습득·진화 AI 화면 답변 · 무키보드 종료 · 랜덤 20분 차단",
    "5개 언어 연속 통합 안내 테스트",
    "3. 이벤트 글 + 습득·진화 AI 화면 답변 + 음악 + 곡 사이 5개 언어",
    "랜덤 재생 · 같은 곡 20분 절대 중복 차단",
    "상단 분할 화면 이미지 좌우 여백 없이 꽉 채우기",
    "습득·진화 대화형 AI · 작은 화면 답변 · 키보드 없음",
    "안내 후 완전 종료",
    "즉시 완전 종료",
}
EXPECTED_RAW_HASHES = {
    "res/raw/actual_music.mp3": "0675b96d48ec97cec56303b620e7652dc3408c0d27df03803653086af723e0b3",
    "res/raw/actual_lyrics.lrc": "dc11c908183a232e5c00e691da9f8895ac1022279cd07dc04e157ab1d8950564",
}
def read_uleb(data, offset):
    value = 0
    shift = 0
    while True:
        b = data[offset]
        offset += 1
        value |= (b & 0x7F) << shift
        if b < 0x80:
            return value, offset
        shift += 7

def dex_strings(data):
    if not data.startswith(b"dex\n"):
        return []
    count = struct.unpack_from("<I", data, 0x38)[0]
    ids_off = struct.unpack_from("<I", data, 0x3C)[0]
    values = []
    for i in range(count):
        string_off = struct.unpack_from("<I", data, ids_off + i * 4)[0]
        _, pos = read_uleb(data, string_off)
        end = data.find(b"\x00", pos)
        values.append(data[pos:end].decode("utf-8", errors="replace"))
    return values

def has_signing_block(data):
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        return False
    cd_off = struct.unpack_from("<I", data, eocd + 16)[0]
    return (
        cd_off >= 24
        and data[cd_off - 16:cd_off] == b"APK Sig Block 42"
    )

def main():
    if len(sys.argv) != 2:
        raise SystemExit("usage: check_built_apk.py <apk>")
    apk = Path(sys.argv[1])
    if not apk.is_file():
        raise SystemExit(f"APK not found: {apk}")

    apk_bytes = apk.read_bytes()
    failures = []

    with zipfile.ZipFile(apk, "r") as archive:
        corrupt = archive.testzip()
        if corrupt:
            failures.append("ZIP CRC failure: " + corrupt)
        names = set(archive.namelist())

        stale_entries = sorted(names & FORBIDDEN_ENTRIES)
        if stale_entries:
            failures.append("stale male files: " + ", ".join(stale_entries))

        strings = set()
        for name in names:
            if name == "classes.dex" or (
                name.startswith("classes") and name.endswith(".dex")
            ):
                strings.update(dex_strings(archive.read(name)))

        stale_strings = sorted(strings & FORBIDDEN_DEX_STRINGS)
        if stale_strings:
            failures.append("stale classes/text: " + " | ".join(stale_strings))

        missing_strings = sorted(REQUIRED_DEX_STRINGS - strings)
        if missing_strings:
            failures.append("missing V3.1.2 strings: " + " | ".join(missing_strings))

        required = {
            "AndroidManifest.xml",
            "resources.arsc",
            "classes.dex",
            "res/raw/actual_music.mp3",
            "res/raw/actual_lyrics.lrc",
        }
        missing = sorted(required - names)
        if missing:
            failures.append("missing APK entries: " + ", ".join(missing))
            raw_entries = sorted(
                name for name in names
                if name.startswith("res/raw/") or "actual_music" in name or "actual_lyrics" in name
            )
            failures.append(
                "APK raw entries seen: "
                + (", ".join(raw_entries) if raw_entries else "(none)")
            )
        for name, expected_hash in EXPECTED_RAW_HASHES.items():
            if name not in names:
                continue
            actual_hash = hashlib.sha256(archive.read(name)).hexdigest()
            if actual_hash != expected_hash:
                failures.append(
                    f"raw resource hash mismatch: {name} "
                    f"(sha256={actual_hash})"
                )

    if not has_signing_block(apk_bytes):
        failures.append("APK signing block not found")

    if failures:
        print("BUILT-APK-CHECK: FAIL")
        for failure in failures:
            print(" -", failure)
        raise SystemExit(1)

    print("BUILT-APK-CHECK: PASS")
    print("APK:", apk.name)
    print("BYTES:", len(apk_bytes))
    print("SHA256:", hashlib.sha256(apk_bytes).hexdigest())

if __name__ == "__main__":
    main()
