package com.maru.voicefx;
/** Local 48kHz voice DSP. Basic noise expander, EQ, compression and limiting.
 * No voice cloning or cross-app audio injection. */
public final class VoiceProcessor {
  private volatile int noise=50,warmth=57,clarity=70,compression=70;
  private float hpX,hpY,lowBass,lowMid,lowHigh,noiseEnv,noiseGain=1f,compEnv,compGain=1f;
  public void settings(int n,int w,int c,int k) {
    noise=limit(n);warmth=limit(w);clarity=limit(c);compression=limit(k);
  }
  private static int limit(int a){return Math.max(0,Math.min(a,100));}
  private static float bound(float a,float b,float c){return Math.max(b,Math.min(c,a));}
  public void process(short[] input,short[] output,int count){
    if(count<0||count>input.length||count>output.length)throw new IllegalArgumentException();
    final float sampleRate=48000f,dt=1f/sampleRate;
    final float rc=(float)(1d/(2*Math.PI*82));
    final float hpA=rc/(rc+dt);
    final float aBass=(float)(1-Math.exp(-2*Math.PI*195/sampleRate));
    final float aMid=(float)(1-Math.exp(-2*Math.PI*1700/sampleRate));
    final float aHigh=(float)(1-Math.exp(-2*Math.PI*6500/sampleRate));
    final float noiseAttack=(float)Math.exp(-dt/.006);
    final float noiseRelease=(float)Math.exp(-dt/.075);
    final float gateAttack=(float)Math.exp(-dt/.018);
    final float gateRelease=(float)Math.exp(-dt/.145);
    final float compAttack=(float)Math.exp(-dt/.007);
    final float compRelease=(float)Math.exp(-dt/.120);
    final int n=noise,w=warmth,c=clarity,k=compression;
    final float warm=(w-50f)/125f,presence=(c-50f)/145f;
    final float gateThreshold=.003f+n*.000105f,minGate=1f-n*.0082f;
    final float threshold=.11f-k*.00055f,ratio=1.3f+k*.05f,preGain=1f+k*.0145f;
    for(int i=0;i<count;i++){
      float x=input[i]/32768f;
      float hp=hpA*(hpY+x-hpX);hpY=hp;hpX=x;
      lowBass+=aBass*(hp-lowBass);
      lowMid+=aMid*(hp-lowMid);
      lowHigh+=aHigh*(hp-lowHigh);
      float y=hp+lowBass*warm+(lowHigh-lowMid)*presence;
      float e=Math.abs(y);
      noiseEnv=e>noiseEnv?noiseAttack*noiseEnv+(1-noiseAttack)*e:noiseRelease*noiseEnv+(1-noiseRelease)*e;
      float target=n==0||noiseEnv>gateThreshold?1f:minGate;
      float gateFactor=target>noiseGain?gateAttack:gateRelease;
      noiseGain=gateFactor*noiseGain+(1-gateFactor)*target;
      y*=noiseGain;
      e=Math.abs(y);
      compEnv=e>compEnv?compAttack*compEnv+(1-compAttack)*e:compRelease*compEnv+(1-compRelease)*e;
      float desired=1f;
      if(compEnv>threshold){float compressed=threshold*(float)Math.pow(compEnv/threshold,1f/ratio);desired=compressed/Math.max(compEnv,.000001f);}
      float factor=desired<compGain?compAttack:compRelease;
      compGain=factor*compGain+(1-factor)*desired;
      y*=compGain*preGain;
      if(y>.88f)y=.88f+(y-.88f)/(1f+Math.abs(y-.88f)*12f);
      if(y<-.88f)y=-.88f+(y+.88f)/(1f+Math.abs(y+.88f)*12f);
      output[i]=(short)Math.round(bound(y,-.96f,.96f)*32767f);
    }
  }
}