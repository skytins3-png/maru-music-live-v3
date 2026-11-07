package com.maru.voicefx;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class WavWriter implements Closeable {
 private final RandomAccessFile output;private long bytes;private boolean closed;
 public WavWriter(File file)throws IOException{output=new RandomAccessFile(file,"rw");output.setLength(0);header(0);}
 private void str(String s)throws IOException{output.write(s.getBytes(StandardCharsets.US_ASCII));}
 private void le16(int x)throws IOException{output.write(x&255);output.write((x>>>8)&255);}
 private void le32(long x)throws IOException{for(int i=0;i<4;i++)output.write((int)(x>>>(i*8))&255);}
 private void header(long n)throws IOException{
  output.seek(0);str("RIFF");le32(n+36);str("WAVE");str("fmt ");le32(16);le16(1);le16(1);
  le32(48000);le32(96000);le16(2);le16(16);str("data");le32(n);
 }
 public void append(short[] samples,int count)throws IOException{
  if(closed)throw new IOException("closed");if(count<0||count>samples.length)throw new IllegalArgumentException();
  if(bytes+count*2L>0xFFFFFFFFL-36)throw new IOException("WAV too big");
  byte[] buffer=new byte[count*2];
  for(int i=0;i<count;i++){buffer[i*2]=(byte)samples[i];buffer[i*2+1]=(byte)(samples[i]>>>8);}
  output.write(buffer);bytes+=buffer.length;
 }
 @Override public void close()throws IOException{if(closed)return;closed=true;try{header(bytes);}finally{output.close();}}
}