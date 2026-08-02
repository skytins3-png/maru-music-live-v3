#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="build/auto-reply-policy-contract"
SRC_DIR="build/auto-reply-policy-contract-src"
rm -rf "$OUT_DIR" "$SRC_DIR"
mkdir -p "$OUT_DIR" "$SRC_DIR/com/maru/musiclive"

APP_SOURCES=(
  TextNormalizer.java
  JoinEvent.java
  BigoJoinParser.java
  GreetingLanguage.java
  EventType.java
  LiveEvent.java
  BigoEventParser.java
  ChatMessage.java
  AutoReplyPolicy.java
)

for name in "${APP_SOURCES[@]}"; do
  src="app/src/main/java/com/maru/musiclive/$name"
  test -f "$src" || { echo "Missing auto-reply source: $src" >&2; exit 1; }
  cp "$src" "$SRC_DIR/com/maru/musiclive/$name"
done

cp tools/AutoReplyPolicyContractTest.java "$SRC_DIR/AutoReplyPolicyContractTest.java"
find "$SRC_DIR" -type f -name '*.java' -print | sort > "$SRC_DIR/sources.txt"

javac \
  -encoding UTF-8 \
  --release 17 \
  -sourcepath "$SRC_DIR" \
  -d "$OUT_DIR" \
  @"$SRC_DIR/sources.txt"

java -cp "$OUT_DIR" AutoReplyPolicyContractTest
