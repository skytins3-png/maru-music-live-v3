package com.maru.pyscore;

import android.app.Activity;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.content.Intent;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.AudioFormat;
import android.view.View;
import android.widget.*;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.io.*;
import java.nio.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  static final int PICK_AUDIO=200, SAVE_FILE=201;
  final ExecutorService work=Executors.newSingleThreadExecutor();
  LinearLayout body;
  TextView status;
  ImageView preview;
  Button pick,pdf,xml,mid;
  File output;
  String exportExt=null;
  String fileTitle="MARU";
  final int blue=Color.rgb(8,147,231),dark=Color.rgb(7,22,42);
  void status(String msg){runOnUiThread(()->status.setText(msg));}
  @Override public void onCreate(Bundle saved){
    super.onCreate(saved);
    if(!Python.isStarted())Python.start(new AndroidPlatform(getApplicationContext()));
    ScrollView scroll=new ScrollView(this);
    scroll.setBackgroundColor(dark);
    body=new LinearLayout(this);body.setPadding(22,28,22,30);
    body.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(body);setContentView(scroll);
    text("MARU PYTHON SCORE",26,Color.WHITE);
    text("음원 하나 선택 → 파이썬 자동 악보 생성",17,0xffb9e8ff);
    pick=button("🎵 음원 선택 · 악보 만들기",()->selectAudio(),blue);
    status=text("MP3 · M4A · WAV, 인터넷 없이 작동",15,0xffd9eaf7);
    preview=new ImageView(this);preview.setBackgroundColor(Color.WHITE);
    preview.setAdjustViewBounds(true);
    preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,560);
    params.topMargin=20;params.bottomMargin=18;
    body.addView(preview,params);
    pdf=button("악보 PDF 저장",()->save("pdf","application/pdf"),blue);
    xml=button("MusicXML 저장",()->save("musicxml","application/xml"),0xff204e76);
    mid=button("MIDI 저장",()->save("mid","audio/midi"),0xff204e76);
    enable(false);
    text("완성곡의 주선율을 추정하는 악보입니다. 반주 전체 총보는 아닙니다.\n기존 MARU RADIO 105곡은 변경하지 않습니다.",13,0xffbccbd7);
    output=new File(getCacheDir(),"maru_python_score");
    output.mkdirs();
  }
  TextView text(String value,int sp,int color){
    TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(sp);
    v.setPadding(0,10,0,16);body.addView(v);return v;
  }
  Button button(String label,Runnable action,int color){
    Button b=new Button(this);b.setText(label);b.setTextSize(17);
    b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,122);lp.bottomMargin=12;
    body.addView(b,lp);b.setOnClickListener(v->action.run());return b;
  }
  void enable(boolean ready){pdf.setEnabled(ready);xml.setEnabled(ready);mid.setEnabled(ready);}
  void selectAudio(){
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("audio/*");
    startActivityForResult(intent,PICK_AUDIO);
  }
  String titleOf(Uri uri){
    try(Cursor c=getContentResolver().query(uri,null,null,null,null)){
      if(c!=null && c.moveToFirst()){
        int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(idx>=0){String title=c.getString(idx);if(title!=null)return title.replaceFirst("(?i)\\.(mp3|m4a|wav|aac|ogg)$","");}
      }
    }catch(Exception ignored){}
    return "MARU";
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();
    if(request==PICK_AUDIO){
      pick.setEnabled(false);enable(false);preview.setImageDrawable(null);
      fileTitle=titleOf(uri);
      status("음원 분석 중…");
      work.submit(()->{
        try{
          File input=new File(getCacheDir(),"maru_source.wav");
          decodeAudio(uri,input,180);
          Python python=Python.getInstance();
          String resultText=python.getModule("create_score")
            .callAttr("make",input.getAbsolutePath(),output.getAbsolutePath(),fileTitle).toString();
          showPdf(new File(output,"score.pdf"));
          status(fileTitle+"\n"+resultText+"\nPDF·MusicXML·MIDI 저장 가능");
          runOnUiThread(()->{pick.setEnabled(true);enable(true);});
        }catch(Exception e){
          status("악보 생성 실패: "+e.getMessage());
          runOnUiThread(()->pick.setEnabled(true));
        }
      });
    }else if(request==SAVE_FILE&&exportExt!=null){
      final String ext=exportExt;exportExt=null;
      work.submit(()->{
        try(InputStream in=new FileInputStream(new File(output,"score."+ext));
            OutputStream out=getContentResolver().openOutputStream(uri)){
          if(out==null)throw new IOException("저장 위치 접근 실패");
          byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
          status("악보 저장 완료: "+ext);
        }catch(Exception e){status("악보 저장 실패: "+e.getMessage());}
      });
    }
  }
  void save(String ext,String mime){
    String fileExt=ext.equals("musicxml")?"musicxml":ext;
    File f=new File(output,"score."+fileExt);
    if(!f.exists()){status("먼저 음원을 선택하세요.");return;}
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);
    i.putExtra(Intent.EXTRA_TITLE,fileTitle.replaceAll("[\\\\/:*?\"<>|]","_")+"."+ext);
    exportExt=fileExt;
    startActivityForResult(i,SAVE_FILE);
  }
  void showPdf(File path) throws Exception{
    try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(path,ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer=new PdfRenderer(fd)){
      PdfRenderer.Page page=renderer.openPage(0);
      try{
        Bitmap bitmap=Bitmap.createBitmap(page.getWidth()*2,page.getHeight()*2,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        runOnUiThread(()->preview.setImageBitmap(bitmap));
      }finally{page.close();}
    }
  }
  /** Android MediaCodec extracts PCM16 into a temporary, mono WAV; Python performs ALL scoring. */
  void decodeAudio(Uri uri,File wav,int limitSeconds)throws Exception{
    MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
    ByteArrayOutputStream pcm=new ByteArrayOutputStream();
    int rate=44100,channels=1,encoding=2;
    try{
      extractor.setDataSource(this,uri,null);
      MediaFormat fmt=null;
      for(int i=0;i<extractor.getTrackCount();i++){
        MediaFormat candidate=extractor.getTrackFormat(i);
        if(candidate.getString(MediaFormat.KEY_MIME).startsWith("audio/")){fmt=candidate;extractor.selectTrack(i);break;}
      }
      if(fmt==null)throw new IOException("읽을 수 있는 음원이 없습니다.");
      rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
      channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
      codec=MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
      codec.configure(fmt,null,null,0);codec.start();
      MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
      boolean eofInput=false,eofOutput=false;
      int idle=0,totalFrames=0;
      while(!eofOutput && totalFrames<rate*limitSeconds && idle<500){
        if(!eofInput){
          int input=codec.dequeueInputBuffer(10000);
          if(input>=0){
            ByteBuffer b=codec.getInputBuffer(input);b.clear();
            int n=extractor.readSampleData(b,0);
            if(n<0){
              codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              eofInput=true;
            }else{
              codec.queueInputBuffer(input,0,n,extractor.getSampleTime(),0);
              extractor.advance();
            }
          }
        }
        int out=codec.dequeueOutputBuffer(info,10000);
        if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
          MediaFormat decoded=codec.getOutputFormat();
          rate=decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE);
          channels=decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
          if(decoded.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=decoded.getInteger(MediaFormat.KEY_PCM_ENCODING);
          else encoding=AudioFormat.ENCODING_PCM_16BIT;
        }else if(out>=0){
          idle=0;
          ByteBuffer b=codec.getOutputBuffer(out);
          if(b!=null&&info.size>0){
            b.position(info.offset);b.limit(info.offset+info.size);b.order(ByteOrder.LITTLE_ENDIAN);
            int bytesPer=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
            int samples=info.size/(bytesPer*Math.max(1,channels));
            for(int i=0;i<samples && totalFrames<rate*limitSeconds;i++){
              float sample=0;
              for(int c=0;c<channels;c++){
                if(encoding==AudioFormat.ENCODING_PCM_FLOAT)sample+=b.getFloat();
                else sample+=b.getShort()/32768f;
              }
              sample/=channels;
              int value=(int)(Math.max(-1,Math.min(1,sample))*32767);
              pcm.write(value&255);pcm.write((value>>>8)&255);
              totalFrames++;
            }
          }
          eofOutput=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
          codec.releaseOutputBuffer(out,false);
        }else idle++;
      }
      if(pcm.size()<rate)throw new IOException("음원 길이가 너무 짧거나 읽을 수 없습니다.");
      byte[] content=pcm.toByteArray();
      try(FileOutputStream f=new FileOutputStream(wav)){
        putFour(f,"RIFF");write32(f,36+content.length);putFour(f,"WAVE");
        putFour(f,"fmt ");write32(f,16);write16(f,1);write16(f,1);
        write32(f,rate);write32(f,rate*2);write16(f,2);write16(f,16);
        putFour(f,"data");write32(f,content.length);f.write(content);
      }
    }finally{
      if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
      extractor.release();
    }
  }
  void putFour(OutputStream out,String s)throws IOException{out.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
  void write16(OutputStream out,int n)throws IOException{out.write(n&255);out.write(n>>>8&255);}
  void write32(OutputStream out,int n)throws IOException{write16(out,n);write16(out,n>>>16);}
  @Override protected void onDestroy(){work.shutdownNow();super.onDestroy();}
}
