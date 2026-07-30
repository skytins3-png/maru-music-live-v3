#!/usr/bin/env python3
from pathlib import Path
import hashlib
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "app/src/main/res/drawable-nodpi/actual_image_01.jpg": "f91194648ea333dd91a53156a589db0ed10a2e06dc84b5956b885c349dada001",
    "app/src/main/res/drawable-nodpi/actual_image_02.png": "d5ebdf8d0a6e44d41f12bc55ede97721e1060bd8016a077fe0a3926264119512",
    "app/src/main/res/raw/actual_music.mp3": "0675b96d48ec97cec56303b620e7652dc3408c0d27df03803653086af723e0b3",
    "app/src/main/res/raw/actual_lyrics.lrc": "dc11c908183a232e5c00e691da9f8895ac1022279cd07dc04e157ab1d8950564",
}
APK_EXPECTED = {
    "res/drawable-nodpi-v4/actual_image_01.jpg": EXPECTED["app/src/main/res/drawable-nodpi/actual_image_01.jpg"],
    "res/drawable-nodpi-v4/actual_image_02.png": EXPECTED["app/src/main/res/drawable-nodpi/actual_image_02.png"],
    "res/raw/actual_music.mp3": EXPECTED["app/src/main/res/raw/actual_music.mp3"],
    "res/raw/actual_lyrics.lrc": EXPECTED["app/src/main/res/raw/actual_lyrics.lrc"],
}

def sha(data): return hashlib.sha256(data).hexdigest()

def main():
    failures=[]
    for rel, expected in EXPECTED.items():
        path=ROOT/rel
        if not path.is_file(): failures.append("missing source asset: "+rel); continue
        actual=sha(path.read_bytes())
        if actual != expected: failures.append(f"source asset mismatch: {rel} {actual}")

    if len(sys.argv) == 2:
        supplied=Path(sys.argv[1])
        apk=supplied
        if supplied.suffix.lower()=='.zip':
            with zipfile.ZipFile(supplied) as outer:
                apks=[n for n in outer.namelist() if n.lower().endswith('.apk')]
                if len(apks)!=1: failures.append("reference zip must contain exactly one APK")
                else:
                    data=outer.read(apks[0])
                    from io import BytesIO
                    with zipfile.ZipFile(BytesIO(data)) as z:
                        for name, expected in APK_EXPECTED.items():
                            if name not in z.namelist(): failures.append("missing APK asset: "+name)
                            elif sha(z.read(name)) != expected: failures.append("APK asset mismatch: "+name)
        elif supplied.suffix.lower()=='.apk':
            with zipfile.ZipFile(apk) as z:
                for name, expected in APK_EXPECTED.items():
                    if name not in z.namelist(): failures.append("missing APK asset: "+name)
                    elif sha(z.read(name)) != expected: failures.append("APK asset mismatch: "+name)
        else: failures.append("reference must be APK or ZIP")

    if failures:
        print("V2.7.5-REFERENCE-CHECK: FAIL")
        for f in failures: print(" -", f)
        raise SystemExit(1)
    print("V2.7.5-REFERENCE-CHECK: PASS")
    print("SOURCE-ASSETS:", len(EXPECTED), "/", len(EXPECTED))
    if len(sys.argv)==2: print("REFERENCE-APK-ASSETS:", len(APK_EXPECTED), "/", len(APK_EXPECTED))

if __name__=='__main__': main()
