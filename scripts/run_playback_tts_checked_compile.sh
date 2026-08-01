#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/playback-tts-checked"
SRC="$OUT/src"
CLS="$OUT/classes"
rm -rf "$OUT"
mkdir -p "$SRC/android/app" "$SRC/android/content" "$SRC/android/media" \
  "$SRC/android/net" "$SRC/android/os" "$SRC/android/speech/tts" \
  "$SRC/androidx/core/app" "$SRC/com/maru/musiclive" "$CLS"

cat > "$SRC/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {
 boolean getBoolean(String k, boolean d); String getString(String k, String d);
 int getInt(String k, int d); long getLong(String k, long d); Editor edit();
 interface Editor { Editor putBoolean(String k, boolean v); Editor putString(String k, String v);
  Editor putInt(String k, int v); Editor putLong(String k, long v); Editor remove(String k); void apply(); }
}
JAVA
cat > "$SRC/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public SharedPreferences getSharedPreferences(String n,int m){return null;}
 public Intent startForegroundService(Intent i){return i;} public Intent startService(Intent i){return i;}
 public <T> T getSystemService(Class<T> c){return null;} public String getString(int id){return "";}
 public void sendBroadcast(Intent i){} public String getPackageName(){return "com.maru.musiclive";}
}
JAVA
cat > "$SRC/android/content/Intent.java" <<'JAVA'
package android.content;
import java.util.ArrayList;
public class Intent {
 private String action; public Intent(){} public Intent(Context c,Class<?> k){}
 public Intent setAction(String a){action=a;return this;} public String getAction(){return action;} public Intent setPackage(String p){return this;}
 public Intent putExtra(String k,String v){return this;} public Intent putExtra(String k,int v){return this;}
 public Intent putStringArrayListExtra(String k,ArrayList<String> v){return this;}
 public String getStringExtra(String k){return null;} public int getIntExtra(String k,int d){return d;}
 public ArrayList<String> getStringArrayListExtra(String k){return null;}
}
JAVA
cat > "$SRC/android/app/Notification.java" <<'JAVA'
package android.app; public class Notification {}
JAVA
cat > "$SRC/android/app/NotificationChannel.java" <<'JAVA'
package android.app; public class NotificationChannel { public NotificationChannel(String a,String b,int c){} public void setSound(Object a,Object b){} public void setDescription(String s){} }
JAVA
cat > "$SRC/android/app/NotificationManager.java" <<'JAVA'
package android.app; public class NotificationManager { public static final int IMPORTANCE_LOW=1; public void createNotificationChannel(NotificationChannel c){} }
JAVA
cat > "$SRC/android/app/PendingIntent.java" <<'JAVA'
package android.app; import android.content.*; public class PendingIntent { public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2; public static PendingIntent getActivity(Context c,int r,Intent i,int f){return new PendingIntent();} public static PendingIntent getService(Context c,int r,Intent i,int f){return new PendingIntent();} }
JAVA
cat > "$SRC/android/app/Service.java" <<'JAVA'
package android.app; import android.content.*; import android.os.IBinder;
public class Service extends Context { public static final int START_STICKY=1,START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;
 public void onCreate(){} public int onStartCommand(Intent i,int f,int s){return 0;} public IBinder onBind(Intent i){return null;} public void onDestroy(){}
 public void startForeground(int id,Notification n){} public void stopForeground(boolean r){} public void stopForeground(int r){} public void stopSelf(){} }
JAVA
cat > "$SRC/android/media/AudioAttributes.java" <<'JAVA'
package android.media; public class AudioAttributes { public static final int USAGE_GAME=1,CONTENT_TYPE_SPEECH=2,CONTENT_TYPE_MUSIC=3,ALLOW_CAPTURE_BY_ALL=4;
 public static class Builder { public Builder setUsage(int v){return this;} public Builder setContentType(int v){return this;} public Builder setAllowedCapturePolicy(int v){return this;} public AudioAttributes build(){return new AudioAttributes();} } }
JAVA
cat > "$SRC/android/media/AudioManager.java" <<'JAVA'
package android.media; public class AudioManager { public void setAllowedCapturePolicy(int p){} }
JAVA
cat > "$SRC/android/media/MediaPlayer.java" <<'JAVA'
package android.media; import android.content.Context; import android.net.Uri; import android.os.PowerManager; import java.io.IOException;
public class MediaPlayer { public interface OnPreparedListener{void onPrepared(MediaPlayer m);} public interface OnCompletionListener{void onCompletion(MediaPlayer m);} public interface OnErrorListener{boolean onError(MediaPlayer m,int w,int e);}
 public void setAudioAttributes(AudioAttributes a){} public void setWakeMode(Context c,int m){} public void setOnPreparedListener(OnPreparedListener l){} public void setOnCompletionListener(OnCompletionListener l){} public void setOnErrorListener(OnErrorListener l){}
 public void setDataSource(Context c, Uri u) throws IOException{} public void prepareAsync(){} public void start(){} public void pause(){} public void stop(){} public void release(){} public boolean isPlaying(){return false;} public int getCurrentPosition(){return 0;} public int getDuration(){return 0;} public void seekTo(int p){} public void setVolume(float l,float r){} }
JAVA
cat > "$SRC/android/net/Uri.java" <<'JAVA'
package android.net; public class Uri { public static Uri parse(String v){return new Uri();} }
JAVA
cat > "$SRC/android/os/Build.java" <<'JAVA'
package android.os; public class Build { public static class VERSION { public static int SDK_INT=36; } public static class VERSION_CODES { public static final int N=24,O=26,Q=29; } }
JAVA
cat > "$SRC/android/os/Bundle.java" <<'JAVA'
package android.os; public class Bundle { public void putFloat(String k,float v){} }
JAVA
cat > "$SRC/android/os/Handler.java" <<'JAVA'
package android.os; public class Handler { public Handler(Looper l){} public boolean post(Runnable r){return true;} public boolean postDelayed(Runnable r,long d){return true;} public void removeCallbacks(Runnable r){} public void removeCallbacksAndMessages(Object o){} }
JAVA
cat > "$SRC/android/os/Looper.java" <<'JAVA'
package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }
JAVA
cat > "$SRC/android/os/IBinder.java" <<'JAVA'
package android.os; public interface IBinder {}
JAVA
cat > "$SRC/android/os/Binder.java" <<'JAVA'
package android.os; public class Binder implements IBinder {}
JAVA
cat > "$SRC/android/os/PowerManager.java" <<'JAVA'
package android.os; public class PowerManager { public static final int PARTIAL_WAKE_LOCK=1; }
JAVA
cat > "$SRC/android/speech/tts/UtteranceProgressListener.java" <<'JAVA'
package android.speech.tts; public abstract class UtteranceProgressListener { public abstract void onStart(String id); public abstract void onDone(String id); public abstract void onError(String id); public void onError(String id,int code){} }
JAVA
cat > "$SRC/android/speech/tts/TextToSpeech.java" <<'JAVA'
package android.speech.tts; import android.content.Context; import android.media.AudioAttributes; import android.os.Bundle; import java.util.Locale;
public class TextToSpeech { public interface OnInitListener{void onInit(int s);} public static final int SUCCESS=0,LANG_MISSING_DATA=-1,LANG_NOT_SUPPORTED=-2,QUEUE_FLUSH=0,ERROR=-1; public static class Engine{public static final String KEY_PARAM_VOLUME="volume";}
 public TextToSpeech(Context c,OnInitListener l){} public void setSpeechRate(float v){} public void setPitch(float v){} public void setAudioAttributes(AudioAttributes a){} public void setOnUtteranceProgressListener(UtteranceProgressListener l){} public int isLanguageAvailable(Locale l){return 0;} public int setLanguage(Locale l){return 0;} public int speak(String s,int q,Bundle b,String id){return 0;} public int stop(){return 0;} public void shutdown(){} }
JAVA
cat > "$SRC/androidx/core/app/NotificationCompat.java" <<'JAVA'
package androidx.core.app; import android.app.*; import android.content.Context;
public class NotificationCompat { public static class Builder { public Builder(Context c,String id){} public Builder setSmallIcon(int v){return this;} public Builder setContentTitle(CharSequence v){return this;} public Builder setContentText(CharSequence v){return this;} public Builder setContentIntent(PendingIntent v){return this;} public Builder addAction(int i,CharSequence t,PendingIntent p){return this;} public Builder setOnlyAlertOnce(boolean v){return this;} public Builder setOngoing(boolean v){return this;} public Notification build(){return new Notification();} } }
JAVA
cat > "$SRC/com/maru/musiclive/AppStorage.java" <<'JAVA'
package com.maru.musiclive; import android.content.Context; import java.util.*; public final class AppStorage {
 public static boolean songTitleTts(Context c){return true;}
 public static Map<String,Long> loadRandomHistory(Context c){return new LinkedHashMap<>();}
 public static Set<String> loadRandomCycle(Context c){return new HashSet<>();}
 public static void saveRandomPlaybackState(Context c,Map<String,Long> h,Set<String> s){}
}
JAVA
cat > "$SRC/com/maru/musiclive/SongTitleResolver.java" <<'JAVA'
package com.maru.musiclive; import android.content.Context; public final class SongTitleResolver { public static String resolve(Context c,String u){return "title";} }
JAVA
cat > "$SRC/com/maru/musiclive/AutoGreetingStore.java" <<'JAVA'
package com.maru.musiclive; import android.content.Context; public final class AutoGreetingStore { public static void setStatus(Context c,String s){} public static void setRunningMode(Context c,String s){} public static void recordGreetingPlayed(Context c,long t){} }
JAVA
cat > "$SRC/com/maru/musiclive/ScreenOcrGreetingService.java" <<'JAVA'
package com.maru.musiclive; import android.content.Context; public final class ScreenOcrGreetingService { public static void stop(Context c){} }
JAVA
cat > "$SRC/com/maru/musiclive/MainActivity.java" <<'JAVA'
package com.maru.musiclive; public class MainActivity {}
JAVA
cat > "$SRC/com/maru/musiclive/R.java" <<'JAVA'
package com.maru.musiclive; public final class R { public static final class drawable { public static final int ic_stat_music=1; } public static final class string { public static final int auto_greeting_channel_name=1,auto_greeting_channel_description=2,channel_name=3,channel_description=4,app_name=5; } }
JAVA

javac -encoding UTF-8 -d "$CLS" \
  $(find "$SRC" -name '*.java' | sort) \
  "$ROOT/app/src/main/java/com/maru/musiclive/EventType.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/LiveEvent.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/GreetingLanguage.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/BroadcastVoicePolicy.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/SongTitleFormatter.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/TtsAnnouncementText.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/IntermissionAnnouncementText.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/BroadcastClosingText.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/IntermissionStore.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/VolumeDucking.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/RandomPlaybackGuard.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/AutoGreetingService.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/PlaybackService.java"

echo "PLAYBACK-TTS-CHECKED-COMPILE-TEST: PASS"
