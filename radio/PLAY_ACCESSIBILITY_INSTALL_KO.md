# 접근성 기능을 제거하지 않고 합법적으로 설치하기 — MARU RADIO V1.9.4

## 왜 이전 ZIP에서 APK를 열면 차단될까
사용자가 제공한 Google Play 프로텍트 팝업은 구글의 **기기 보호를 위해 앱 차단됨** 경고와 일치합니다. 구글 공식 개발자 안내(https://developers.google.com/android/play-protect/warning-dev-guidance?hl=ko)에 따르면, 인터넷/파일 관리자로 다운로드한 APK가 ACCESSIBILITY/READ_SMS/RECEIVE_SMS/NOTIFICATION_LISTENER 같은 민감한 권한을 선언하는 경우 지역·정책에 따라 설치가 **자동 차단**될 수 있습니다.
V1.9.2에는 BIGO 화면 자동 조작 실험용 접근성 서비스가 포함되었습니다. 따라서 APK에서 코드를 제거하거나 보호 기능을 끄는 것만이 답은 아니지만, **같은 웹 다운로드 방식으로 민감 권한을 유지하면서 차단을 무조건 해제할 수 있다는 보장은 없습니다.**

## 접근성 기능을 유지한 합법적인 배포 과정
1. MARU의 BigoControlAssist 및 isAccessibilityTool=false 접근성 XML은 그대로 보존합니다. 장애인 지원 목적 앱이 아니므로 `isAccessibilityTool=true`로 거짓 선언하지 않습니다.
2. Android 16 API 36을 타겟팅합니다(2026년 8월 31일 이후 새 Google Play 앱/업데이트 제출 요구).
3. Android App Bundle(AAB)을 **소유자 전용 업로드 서명키**로 빌드합니다. 프로젝트의 일회용 CI 디버그 서명키는 Google Play 릴리스에 사용하지 않습니다.
4. Play Console 개발자 계정을 이용해 앱을 등록하고 안정적인 패키지명 `com.maru.radio.ahwan`을 유지합니다. 앱 내부 정책/개인정보처리방침을 공개 호스팅합니다.
5. **앱 콘텐츠 → 접근성 API 사용 선언**에서 장애인용이 아닌 자동화 앱임을 정확히 설명하고, 명확한 인앱 공개/사용자 동의 및 BIGO에 한정된 정적 화면 동작을 설명합니다. 휴대폰에서 실제 동의/접근성 활성화/동작을 보여주는 검토 영상이 필요할 수 있습니다.
6. **내부 테스트** 또는 심사에 필요한 트랙에 AAB을 제출하고 Play Console에서 지시하는 검토/승인을 기다립니다. 거절되면 구체적인 정책 사유에 따라 수정하고 재심사합니다.
7. Play Console에서 발행한 **구글 플레이 테스트 참여 링크**로 형의 갤럭시에 설치를 시도합니다. 이는 인터넷에서 APK를 직접 내려받아 설치하는 방식과 다릅니다.

## 업로드 서명키는 형 소유로 보관
1. 본인 PC/안전한 키 관리 환경에서 `.jks` 업로드 키를 만들고 안전하게 백업합니다.
2. GitHub 저장소 **Settings → Secrets and variables → Actions**에 다음 네 가지 Secret을 저장합니다.
   - `MARU_UPLOAD_KEYSTORE_BASE64`: 업로드 키 파일을 base64로 인코딩한 값
   - `MARU_UPLOAD_STORE_PASSWORD`: 키 저장소 비밀번호
   - `MARU_UPLOAD_KEY_ALIAS`: 키 별명
   - `MARU_UPLOAD_KEY_PASSWORD`: 키 비밀번호
3. GitHub Actions → **MARU Ahwan signed Play App Bundle (manual upload only)** → Run workflow 실행.
4. 비밀키가 없으면 작업이 **실패하도록 설계**돼 있어 공개 CI 디버그 키로 업로드용 AAB가 만들어지는 일을 방지합니다.
5. 생성된 AAB를 Play Console에서 올리되, 승인/테스트 링크 생성은 개발자 계정에 접근할 수 있는 소유자만 할 수 있습니다.

## 현재 작업 환경의 한계
GitHub 소스/CI에는 연결되어 있으나 형의 Google Play Console 개발자 계정, AAB 업로드 서명키, 실물 갤럭시/BIGO 계정에는 접근할 수 없습니다. **APK를 만들어 직접 다운로드 링크를 드리더라도 이전과 같은 차단이 날 가능성이 있으므로 V1.9.4 직접 다운로드 APK는 설치하라고 안내하지 않습니다.**

## Google Play 정책 준수 점검
- 정적이고 목적이 제한된 규칙 기반 자동화는 요건 하에 가능하지만 자율적으로 작업/의사결정을 시작·계획·실행하는 접근성 자동화는 금지됩니다.
- 스크린 전체를 읽어 저장하거나 채팅/비밀번호 수집, 원격 감시, 접근성 설정/Play Protect 우회는 허용하지 않습니다.
- 실시간 BIGO 시청자 음악 청취와 서버 측 종료 성공은 여전히 실기기에서 검증되지 않았습니다.
- 공식 문서: https://support.google.com/googleplay/android-developer/answer/10964491?hl=ko
- Play 타겟 정책: https://support.google.com/googleplay/android-developer/answer/11926878?hl=ko

## 결론
기능을 남겨두는 배포판 소스·AAB 빌드 흐름은 준비할 수 있습니다. **정식 설치 완료는 Play Console 심사·정상 배포 뒤 실제 기기로 검증되어야 합니다.** Play 프로텍트를 끄거나 우회하는 방식은 제공하지 않습니다.
