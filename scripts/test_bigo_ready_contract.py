#!/usr/bin/env python3
"""Enforce honest Android BIGO preparation and user-confirmed reception.
No false claim of BIGO login, UI automation or audio microphone injection.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
main=(root/"app/src/main/java/com/maru/musiclive/MainActivity.java").read_text(encoding="utf8")
exporter=(root/"app/src/main/java/com/maru/musiclive/BigoMusicExporter.java").read_text(encoding="utf8")
plan=(root/"app/src/main/java/com/maru/musiclive/OneClickBroadcastPlan.java").read_text(encoding="utf8")
consumer=(root/"app/src/main/java/com/maru/musiclive/BigoListenerEvidence.java").read_text(encoding="utf8")
gradle=(root/"app/build.gradle").read_text(encoding="utf8")
manifest=(root/"app/src/main/AndroidManifest.xml").read_text(encoding="utf8")
checks={
"unique non-overwriting Android package": "com.maru.musiclive.bigo.ready20261124" in gradle,
"BIGO edition opens unified home": "project.hasProperty('maruBigoReady')) ? 'true' : 'false'" in gradle,
"real BIGO panel at app home": "showBigoDeliveryPanel()" in main and "BIGO 오디오 LIVE 연결" in main,
"official audio live handoff": 'startCommunityLive("오디오 LIVE")' in main,
"native BIGO app launch": 'getLaunchIntentForPackage(OneClickBroadcastPlan.BIGO_PACKAGE)' in main,
"Android auto-scan preserved": "scanDeviceMusic(false)" in main and "scanDeviceMusic(true)" in main,
"shareable audio export": "Music/MARU_BIGO/" in (root/"app/src/main/java/com/maru/musiclive/BigoMusicExportPlan.java").read_text(encoding="utf8"),
"non-destructive MediaStore copy": "resolver.insert(" in exporter and "resolver.openInputStream(source)" in exporter and "resolver.openOutputStream(dest" in exporter,
"duplicate export guard": "if (exists(resolver,fileName))" in exporter,
"partial file atomic cleanup": "if (!success) resolver.delete(dest" in exporter,
"no original deletion": "resolver.delete(source" not in exporter,
"listener evidence opt-in": "showBigoListenerTest()" in main and 'setMultiChoiceItems(labels,flags' in main,
"fail-closed no BIGO server proof": "BIGO 서버 인증은 아닙니다." in consumer,
"no remote BIGO UI automation": "CONTROLS_EXTERNAL_APP_UI = false" in plan,
"no Accessibility control": "USES_USER_ENABLED_ACCESSIBILITY_NAVIGATOR = false" in plan,
"stops before BIGO live press": "STOPS_BEFORE_FINAL_BROADCAST_START = true" in plan,
"audio read permission only": "READ_MEDIA_AUDIO" in manifest and "MANAGE_EXTERNAL_STORAGE" not in manifest,
"no Accessibility declaration": "BIND_ACCESSIBILITY_SERVICE" not in manifest,
"no audio feed injection": "CAPTURE_AUDIO_OUTPUT" not in manifest,
"retains original MARU project files": "showMaruUnifiedHome()" in main and "마루총괄SUP_ALL_IN_ONE.py" in main,
}
for key,val in checks.items():
 print(("PASS" if val else "FAIL")+" "+key)
assert all(checks.values()),"Native BIGO contract failed"
print(f"{len(checks)} enforced source-level properties, not a claim of actual BIGO live transmission.")
