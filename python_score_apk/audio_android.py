"""Offline MP3/M4A/WAV to 11025Hz mono PCM using Android platform codecs.

All conversion is initiated in Python. No network, microphone, or media scan.
"""
from __future__ import annotations
import os,wave
import numpy as np

def from_wav(path,limit=180,rate_out=11025):
    with wave.open(path,"rb") as w:
        sr=w.getframerate();channels=w.getnchannels();width=w.getsampwidth()
        data=w.readframes(min(w.getnframes(),sr*limit))
    if width==1:
        x=(np.frombuffer(data,dtype=np.uint8).astype(np.float32)-128)/128
    elif width==2:x=np.frombuffer(data,dtype="<i2").astype(np.float32)/32768
    elif width==4:x=np.frombuffer(data,dtype="<i4").astype(np.float32)/2147483648
    else:raise ValueError("이 WAV 형식은 지원되지 않습니다.")
    if channels>1:
        x=x[:len(x)//channels*channels].reshape(-1,channels).mean(axis=1)
    if sr!=rate_out:
        indices=np.arange(0,len(x)-1,sr/rate_out)
        x=np.interp(indices,np.arange(len(x)),x).astype(np.float32)
    return x

def copy_from_uri(uri,path,maxsize=200*1024*1024):
    from android import mActivity
    from jnius import autoclass,jarray
    source=mActivity.getContentResolver().openInputStream(uri)
    if source is None:raise ValueError("음원 파일에 접근할 수 없습니다.")
    FileOutputStream=autoclass("java.io.FileOutputStream")
    target=FileOutputStream(path)
    buf=jarray("b")([0]*32768);written=0
    try:
        while True:
            got=source.read(buf)
            if got<0:break
            if got==0:continue
            written+=got
            if written>maxsize:raise ValueError("음원 크기는 최대 200MB입니다.")
            target.write(buf,0,got)
        target.flush()
    finally:
        source.close();target.close()
    if written<1024:raise ValueError("비어 있는 음원입니다.")
    return written

def decode(path,limit=180,target_rate=11025):
    if path.lower().endswith(".wav"):return from_wav(path,limit,target_rate)
    from jnius import autoclass,jarray
    Extractor=autoclass("android.media.MediaExtractor")
    Codec=autoclass("android.media.MediaCodec")
    Info=autoclass("android.media.MediaCodec$BufferInfo")
    extractor=Extractor();codec=None
    try:
        extractor.setDataSource(path)
        fmt=None
        for t in range(extractor.getTrackCount()):
            item=extractor.getTrackFormat(t)
            if str(item.getString("mime")).startswith("audio/"):
                fmt=item;extractor.selectTrack(t);break
        if fmt is None:raise ValueError("오디오 트랙이 없습니다.")
        codec=Codec.createDecoderByType(str(fmt.getString("mime")))
        codec.configure(fmt,None,None,0)
        codec.start()
        sample_rate=int(fmt.getInteger("sample-rate"))
        channels=int(fmt.getInteger("channel-count"))
        encoding=2;step=sample_rate/target_rate
        info=Info();eof_in=False;eof_out=False
        read=0;next_at=0.0;chunks=[];timeouts=0
        while not eof_out and read/max(sample_rate,1)<limit:
            if not eof_in:
                ind=codec.dequeueInputBuffer(10000)
                if ind>=0:
                    buffer=codec.getInputBuffer(ind);buffer.clear()
                    size=extractor.readSampleData(buffer,0)
                    if size<0:
                        codec.queueInputBuffer(ind,0,0,0,Codec.BUFFER_FLAG_END_OF_STREAM)
                        eof_in=True
                    else:
                        codec.queueInputBuffer(ind,0,size,max(0,extractor.getSampleTime()),0)
                        extractor.advance()
            index=codec.dequeueOutputBuffer(info,10000)
            if index==Codec.INFO_OUTPUT_FORMAT_CHANGED:
                fmt=codec.getOutputFormat()
                sample_rate=int(fmt.getInteger("sample-rate"))
                channels=int(fmt.getInteger("channel-count"))
                encoding=int(fmt.getInteger("pcm-encoding")) if fmt.containsKey("pcm-encoding") else 2
                step=sample_rate/target_rate
            elif index>=0:
                timeouts=0
                n=int(info.size)
                if n:
                    buffer=codec.getOutputBuffer(index)
                    buffer.position(int(info.offset))
                    buffer.limit(int(info.offset+n))
                    output=jarray("b")([0]*n)
                    buffer.get(output)
                    raw=bytes((int(q)&255 for q in output))
                    if encoding==2:
                        x=np.frombuffer(raw[:len(raw)//2*2],dtype="<i2").astype(np.float32)/32768
                    elif encoding==4:
                        x=np.frombuffer(raw[:len(raw)//4*4],dtype="<f4").astype(np.float32)
                    else:raise ValueError("지원하지 않는 PCM 오디오 형식입니다.")
                    if channels>1:
                        x=x[:len(x)//channels*channels].reshape(-1,channels).mean(axis=1)
                    if len(x)>1:
                        stop=read+len(x)-1
                        if next_at<stop:
                            locations=np.arange(next_at,stop,step)
                            if len(locations):
                                chunks.append(np.interp(locations-read,np.arange(len(x)),x).astype(np.float32))
                                next_at=float(locations[-1])+step
                        read+=len(x)
                if info.flags & Codec.BUFFER_FLAG_END_OF_STREAM:eof_out=True
                codec.releaseOutputBuffer(index,False)
            else:
                timeouts+=1
                if timeouts>350:raise ValueError("디코딩 제한시간을 초과했습니다.")
        if not chunks:raise ValueError("읽을 수 있는 음악 데이터가 없습니다.")
        return np.concatenate(chunks)[:target_rate*limit]
    finally:
        if codec is not None:
            try:codec.stop()
            except Exception:pass
            codec.release()
        extractor.release()

def save_uri(uri,data):
    from android import mActivity
    from jnius import jarray
    sink=mActivity.getContentResolver().openOutputStream(uri)
    if sink is None:raise ValueError("저장 위치에 접근할 수 없습니다.")
    try:
        for offset in range(0,len(data),32768):
            bs=data[offset:offset+32768]
            sink.write(jarray("b")([i if i<128 else i-256 for i in bs]))
        sink.flush()
    finally:sink.close()
