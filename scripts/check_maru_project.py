#!/usr/bin/env python3
from pathlib import Path
import hashlib
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
checks = []

def check(name, value):
    checks.append((name, bool(value)))

required = [
    '.github/workflows/build-apk.yml', 'README.md', 'PRIVACY.md',
    'app/build.gradle', 'app/src/main/AndroidManifest.xml',
    'app/src/main/java/com/maru/musiclive/MainActivity.java',
    'app/src/main/java/com/maru/musiclive/BroadcastVisualProfile.java',
    'app/src/main/java/com/maru/musiclive/BroadcastVoicePolicy.java',
    'app/src/main/java/com/maru/musiclive/PlaybackService.java',
    'app/src/main/java/com/maru/musiclive/OneClickBroadcastPlan.java',
    'app/src/main/java/com/maru/musiclive/BigoBroadcastNavigatorService.java',
    'app/src/main/java/com/maru/musiclive/BigoNavigationPolicy.java',
    'app/src/main/res/xml/accessibility_service_config.xml',
    'app/src/main/java/com/maru/musiclive/ConversationEngine.java',
    'app/src/main/java/com/maru/musiclive/AutoReplyPolicy.java',
    'app/src/main/java/com/maru/musiclive/AdaptiveAiStore.java',
    'app/src/main/java/com/maru/musiclive/RandomPlaybackGuard.java',
    'app/src/main/java/com/maru/musiclive/AutoGreetingService.java',
    'app/src/main/java/com/maru/musiclive/ScreenOcrGreetingService.java',
    'app/src/main/java/com/maru/musiclive/BigoEventParser.java',
    'app/src/main/java/com/maru/musiclive/IntermissionAnnouncementText.java',
    'app/src/main/java/com/maru/musiclive/IntermissionStore.java',
    'app/src/main/java/com/maru/musiclive/BroadcastClosingText.java',
    'app/src/main/java/com/maru/musiclive/EventOverlayText.java',
    'app/src/main/java/com/maru/musiclive/SongMediaStore.java',
    'scripts/run_core_self_test.sh',
    'scripts/run_auto_reply_policy_checked_compile.sh',
    'scripts/run_playback_tts_checked_compile.sh', 'scripts/check_built_apk.py',
    'scripts/test_built_apk_text_matching.py',
    'tools/IntermissionStressSelfTest.java',
    'tools/VisualCompatibilityStressSelfTest.java', 'tools/RandomPlaybackStressSelfTest.java',
    'tools/UiAiClosingStressSelfTest.java',
    'tools/OneClickBroadcastStressSelfTest.java',
    'tools/SongRequestPolicyStressSelfTest.java',
    'tools/VoicePolicyStressSelfTest.java',
    'tools/AutoReplyPolicyStressSelfTest.java',
    'tools/AutoReplyPolicyContractTest.java',
    'app/src/test/java/com/maru/musiclive/AutoReplyPolicyTest.java',
    'app/src/test/java/com/maru/musiclive/BigoNavigationPolicyTest.java',
    'tools/BigoNavigationPolicyStressSelfTest.java',
    'scripts/check_v275_reference.py',
    'scripts/check_required_media.py',
    'scripts/check_voice_policy.py',
    'scripts/check_playback_ui_regression.py',
    'scripts/test_home_player_ui_1000.py',
    'scripts/run_source_integrity_1000.py',
    '.github/workflows/cleanup-stale-repository.yml',
    'app/src/main/res/raw/actual_music.mp3',
    'app/src/main/res/raw/actual_lyrics.lrc'
]
for rel in required:
    check('file:' + rel, (root / rel).is_file())

expected_media_hashes = {
    'app/src/main/res/raw/actual_music.mp3': '0675b96d48ec97cec56303b620e7652dc3408c0d27df03803653086af723e0b3',
    'app/src/main/res/raw/actual_lyrics.lrc': 'dc11c908183a232e5c00e691da9f8895ac1022279cd07dc04e157ab1d8950564',
}
for rel, expected_hash in expected_media_hashes.items():
    path = root / rel
    digest = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else ''
    check('media hash:' + rel, digest == expected_hash)

manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')
main = (root / 'app/src/main/java/com/maru/musiclive/MainActivity.java').read_text(encoding='utf-8')
visual = (root / 'app/src/main/java/com/maru/musiclive/BroadcastVisualProfile.java').read_text(encoding='utf-8')
playback = (root / 'app/src/main/java/com/maru/musiclive/PlaybackService.java').read_text(encoding='utf-8')
auto = (root / 'app/src/main/java/com/maru/musiclive/AutoGreetingService.java').read_text(encoding='utf-8')
ocr = (root / 'app/src/main/java/com/maru/musiclive/ScreenOcrGreetingService.java').read_text(encoding='utf-8')
intermission = (root / 'app/src/main/java/com/maru/musiclive/IntermissionAnnouncementText.java').read_text(encoding='utf-8')
store = (root / 'app/src/main/java/com/maru/musiclive/IntermissionStore.java').read_text(encoding='utf-8')
all_text = '\n'.join(
    p.read_text(encoding='utf-8', errors='ignore')
    for p in root.rglob('*')
    if p.is_file() and p.suffix in {'.java', '.xml', '.gradle', '.md', '.yml', '.sh'}
)

version_code_match = re.search(r'\bversionCode\s+(\d+)', gradle)
version_name_match = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", gradle)
version_code = version_code_match.group(1) if version_code_match else ''
version_name = version_name_match.group(1) if version_name_match else ''
workflow_text = (root / '.github/workflows/build-apk.yml').read_text(encoding='utf-8')
workflow_tag = f'V{version_name}' if version_name else ''

check('version code', version_code == '3025')
check('version name', version_name == '3.2.5')
# Keep the workflow check tied to app/build.gradle rather than a second hard-coded
# version. This prevents a false failure after a version bump while still catching
# a stale workflow such as V3.0.9 paired with app version 3.1.6.
check('workflow version', bool(workflow_tag) and workflow_tag in workflow_text)
check('workflow 1000 source integrity',
      'python3 scripts/run_source_integrity_1000.py' in workflow_text)
check('workflow auto-reply contract compile',
      'bash scripts/run_auto_reply_policy_checked_compile.sh' in workflow_text)
check('workflow built-APK text matching regression',
      'python3 scripts/test_built_apk_text_matching.py' in workflow_text)
check('workflow has no missing purge-helper dependency',
      'purge_repository_leftovers.py' not in workflow_text)
cleanup_tokens = (
    "find . -maxdepth 1 -type f -name '*.java' -print -delete",
    'rm -rf build',
)
source_check_pos = workflow_text.find('run: python3 scripts/check_maru_clean.py')
integrity_check_pos = workflow_text.find('run: python3 scripts/run_source_integrity_1000.py')
cleanup_positions = [workflow_text.find(token) for token in cleanup_tokens]
cleanup_before_checks = (
    source_check_pos >= 0
    and integrity_check_pos >= 0
    and all(pos >= 0 and pos < source_check_pos and pos < integrity_check_pos
            for pos in cleanup_positions)
)
check('workflow removes root Java leftovers before project checks', cleanup_before_checks)
check('workflow removes root build artifact before project checks',
      cleanup_before_checks and workflow_text.find('rm -rf build') < source_check_pos)
check('permanent cleanup workflow exists',
      (root / '.github/workflows/cleanup-stale-repository.yml').is_file())
clean_script_text = (root / 'scripts/check_maru_clean.py').read_text(encoding='utf-8')
check('clean checker is self-contained',
      'def collect_leftovers()' in clean_script_text
      and 'def purge()' in clean_script_text
      and 'purge_repository_leftovers.py' not in clean_script_text)
check('generated roots ignored',
      all(token in (root / '.gitignore').read_text(encoding='utf-8')
          for token in ('/build/', '/app/build/', '/.gradle/')))
root_java_files = sorted(path.name for path in root.glob('*.java') if path.is_file())
check('no repository-root Java sources', not root_java_files)
self_test_text = (root / 'scripts/run_core_self_test.sh').read_text(encoding='utf-8')
check('core self-test isolated sourcepath',
      'SRC_DIR="build/core-self-test-src"' in self_test_text
      and '-sourcepath "$SRC_DIR"' in self_test_text
      and '@"$SRC_DIR/sources.txt"' in self_test_text
      and 'find "$SRC_DIR"' in self_test_text)
one_click_plan_text = (
    root / 'app/src/main/java/com/maru/musiclive/OneClickBroadcastPlan.java'
).read_text(encoding='utf-8')
check('community live with BIGO preparation-screen navigation',
      '일반 LIVE 음악방송 시작' in main
      and '오디오 LIVE 음악방송 시작' in main
      and 'startCommunityLive' in main
      and 'startMusicForBroadcast();' in main
      and 'BigoBroadcastNavigatorService.arm(this, liveMode);' in main
      and 'openBigoChat();' in main
      and '실제 공개 방송을 시작하는 마지막 버튼은 안전을 위해 직접 누릅니다' in main
      and 'ACTION_PREPARE_FOR_BROADCAST' not in playback
      and 'scheduleOneClickPlaybackRecovery' not in main
      and 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
      and 'BigoBroadcastNavigatorService' in manifest
      and 'dispatchGesture' in all_text
      and 'UiAutomator' not in all_text)
auto_reply_test_text = (
    root / 'app/src/test/java/com/maru/musiclive/AutoReplyPolicyTest.java'
).read_text(encoding='utf-8')
check('auto-reply test uses correct join-detector semantics',
      'assertTrue("English join must be recognized as a join notification"' in auto_reply_test_text
      and 'assertFalse("Korean join must not auto-reply"' in auto_reply_test_text
      and 'assertFalse(AutoReplyPolicy.containsJoinNotification("Blue Moon joined the live"))' not in auto_reply_test_text)
check('complete stop button calls existing method',
      'column.addView(button("완전 종료", v -> stopAllBroadcastNow()));' in main
      and 'performImmediateFullStop()' not in main)
built_apk_checker_text = (root / 'scripts/check_built_apk.py').read_text(encoding='utf-8')
built_apk_text_test = (root / 'scripts/test_built_apk_text_matching.py').read_text(encoding='utf-8')
check('built APK checker accepts folded guide fragments without weakening labels',
      'REQUIRED_EXACT_DEX_STRINGS' in built_apk_checker_text
      and 'REQUIRED_DEX_TEXT_FRAGMENTS' in built_apk_checker_text
      and 'if not any(fragment in value for value in values)' in built_apk_checker_text
      and 'CYCLES: 1000/1000' in built_apk_text_test
      and 'missing_button.remove("완전 종료")' in built_apk_text_test)
check('auto-reply checked compile uses real policy sources',
      'AutoReplyPolicyContractTest.java' in (root / 'scripts/run_auto_reply_policy_checked_compile.sh').read_text(encoding='utf-8')
      and 'src="app/src/main/java/com/maru/musiclive/$name"' in (root / 'scripts/run_auto_reply_policy_checked_compile.sh').read_text(encoding='utf-8')
      and 'AutoReplyPolicy.java' in (root / 'scripts/run_auto_reply_policy_checked_compile.sh').read_text(encoding='utf-8'))

check('existing functional playback controls restored',
      'smallButton("이전"' in main
      and 'smallButton("재생"' in main
      and 'smallButton("다음"' in main
      and 'smallButton("답변"' in main
      and 'smallButton("말하기"' in main
      and 'smallButton("복귀"' in main
      and 'smallButton("BIGO"' in main
      and 'smallButton("종료"' in main
      and 'dp(homePlayerMode ? 72 : 64)' in main
      and 'PLAYBACK_CONTROL_BOTTOM_MARGIN_DP' not in visual
      and 'ACTION_PREPARE_FOR_BROADCAST' not in playback)
check('default full-screen image player visible',
      'showHomePlayer();' in main
      and 'private boolean homePlayerMode;' in main
      and 'private void showHomePlayer()' in main
      and 'loadActiveSongMedia();' in main
      and 'showActiveSongMedia();' in main
      and 'BitmapFactory.decodeResource' in main
      and 'smallButton("LIVE"' in main
      and 'smallButton("설정"' in main
      and 'if (!localTestMode && !homePlayerMode)' in main
      and '전체화면 이미지 플레이어 열기' in main)
check('game category', 'android:appCategory="game"' in manifest and 'android:isGame="true"' in manifest)
check('release signed', 'signingConfig signingConfigs.debug' in gradle)
check('direct raw resources only',
      'stageRequiredRawMedia' not in gradle
      and not (root / 'required_media').exists()
      and not (root / 'scripts/restore_required_media.py').exists()
      and not (root / 'scripts/media_payload').exists())
voice = (root / 'app/src/main/java/com/maru/musiclive/GreetingLanguage.java').read_text(encoding='utf-8')
ocr_test = (root / 'app/src/main/java/com/maru/musiclive/OcrTestActivity.java').read_text(encoding='utf-8')
check('voice gender not forced',
      '한국어 음성' in voice and '영어 음성' in voice
      and '한국어 여성' not in voice and '영어 여성' not in voice
      and '여성 TTS' not in ocr_test and '남성 TTS' not in ocr_test)
voice_policy = (root / 'app/src/main/java/com/maru/musiclive/BroadcastVoicePolicy.java').read_text(encoding='utf-8')
check('central five-language voice policy',
      all(token in voice_policy for token in (
          'GreetingLanguage.KOREAN', 'GreetingLanguage.ENGLISH',
          'GreetingLanguage.CHINESE', 'GreetingLanguage.JAPANESE',
          'GreetingLanguage.RUSSIAN'))
      and 'BroadcastVoicePolicy.orderedLanguages()' in auto
      and 'BroadcastVoicePolicy.orderedLanguages()' in intermission)
check('voice comments remain visual only',
      'SPEAK_COMMENTS = false' in voice_policy
      and 'SPEAK_EVENTS_DURING_SONG = false' in voice_policy
      and 'AutoGreetingService.speakDialogue' not in ocr)
check('voice tuning centralized',
      'SPEECH_RATE = 0.94f' in voice_policy
      and 'PITCH = 1.00f' in voice_policy
      and 'VOLUME = 1.00f' in voice_policy
      and 'BroadcastVoicePolicy.SPEECH_RATE' in auto
      and 'BroadcastVoicePolicy.PITCH' in auto
      and 'BroadcastVoicePolicy.VOLUME' in auto)
check('voice policy stress test',
      'expected 1000 checks' in (root / 'tools/VoicePolicyStressSelfTest.java').read_text(encoding='utf-8')
      and 'VoicePolicyStressSelfTest.java' in self_test_text)
check('workflow voice policy check',
      'python3 scripts/check_voice_policy.py' in workflow_text)
check('workflow playback UI regression check',
      'python3 scripts/check_playback_ui_regression.py' in workflow_text)
check('workflow home player UI stress check',
      'python3 scripts/test_home_player_ui_1000.py' in workflow_text)
check('user-enabled BIGO navigator accessibility service',
      'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
      and 'android.accessibilityservice.AccessibilityService' in manifest
      and 'BigoBroadcastNavigatorService' in manifest)
check('media projection', 'FOREGROUND_SERVICE_MEDIA_PROJECTION' in manifest and 'createScreenCaptureIntent()' in main)
check('playback capture', 'ALLOW_CAPTURE_BY_ALL' in playback and 'USAGE_GAME' in playback)

check('music only while track plays',
      '중요: 제목/인사 TTS는 곡이 시작된 뒤 절대 재생하지 않는다.' in playback
      and 'announceCurrentSongTitle' not in playback)
check('random 20 minute guard',
      'RandomPlaybackGuard' in playback
      and 'DEFAULT_COOLDOWN_MS = 20L * 60L * 1000L' in (root / 'app/src/main/java/com/maru/musiclive/RandomPlaybackGuard.java').read_text(encoding='utf-8')
      and 'randomGuard.markStarted' in playback
      and 'randomGuard.chooseNext' in playback
      and 'saveRandomPlaybackState' in playback
      and '같은 곡 20분 중복 방지' in main
      and 'lastChoiceBlockedByCooldown' in (root / 'app/src/main/java/com/maru/musiclive/RandomPlaybackGuard.java').read_text(encoding='utf-8')
      and 'lastChoiceUsedFallback' not in (root / 'app/src/main/java/com/maru/musiclive/RandomPlaybackGuard.java').read_text(encoding='utf-8')
      and 'scheduleRandomRetry' in playback
      and 'trackKeys' in playback)
check('random state persisted',
      'KEY_RANDOM_HISTORY' in (root / 'app/src/main/java/com/maru/musiclive/AppStorage.java').read_text(encoding='utf-8')
      and 'KEY_RANDOM_CYCLE' in (root / 'app/src/main/java/com/maru/musiclive/AppStorage.java').read_text(encoding='utf-8')
      and 'loadRandomHistory' in playback
      and 'loadRandomCycle' in playback)
check('intermission on completion',
      'setOnCompletionListener' in playback
      and 'transitionToNext();' in playback
      and 'AutoGreetingService.announceIntermission' in playback)
check('next starts after announcement',
      'ACTION_PLAY_AFTER_ANNOUNCEMENT' in playback
      and 'resumeTrack' in auto
      and 'activeResumeIndex' in auto)
check('no automatic music duck for TTS',
      'duckMusic(' not in auto
      and '곡 사이 안내와 사용자가 누른 방송 종료 안내' in auto)
check('TTS failure resumes song',
      'resumeQueuedWithoutSpeech' in auto
      and 'TTS 초기화 실패 · 다음 노래를 바로 시작합니다.' in auto
      and 'SPEAK_TIMEOUT_MS = 35_000L' in auto
      and 'advanceLanguage(false)' in auto)

check('five languages every intermission',
      all(code in voice_policy for code in (
          'GreetingLanguage.KOREAN', 'GreetingLanguage.ENGLISH',
          'GreetingLanguage.CHINESE', 'GreetingLanguage.JAPANESE',
          'GreetingLanguage.RUSSIAN'))
      and 'BroadcastVoicePolicy.orderedLanguages()' in intermission
      and 'announcementLanguages' in store
      and 'orderedLanguages' in intermission
      and 'nextLanguage' not in store
      and 'INTERMISSION_LANGUAGES' in auto
      and 'advanceLanguage' in auto
      and 'IntermissionStore.nextLanguage' not in playback)
check('five integrated texts',
      all(text in intermission for text in (
          '방송에 오신 분들 모두 환영합니다',
          'Welcome, everyone', '欢迎来到直播间', '皆さん、ようこそ',
          'Добро пожаловать на трансляцию')))
check('like prompt',
      '좋아요 한 번 눌러 주세요' in intermission
      and 'tap the like button once' in intermission
      and '请点一下赞' in intermission
      and 'いいねを一回お願いします' in intermission
      and 'Пожалуйста, поставьте лайк' in intermission)
check('gift follow thanks',
      '선물 감사합니다' in intermission
      and '팔로우 감사합니다' in intermission
      and 'thank you for the gifts' in intermission
      and 'thank you for following' in intermission
      and 'спасибо за подарки' in intermission
      and 'спасибо за подписку' in intermission)
check('next title included',
      '다음 노래는 ' in intermission
      and 'The next song is ' in intermission
      and '下一首歌是《' in intermission
      and '次の曲は「' in intermission
      and 'Следующая песня — «' in intermission)
check('event names accumulated',
      'KEY_GIFT_NAMES' in store and 'KEY_FOLLOW_NAMES' in store
      and 'takeSnapshot' in store)

check('events text only during song',
      'IntermissionStore.recordEvent(this, event);' in ocr
      and 'AutoGreetingService.announceEvent(this, event);' not in ocr
      and '곡 재생 중에는 어떤 이벤트도 TTS로 읽지 않고' in ocr)
conversation = (root / 'app/src/main/java/com/maru/musiclive/ConversationEngine.java').read_text(encoding='utf-8')
adaptive = (root / 'app/src/main/java/com/maru/musiclive/AdaptiveAiStore.java').read_text(encoding='utf-8')
check('adaptive original-song request refusal',
      'songRequestRefusal' in conversation
      and '신청곡은 받지 않습니다' in conversation
      and '자작곡' in conversation
      and 'recordSongRequest' in adaptive
      and 'isLearnedSongRequest' in adaptive
      and 'songRequests' in adaptive
      and '거절 정책은 학습으로 바뀌지 않습니다' in all_text
      and 'intent == ConversationIntent.SONG_REQUEST || learnedSongRequest' in ocr)
check('safe adaptive conversation',
      'handleSafeChatMessages' in ocr
      and 'LiveOverlayController.showDialogue' in ocr
      and 'AutoGreetingService.speakDialogue' not in ocr
      and 'CHAT_REPLY_TTL_MS = 10L * 60L * 1000L' in ocr
      and '습득·진화 대화형 AI · 작은 화면 답변 · 키보드 없음' in all_text
      and '게임 좋아하세요?' in all_text
      and 'migrateSafeConversationV311' in main
      and 'KEY_SAFE_CONVERSATION_V311' in (root / 'app/src/main/java/com/maru/musiclive/AdaptiveAiStore.java').read_text(encoding='utf-8'))
check('mobile event typography',
      'BroadcastVisualProfile.EVENT_SP' in main
      and 'BroadcastVisualProfile.LYRIC_SP' in main
      and 'BroadcastVisualProfile.TIME_SP' in main
      and 'EVENT_SP = 14f' in visual
      and 'LYRIC_SP = 17f' in visual
      and 'TIME_SP = 12f' in visual
      and 'setSingleLine(true)' in main
      and 'setEllipsize(TextUtils.TruncateAt.END)' in main)
check('top split image fill',
      'fillBroadcastImage' in main
      and 'ImageView.ScaleType.CENTER_CROP' in main
      and '상단 분할 화면 이미지 좌우 여백 없이 꽉 채우기' in all_text
      and 'KEY_FILL_BROADCAST_IMAGE' in (root / 'app/src/main/java/com/maru/musiclive/AppStorage.java').read_text(encoding='utf-8'))
check('compact mobile event visual profile',
      'EVENT_WIDTH_RATIO = 0.82f' in visual
      and 'EVENT_HEIGHT_DP = 42' in visual
      and (root / 'scripts/check_v275_reference.py').is_file())
check('session reset', 'IntermissionStore.resetSession(this);' in main)
check('UI explains community live behavior',
      '일반 LIVE 또는 오디오 LIVE를 누르면 MARU가 음악을 재생한 뒤 BIGO의 방송 준비 화면까지 자동으로 이동합니다' in main
      and '처음 한 번은 ‘MARU BIGO 방송 화면 이동’ 접근성 권한을 켜야 합니다' in main
      and '실제 공개 방송을 시작하는 마지막 버튼은 안전을 위해 직접 누릅니다' in main)
check('keyboard free closing',
      'showBroadcastEndMenu' in main
      and 'beginPresetBroadcastClosing' in main
      and 'AutoGreetingService.announceClosing' in main
      and 'BroadcastClosingText.build' in auto
      and 'ACTION_BROADCAST_CLOSED' in auto)
check('background notification controls',
      'ACTION_TOGGLE' in playback
      and 'ACTION_NEXT' in playback
      and 'ACTION_STOP_ALL' in playback
      and '완전 종료' in playback
      and 'PendingIntent.getService' in playback)
check('1000 parser stress',
      'checks != 1000' in (root / 'tools/StressSelfTest.java').read_text(encoding='utf-8'))
check('1000 intermission stress',
      'expected 1000 checks' in (root / 'tools/IntermissionStressSelfTest.java').read_text(encoding='utf-8'))
check('1000 random playback stress',
      'expected 1000 checks' in (root / 'tools/RandomPlaybackStressSelfTest.java').read_text(encoding='utf-8'))
check('random playback junit',
      (root / 'app/src/test/java/com/maru/musiclive/RandomPlaybackGuardTest.java').is_file())
check('1000 visual compatibility stress',
      'expected 1000 checks' in (root / 'tools/VisualCompatibilityStressSelfTest.java').read_text(encoding='utf-8'))
check('1000 UI AI closing stress',
      'expected 1000 checks' in (root / 'tools/UiAiClosingStressSelfTest.java').read_text(encoding='utf-8'))
check('1000 one-click broadcast stress',
      'expected 1000 checks' in (root / 'tools/OneClickBroadcastStressSelfTest.java').read_text(encoding='utf-8'))
check('intermission junit',
      (root / 'app/src/test/java/com/maru/musiclive/IntermissionAnnouncementTextTest.java').is_file())
check('no merge markers', not any(x in all_text for x in ('<<<<<<<', '=======', '>>>>>>>')))

stale_paths = [
    root / 'app/src/main/java/com/maru/musiclive/GreetingAudioResolver.java',
    root / 'app/src/main/java/com/maru/musiclive/NodeTextCollector.java',
]
stale_greeting_audio = list(
    (root / 'app/src/main/res/raw').glob('default_male_greeting*.mp3')
) if (root / 'app/src/main/res/raw').is_dir() else []
check(
    'no stale files',
    not any(path.exists() for path in stale_paths)
    and not stale_greeting_audio,
)

for path in list((root / 'app/src/main/res').rglob('*.xml')) + [root / 'app/src/main/AndroidManifest.xml']:
    try:
        ET.parse(path)
        ok = True
    except Exception:
        ok = False
    check('xml:' + str(path.relative_to(root)), ok)


auto_reply = (root / 'app/src/main/java/com/maru/musiclive/AutoReplyPolicy.java').read_text(encoding='utf-8')
event_type = (root / 'app/src/main/java/com/maru/musiclive/EventType.java').read_text(encoding='utf-8')
conversation = (root / 'app/src/main/java/com/maru/musiclive/ConversationEngine.java').read_text(encoding='utf-8')
check('join excluded from AI auto reply',
      'if (!AutoReplyPolicy.shouldAutoReply(chat)) continue;' in ocr
      and 'EventType.JOIN' in auto_reply
      and 'containsJoinNotification' in auto_reply)
check('AI visual reply limited to two seconds',
      'MAX_VISUAL_REPLY_MS = 2_000L' in auto_reply
      and 'AutoReplyPolicy.MAX_VISUAL_REPLY_MS' in event_type)
check('between-song welcome and next-song voice retained',
      '방송에 오신 분들 모두 환영합니다' in intermission
      and '다음 노래는' in intermission
      and 'AutoGreetingService.announceIntermission' in playback)
check('concise original-song request refusal retained',
      '이 방송은 자작곡만 들려드립니다. 신청곡은 받지 않습니다.' in conversation)

failed = [name for name, ok in checks if not ok]
if failed:
    print('PROJECT-CHECK FAILED')
    for name in failed:
        print(' -', name)
    if 'workflow version' in failed:
        print(f"   app/build.gradle version: {version_name or 'not found'}")
        print(f"   expected workflow token: {workflow_tag or 'not found'}")
        first_line = workflow_text.splitlines()[0] if workflow_text.splitlines() else '(empty workflow)'
        print(f"   workflow first line: {first_line}")
        print('   Fix: replace .github/workflows/build-apk.yml with the workflow from this package.')
    sys.exit(1)
print(f'PROJECT-CHECK: {len(checks)}/{len(checks)}')
