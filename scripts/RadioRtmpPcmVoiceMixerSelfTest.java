package com.maru.radio;

/** Pure JVM checks of bounded stereo network PCM voice mixing, no BIGO access. */
public final class RadioRtmpPcmVoiceMixerSelfTest {
    private static void check(boolean yes, String name) {
        if (!yes) throw new AssertionError("FAIL " + name);
        System.out.println("PASS " + name);
    }
    private static short get(byte[] b, int p) {
        return (short)((b[p]&255) | (b[p+1]<<8));
    }
    private static void put(byte[] b, int p, short value) {
        b[p]=(byte)value;b[p+1]=(byte)(value>>8);
    }
    public static void main(String[] args) {
        RadioRtmpPcmVoiceMixer mixer = new RadioRtmpPcmVoiceMixer();
        byte[] pcm = new byte[32];
        for (int i=0;i<pcm.length;i+=2) put(pcm,i,(short)12000);
        mixer.mix(pcm,0,pcm.length,true);
        check(get(pcm,0)==12000, "preserve music when there is no announcer");
        check(mixer.enqueue(new short[]{24000,24000,24000,24000}),
                "accept a short approved TTS PCM message");
        mixer.mix(pcm,0,pcm.length,true);
        check(get(pcm,0)>12000,"audible synthesized speech added to quieter music");
        check(get(pcm,0)==get(pcm,2),"mono announcer stays centered in stereo network output");
        check(mixer.queued()==0,"short spoken clip finishes and queue clears");
        mixer.mix(pcm,0,pcm.length,true);
        check(mixer.enqueue(new short[]{Short.MAX_VALUE}),
                "clip at full-scale for limiter check");
        for(int i=0;i<pcm.length;i+=2)put(pcm,i,Short.MAX_VALUE);
        mixer.mix(pcm,0,pcm.length,true);
        check(Math.abs(get(pcm,0))<=29819,"network PCM is peak limited");
        mixer.clear();
        check(mixer.queued()==0,"stop empties queued voice clips");
        System.out.println("PCM mixer checks passed (NOT an actual BIGO listener test).");
    }
}
