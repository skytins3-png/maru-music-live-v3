#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="build/core-self-test"
SRC_DIR="build/core-self-test-src"
rm -rf "$OUT_DIR" "$SRC_DIR"
mkdir -p "$OUT_DIR" "$SRC_DIR/com/maru/musiclive"

# JVM-only self-test allowlist. Android-dependent classes are intentionally
# excluded, and javac is pointed only at the isolated staging source tree.
APP_SOURCES=(
  BroadcastVisualProfile.java
  BroadcastMode.java
  BroadcastVoicePolicy.java
  OneClickBroadcastPlan.java
  BigoNavigationPolicy.java
  EventType.java
  LiveEvent.java
  EventOverlayText.java
  LearnedRule.java
  BigoEventParser.java
  LearnedEventMatcher.java
  ConversationIntent.java
  ConversationEngine.java
  AutoReplyPolicy.java
  ChatMessage.java
  ChatMessageParser.java
  LiveEventCooldown.java
  JoinEvent.java
  GreetingLanguage.java
  TtsAnnouncementText.java
  IntermissionAnnouncementText.java
  BroadcastClosingText.java
  SongTitleFormatter.java
  TextNormalizer.java
  BigoJoinParser.java
  SeenCache.java
  VolumeDucking.java
  LyricsCore.java
  YoutubeUrlParser.java
  RandomPlaybackGuard.java
)

TOOL_SOURCES=(
  CoreSelfTest.java
  StressSelfTest.java
  IntermissionStressSelfTest.java
  VisualCompatibilityStressSelfTest.java
  RandomPlaybackStressSelfTest.java
  UiAiClosingStressSelfTest.java
  OneClickBroadcastStressSelfTest.java
  BigoNavigationPolicyStressSelfTest.java
  SongRequestPolicyStressSelfTest.java
  VoicePolicyStressSelfTest.java
  AutoReplyPolicyStressSelfTest.java
)

for name in "${APP_SOURCES[@]}"; do
  src="app/src/main/java/com/maru/musiclive/$name"
  test -f "$src" || { echo "Missing JVM self-test source: $src" >&2; exit 1; }
  cp "$src" "$SRC_DIR/com/maru/musiclive/$name"
done

for name in "${TOOL_SOURCES[@]}"; do
  src="tools/$name"
  test -f "$src" || { echo "Missing JVM self-test tool: $src" >&2; exit 1; }
  cp "$src" "$SRC_DIR/$name"
done

find "$SRC_DIR" -type f -name '*.java' -print | sort > "$SRC_DIR/sources.txt"
test -s "$SRC_DIR/sources.txt"

# The restricted sourcepath prevents javac from discovering stray repository-
# root .java files or Android framework sources outside this staging tree.
javac \
  -encoding UTF-8 \
  --release 17 \
  -sourcepath "$SRC_DIR" \
  -d "$OUT_DIR" \
  @"$SRC_DIR/sources.txt"

java -cp "$OUT_DIR" CoreSelfTest
java -cp "$OUT_DIR" StressSelfTest
java -cp "$OUT_DIR" IntermissionStressSelfTest
java -cp "$OUT_DIR" VisualCompatibilityStressSelfTest
java -cp "$OUT_DIR" RandomPlaybackStressSelfTest
java -cp "$OUT_DIR" UiAiClosingStressSelfTest
java -cp "$OUT_DIR" OneClickBroadcastStressSelfTest
java -cp "$OUT_DIR" BigoNavigationPolicyStressSelfTest
java -cp "$OUT_DIR" SongRequestPolicyStressSelfTest
java -cp "$OUT_DIR" VoicePolicyStressSelfTest
java -cp "$OUT_DIR" AutoReplyPolicyStressSelfTest
