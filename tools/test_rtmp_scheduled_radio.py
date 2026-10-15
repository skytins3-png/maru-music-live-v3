#!/usr/bin/env python3
"""Offline tests: no RTMP connection, no real broadcast, no secrets."""
import tempfile
from datetime import datetime, timedelta, timezone
from pathlib import Path
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import rtmp_scheduled_radio as radio


class RtmpScheduleTests(unittest.TestCase):
    def test_kst_rollover(self):
        now = datetime(2026, 10, 16, 19, 30, tzinfo=radio.KST)
        start, end = radio.next_window(now, "20:00", "02:00")
        self.assertEqual(start.isoformat(), "2026-10-16T20:00:00+09:00")
        self.assertEqual(end.isoformat(), "2026-10-17T02:00:00+09:00")
        start, end = radio.next_window(now, "06:00", "09:00")
        self.assertEqual(start.date().isoformat(), "2026-10-17")
        self.assertEqual(end.date().isoformat(), "2026-10-17")

    def test_end_same_clock_next_day(self):
        now = datetime(2026, 10, 16, 15, 0, tzinfo=timezone.utc)
        start, end = radio.next_window(now, "02:00", "02:00")
        self.assertEqual(end - start, timedelta(days=1))

    def test_invalid_time(self):
        for value in ("24:00", "06:60", "6", "a:b", "23:1:12"):
            with self.assertRaises(ValueError, msg=value):
                radio.parse_clock(value)

    def test_content_dedupe_and_interleave(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for genre in radio.GENRES:
                (root / genre).mkdir()
            (root / "한국노래" / "a.mp3").write_bytes(b"same audio")
            (root / "한국노래" / "b.wav").write_bytes(b"second audio")
            (root / "중국노래" / "one.mp3").write_bytes(b"china audio")
            (root / "서양노래" / "dup.mp3").write_bytes(b"same audio")
            (root / "연주곡" / "other.wav").write_bytes(b"instruments audio")
            names = [(song.genre, song.path.name) for song in
                     radio.playlist(root, list(radio.GENRES))]
            self.assertEqual(names, [
                ("한국노래", "a.mp3"), ("중국노래", "one.mp3"),
                ("연주곡", "other.wav"), ("한국노래", "b.wav"),
            ])
            # The file data is NEVER altered during deduplication.
            self.assertEqual((root / "서양노래" / "dup.mp3").read_bytes(), b"same audio")

    def test_rtmp_url_rejected_when_missing(self):
        for value in ("", "file:///tmp/test", "http://example.com", "rtmp://x hello"):
            with self.assertRaises(ValueError):
                radio.check_public_rtmp(value)

    def test_ffmpeg_argv_safe_no_shell(self):
        with mock.patch.object(radio.shutil, "which", return_value="/usr/bin/ffmpeg"):
            command = radio.ffmpeg_command("rtmp://example.invalid/live/TEST", None, 60)
        self.assertEqual(command[0], "ffmpeg")
        self.assertIn("pipe:0", command)
        self.assertEqual(command[-1], "rtmp://example.invalid/live/TEST")
        self.assertIn("aac", command)
        self.assertIn("libx264", command)
        self.assertEqual(command[command.index("-t") + 1], "60")


if __name__ == "__main__":
    unittest.main(verbosity=2)
