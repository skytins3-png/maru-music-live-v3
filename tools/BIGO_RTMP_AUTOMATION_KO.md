# MARU → BIGO 공식 RTMP 예약 송출 실험 (PC, OBS 불필요)

**이 프로그램은 'BIGO 휴대폰 오디오 라이브' 자동 터치 기능이 아닙니다.**
BIGO 공식 OBS Stream 경로가 본인 계정에서 열려 있다면, OBS 앱 없이 PC의
FFmpeg에서 공식 RTMP 연결을 예약된 시각에 열고 끊는 **별도 기술 경로**입니다.
공식 BIGO 안내: https://www.bigo.tv/ko/bigo-streamer/obs/

## 자동이 되는 것과 아닌 것

- ✅ PC의 파일을 읽어 원곡을 재생하고 서버 연결을 예약 시각에 생성.
- ✅ 음악 장르 폴더 한국노래 → 중국노래 → 서양노래 → 연주곡 순환.
- ✅ 완전히 동일한 음원 파일은 SHA256 기준 한 번만 사용, 원본은 변경·삭제하지 않음.
- ✅ 시작시각/종료시각 지정, 다음날 새벽 종료, 해당 시각에 RTMP 미디어 송출 중단.
- ✅ 카메라/마이크 촬영 없이 정지 이미지(없다면 단색 영상) + 음악 송출.
- ❗ 오디오를 RTMP용 AAC 256k로 인코딩하며 Suno MP3/WAV 원본의 **바이트 동일성은 유지할 수 없음**.
- ❗ BIGO가 제공한 RTMP URL/스트림키가 있어야 하며, 계정마다 이 기능이 없을 수 있음.
- ❗ RTMP는 모바일의 '오디오 라이브' 메뉴와 **같은 방송 모드가 아닐 수 있습니다**.
- ❗ 공식 안내상 RTMP 연결 시 라이브가 시작될 수 있지만, 연결을 끊으면
  **BIGO 서버에서 실제 방이 종료됐는지 별도 확인해야 합니다**.
- ❗ 휴대폰만으로 돌아가는 앱이 아님. PC가 켜져 있고 Python 대기 프로세스가 실행 중이어야 함.
- ❗ BIGO 댓글·선물 데이터 자동 수집/답변 및 모바일 믹서 마이크 1 설정 미지원.

## 준비물 (계정 소유자가 한 번 설정)

1. BIGO 계정에서 OBS Stream이 허용되는지 확인합니다.
   공식 가이드에 따라 개인센터 → 설정 → OBS Stream을 켜고 **서버 URL과 스트림 키**를
   확인합니다. 모든 계정에 제공된다는 보장은 없습니다.
2. 이 스트림 키는 계정의 비밀입니다. GitHub/ChatGPT 채팅/스크린샷에 **절대 올리지 마세요**.
3. Windows PC에 Python 3.10 이상 및 FFmpeg를 설치하고 명령 프롬프트에서
   `python --version`, `ffmpeg -version`이 되는지 확인합니다.
4. PC에 음악 폴더를 만들고 아래처럼 분류합니다. 빈 장르는 생략해도 됩니다.

```text
C:\\MARU\\music\\
   한국노래\\ (MP3/WAV)
   중국노래\\ (MP3/WAV)
   서양노래\\ (MP3/WAV)
   연주곡\\  (MP3/WAV)
```

5. 공개 저장소 안이 아닌 **개인 PC에** `C:\\MARU\\schedule.json` 만들기:

```json
{
  "enabled": false,
  "start": "20:00",
  "end": "02:00",
  "music_dir": "C:/MARU/music",
  "cover_image": null,
  "genre_order": ["한국노래", "중국노래", "서양노래", "연주곡"]
}
```

6. 방송 시간/음악 목록 확인만 할 때 (네트워크나 BIGO 연결 없음):

```powershell
python tools/rtmp_scheduled_radio.py --config C:/MARU/schedule.json --plan
```

## 실제 방송 예약 (내 방송 계정 / 내가 선택한 시간만)

계정에서 별도 발급받은 URL+키를 **연결 가능한 하나의 주소로 로컬 환경변수**
`MARU_RTMP_URL`에 넣어야 합니다. 예제의 가짜 주소를 입력해도 방송되지 않습니다.
URL과 키의 결합 형식은 BIGO에서 받은 서버 URL 형태에 맞춰야 합니다.
그 정보를 이 문서, 코드, 공개 GitHub Secret 아닌 일반 파일에 저장하지 마세요.

개인 PowerShell 세션 안에서만 입력하는 예:

```powershell
$env:MARU_RTMP_URL = Read-Host "내 BIGO 공식 전체 RTMP URL+키 (화면에 노출됨)"
# 로컬 설정 파일에서 enabled 값을 true로 바꾸고, 실제 생방송임을 확인합니다.
python tools/rtmp_scheduled_radio.py --config C:/MARU/schedule.json --arm
Remove-Item Env:MARU_RTMP_URL
```

예약된 시각이 되면 FFmpeg가 공식 RTMP 주소에 연결하고 음악을 송출합니다.
예약 종료시각이 되면 FFmpeg 송출을 종료합니다. 안전한 네트워크 조건 및
실제 시청자 측 방송 상태 확인은 계정 소유자가 담당합니다.

**주의:** 이 버전은 무한 반복 자동 서비스가 아니라 다음 번 시작과 종료를 한 쌍으로
실행하는 일회성 예약입니다. PC가 꺼지거나 절전/로그오프되면 실패할 수 있습니다.
여러 시간대별 예약, 부팅 후 자동 실행, 방송 상태 확인은 실제 계정·송출 테스트 후
별도 개발합니다. 자정 넘김 로직은 오프라인 테스트했습니다.

**BIGO 모바일 오디오 LIVE만 사용하고 PC를 쓰지 않으려는 경우**:
이 PC용 공식 RTMP 경로는 요구사항을 만족하지 않습니다. 모바일 Audio LIVE의
공식 시작/종료 API가 확인되지 않아 기존 MARU 앱에서 실제 무인 BIGO
시작/종료라고 광고할 수 없습니다. 정확한 연동을 하려면 BIGO의 제공 범위 확인이
먼저 필요합니다.
