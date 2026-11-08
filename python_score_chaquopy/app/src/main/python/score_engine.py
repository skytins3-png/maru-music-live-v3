"""Python-only lead-melody transcription and vector sheet music.

Offline algorithm: FFT-based harmonic salience, 4/4 quantized melody.
It is NOT full multi-instrument transcription.
"""
from __future__ import annotations
from dataclasses import dataclass
from collections import Counter
from xml.etree import ElementTree as ET
import math,struct
import numpy as np

SR=11025
@dataclass
class Note:
    pos:int
    length:int
    midi:int|None

STEPS="CDEFGAB"
PITCH=[('C',0),('C',1),('D',0),('E',-1),('E',0),('F',0),('F',1),('G',0),('A',-1),('A',0),('B',-1),('B',0)]

def transcribe(samples,bpm=80,limit_s=180):
    x=np.asarray(samples,dtype=np.float32)[:int(SR*limit_s)]
    if len(x)<SR//2 or float(np.max(abs(x)))<0.0005:raise ValueError("분석할 오디오가 너무 짧거나 무음입니다.")
    if not 45<=bpm<=200:raise ValueError("지원하는 템포는 45~200 BPM입니다.")
    length=4096;win=np.hanning(length).astype(np.float32)
    mids=np.arange(48,89)
    hz=440*2**((mids-69)/12)
    bins=np.clip(np.round(hz[:,None]*np.arange(1,6)[None,:]*length/SR).astype(int),2,length//2-2)
    weights=np.array([1,.64,.37,.21,.13],dtype=np.float32)
    step=60/bpm/4
    total=int(np.ceil(len(x)/SR/step))
    pitches=[];volumes=[]
    for k in range(total):
        midpoint=int((k+.5)*step*SR)
        a=max(0,midpoint-length//2);b=min(len(x),midpoint+length//2)
        frame=np.zeros(length,dtype=np.float32)
        offset=a-(midpoint-length//2)
        frame[offset:offset+b-a]=x[a:b]
        frame=frame-float(frame.mean())
        volumes.append(float(np.sqrt(np.mean(frame**2))))
        spectrum=abs(np.fft.rfft(frame*win))
        peaks=np.maximum(np.maximum(spectrum[bins-1],spectrum[bins]),spectrum[bins+1])
        score=(peaks*weights).sum(axis=1)
        pitches.append(int(mids[int(np.argmax(score))]))
    floor=max(.00065,float(np.percentile(volumes,85))*.12)
    melody=[m if volumes[i]>floor else None for i,m in enumerate(pitches)]
    for i in range(1,len(melody)-1):
        if melody[i-1] is not None and melody[i-1]==melody[i+1]:
            melody[i]=melody[i-1]
    notes=[];last=melody[0];begin=0
    for i in range(1,len(melody)+1):
        if i==len(melody) or melody[i]!=last:
            notes.append(Note(begin,i-begin,last))
            if i<len(melody):begin=i;last=melody[i]
    if all(n.midi is None for n in notes):raise ValueError("음표를 추출하지 못했습니다.")
    return notes

def bars(notes):
    result=[]
    for note in notes:
        pos=note.pos;left=note.length
        while left:
            bar=pos//16
            while len(result)<=bar:result.append([])
            part=min(left,16-(pos%16))
            result[bar].append((pos%16,part,note.midi))
            pos+=part;left-=part
    return result

def staff_level(midi):
    letter=PITCH[midi%12][0]
    octave=midi//12-1
    return (octave-4)*7+STEPS.index(letter)-2

def pitch(midi):
    step,alter=PITCH[midi%12]
    return step,alter,midi//12-1

def xml_bytes(notes,bpm,title):
    root=ET.Element("score-partwise",version="4.0")
    ET.SubElement(root,"movement-title").text=title
    parts=ET.SubElement(root,"part-list")
    entry=ET.SubElement(parts,"score-part",id="P1")
    ET.SubElement(entry,"part-name").text="Estimated melody"
    voice=ET.SubElement(root,"part",id="P1")
    widths=(16,12,8,6,4,3,2,1)
    styles={16:("whole",False),12:("half",True),8:("half",False),6:("quarter",True),4:("quarter",False),3:("eighth",True),2:("eighth",False),1:("16th",False)}
    for i,entries in enumerate(bars(notes),1):
        meas=ET.SubElement(voice,"measure",number=str(i))
        if i==1:
            attrs=ET.SubElement(meas,"attributes")
            ET.SubElement(attrs,"divisions").text="4"
            t=ET.SubElement(attrs,"time")
            ET.SubElement(t,"beats").text="4"
            ET.SubElement(t,"beat-type").text="4"
            clef=ET.SubElement(attrs,"clef")
            ET.SubElement(clef,"sign").text="G"
            ET.SubElement(clef,"line").text="2"
            direction=ET.SubElement(meas,"direction")
            ET.SubElement(direction,"sound",tempo=str(bpm))
        for _,units,midi in entries:
            remaining=units
            while remaining:
                w=next(v for v in widths if v<=remaining)
                remaining-=w
                n=ET.SubElement(meas,"note")
                if midi is None:ET.SubElement(n,"rest")
                else:
                    p=ET.SubElement(n,"pitch")
                    step,alter,octave=pitch(midi)
                    ET.SubElement(p,"step").text=step
                    if alter:ET.SubElement(p,"alter").text=str(alter)
                    ET.SubElement(p,"octave").text=str(octave)
                ET.SubElement(n,"duration").text=str(w)
                typ,dotted=styles[w]
                ET.SubElement(n,"type").text=typ
                if dotted:ET.SubElement(n,"dot")
    return ET.tostring(root,encoding="utf-8",xml_declaration=True)

def varint(i):
    b=[i&127]
    while i>>7:
        i>>=7;b.insert(0,(i&127)|128)
    return bytes(b)

def midi_bytes(notes,bpm):
    tempo=round(60000000/bpm)
    body=bytearray(b"\0\xff\x51\x03"+tempo.to_bytes(3,"big")+b"\0\xc0\0")
    msgs=[]
    for n in notes:
        if n.midi is not None:
            msgs.extend(((n.pos*120,1,n.midi),((n.pos+n.length)*120,0,n.midi)))
    msgs.sort(key=lambda v:(v[0],v[1]))
    last=0
    for at,on,m in msgs:
        body.extend(varint(at-last)+bytes((0x90 if on else 0x80,m,88 if on else 0)))
        last=at
    body.extend(b"\0\xff\x2f\0")
    return b"MThd"+struct.pack(">IHHH",6,0,1,480)+b"MTrk"+struct.pack(">I",len(body))+body

def pdf_bytes(notes,bpm,title):
    """Page-by-page PDF vector output, using built-in font and no external PDF lib."""
    grouped=bars(notes)
    page_contents=[]
    for offset in range(0,len(grouped),10):
        ops=["1 1 1 rg 0 0 595 842 re f\n"]
        def line(x,y,X,Y,width=.9):
            ops.append(f".16 .27 .39 RG {width} w {x:.2f} {842-y:.2f} m {X:.2f} {842-Y:.2f} l S\n")
        def txt(x,y,t,size=11):
            if any(ord(ch)>126 for ch in t):t="MARU MUSIC - Lead melody draft"
            t=t.replace("\\","\\\\").replace("(","\\(").replace(")","\\)")
            ops.append(f".11 .2 .33 rg BT /F1 {size} Tf {x} {842-y} Td ({t}) Tj ET\n")
        txt(38,37,"MARU PYTHON SCORE",18)
        txt(38,58,title[:50],12)
        txt(38,76,f"BPM {bpm}   |   4/4   |   Estimated melody - review required",9)
        for slot,entries in enumerate(grouped[offset:offset+10]):
            row,col=divmod(slot,2)
            x0=38+col*260;top=112+row*133
            txt(x0,top-11,str(offset+slot+1),9)
            for j in range(5):line(x0,top+j*11,x0+246,top+j*11,.65)
            line(x0+246,top,x0+246,top+44,.8)
            start=x0+16
            for beat,dur,midi in entries:
                if midi is None:
                    txt(start+205*(beat+dur/2)/16,top+22,"r",9)
                    continue
                x=start+205*(beat+dur/2)/16
                d=staff_level(midi);y=top+44-d*5.5
                for d2 in range(-6,13,2):
                    if d2<0 and d<=d2 or d2>8 and d>=d2:
                        yy=top+44-d2*5.5;line(x-9,yy,x+9,yy,1.1)
                ops.append(f".08 .17 .28 rg {x-5:.2f} {842-y-3.3:.2f} 10 6.6 re f\n")
                line(x+5,y,x+5,y-28,1.0)
                if PITCH[midi%12][1]:
                    txt(int(x-15),int(y+3),"#" if PITCH[midi%12][1]>0 else "b",9)
        page_contents.append("".join(ops).encode("ascii"))
    if not page_contents:
        raise ValueError("No notation")
    ids=[4+2*i for i in range(len(page_contents))]
    objects=[b"<< /Type /Catalog /Pages 2 0 R >>",
        ("<< /Type /Pages /Count %d /Kids [%s] >>"%(len(ids)," ".join("%d 0 R"%i for i in ids))).encode(),
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    for i,payload in enumerate(page_contents):
        objects.append(f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents {ids[i]+1} 0 R >>".encode())
        objects.append(b"<< /Length "+str(len(payload)).encode()+b" >>\nstream\n"+payload+b"\nendstream")
    pdf=bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n");positions=[0]
    for i,obj in enumerate(objects,1):
        positions.append(len(pdf))
        pdf.extend(str(i).encode()+b" 0 obj\n"+obj+b"\nendobj\n")
    start=len(pdf)
    pdf.extend(f"xref\n0 {len(positions)}\n0000000000 65535 f \n".encode())
    for p in positions[1:]:pdf.extend(f"{p:010d} 00000 n \n".encode())
    pdf.extend(f"trailer\n<< /Root 1 0 R /Size {len(positions)} >>\nstartxref\n{start}\n%%EOF".encode())
    return bytes(pdf)
