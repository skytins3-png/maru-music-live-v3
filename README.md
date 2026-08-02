# MARU MUSIC LIVE GAME V3.1.7

## V3.1.7 원클릭 BIGO 방송 준비

홈 화면의 `원클릭 BIGO 방송 시작`을 누르면 Android 화면 공유 동의창이 바로 열립니다. 사용자가 `전체 화면` 공유를 허용하면 MARU가 세로 9:16 화면, OCR·이벤트 글·AI 화면 답변, 음악·가사·이미지를 함께 시작하고 BIGO LIVE를 엽니다. BIGO의 `게임 LIVE` 선택, `MARU MUSIC LIVE` 선택, BIGO 화면 공유 허용, 실제 방송 시작은 자동으로 대신 누르지 않습니다.


BIGO 게임 LIVE에서 자작곡·곡 이미지·가사를 송출하기 위한 Android 프로젝트입니다.


### 학습형 댓글 답변과 신청곡 거절

- AI는 시청자 언어, 인사·감사·자작곡 칭찬, 반복 댓글과 승인된 맞춤 답변을 기억합니다.
- 신청곡 요청은 요청 문구와 시청자별 반복 횟수를 학습합니다.
- 첫 요청에는 이 방송이 진행자의 자작곡만 들려주는 방송임을 설명하며 정중히 거절합니다.
- 반복 요청에는 앞서 안내했다는 문구로 다시 설명합니다.
- 학습 데이터가 쌓여도 신청곡 거절 정책은 바뀌지 않으며 BIGO 채팅창 자동 입력·전송은 하지 않습니다.

## 핵심 동작
- 노래 재생 중에는 노래 소리만 출력하고 이벤트 TTS는 재생하지 않음
- 곡이 끝날 때마다 한국어 → 영어 → 중국어 → 일본어 → 러시아어 안내를 순서대로 모두 재생한 뒤 다음 곡 시작
- 랜덤 재생 시 같은 곡과 중복 별칭을 20분 동안 다시 선택하지 않음
- 상단 곡 이미지를 좌우 여백 없이 채우는 `CENTER_CROP` 방식
- 입장·좋아요·선물·팔로우는 작은 한 줄 화면 문구로 표시
- 습득·진화 대화형 AI는 안전한 작은 화면 답변만 표시하며 BIGO 키보드에 자동 입력하지 않음
- 키보드 없이 5개 언어 종료 안내 후 음악·OCR·AI를 완전히 종료
- 알림창에서 일시정지·다음 곡·완전 종료 가능
- 안내 음성 성별을 앱에서 강제로 지정하지 않고 휴대폰 TTS 설정의 자연스러운 음성을 사용
- 음성 속도 0.94, 피치 1.00, 음량 1.00으로 통일하고 중앙 음성 정책 검사와 1,000회 스트레스 테스트 수행

## 필수 내장 리소스
실제 파일이 아래 Android 표준 경로에 직접 포함되어 있습니다.

```text
app/src/main/res/raw/actual_music.mp3
app/src/main/res/raw/actual_lyrics.lrc
```

복원 스크립트나 임시 placeholder를 사용하지 않습니다. GitHub Actions는 빌드 전과 APK 생성 후에 파일의 위치와 SHA-256을 검사합니다.

## GitHub 빌드
이 압축 안의 파일을 저장소 최상단에 올린 뒤 Actions에서 `Build MARU MUSIC LIVE V3.1.7 APK`를 실행합니다. 이전 패치와 섞지 말고 이 전체 파일을 기준으로 사용합니다.

생성 파일:
- `MARU-MUSIC-LIVE-V3.1.7-DEBUG.apk`
- `MARU-MUSIC-LIVE-V3.1.7-GAME-RELEASE.apk`

## 실제 기기 확인
설치 후 `DEVICE_TEST_GUIDE_KO.md` 순서대로 울트라 26과 BIGO LIVE 화면공유에서 최종 확인합니다.

## 검증 범위
- `scripts/run_release_validation.sh`: 프로젝트 규칙, 5개 언어 TTS 정책, raw 미디어, 소스 무결성, Java import, 핵심 자바 자체 테스트, 예외 처리 컴파일 테스트를 실행합니다.
- `scripts/run_source_integrity_1000.py`: 버전·워크플로·금지된 구형 파일·raw 해시·Gradle 핵심값·XML·UTF-8·생성물 혼입 여부를 1,000회 반복 확인합니다.
- Java 스트레스 테스트는 파서, 곡 사이 안내, 화면 호환성, 중복 별칭, 랜덤 재생, UI/AI/종료 흐름을 각각 1,000건씩 확인합니다.
- 실제 `assembleDebug`, `assembleRelease`, Android lint, JUnit 및 APK 서명/내용 검사는 GitHub Actions의 Android SDK 환경에서 수행됩니다. 로컬에 Gradle과 Android SDK가 없으면 실행된 것으로 간주하지 않습니다.

## BIGO 사용 주의
이 앱은 자작곡·이미지·가사·화면 안내의 자동 재생을 돕습니다. BIGO의 자리 비움 또는 무인 방송 정책을 우회하거나 노출 제한 해제를 보장하지 않습니다. 방송 운영은 BIGO의 현재 운영정책을 따라야 합니다.

## V3.1.7 저장소 잔여 파일 오류를 먼저 정리하는 방법

GitHub 웹 업로드는 기존 파일을 자동 삭제하지 않습니다. 저장소에 루트 Java 파일이나
`build/`가 이미 커밋되어 있다면 Actions에서 **Permanently remove stale repository files**를
한 번 실행한 뒤 APK 빌드 워크플로를 실행하세요. 자세한 순서는
`CLEAN_REPOSITORY_FIRST_KO.md`에 있습니다.


## V3.1.7 BIGO 네이티브 댓글 도구막대
원클릭 실방송은 MARU OCR 화면 공유를 시작하지 않습니다. BIGO 게임 LIVE의 댓글·입장·선물·팔로우 및 하단 마이크·카메라·소통·상점 도구막대를 사용합니다. 처음 한 번 BIGO의 다른 앱 위에 표시 권한을 확인하세요. 실방송 하단 220dp는 BIGO 도구막대용으로 비워 둡니다.
