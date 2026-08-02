#!/usr/bin/env python3
"""Remove stale repository leftovers, then validate MARU V3.2.4.

This file is deliberately self-contained so CI cannot fail because a separate
cleanup helper was omitted during GitHub web upload.
"""
from __future__ import annotations

from pathlib import Path
import runpy
import shutil

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app" / "src" / "main"
SCRIPTS = ROOT / "scripts"

KNOWN_FILES = {
    MAIN / "res" / "xml" / "accessibility_service_config.xml",
    MAIN / "java" / "com" / "maru" / "musiclive" / "BigoAccessibilityService.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "AccessibilityEventRelay.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "AutoHostAccessibilityService.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "GreetingAudioResolver.java",
    MAIN / "java" / "com" / "maru" / "musiclive" / "NodeTextCollector.java",
    SCRIPTS / "restore_required_media.py",
}
KNOWN_DIRECTORIES = {
    ROOT / "required_media",
    SCRIPTS / "media_payload",
    ROOT / "build",
    ROOT / "app" / "build",
    ROOT / ".gradle",
}


def collect_leftovers() -> list[Path]:
    paths: set[Path] = set(KNOWN_FILES) | set(KNOWN_DIRECTORIES)
    raw = MAIN / "res" / "raw"
    if raw.is_dir():
        paths.update(raw.glob("default_male_greeting*.mp3"))

    # Valid Java sources live in app/src/main/java or tools/, never repo root.
    paths.update(path for path in ROOT.glob("*.java") if path.is_file())
    paths.update(path for path in ROOT.rglob("__pycache__") if path.is_dir())
    paths.update(path for path in ROOT.rglob("*.pyc") if path.is_file())
    return sorted(
        (path for path in paths if path.exists() or path.is_symlink()),
        key=lambda path: path.as_posix(),
    )


def remove_path(path: Path) -> None:
    if path.is_symlink() or path.is_file():
        path.unlink(missing_ok=True)
    elif path.is_dir():
        shutil.rmtree(path)


def relative(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def purge() -> None:
    leftovers = collect_leftovers()
    for path in leftovers:
        remove_path(path)

    remaining = collect_leftovers()
    print(f"REPOSITORY-CLEANUP: removed {len(leftovers)}")
    for path in leftovers:
        print(" -", relative(path))
    if remaining:
        detail = ", ".join(relative(path) for path in remaining)
        raise SystemExit(f"REPOSITORY-CLEANUP: FAIL; leftovers remain: {detail}")
    print("REPOSITORY-CLEANUP: PASS")


def main() -> None:
    purge()
    runpy.run_path(str(SCRIPTS / "check_maru_project.py"), run_name="__main__")


if __name__ == "__main__":
    main()
