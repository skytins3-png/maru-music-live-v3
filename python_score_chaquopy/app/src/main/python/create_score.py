"""Offline transcription from Android-decoded mono WAV, using Python."""
import numpy as np
import wave, os
from score_engine import SR,transcribe,pdf_bytes,xml_bytes,midi_bytes

def make(wav_path, destination, title="MARU"):
    with wave.open(wav_path,"rb") as w:
        if w.getsampwidth()!=2 or w.getnchannels()!=1:raise ValueError("16-bit mono PCM 필요")
        rate=w.getframerate()
        if rate<8000 or rate>192000:raise ValueError("Unsupported sample rate")
        pcm=w.readframes(min(w.getnframes(),rate*180))
    x=np.frombuffer(pcm,dtype="<i2").astype(np.float32)/32768.
    if len(x)<rate//2:raise ValueError("음원 길이가 너무 짧습니다")
    if rate!=SR:
        idx=np.arange(0,len(x)-1,rate/SR)
        x=np.interp(idx,np.arange(len(x)),x).astype(np.float32)
    notes=transcribe(x,80,180)
    clean="".join(c for c in title if c not in '\\/:*?"<>|')[:80] or "MARU"
    os.makedirs(destination,exist_ok=True)
    files={
       "pdf":pdf_bytes(notes,80,clean),
       "xml":xml_bytes(notes,80,clean),
       "mid":midi_bytes(notes,80),
    }
    extensions={"pdf":".pdf","xml":".musicxml","mid":".mid"}
    for typ,raw in files.items():
        with open(os.path.join(destination,"score"+extensions[typ]),"wb") as f:f.write(raw)
    return f"{len(notes)}개 음구간, {len(x)/SR:.0f}초 분석 완료"
