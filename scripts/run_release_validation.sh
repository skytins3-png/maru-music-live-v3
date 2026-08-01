#!/usr/bin/env bash
set -euo pipefail
python3 scripts/check_maru_clean.py
python3 scripts/check_voice_policy.py
python3 scripts/check_required_media.py
python3 scripts/run_source_integrity_1000.py
python3 scripts/check_java_imports.py
python3 scripts/check_v275_reference.py
bash scripts/run_core_self_test.sh
bash scripts/run_adaptive_store_checked_compile.sh
bash scripts/run_song_title_resolver_checked_compile.sh
bash scripts/run_playback_tts_checked_compile.sh
python3 scripts/check_maru_project.py
