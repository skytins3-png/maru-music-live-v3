## Suno + Ahwan integrated companion (development, not released to installed APK)

- MARU's setup screen offers **① 내 MP3 + 짧은 영어 토크**, **② Suno 음악 + 아환 자동 토크**, **③ Suno 음악 + 아환 수동 토크**, **아환 짧은 토크 지금 말하기**, and **Suno 아환 토크 완전 종료**.
- MARU's full-screen home menu and Samsung pop-up mini controller also expose the Suno companion.
- Suno mode pauses MARU's *own* player before starting an **opt-in foreground TextToSpeech service**, then opens the official Android Suno app `com.suno.android` or Suno's HTTPS site. Suno itself remains the owner of its song playback, playlist, rights, and account.
- Automatic mode speaks four short original Ahwan/guest turns **after an initial ~3 minutes and at approximately 6-minute intervals**. A notification button triggers a manual short exchange. Neither the companion nor the user should call this 'between songs': no Suno title, playlist, or playback timeline is read.
- During a spoken exchange, the service requests only `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, and abandons focus as soon as it finishes. Android may reduce Suno's volume, interrupt playback, or reject focus, depending on the OS and third-party app; **continuous mixed audio is NOT guaranteed**.
- The service needs no accessibility, notification listener, cross-app capture, overlay, Suno token, or network API. It does not read Suno data, control Suno buttons, capture Suno audio, mix audio for BIGO, or automatically start/stop BIGO's broadcast.
- BIGO listener output must be checked separately using an actual second listening device; it is **not** proven by hearing both apps locally.
- Important: the prior official V3.2.23 APK is signed by a different certificate from the development build; **do not uninstall the installed app** or install an incompatible APK. The release job is guarded against replacing V3.2.23 with a mismatched signer. A matching signing key or user-consented migration is required before any in-place update.
- If Android audio focus prevents coexistence, use MARU's internally imported original MP3 radio mode, which does not rely on Suno playback, instead.

## V3.2.24 · 삼성 갤럭시 팝업 화면용 미니 조종창

- MARU 앱의 '미니 조종창'에서 이전곡/재생·일시정지/다음곡/BIGO 열기를 작은 화면으로 조작합니다.
- 갤럭시 **최근 앱 → MARU 아이콘 → 팝업 화면으로 열기**는 사용자가 직접 실행합니다. 방송 화면 위에 시스템 팝업으로 올리는 방식입니다.
- 시스템 팝업 모드는 기기/One UI/BIGO 호환 여부에 따라 표시되지 않을 수 있습니다. 표시되지 않으면 '분할 화면으로 열기'를 대신 사용하세요.
- SYSTEM_ALERT_WINDOW(다른 앱 위에 표시), 접근성 권한, BIGO 자동 터치를 새로 추가하지 않았습니다.
- 기존 V3.2.23과 Android 서명 인증서가 다른 경우 덮어쓰기 설치를 할 수 없으며, 기존 설치 앱을 삭제하면 앱 내부 목록/설정이 사라질 수 있으므로 삭제하지 마세요.
- 미니 창 표시만으로 BIGO 청취자에게 MARU 음악/TTS가 송출되는 것은 아닙니다. BIGO 내부 오디오 입력과 별도 청취 단말로 검증해야 합니다.

# MARU RADIO EN V3.2.24 · English Weather & News first install

**If you have never installed MARU**, use the single v3.2.24 APK from the signed official GitHub Release. This is Android package `com.maru.musiclive.english` and home-screen name `MARU RADIO EN`; no previous versions need to be installed. Download only `MARU-RADIO.apk` under the v3.2.24 Release Assets; source ZIP is not an installer.

- The top **Daily Weather & News Radio · Start English Show** button opens the verified MARU RADIO web page, which runs Ahwan's fictional English DJ/guest banter, producer-approved listener stories, optional local MP3/WAV, cited Open-Meteo weather for user-selectable cities and timestamped BBC headline updates.
- Automatic upstream retrieval is done by the independent `maru-ai-song-studio` GitHub Actions scheduled job (~every 4 hours). A new APK is NOT needed for each news/weather refresh. Data may lag; if stale or unavailable, the show continues without claiming fake live facts.
- Click Start radio once in the browser. This is an online web-radio **preview**, not automatic entry into BIGO or routing audio to BIGO microphone. Android battery/screens-off may suspend it.
- Other local radio buttons still play user-added files or old fictional TTS; they do not silently gain the new weather/news data. Only the labelled online weather/news button opens the daily content.
- **Important signing limitation**: current workflow builds use ephemeral debug signing. This fresh install will succeed on a compatible Android device if the package does not already exist, but future *in-place updates* require a securely stored, fixed release keystore in GitHub Secrets. Do not uninstall older apps merely to install another build. No real-device install or BIGO mic routing is certified by CI.

# MARU RADIO EN V3.2.22 — English human-style talk and Suno playlist workflow

- Spoken radio conversation **defaults to English**. On-air characters are host Ahwan and one fictional guest for the whole show, not 32 people repeating the same topic. Episodes rotate 16 original everyday conversation themes and include gentle humor, empathy, informal reactions, and short textual chuckles. This is still **prewritten TTS**, not a spontaneous human recording or real voice singing. Laughter text is not the same as a genuine recorded laughter sound effect.
- `EnglishRadioTalkShow` builds 40+ conversational turns for long show mode; short show mode remains in English too. Guests use the phone's available offline English TTS voice choices; no real-person voice clones or unauthorized recordings.
- Story text is included **only when approved and entered** via the app by the producer; song requests play imported, locally available originals after the current track. The English voice reads story text as entered; there is no automatic Korean-to-English translation.
- `Suno playlist` button stores and opens an official `https://suno.com/playlist/...` URL, then offers Android multi-file MP3/WAV selection. Suno accounts, playlists and private song downloads are not scraped; actual original audio files are added only through authorized user selection (or Suno share of audio files).
- `MUSIC SHARE` button opens the separate static website's music uploads, free creator-approved downloads and consent-based public story submission. The public story route requires GitHub login; it is **not** a private inbox and does not auto-feed unapproved content to live broadcast.
- **Side-by-side install**: V3.2.22 uses `com.maru.musiclive.english` and launcher name **MARU RADIO EN**, so it does not overwrite the previous `com.maru.musiclive.ocr` or `com.maru.musiclive.station` signed with different temporary build keys. Existing app private data do not automatically move.
- **Important unsolved signing issue**: hosted GitHub Actions still uses per-build debug signing keys. Without a durable privately stored signing key configured as GitHub Actions Secrets, subsequent releases of the *same* English app cannot reliably install over this one. Never commit a keystore/password to the public repository.
- No direct BIGO microphone mixer injection, no actual 32 recorded singing voices, no automatic Suno playlist download, no remote private story server, and no real-device broadcast certification.

# MARU RADIO STATION V3.2.21 — 기존 앱 보존·별도 설치

## 설치 불가 오류를 해결하는 방법
- `V3.2.19`와 `V3.2.20`은 같은 Android 패키지명(`com.maru.musiclive.ocr`)이지만 다른 임시 서명키로 제작되어 **업데이트 설치가 차단**됩니다. APK 다운로드 문제가 아닙니다.
- 이번 버전은 `applicationId = com.maru.musiclive.station`과 홈 화면 이름 **MARU RADIO STATION**을 사용합니다. **기존 MARU MUSIC LIVE를 삭제하지 않고 두 앱을 나란히 설치**합니다.
- 새 앱은 기존 앱의 내부 비공개 노래·설정·사연을 자동으로 가져올 수 없습니다. Suno 원곡이 다운로드 폴더 등 공유 저장공간에 있다면 새 앱의 '노래 직접 추가'로 다시 선택하세요. 기존 앱의 내부 데이터는 그대로 유지됩니다.
- 현재는 서명이 매 GitHub Actions 빌드마다 바뀌므로 향후 `station` 앱을 *그대로* 업데이트할 수 있다는 보장은 없습니다. 장기간 안전한 업데이트를 위해 비공개 영구 서명키를 GitHub Actions Secrets에 설정해야 합니다. 개인키·암호를 공개 저장소나 채팅에 올리지 마세요.
- GitHub Actions에서 새 패키지명, 버전코드 3040, ARM64 지원, 네이티브 OCR 모듈, APK 크기, 서명·압축 무결성, 단위 테스트 및 린트를 모두 확인합니다.
- 기존 3.2.20의 긴 가상 AI 가수 초대석 대화·동의받은 사연·등록 곡 신청·검토 자료 메모는 유지합니다. 실제 BIGO 송출 및 32명 고유 가창 음원은 별도 구현·검증이 필요합니다.

# MARU RADIO V3.2.20 — AI 초대석·청취자 사연·신청곡 라디오

### 이 버전에서 구현한 실제 범위
- 아환 DJ와 32명 가상 AI 가수가 **한 곡 재생 전 약 40여 차례 TTS로 대화**하는 긴 초대석 모드. 노래는 별도로 등록된 원곡 MP3/WAV를 재생하고, 대화는 가상 캐릭터 대본입니다.
- 방송 운영자가 **사연 공개 동의를 확인하고 수동으로 접수**하면 곡 사이 초대석에서 1건씩 읽습니다. 공개 동의 없는 사연이나 BIGO 댓글 자동 수집은 없습니다.
- 등록한 노래를 신청곡으로 선택하면 현재 곡이 끝난 후 우선 편성합니다. 랜덤 재생의 20분 동일곡 중복 방지 정책은 유지하며, 실제 재생을 시작한 후에만 신청곡 접수를 처리 완료로 표시합니다.
- 유저가 지정한 **https 자료 사이트를 직접 브라우저로 열고 검토 후 입력한 요약만** 앱 안에 저장해 이후 초대석 주제로 순환 소개합니다. 무단 크롤링·자동 로그인·무검증 인터넷 학습은 하지 않습니다.
- 사연과 학습 메모는 앱의 비공개 로컬 설정에만 저장하며, 방송 이외의 인터넷 서버에 자동 업로드하지 않습니다. 원치 않는 실명·개인정보를 넣지 마세요.
- 실제 AI 가창 보컬 32명, Suno 자동 생성, BIGO 오디오 LIVE 믹서 자동 연결·실시간 댓글 수집, 무인 웹 학습, 안전한 자동 APK 설치는 여전히 미구현/미검증입니다.
- 갤럭시 ARM64 전용 경량 APK를 유지합니다. 기존 v3.2.19 APK와의 **디지털 서명이 다르면 바로 덮어쓸 수 없습니다**. 기존 데이터를 먼저 백업하지 않은 상태에서 앱을 삭제하지 마세요.

# MARU RADIO V3.2.19 — Galaxy ARM64 경량 APK

## 첫 설치 파일을 51MB에서 줄이기 위한 실전 변경

- 기존 V3.2.18 APK에는 x86/x86_64/armeabi-v7a/arm64-v8a 네 종류의 Google ML Kit OCR 네이티브 라이브러리가 모두 포함되어 **약 51MB**였습니다.
- 삼성 Galaxy S26 Ultra에 맞춰 **arm64-v8a** 라이브러리만 포함하도록 Android Gradle `ndk.abiFilters` 설정을 변경했습니다.
- 한국어·중국어 글자 인식 모델과 OCR 코드, 기존 원곡 샘플 및 음악 플레이어를 삭제하지 않습니다. 대신 32비트 또는 PC x86 기기에서는 이 APK를 설치할 수 없을 수 있습니다.
- GitHub Actions에서 결과 APK의 arm64-v8a 라이브러리 존재 여부, 다른 아키텍처 라이브러리 부재, **28MiB 이하** 크기, 버전Code 3038/3.2.19, 무결성 및 서명을 검증한 뒤 배포합니다.
- 처음 설치하는 휴대폰을 대상으로 합니다. Android 앱 업데이트는 서명이 같은 APK끼리만 가능하며, GitHub 임시 개발용 서명으로 빌드된 3.2.17/3.2.18의 기존 설치판과 바로 호환되지는 않습니다. 기존 앱을 삭제하지 마세요.
- Suno 실제 32명 보컬 음원 제작 및 BIGO 청취자 직접 송출은 완료 또는 검증되지 않았습니다.


## V3.2.18 · 음악 중단 방지 작업

- Android TTS 엔진이 응답 없이 멈추면 15초 후 대화를 건너뛰고 다음 곡을 재생합니다. 기존 35초 발화 제한은 유지합니다.
- 원본 MP3/WAV 파일이 삭제되었거나 읽기 권한이 없거나 플레이어에서 오류가 발생하면 다음 정상 파일을 자동 시도합니다. 잘못된 파일은 사용자 직접 재선택 또는 목록 변경 전까지 건너뛰며, **모든 곡이 실패하면 무한 반복하지 않고 멈춥니다.**
- 곡 사이 진행 도중 **다음 곡** 버튼을 누르면 대화를 건너뛰고 다음 음악을 시작하며, **일시정지** 버튼은 진행 멘트를 중단합니다.
- 취소된 이전 안내에서 늦게 도착한 TTS 재생 명령은 고유 토큰 검증으로 거부해 수동 선택 곡을 덮어쓰지 않습니다.
- 기존 32명은 독립된 가상 DJ·게스트 대화 캐릭터입니다. 실제 서로 다른 Suno 노래 보컬과 32곡 MP3 제작, BIGO LIVE 청취자 송출은 아직 미완료입니다.
- GitHub Actions 빌드·단위 테스트·린트·설치 APK 검사와 GitHub 정식 배포를 활용합니다. APK 간 **서명 불일치 시 기존 앱을 삭제하지 마세요.** 휴대폰 실행·BIGO 송출은 별도 실기기 검증이 필요합니다.


## V3.2.17 · 자동 업데이트 확인 (새 APK 최초 1회 설치 필요)

- MARU 앱을 열 때 백그라운드 네트워크 스레드에서 하루 1회 공식 공개 GitHub Releases 최신 **정식 릴리스**를 확인합니다. 원격 다운로드는 HTTPS로만 진행됩니다.
- 화면 메뉴 **새 버전 확인 · 업데이트** 및 설정 화면의 **새 버전 확인 · 업데이트 다운로드** 버튼으로 언제든 다시 확인합니다.
- 등록된 최신 버전이 설치판보다 높은 경우에만 APK 다운로드 **확인 대화상자**를 표시합니다. 사용자가 직접 다운로드를 누르면 GitHub Release의 `MARU-RADIO.apk` 주소를 브라우저로 엽니다.
- 임의의 외부 URL이나 프리릴리스·베타, 버전이 낮거나 같은 APK는 자동 업데이트 대상으로 취급하지 않습니다.
- 배포본에 기록된 서명 지문과 현재 설치된 앱의 서명 지문을 먼저 비교합니다. 서명이 다르거나 확인되지 않으면 다운로드 버튼을 표시하지 않고 기존 앱 삭제 금지 경고를 제공합니다. 기존 V3.2.15와 V3.2.16도 실제 서명이 달랐기 때문에 **안전한 서명키 관리가 완성되기 전에는 기존 설치판을 덮어쓰는 자동 업데이트가 불가능**합니다.
- **앱은 APK를 자동 설치하거나 Android 승인 화면을 우회하지 않습니다.** OS 또는 브라우저의 설치 확인은 사용자 직접 조작이 필요합니다. 별도의 위험한 접근성·앱 설치 권한을 요청하지 않습니다.
- GitHub Actions가 메인 브랜치에서 테스트·린트·서명·버전·APK 파일 검사를 모두 통과한 뒤 공개 GitHub Release에 해당 APK를 배포합니다. 새 버전이 필요할 때마다 빌드와 태그 버전을 올려야 합니다.
- 최초 업데이트 검사 기능은 **V3.2.17부터** 있으므로, 기존 3.2.16 이하는 먼저 새 APK를 수동으로 설치해야 합니다.
- 서명 불일치 시 Android는 업데이트 설치를 막습니다. 기존 앱을 삭제하면 데이터가 손실될 수 있으니 삭제하지 말고 설치 파일의 서명과 기존 앱의 서명을 확인해야 합니다.
- 매일 새로운 대화 주제로 바뀌는 기능은 인터넷 업데이트와는 별도이며, 음원 자동 제작 또는 BIGO 직접 송출까지 자동화했다는 의미가 아닙니다.

## 이전 버전 안내

## V3.2.16 · 신규 설치 파일 구별 안내

- 앱 내부 버전 **3.2.16 (versionCode 3035)**. 이전의 3.2.15 (versionCode 3034) APK를 이름만 바꾼 파일이 아닙니다.
- 아환과 32명의 독창적 가상 아티스트가 곡 사이 차례로 대화하며 매일 다른 오프라인 주제를 사용합니다. 한 곡만 반복해도 방송 회차를 기준으로 출연 게스트가 교대합니다.
- MARU 앱 안에서 32명 가수별 발라드·트로트·팝송·중국어·러시아어 보컬 제작표와 Suno Custom 스타일을 보고 복사할 수 있습니다. 등록곡 길게 누르기로 가수 배정도 할 수 있습니다.
- **중요:** 32명 각각의 실제 Suno 가창 음색·Persona·신곡 오디오 제작/검증은 아직 완료되지 않았습니다. 휴대폰 TTS의 여러 캐릭터 음성이 전문 AI 가수 보컬을 의미하지 않습니다.
- 날짜별 대화 주제는 앱 재설치 없이 바뀝니다. 새로운 APK 버전 설치는 사용자 승인이 필요합니다.
- BIGO 실제 오디오 LIVE 송출과 화면 잠금 중 지속성은 스마트폰에서 별도 실기기 테스트가 필요합니다.

## 안전한 음악 방송 사용 안내

안드로이드에서 음악을 백그라운드 재생하고 BIGO LIVE를 여는 **최소 권한 음악 방송** 버전입니다.

## V3.2.15의 Play 프로텍트 대응

기존 V3.2.13 APK에는 다른 앱의 화면을 탐색·클릭하는 Android 접근성 서비스가 선언되어 있었습니다. 인터넷에서 다운로드한 앱에 이런 민감 권한이 있으면 Google Play 프로텍트에서 설치를 차단할 수 있습니다.

V3.2.15에서는 **접근성 서비스와 해당 권한 선언 및 제3자 앱 자동 클릭 구현을 제거**했습니다. 다른 앱 화면을 자동으로 조작할 수 없으며 접근성 권한을 요구하지 않습니다.

## 사용 순서

1. MARU에서 원곡 음악 목록을 선택해 재생합니다.
2. ‘일반 LIVE 음악방송 시작’ 또는 ‘오디오 LIVE 음악방송 시작’을 누르면 MARU 음악을 계속 재생하며 BIGO 앱을 엽니다.
3. BIGO에서 LIVE → 방송 종류(일반/오디오) → 방송 시작을 직접 눌러 진행합니다.
4. 실제 시청자 기기에서 음악이 잘 들리는지 확인합니다. BIGO의 음성 입력/미디어 캡처 정책은 MARU가 무시하거나 강제로 변경할 수 없습니다.

## V3.2.15 · 오디오 LIVE 경량판

- 첫 화면은 **BIGO 오디오 LIVE, Suno 원곡 추가·공유, 노래 목록, 곡별 이미지·영상, 가사, 곡 사이 다국어 안내** 중심으로 정리했습니다.
- 사용하지 않던 **일반 LIVE 시작, 게임 LIVE 보조·원클릭 테스트, OCR 테스트 메뉴, AI 학습·백업 관리 메뉴, 가짜 이벤트·음성 검사 메뉴**는 실제 사용자 UI 및 해당 기능에만 필요했던 MainActivity 메서드에서 제거했습니다.
- 기존에 쓰던 댓글 화면 답변 및 입장 이미지, 노래·영상 연결은 유지합니다.
- 시작 시 휴대폰 전체 노래의 SHA-256 자동 검사(저장소가 클 때 과도한 CPU·I/O)를 중단했습니다. 전체 음악 스캔은 사용자가 버튼을 눌렀을 때만 실행합니다. **기존 저장 목록은 유지합니다.**
- 도달할 수 없던 구형 YouTube WebView 액티비티를 APK에서 제거합니다. Suno·YouTube 앱은 평소처럼 휴대폰에서 별도로 사용합니다.
- **중요:** BIGO 오디오 LIVE에 대한 송출 음량 고정은 여전히 BIGO가 지원하는 로컬 BGM 입력 경로/실기기 확인이 필요합니다. 이 릴리스로 Suno **앱 직접 재생 중인 소리**까지 방송 출력에 볼륨 독립적으로 넣었다고 주장하지 않습니다.

## Suno 원본 음악 / 방송 음량 안내

- MARU에서 **노래 직접 추가 (Suno MP3/WAV 포함)**을 누르고 Suno에서 정식으로 내려받은 MP3/WAV 파일을 선택하면 파일을 재인코딩하거나 음질개선하지 않고 재생합니다. Suno 앱에서 재생 중인 소리를 MARU가 강제로 빼오지는 않습니다.
- BIGO 연동 중에는 MARU **앱 내부**의 일시적 음량 감소(오디오 더킹) 대신 재생 게인을 100%로 유지합니다. 음악 재생량을 18%로 줄이는 말하기 버튼도 BIGO 모드에서는 음악을 낮추지 않습니다.
- **주의: 휴대폰 볼륨 버튼과 청취자에게 나가는 볼륨의 분리는 이 변경만으로 보장할 수 없습니다.** Android 출력 경로와 BIGO 오디오 라이브의 캡처/믹서 방식에 달려 있습니다. BIGO 내장 BGM에 음원을 입력할 수 있으면 그 경로를 먼저 검증하세요.
- Suno 자체 앱의 오디오를 BIGO로 직접 틀 경우 이 MARU 변경 사항이 적용되지 않습니다.

## Suno 앱 → MARU 원본 공유 (신규)
- Suno에서 **본인 소유 원곡 MP3/WAV를 내려받고**, 내 파일 앱 등에서 **공유 → MARU MUSIC**을 선택하세요. 여러 음악 파일 공유도 지원하며, 같은 파일의 완전 동일 데이터는 SHA-256으로 중복 보관하지 않습니다.
- 수신한 파일은 앱 내부 저장소에 **바이트 그대로 복사**하므로 앱 전환·재시작 후에도 목록에서 재생됩니다. 음악 압축, 리마스터, 음량 정규화, 형식 변환을 하지 않습니다.
- MARU의 **Suno → BIGO 원곡 방송 안내**에서 BGM/로컬 음원 사용 안내와 해당 기기의 제약을 볼 수 있습니다.
- **Suno 앱의 재생음을 직접 훔쳐오거나 BIGO 오디오 LIVE의 마이크로 주입하지 않습니다.** Android의 다른 앱 재생 캡처는 원본 앱의 캡처 정책 및 사용자 승인에 달리고, 캡처했다고 해도 BIGO 마이크로 전달할 표준 Android API가 없습니다.
- 휴대폰 물리 볼륨키가 방송에 영향이 없는 음원 송출은 BIGO 자체 BGM/로컬 음악 입력처럼 **방송 앱 내부에서 직접 음원을 재생하는 지원 경로**가 필요한 경우가 있습니다. BIGO의 현재 계정/방송 모드에서 로컬 음원 선택이 되는지 실제 화면과 시청자 기기로 확인해야 합니다.

### 오디오 LIVE 테스트
1. Suno 원곡 MP3/WAV를 휴대폰에 내려받고 BIGO 오디오 LIVE의 **음악/배경음악/믹서**에서 로컬 파일 선택 항목을 확인합니다(없을 수 있음).
2. 로컬 원곡을 선택할 수 있으면 그 음원을 BIGO 내에서 재생한 뒤 **별도 시청자 기기**로 들으며 휴대폰 측면 볼륨 0→25→50→100%를 바꿉니다. 시청자 음량이 유지되는지 확인합니다.
3. 같은 방식으로 Suno 앱에서 직접 재생한 음악도 테스트합니다. Suno 원본 앱의 캡처 허용 및 BIGO 내부 처리에 따라 전달되지 않거나 음량이 함께 변할 수 있습니다.
4. 차이가 있으면 **BIGO 내장 음악 입력** 경로와 **외부 Suno 앱 재생** 경로를 구분해서 기록합니다. MARU 내부 게인 고정은 별도 경로이며 송출 분리 보장은 아닙니다.

## 유지되는 기능

- Android PlaybackService 포그라운드 백그라운드 원곡 재생
- 오디오 우선권 및 재생 중지 복구 처리
- 곡 사이 5개 언어 안내, 곡 반복 및 랜덤 중복 방지
- 음악 목록, 이미지·영상·가사, 사용자가 승인한 화면 OCR 기능
- 실제 LIVE 시작과 개인정보 권한 부여는 사용자 선택

## 주의

Google Play 프로텍트 경고가 사라진다고 보장하지 않습니다. Play 프로텍트를 끄거나 경고를 우회하지 마세요. 계속 차단되면 설치를 중단하고 앱을 검토·재분류 요청해야 합니다.

GitHub Actions에서 만든 릴리스 APK는 개발용 debug 서명 방식입니다. 기존 설치 앱과 서명이 다르면 '업데이트 불가'가 뜰 수 있습니다. 기존 앱을 삭제하면 사용자 데이터가 사라질 수 있으니 삭제하지 마세요.

## 방송 채팅 정책

이 방송은 자작곡을 재생하며 외부 신청곡은 받지 않습니다. 신청곡 거절 정책은 학습으로 바뀌지 않습니다.
대화형 AI는 화면 답변용이며 직접 입력 예시로 “게임 좋아하세요?” 같은 일상 질문을 표시할 수 있습니다.

실행 UI 설명: 습득·진화 대화형 AI · 작은 화면 답변 · 키보드 없음. 이 기능은 자작곡 방송 정책과 분리되어 작동합니다.
