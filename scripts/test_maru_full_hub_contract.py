#!/usr/bin/env python3
"""Fail-closed source contract for distinct MARU ONE unified mobile installer.
A passing source contract does not establish Android UI or BIGO live broadcast.
"""
from pathlib import Path
main=Path("app/src/main/java/com/maru/musiclive/MainActivity.java").read_text(encoding="utf-8")
gradle=Path("app/build.gradle").read_text(encoding="utf-8")
trust=Path("app/src/main/java/com/maru/musiclive/TrustedWebStudio.java").read_text(encoding="utf-8")
scanner=Path("app/src/main/java/com/maru/musiclive/DeviceMusicScanner.java").read_text(encoding="utf-8")
checks={
"distinct package": "com.maru.musiclive.fullhub.v20261124" in gradle,
"distinct app label": "MARU ONE 통합 조종실" in gradle,
"full-hub build constant": "'MARU_FULL_HUB'" in gradle,
"full-hub native on-create": "if (BuildConfig.MARU_FULL_HUB) showMaruUnifiedHome();" in main,
"native Android music scanner action": 'button("휴대폰 전체 노래 자동 검색·등록"' in main and "scanDeviceMusic(true)" in main,
"seven original role names": all(x in main for x in ["MARU · LEO · TOTO · MIMI · HAPPY · TORI · NURI"]),
"seven-role activity page": 'openMaruHubPage("mobile-hub/agents.html"' in main,
"ten experts page": 'openMaruHubPage("music-share/experts.html"' in main,
"agency page": 'openMaruHubPage("mobile-hub/agency.html"' in main,
"30 singer original-song import": 'openMaruHubPage("vocal-studio/mobile-suno.html"' in main,
"radio prerecord preview": 'openMaruHubPage("vocal-studio/radio-show.html"' in main,
"studio native embedded browser": "openEmbeddedWebStudio(TRUSTED_STUDIO_URL" in main,
"music share": 'openMaruHubPage("music-share/index.html"' in main,
"legacy inventory": 'openMaruHubPage("mobile-hub/legacy.html"' in main,
"2025 unavailable-source honesty": "마루총괄SUP_ALL_IN_ONE.py" in main and "복원했다고 표시하지 않습니다" in main,
"independent AI limitations": "독립 AI 모델의 상시 실행" in main,
"native/web storage separation": "두 보관함이 자동으로 완전 동기화되지는 않습니다" in main,
"web navigation HTTPS allowlist": '"https".equalsIgnoreCase(parsed.getScheme())' in trust and 'maru-ai-song-studio/' in trust,
"no unsafe JavaScript bridge": "addJavascriptInterface" not in main,
"native file chooser": "webStudioFileLauncher.launch(picker)" in main,
"preserve songs during scan": "Incremental, non-destructive import." in main,
"no access all files": "MANAGE_EXTERNAL_STORAGE" not in Path("app/src/main/AndroidManifest.xml").read_text(),
}
for k,v in checks.items():
 print(("PASS" if v else "FAIL")+" "+k)
assert all(checks.values()),"Missing or broken full-hub contract"
print(f"{len(checks)} source-level checks passed; real device usage still unverified.")
