#!/usr/bin/env sh
set -eu
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed. Use the included GitHub Actions workflow." >&2
exit 1
