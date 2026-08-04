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

# TextView/button labels are emitted as standalone DEX strings, so they are
# checked by exact equality. The two guide sentences below are compile-time
# fragments of one longer Java string. javac folds adjacent literals, so DEX
# contains the whole guide string rather than each fragment as a separate entry.
REQUIRED_EXACT_DEX_STRINGS = {
    "V3.2.10 · 분할화면 이미지 전체맞춤 · BIGO 오디오 LIVE",
    "일반 LIVE 음악방송 시작",
    "오디오 LIVE 음악방송 시작",
    "곡 사이 5개 언어 통합 안내",
    "랜덤 재생 · 같은 곡 20분 중복 방지",
    "오류검사·고급 기능",
    "완전 종료",
    "전체화면 이미지 플레이어 열기",
    "BIGO 방송 화면 이동 권한 설정",
    "LIVE",
    "설정",
}

REQUIRED_DEX_TEXT_FRAGMENTS = {
    "BIGO 자체 입장·팔로우·선물 인사는 사용하고, MARU는 일반 댓글만 감지합니다. AI 댓글 답변은 음성 없이 글로만 자동 입력·전송합니다.",
    "실제 공개 방송을 시작하는 마지막 버튼은 안전을 위해 직접 누릅니다.",
    "처음 한 번은 ‘MARU BIGO 방송 화면 이동’ 접근성 권한을 켜야 합니다.",
}

EXPECTED_RAW_HASHES = {
    "res/raw/actual_music.mp3":
        "0675b96d48ec97cec56303b620e7652dc3408c0d27df03803653086af723e0b3",
    "res/raw/actual_lyrics.lrc":
        "dc11c908183a232e5c00e691da9f8895ac1022279cd07dc04e157ab1d8950564",
}


def missing_required_dex_texts(strings):
    """Return required APK UI texts not represented in the DEX string pool.

    Standalone labels must be exact entries. Guide sentences may be fragments
    of a longer compile-time folded string, so they are matched as substrings.
    """
    values = set(strings)
    missing_exact = sorted(
        REQUIRED_EXACT_DEX_STRINGS - values
    )
    missing_fragments = sorted(
        fragment
        for fragment in REQUIRED_DEX_TEXT_FRAGMENTS
        if not any(fragment in value for value in values)
    )
    return missing_exact + missing_fragments


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
        string_off = struct.unpack_from(
            "<I",
            data,
            ids_off + i * 4,
        )[0]

        _, pos = read_uleb(data, string_off)
        end = data.find(b"\x00", pos)

        values.append(
            data[pos:end].decode(
                "utf-8",
                errors="replace",
            )
        )

    return values


def has_signing_block(data):
    eocd = data.rfind(b"PK\x05\x06")

    if eocd < 0:
        return False

    cd_off = struct.unpack_from(
        "<I",
        data,
        eocd + 16,
    )[0]

    return (
        cd_off >= 24
        and data[cd_off - 16:cd_off]
        == b"APK Sig Block 42"
    )


def main():
    if len(sys.argv) != 2:
        raise SystemExit(
            "usage: check_built_apk.py <apk>"
        )

    apk = Path(sys.argv[1])

    if not apk.is_file():
        raise SystemExit(
            f"APK not found: {apk}"
        )

    apk_bytes = apk.read_bytes()
    failures = []

    with zipfile.ZipFile(apk, "r") as archive:
        corrupt = archive.testzip()

        if corrupt:
            failures.append(
                "ZIP CRC failure: " + corrupt
            )

        names = set(archive.namelist())

        stale_entries = sorted(
            names & FORBIDDEN_ENTRIES
        )

        if stale_entries:
            failures.append(
                "stale male files: "
                + ", ".join(stale_entries)
            )

        strings = set()

        for name in names:
            if name == "classes.dex" or (
                name.startswith("classes")
                and name.endswith(".dex")
            ):
                strings.update(
                    dex_strings(
                        archive.read(name)
                    )
                )

        stale_strings = sorted(
            strings & FORBIDDEN_DEX_STRINGS
        )

        if stale_strings:
            failures.append(
                "stale classes/text: "
                + " | ".join(stale_strings)
            )

        missing_strings = missing_required_dex_texts(
            strings
        )

        if missing_strings:
            failures.append(
                "missing V3.2.10 navigator/player strings: "
                + " | ".join(missing_strings)
            )

        required = {
            "AndroidManifest.xml",
            "resources.arsc",
            "classes.dex",
        }

        missing = sorted(required - names)

        if missing:
            failures.append(
                "missing APK entries: "
                + ", ".join(missing)
            )

        resource_entries = sorted(
            name
            for name in names
            if name.startswith("res/")
            and not name.endswith("/")
        )

        resource_hashes = {}

        def sha256_entry(name):
            if name not in resource_hashes:
                resource_hashes[name] = (
                    hashlib.sha256(
                        archive.read(name)
                    ).hexdigest()
                )

            return resource_hashes[name]

        matched_media = {}

        for logical_name, expected_hash in (
            EXPECTED_RAW_HASHES.items()
        ):
            if logical_name in names:
                actual_hash = sha256_entry(
                    logical_name
                )

                if actual_hash != expected_hash:
                    failures.append(
                        "raw resource hash mismatch: "
                        f"{logical_name} "
                        f"(sha256={actual_hash})"
                    )
                else:
                    matched_media[
                        logical_name
                    ] = logical_name

                continue

            matches = [
                name
                for name in resource_entries
                if sha256_entry(name)
                == expected_hash
            ]

            if not matches:
                failures.append(
                    "missing APK media content: "
                    + logical_name
                    + " (expected sha256="
                    + expected_hash
                    + ")"
                )
            else:
                matched_media[
                    logical_name
                ] = matches[0]

        if (
            len(matched_media)
            != len(EXPECTED_RAW_HASHES)
        ):
            media_entries = sorted(
                name
                for name in resource_entries
                if name.endswith(
                    (".mp3", ".lrc")
                )
                or "actual_music" in name
                or "actual_lyrics" in name
            )

            failures.append(
                "APK media/resource entries seen: "
                + (
                    ", ".join(media_entries)
                    if media_entries
                    else "(none)"
                )
            )

    if not has_signing_block(apk_bytes):
        failures.append(
            "APK signing block not found"
        )

    if failures:
        print("BUILT-APK-CHECK: FAIL")

        for failure in failures:
            print(" -", failure)

        raise SystemExit(1)

    print("BUILT-APK-CHECK: PASS")
    print("APK:", apk.name)
    print("BYTES:", len(apk_bytes))
    print(
        "SHA256:",
        hashlib.sha256(
            apk_bytes
        ).hexdigest(),
    )

    for logical_name, packaged_name in sorted(
        matched_media.items()
    ):
        if logical_name == packaged_name:
            print(
                "MEDIA:",
                logical_name,
                "(exact path, hash PASS)",
            )
        else:
            print(
                "MEDIA:",
                logical_name,
                "->",
                packaged_name,
                "(renamed path, hash PASS)",
            )


if __name__ == "__main__":
    main()
