package com.maru.voicefx;
import org.junit.Test;
import static org.junit.Assert.*;
public final class VoiceProcessorTest {
 @Test public void silence(){VoiceProcessor p=new VoiceProcessor();short[] a=new short[4800],b=new short[a.length];p.process(a,b,a.length);for(short s:b)assertEquals(0,s);}
 @Test public void speech(){VoiceProcessor p=new VoiceProcessor();short[] a=new short[4800],b=new short[a.length];for(int i=0;i<a.length;i++)a[i]=(short)(Math.sin(i*.04)*12000);p.process(a,b,a.length);int peak=0;for(short x:b)peak=Math.max(peak,Math.abs((int)x));assertTrue(peak>1000);assertTrue(peak<=32767);}
 @Test(expected=IllegalArgumentException.class)public void invalid(){new VoiceProcessor().process(new short[1],new short[1],2);}
}