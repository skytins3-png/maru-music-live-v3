"""Compile the real narrator against deterministic Android speech/handler doubles."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1]; b=r/'build/narrator-test';b.mkdir(parents=True,exist_ok=True)
sources={
'android/content/Context.java':'package android.content; public class Context {public Context getApplicationContext(){return this;}}',
'android/os/Looper.java':'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Bundle.java':'package android.os; public class Bundle {}',
'android/os/Handler.java':'''package android.os; import java.util.*; public class Handler {
 public static List<Runnable> now=new ArrayList<>(), later=new ArrayList<>();
 public Handler(Looper l){} public void post(Runnable r){now.add(r);} public void postDelayed(Runnable r,long ms){later.add(r);}
 public void removeCallbacks(Runnable r){now.remove(r);later.remove(r);}
 public static void drain(){while(!now.isEmpty())now.remove(0).run();}
 public static void timeout(){if(!later.isEmpty())later.remove(0).run();drain();}
}''',
'android/speech/tts/UtteranceProgressListener.java':'''package android.speech.tts; public abstract class UtteranceProgressListener {public abstract void onStart(String s);public abstract void onDone(String s);public abstract void onError(String s);}''',
'android/speech/tts/TextToSpeech.java':'''package android.speech.tts;import android.content.*;import android.os.*;import java.util.*;
public class TextToSpeech {
 public static final int SUCCESS=0,LANG_AVAILABLE=0,QUEUE_FLUSH=0;
 public interface OnInitListener{void onInit(int i);} public static TextToSpeech last;
 public static int init=0,support=0,result=0; public UtteranceProgressListener listener;public String id;public int count;
 public TextToSpeech(Context c,OnInitListener l){last=this;new Handler(Looper.getMainLooper()).post(()->l.onInit(init));}
 public void setOnUtteranceProgressListener(UtteranceProgressListener l){listener=l;}
 public int isLanguageAvailable(Locale l){return support;} public void setLanguage(Locale l){} public void setPitch(float f){}public void setSpeechRate(float f){}
 public int speak(String text,int q,Bundle b,String key){id=key;count++;return result;}
 public void stop(){}public void shutdown(){}
 public void done(String key){listener.onDone(key);Handler.drain();}
}''',
'com/maru/radio/RadioNarratorTest.java':'''package com.maru.radio;
import android.content.*;import android.os.*;import android.speech.tts.*;import java.util.*;
public class RadioNarratorTest {
 static void ok(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[] args){
 RadioShowNarrator n=new RadioShowNarrator(new Context()); Handler.drain();
 List<RadioShowLines.Line> lines=RadioShowLines.intro(1,"song",1);int[] done={0};
 n.say(lines,()->done[0]++);TextToSpeech t=TextToSpeech.last;String old=t.id;
 ok(n.isSpeaking()&&!n.currentLine().isEmpty(),"dialogue visible");
 t.done(old);ok(t.count==2,"ordered line");t.done(old);ok(t.count==2,"duplicate ignored");
 n.cancel();t.done(t.id);ok(done[0]==0&&!n.isSpeaking()&&n.currentLine().isEmpty(),"cancel never starts music");
 n.say(lines,()->done[0]++);old=t.id;Handler.timeout();ok(!old.equals(t.id),"timeout advances");int count=t.count;t.done(old);ok(count==t.count,"late timeout callback ignored");
 t.done(t.id);t.done(t.id);ok(done[0]==1&&!n.isSpeaking(),"one completion");
 n.say(lines,()->done[0]++);String stale=t.id;n.say(lines,()->done[0]++);t.done(stale);ok(done[0]==1,"replaced run ignored");n.cancel();
 TextToSpeech.support=-1;n.say(lines,()->done[0]++);ok(done[0]==2,"missing language continues music");TextToSpeech.support=0;
 TextToSpeech.result=-1;n.say(lines,()->done[0]++);ok(done[0]==3,"speech failure continues music");TextToSpeech.result=0;
 n.destroy();Handler.drain();ok(Handler.later.isEmpty(),"timers cleaned");
 System.out.println("PASS narrator order, cancel, replacement, timeout, stale callbacks, missing voice and speech errors");
 }
}'''}
for name,source in sources.items():
 p=b/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
files=[str(b/x) for x in sources]+[str(r/'radio/src/main/java/com/maru/radio'/x) for x in ['RadioShowLines.java','RadioShowNarrator.java']]
subprocess.run(['javac','-encoding','UTF-8','-d',str(b/'classes'),*files],check=True)
subprocess.run(['java','-cp',str(b/'classes'),'com.maru.radio.RadioNarratorTest'],check=True)
