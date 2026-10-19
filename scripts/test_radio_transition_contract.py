#!/usr/bin/env python3
"""Fail-closed checks for an independent, non-destructive MARU RADIO install."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
source = (root / "radio/build.gradle").read_text(encoding="utf-8")
transition = (root / "radio_transition/build.gradle").read_text(encoding="utf-8")
settings = (root / "settings.gradle").read_text(encoding="utf-8")
main = (root / "radio/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
new = (root / "radio_transition/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
ui = (root / "radio/src/main/java/com/maru/radio/RadioActivity.java").read_text(encoding="utf-8")
namespace = "{http://schemas.android.com/apk/res/android}"

checks = {
    "old package retained": "applicationId 'com.maru.radio.safev16'" in source,
    "new standalone package": "applicationId 'com.maru.radio.transition.v17'" in transition,
    "both app modules coexist": "include ':radio'" in settings and "include ':radio_transition'" in settings,
    "new app does not reuse old app-owned private storage": "getSharedPreferences(" not in transition
        and "java.srcDirs = ['../radio/src/main/java']" in transition,
    "new app version is independent": "versionName '1.7.0'" in transition,
    "new distinct launcher label": 'android:label="MARU RADIO 1.7 안전전환"' in new,
    "old launcher unchanged": '@string/app_name' in main,
    "non-destructive setup visible": '기존 MARU RADIO는 삭제되거나 바뀌지 않았습니다' in ui
        and '저장곡과 설정이 자동 이전되지 않습니다' in ui,
    "re-select original files through Android SAF": 'this::pickDownloads' in ui
        and 'Intent.ACTION_OPEN_DOCUMENT' in ui,
    "warn actual live transmission unverified": 'BIGO 청취자 실제 소리 전달은 별도 기기로 확인' in ui,
    "playback service stays private": 'android:name=".RadioPlaybackService"' in new
        and 'android:exported="false"' in new,
    "no permission escalation": all(p not in new for p in (
        "RECORD_AUDIO", "BIND_ACCESSIBILITY_SERVICE", "READ_MEDIA_AUDIO",
        "READ_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "MEDIA_PROJECTION",
        "QUERY_ALL_PACKAGES", "SYSTEM_ALERT_WINDOW")),
    "old/new permission sets match": sorted(
        p.attrib.get(namespace+"name") for p in ET.fromstring(main).findall("uses-permission")
    ) == sorted(
        p.attrib.get(namespace+"name") for p in ET.fromstring(new).findall("uses-permission")
    ),
}
for name, okay in checks.items():
    print(("PASS" if okay else "FAIL") + ": " + name)
if not all(checks.values()):
    raise SystemExit("MARU RADIO safe side-by-side contract FAILED")
print("MARU RADIO V1.7 separate install contract PASS")
