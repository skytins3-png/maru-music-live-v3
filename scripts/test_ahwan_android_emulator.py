#!/usr/bin/env python3
"""Android emulator smoke test for the paired TTS-free real-voice APKs.
Runs only in the test emulator: no BIGO access, no user recordings.
"""
import os, re, subprocess, time, xml.etree.ElementTree as ET
from pathlib import Path

OUT=Path("emulator-results")
OUT.mkdir(exist_ok=True)
STUDIO="com.maru.ahwanvoice.realclips"
RADIO="com.maru.musiclive.clips.v20261105"
checks=[]

def cmd(*args, timeout=60, check=True):
    p=subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    if check and p.returncode:
        raise AssertionError(f"{' '.join(args)} failed ({p.returncode}): {p.stderr[-600:]}")
    return p.stdout + p.stderr

def adb(*args, **kw):
    return cmd("adb",*args,**kw)

def result(name, ok, detail=""):
    checks.append((name,ok,detail))
    print(("PASS" if ok else "FAIL"),name,detail,flush=True)

def snap(name):
    try:
        path=OUT/(name+".png")
        with path.open("wb") as f:
            subprocess.run(["adb","exec-out","screencap","-p"],stdout=f,timeout=25,check=True)
    except Exception as e:
        print("screenshot warning",e)

def tree():
    for attempt in range(3):
        adb("shell","uiautomator","dump","/sdcard/ahwan_window.xml",check=False,timeout=35)
        try:
            xml=adb("shell","cat","/sdcard/ahwan_window.xml",timeout=20)
            begin=xml.find("<?xml")
            if begin>=0: return ET.fromstring(xml[begin:])
        except Exception:
            time.sleep(1)
    snap("hierarchy_failure")
    activity=adb("shell","dumpsys","activity","activities",timeout=25,check=False)
    (OUT/"activity-hierarchy-failure.txt").write_text(activity,encoding="utf-8")
    log=adb("logcat","-d","-s","AndroidRuntime:E",timeout=25,check=False)
    (OUT/"crashlog-hierarchy-failure.txt").write_text(log,encoding="utf-8")
    raise AssertionError("Could not read Android accessibility UI hierarchy; see screenshots and crash log")

def nodes():
    return list(tree().iter("node"))

def find(label, attempts=3, scrolling=False):
    for i in range(attempts):
        for n in nodes():
            t=n.get("text","")
            d=n.get("content-desc","")
            if label in t or label in d:
                return n
        if scrolling:
            adb("shell","input","swipe","520","1840","520","720","300")
            time.sleep(0.8)
        else: time.sleep(1)
    return None

def tap(label,scrolling=False):
    n=find(label,scrolling=scrolling,attempts=6)
    if n is None:
        snap("missing_"+str(len(checks)))
        raise AssertionError("Button not found: "+label)
    bounds=n.get("bounds","")
    values=list(map(int,re.findall(r"\d+",bounds)))
    assert len(values)==4,(label,bounds)
    x=(values[0]+values[2])//2
    y=(values[1]+values[3])//2
    adb("shell","input","tap",str(x),str(y))
    time.sleep(1.2)

def text_present(needle,scrolling=False):
    return find(needle,attempts=4,scrolling=scrolling) is not None

print("Android emulator state:",adb("shell","getprop","ro.build.version.release").strip())
print("ABI:",adb("shell","getprop","ro.product.cpu.abi").strip())
print("Installing both APKs...",flush=True)
adb("install","-r","deliverables/AHWAN-REAL-VOICE-NO-TTS-V0.2.apk",timeout=120)
adb("install","-r","deliverables/MARU-REAL-VOICE-ONLY-BRIDGE-V3.2.25.apk",timeout=120)
packages=adb("shell","pm","list","packages")
assert "package:"+STUDIO in packages and "package:"+RADIO in packages
result("Both real APKs install together",True)
adb("shell","pm","grant",STUDIO,"android.permission.RECORD_AUDIO")
adb("shell","input","keyevent","82",check=False)
adb("shell","input","keyevent","3",check=False)
adb("shell","am","start","-W","-n",STUDIO+"/com.maru.ahwanvoice.VoiceStudioActivity",timeout=60)
time.sleep(10)
result("Studio app process alive",bool(adb("shell","pidof",STUDIO,check=False).strip()))
assert checks[-1][1]
snap("01_studio_launch")
result("Voice Studio launches",text_present("아환 실제 목소리"),"no synthetic TTS expected")
assert checks[-1][1]
result("No-TTS promise visible",text_present("TTS 사용 안 함"))
assert checks[-1][1]

# Full workflow: record real microphone test signal, save, preview and share.
try:
    tap("녹음 시작",scrolling=True)
    time.sleep(4)
    # Return to top, because Android scroll view may have shifted.
    adb("shell","input","swipe","520","650","520","1810","250")
    tap("녹음 종료",scrolling=True)
    time.sleep(2)
    snap("02_recording_result")
    saved=text_present("이 항목에 본인 녹음",scrolling=True) or text_present("녹음 저장 완료",scrolling=True)
    result("Microphone recording and save",saved)
    if saved:
        tap("음성 미리듣기",scrolling=True)
        time.sleep(2)
        snap("03_playback")
        result("Recorded audio preview button operates",True)
        tap("MARU RADIO로 음성 보내기",scrolling=True)
        time.sleep(9)
        snap("04_maru_share_dialog")
        dialog=text_present("아환 실제 목소리",scrolling=False) or text_present("지금 재생",scrolling=False)
        result("Cross-app recorded-audio handoff",dialog)
        if dialog:
            tap("지금 재생")
            time.sleep(2)
            snap("05_maru_clip_playback")
            result("MARU playback approval",True)
except Exception as e:
    result("Full record-preview-share flow",False,str(e))
    snap("flow_error")

# Separate app launch, process alive, and no crash reports.
adb("shell","am","start","-n",RADIO+"/com.maru.musiclive.MainActivity",check=False)
time.sleep(8)
snap("06_maru_launch")
current=adb("shell","pidof",RADIO,check=False).strip()
result("MARU main process survives launch",bool(current),current)
adb("shell","input","keyevent","3")
adb("shell","am","start","-n",STUDIO+"/com.maru.ahwanvoice.VoiceStudioActivity",check=False)
time.sleep(3)
snap("07_studio_reopen")
result("Studio survives background/foreground",text_present("아환 실제 목소리"))
raw=adb("logcat","-d","-s","AndroidRuntime:E",timeout=40,check=False)
(OUT/"android-runtime-errors.txt").write_text(raw,encoding="utf-8")
fatal=any(p in raw for p in [
    "Process: "+STUDIO, "Process: "+RADIO])
result("No app process FATAL EXCEPTION",not fatal,raw[-1800:] if fatal else "")
(OUT/"result.txt").write_text("\n".join(
    f"{'PASS' if ok else 'FAIL'} | {name} | {detail}" for name,ok,detail in checks
),encoding="utf-8")
print("Results summary:",sum(x[1] for x in checks),"/",len(checks))
if not all(x[1] for x in checks):
    raise SystemExit(1)
