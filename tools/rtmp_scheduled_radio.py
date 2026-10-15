#!/usr/bin/env python3
"""MARU: timed music -> user-authorized BIGO OBS/RTMP endpoint WITHOUT OBS.

This is *not* BIGO's mobile Audio LIVE mixer: RTMP is a different officially
documented streaming entry point. Streaming credentials never enter the repo.
BIGO room/end state must be confirmed separately. Python 3.10+ / FFmpeg needed.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import datetime, time as clocktime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

KST = timezone(timedelta(hours=9), name="KST")
EXTS = {".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg"}
GENRES = ("한국노래", "중국노래", "서양노래", "연주곡")
BUFFER = 32768


@dataclass(frozen=True)
class Song:
    path: Path
    genre: str


def parse_clock(value: str) -> clocktime:
    fields = value.split(":")
    if len(fields) != 2 or not all(part.isdecimal() for part in fields):
        raise ValueError("시간은 HH:MM 형식으로 지정해 주세요.")
    hh, mm = (int(x) for x in fields)
    if not 0 <= hh <= 23 or not 0 <= mm <= 59:
        raise ValueError("시간은 00:00~23:59이어야 해요.")
    return clocktime(hour=hh, minute=mm)


def next_window(now: datetime, start: str, end: str) -> tuple[datetime, datetime]:
    if now.tzinfo is None:
        raise ValueError("시각에는 시간대 정보가 필요합니다.")
    here = now.astimezone(KST)
    start_clock = parse_clock(start)
    end_clock = parse_clock(end)
    begin = datetime.combine(here.date(), start_clock, tzinfo=KST)
    if begin <= here:
        begin += timedelta(days=1)
    finish = datetime.combine(begin.date(), end_clock, tzinfo=KST)
    if finish <= begin:
        finish += timedelta(days=1)
    return begin, finish


def fingerprint(file: Path) -> str:
    digest = hashlib.sha256()
    with file.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def playlist(root: Path, genre_order: list[str]) -> list[Song]:
    if not root.is_dir():
        raise ValueError("노래 폴더를 찾지 못했어요: " + str(root))
    if not genre_order or any(g not in GENRES for g in genre_order):
        raise ValueError("genre_order는 한국/중국/서양/연주곡 중에서 선택하세요.")
    if len(set(genre_order)) != len(genre_order):
        raise ValueError("장르 분류가 중복됐어요.")
    buckets: dict[str, list[Song]] = {}
    hashes: set[str] = set()
    for genre in genre_order:
        folder = root / genre
        choices = []
        for p in sorted(folder.rglob("*") if folder.exists() else [],
                        key=lambda v: v.name.casefold()):
            if not p.is_file() or p.suffix.lower() not in EXTS:
                continue
            digest = fingerprint(p)  # EXACT byte dedupe, no source modifications.
            if digest in hashes:
                continue
            hashes.add(digest)
            choices.append(Song(p, genre))
        buckets[genre] = choices
    result = []
    count = max((len(v) for v in buckets.values()), default=0)
    for number in range(count):
        for genre in genre_order:
            songs = buckets[genre]
            if number < len(songs):
                result.append(songs[number])
    return result


def check_public_rtmp(full_url: str) -> str:
    """Accept ONLY an OBS Stream server/key URL supplied by the account owner."""
    value = full_url.strip()
    if not value.startswith(("rtmp://", "rtmps://")):
        raise ValueError("공식 BIGO OBS Stream RTMP 주소가 필요해요.")
    if any(ch.isspace() for ch in value) or len(value) > 4096:
        raise ValueError("RTMP 주소 형식이 올바르지 않아요.")
    return value


def ffmpeg_command(url: str, image: Path | None, seconds: int) -> list[str]:
    if not shutil.which("ffmpeg"):
        raise ValueError("FFmpeg 실행 파일이 PATH에 없어요.")
    # An image is a low-frame-rate video. RTMP output may NOT be an Audio LIVE room.
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y"]
    if image is not None:
        if not image.is_file() or image.suffix.lower() not in {".jpg", ".jpeg", ".png"}:
            raise ValueError("표지 이미지는 JPG/PNG 파일이어야 해요.")
        cmd += ["-loop", "1", "-framerate", "5", "-i", str(image)]
    else:
        cmd += ["-f", "lavfi", "-i", "color=c=0x15152a:s=720x1280:r=5"]
    cmd += [
        "-re", "-f", "s16le", "-ar", "48000", "-ac", "2", "-i", "pipe:0",
        "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "libx264", "-preset", "veryfast", "-tune", "stillimage",
        "-pix_fmt", "yuv420p", "-r", "5", "-g", "10", "-b:v", "300k",
        "-c:a", "aac", "-b:a", "256k", "-ar", "48000", "-ac", "2",
        # Bound the output even when Python is interrupted or decoding stalls.
        "-t", str(max(1, seconds)), "-f", "flv", url,
    ]
    return cmd


def wait_until(when: datetime) -> None:
    while True:
        delta = (when - datetime.now(KST)).total_seconds()
        if delta <= 0:
            return
        time.sleep(min(10.0, delta))


def deliver(songs: list[Song], finish: datetime, rtmp_url: str,
            image: Path | None) -> None:
    remaining = max(1, int((finish - datetime.now(KST)).total_seconds()))
    command = ffmpeg_command(rtmp_url, image, remaining)
    print("BIGO 공식 RTMP 송출 연결을 시도합니다 (주소와 키는 표시하지 않음).", flush=True)
    # Errors may echo the stream key, so NEVER print child process stderr.
    encoder = subprocess.Popen(command, stdin=subprocess.PIPE,
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    total_valid = 0
    idx = 0
    try:
        if encoder.stdin is None:
            raise RuntimeError("오디오 입력 파이프를 열지 못했어요.")
        while datetime.now(KST) < finish and encoder.poll() is None:
            song = songs[idx % len(songs)]
            idx += 1
            print(f"재생: {song.genre} / {song.path.name}", flush=True)
            # Decode local originals to PCM without touching their bytes on disk.
            # RTMP requires AAC re-encoding; bit-identical streaming is impossible.
            decoder = subprocess.Popen(
                ["ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
                 "-i", str(song.path), "-vn", "-f", "s16le",
                 "-ac", "2", "-ar", "48000", "pipe:1"],
                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
            wrote = False
            try:
                assert decoder.stdout is not None
                while datetime.now(KST) < finish and encoder.poll() is None:
                    audio = decoder.stdout.read(BUFFER)
                    if not audio:
                        break
                    try:
                        encoder.stdin.write(audio)
                        wrote = True
                    except (BrokenPipeError, OSError):
                        break
            finally:
                if decoder.poll() is None:
                    decoder.terminate()
                try:
                    decoder.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    decoder.kill()
                    decoder.wait()
                if decoder.stdout:
                    decoder.stdout.close()
            if not wrote:
                total_valid += 1
                if total_valid >= len(songs):
                    raise RuntimeError("모든 음원 디코딩이 실패했어요.")
            else:
                total_valid = 0
            if encoder.poll() is not None:
                break
        if datetime.now(KST) >= finish:
            print("예약 종료: RTMP 미디어 송출을 중단합니다.", flush=True)
        else:
            raise RuntimeError("RTMP 송출 프로세스가 종료됐어요. 실제 BIGO 방송 상태 확인이 필요해요.")
    finally:
        if encoder.poll() is None:
            encoder.terminate()
        if encoder.stdin:
            try:
                encoder.stdin.close()
            except OSError:
                pass
        try:
            encoder.wait(timeout=7)
        except subprocess.TimeoutExpired:
            encoder.kill()
            encoder.wait()


def run() -> int:
    cli = argparse.ArgumentParser(description="예약한 시간에 BIGO 공식 RTMP 연결로 원곡 송출")
    cli.add_argument("--config", required=True, help="개인 PC의 설정 JSON, 비밀 키는 저장하지 마세요")
    mode = cli.add_mutually_exclusive_group(required=True)
    mode.add_argument("--plan", action="store_true", help="예약 미리보기만; 네트워크 연결 없음")
    mode.add_argument("--arm", action="store_true", help="미래 예약에 공개 방송 연결: 명시적 선택")
    args = cli.parse_args()
    try:
        config_path = Path(args.config).expanduser().resolve()
        config = json.loads(config_path.read_text(encoding="utf-8"))
        source = Path(config["music_dir"]).expanduser()
        order = config.get("genre_order", list(GENRES))
        tracks = playlist(source, order)
        if not tracks:
            raise ValueError("선택한 장르 폴더에 음악 파일이 없어요.")
        start, end = next_window(datetime.now(KST), config["start"], config["end"])
        cover = Path(config["cover_image"]).expanduser() if config.get("cover_image") else None
        print(f"시작 {start:%Y-%m-%d %H:%M} / 종료 {end:%Y-%m-%d %H:%M} (한국시간)")
        print(f"원곡 {len(tracks)}개, 4장르 선택 순환 · FFmpeg AAC 256k 송출")
        if args.plan:
            print("미리보기 완료: 실제 BIGO 방송은 시작하지 않았습니다.")
            return 0
        if config.get("enabled") is not True:
            raise ValueError("config의 enabled=true로 본인이 자동 송출을 명시 승인해야 합니다.")
        url = check_public_rtmp(os.environ.get("MARU_RTMP_URL", ""))
        # Fail fast before waiting overnight; do not expose URL in logs.
        ffmpeg_command(url, cover, int((end - start).total_seconds()))
        print("방송 예약 대기 중. PC 전원이 켜져 있고 이 프로세스가 실행 중이어야 합니다.")
        wait_until(start)
        deliver(tracks, end, url, cover)
        print("송출 연결 종료 완료. BIGO 라이브 룸이 실제 종료됐는지는 확인해 주세요.")
        return 0
    except (OSError, ValueError, KeyError, RuntimeError) as exc:
        # Never log RTMP URL; secret may appear in exception messages.
        message = str(exc)
        secret = os.environ.get("MARU_RTMP_URL", "")
        if secret:
            message = message.replace(secret, "[REDACTED]")
        print("오류: " + message, file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(run())
