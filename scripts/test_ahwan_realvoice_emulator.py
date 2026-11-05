#!/usr/bin/env python3
"""Real Android API-35 virtual-device smoke test of the two APKs.
Uses operator-equivalent button taps, NOT synthetic Android intent shortcuts.
Does not connect to BIGO, capture private chats, or create fake user voice.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("test-results/ahwan-emulator")
OUT.mkdir(parents=True, exist_ok=True)
STUDIO = "com.maru.ahwanvoice.realclips"
BRIDGE = "com.maru.musiclive.clips.v20261105"
studio_apk = "ahwan_voice/build/outputs/apk/debug/ahwan_voice-debug.apk"
bridge_apk = "app/build/outputs/apk/debug/app-debug.apk"

def shell(*args, check=True, timeout=90):
    p = subprocess.run(["adb", *args], text=True, stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, timeout=timeout)
    if check and p.returncode:
        raise RuntimeError("ADB failed: " + " ".join(args) + "\n" + p.stdout)
    return p.stdout

def snap(reason):
    name = re.sub("[^a-z0-9_-]", "_", reason.lower())[:60]
    p = subprocess.run(["adb", "exec-out", "screencap", "-p"],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode == 0:
        (OUT / (name + ".png")).write_bytes(p.stdout)
    (OUT / (name + ".txt")).write_text(shell("shell", "dumpsys", "activity",
                                            "activities", check=False), encoding="utf-8")

def xml_dump():
    for attempt in range(8):
        shell("shell", "uiautomator", "dump", "/sdcard/window.xml", check=False, timeout=40)
        output = shell("shell", "cat", "/sdcard/window.xml", check=False)
        if "<?xml" in output:
            try:
                return ET.fromstring(output[output.index("<?xml"):])
            except ET.ParseError:
                pass
        time.sleep(1.5)
    raise RuntimeError("Unable to read Android accessibility/UI hierarchy")

def all_text(root):
    return " | ".join(node.attrib.get("text", "") for node in root.iter()
                    if node.attrib.get("text"))

def tap_named(name, scroll=True):
    for attempt in range(10 if scroll else 2):
        root = xml_dump()
        for node in root.iter():
            text = node.attrib.get("text", "")
            if name in text:
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
                             node.attrib.get("bounds", ""))
                if not m:
                    continue
                l,t,r,b = map(int, m.groups())
                if r <= l or b <= t:
                    continue
                shell("shell","input","tap",str((l+r)//2),str((t+b)//2))
                print("Tapped:", name, flush=True)
                time.sleep(2.0)
                return
        if scroll:
            # Scroll the view rather than restarting activities or repeating actions.
            shell("shell","input","swipe","520","1530","520","520","550")
            time.sleep(.8)
    raise AssertionError("Could not find visible button: "+name+
                         "\nvisible: "+all_text(root)[:1800])

def require_text(text):
    root=xml_dump()
    combined=all_text(root)
    if text not in combined:
        raise AssertionError("Expected UI text %r; visible: %s"%(text,combined[:1800]))
    print("Found expected UI:",text, flush=True)

def current_package():
    a=shell("shell","dumpsys","window","windows",check=False)
    b=shell("shell","dumpsys","activity","activities",check=False)
    return a+"\n"+b

try:
    print("1. ADB and signed package installation",flush=True)
    shell("wait-for-device")
    for apk in [bridge_apk, studio_apk]:
        if not Path(apk).is_file():
            raise AssertionError("APK does not exist: "+apk)
        print(shell("install","-r",apk).strip(),flush=True)
    packages=shell("shell","pm","list","packages")
    assert "package:"+STUDIO in packages
    assert "package:"+BRIDGE in packages
    shell("shell","pm","grant",STUDIO,"android.permission.RECORD_AUDIO")
    shell("shell","pm","grant",BRIDGE,"android.permission.POST_NOTIFICATIONS",
          check=False)
    shell("logcat","-c")

    print("2. Launch voice recorder UI and check start-up crashes",flush=True)
    shell("shell","am","start","-W","-n",
          STUDIO+"/com.maru.ahwanvoice.VoiceStudioActivity")
    time.sleep(3.0)
    require_text("아환 실제 목소리 스튜디오")
    snap("01-studio-launched")

    print("3. Operator starts and stops a genuine microphone recording",flush=True)
    tap_named("1. 실제 목소리 녹음 시작")
    time.sleep(3)
    # The virtual microphone may contain silence; the test checks container
    # recording and routing, not the quality of a real user's voice.
    require_text("녹음 중")
    tap_named("2. 녹음 종료")
    require_text("녹음 저장 완료")
    snap("02-recorded")

    print("4. Open the recording using Android MediaPlayer",flush=True)
    tap_named("등록한 음성 미리듣기")
    time.sleep(1.5)
    require_text("실제 녹음")
    snap("03-preview")

    print("5. Send user-approved voice file to MARU with URI read grant",flush=True)
    tap_named("MARU RADIO로 음성 보내기")
    time.sleep(6)
    require_text("아환 실제 목소리")
    require_text("지금 재생")
    snap("04-maru-received")
    print("6. Confirm MARU playback and look for runtime crashes",flush=True)
    tap_named("지금 재생",scroll=False)
    time.sleep(4)
    snap("05-maru-playback")

    # Failure markers are indicative, not proof of end-to-end audio fidelity.
    logs=shell("logcat","-d","-v","brief",check=False,timeout=45)
    (OUT/"logcat.txt").write_text(logs[-150000:],encoding="utf-8")
    app_errors=[line for line in logs.splitlines()
                if "FATAL EXCEPTION" in line or "SecurityException" in line]
    if app_errors:
        raise AssertionError("Android fatal/security error: "+"\n".join(app_errors[:10]))
    (OUT/"RESULT.txt").write_text(
        "PASS: both APKs installed; Studio launched; microphone recorded; "
        "MediaPlayer preview invoked; URI handoff reached MARU; playback "
        "dialog confirmed; no fatal/security errors observed. "
        "Not proof of sound quality, voice cloning or BIGO broadcast.\n",
        encoding="utf-8")
    print("PASS - actual Android emulator end-to-end clip handoff",flush=True)
except Exception as exc:
    snap("failure")
    logs=shell("logcat","-d","-v","brief",check=False,timeout=45)
    (OUT/"logcat.txt").write_text(logs[-150000:],encoding="utf-8")
    (OUT/"RESULT.txt").write_text("FAIL: "+str(exc)+"\n",encoding="utf-8")
    print("FAIL:",repr(exc),flush=True)
    sys.exit(1)
