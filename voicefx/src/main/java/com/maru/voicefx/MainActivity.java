package com.maru.voicefx;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity{
 private static final int RATE=48000,PERMISSION=101,EXPORT=102;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final VoiceProcessor fx=new VoiceProcessor();
 private final Object lock=new Object();
 private volatile boolean running=false;
 private Thread thread;private WavWriter writer;private AudioRecord mic;
 private File file,lastFile;private boolean recording=false;
 private TextView status;private Button start,record,play,save;
 private final SeekBar[] bars=new SeekBar[4];private final TextView[] levels=new TextView[4];
 private final int[] values={50,57,70,70};
 private final String[] labels={"잡음 억제","따뜻한 저음","발음 선명도","음량 일정화"};
 private final int[][] presets={{50,57,70,70},{20,50,50,30},{42,75,55,55},{52,40,82,76},{60,50,65,90}};
 private final String[] names={"아환 라디오 진행","자연스러운 목소리","따뜻한 저음","뉴스 진행","작은 목소리 보강"};
 private CheckBox monitor;private MediaPlayer player;
 private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String s,int font,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(font);t.setTextColor(color);t.setPadding(0,dp(4),0,dp(4));return t;}
 private void space(LinearLayout p){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(12)));}
 private Button button(String s,LinearLayout p){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);p.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));return b;}
 private void message(String m){runOnUiThread(()->status.setText(m));}
 @Override public void onCreate(Bundle b){super.onCreate(b);
   getWindow().setStatusBarColor(Color.rgb(9,19,33));
   ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(9,19,33));
   LinearLayout root=new LinearLayout(this);root.setPadding(dp(16),dp(15),dp(16),dp(20));root.setOrientation(1);scroll.addView(root);setContentView(scroll);
   TextView title=text("MARU VOICE",29,Color.rgb(99,218,192));title.setTypeface(null,Typeface.BOLD);root.addView(title);
   root.addView(text("갤럭시 목소리 실시간 보정 · V1.1",14,Color.WHITE));
   root.addView(text("✓ 모든 음성 처리는 휴대폰 내부에서 진행",13,Color.rgb(99,218,192)));space(root);
   root.addView(text("목소리 스타일",19,Color.WHITE));
   for(int i=0;i<names.length;i++){int idx=i;Button item=button(names[i],root);item.setOnClickListener(v->{for(int j=0;j<4;j++)bars[j].setProgress(presets[idx][j]);message(names[idx]+" 적용");});}
   space(root);
   for(int i=0;i<4;i++){final int idx=i;
     levels[i]=text(labels[i]+"   "+values[i],15,Color.WHITE);root.addView(levels[i]);
     SeekBar bar=new SeekBar(this);bars[i]=bar;bar.setMax(100);bar.setProgress(values[i]);root.addView(bar);
     bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
       public void onProgressChanged(SeekBar s,int v,boolean u){values[idx]=v;levels[idx].setText(labels[idx]+"   "+v);fx.settings(values[0],values[1],values[2],values[3]);}
       public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
     });
   }
   space(root);root.addView(text("마이크 보정 · 녹음",19,Color.WHITE));
   monitor=new CheckBox(this);monitor.setText("이어폰으로 실시간 듣기 (하울링 주의)");monitor.setTextColor(Color.WHITE);root.addView(monitor);
   start=button("🎙 마이크 보정 시작",root);start.setOnClickListener(v->{if(running)stopMic();else requestMic();});
   record=button("● 보정 목소리 녹음 시작",root);record.setEnabled(false);record.setOnClickListener(v->{if(recording)finishRecording();else beginRecording();});
   play=button("▶ 마지막 녹음 듣기",root);play.setEnabled(false);play.setOnClickListener(v->play());
   save=button("⬇ WAV 파일 저장",root);save.setEnabled(false);save.setOnClickListener(v->export());
   space(root);status=text("준비 완료",14,Color.rgb(99,218,192));root.addView(status);
   space(root);root.addView(text("BIGO의 마이크를 직접 교체하거나 가상 마이크로 송출하는 기능은 없습니다. 앱을 벗어나면 마이크가 종료됩니다. 다른 MARU 앱의 데이터는 변경하지 않습니다.",12,Color.rgb(190,198,215)));
   fx.settings(values[0],values[1],values[2],values[3]);
 }
 private void requestMic(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},PERMISSION);return;}beginMic();}
 @Override public void onRequestPermissionsResult(int req,String[] p,int[] results){super.onRequestPermissionsResult(req,p,results);
   if(req==PERMISSION){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)beginMic();else message("마이크 사용 권한이 필요합니다.");}}
 private void beginMic(){if(running||thread!=null)return;stopPlayer();
   int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(min<=0){message("48kHz 마이크가 지원되지 않습니다.");return;}
   try{mic=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,8192));
     if(mic.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("마이크 초기화 실패");mic.startRecording();
   }catch(Exception e){if(mic!=null){mic.release();mic=null;}message("마이크 시작 실패: "+e.getMessage());return;}
   boolean monitoring=monitor.isChecked();monitor.setEnabled(false);running=true;start.setText("■ 마이크 보정 종료");record.setEnabled(true);
   message("실시간 보정 중. 녹음 시작을 누르세요.");
   thread=new Thread(()->loop(monitoring),"MARU-VOICE-DSP");thread.start();
 }
 private void loop(boolean monitoring){AudioRecord r=mic;AudioTrack output=null;
   short[] in=new short[960],out=new short[960];
   try{
     if(monitoring){int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
       if(min<=0)throw new IllegalStateException("이어폰 출력 불가");
       output=new AudioTrack(AudioManager.STREAM_MUSIC,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,8192),AudioTrack.MODE_STREAM);
       if(output.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("출력 초기화 실패");output.play();
     }
     while(running){int n=r.read(in,0,in.length,AudioRecord.READ_BLOCKING);
       if(n<=0){if(running)throw new IOException("마이크 오류: "+n);break;}fx.process(in,out,n);
       synchronized(lock){if(writer!=null)writer.append(out,n);}
       if(output!=null){int sent=0;while(sent<n&&running){int m=output.write(out,sent,n-sent);if(m<=0)break;sent+=m;}}
     }
   }catch(Exception e){message("오디오 처리 오류: "+e.getMessage());}
   finally{running=false;synchronized(lock){closeWriter();}
     if(output!=null){try{output.pause();output.flush();output.release();}catch(Exception ignored){}}
     if(r!=null){try{r.stop();}catch(Exception ignored){}r.release();}
     handler.post(()->{mic=null;thread=null;recording=false;start.setText("🎙 마이크 보정 시작");record.setText("● 보정 목소리 녹음 시작");record.setEnabled(false);monitor.setEnabled(true);
       if(lastFile!=null){play.setEnabled(true);save.setEnabled(true);}
     });
   }
 }
 private void stopMic(){running=false;message("마이크 종료 중...");}
 private void beginRecording(){if(!running)return;
   synchronized(lock){if(writer!=null)return;file=new File(getFilesDir(),"MARU_VOICE_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.KOREA).format(new Date())+".wav");
     try{writer=new WavWriter(file);}catch(Exception e){message("녹음 생성 오류: "+e.getMessage());return;}
   }
   recording=true;record.setText("■ 녹음 마치기");message("보정된 음성을 WAV로 녹음 중");
 }
 private void closeWriter(){if(writer==null)return;try{writer.close();lastFile=file;}catch(Exception e){message("저장 오류: "+e.getMessage());}writer=null;}
 private void finishRecording(){synchronized(lock){closeWriter();}recording=false;record.setText("● 보정 목소리 녹음 시작");
   if(lastFile!=null){play.setEnabled(true);save.setEnabled(true);message("녹음 완료: "+lastFile.getName());}
 }
 private void stopPlayer(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
 private void play(){if(lastFile==null)return;stopPlayer();try{player=new MediaPlayer();player.setDataSource(lastFile.getAbsolutePath());player.prepare();player.start();message("녹음 재생 중");}catch(Exception e){message("재생 오류: "+e.getMessage());}}
 private void export(){if(lastFile==null)return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/wav");i.putExtra(Intent.EXTRA_TITLE,lastFile.getName());startActivityForResult(i,EXPORT);}
 @Override protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);
   if(code!=EXPORT||result!=RESULT_OK||data==null||data.getData()==null||lastFile==null)return;
   Uri uri=data.getData();File src=lastFile;new Thread(()->{
    try(InputStream in=new FileInputStream(src);OutputStream out=getContentResolver().openOutputStream(uri)){
      if(out==null)throw new IOException("대상 파일 오류");byte[] block=new byte[65536];int n;while((n=in.read(block))!=-1)out.write(block,0,n);
      message("WAV 저장 완료: "+src.getName());
    }catch(Exception e){message("저장 실패: "+e.getMessage());}
   },"MARU-SAVE").start();
 }
 @Override protected void onPause(){super.onPause();if(running)stopMic();}
 @Override protected void onDestroy(){if(running)stopMic();stopPlayer();super.onDestroy();}
}