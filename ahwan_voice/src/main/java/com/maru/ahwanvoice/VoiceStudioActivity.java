package com.maru.ahwanvoice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Color;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * Audio-only operator voice station. NO TTS and NO voice imitation.
 * Audio must be recorded by this user or explicitly selected from their phone.
 * Audio notes can be sent to the linked MARU app for immediate playback.
 */
public final class VoiceStudioActivity extends Activity {
    private static final int PICK_AUDIO = 21;
    private static final int MIC_PERMISSION = 22;
    private static final String TARGET_PACKAGE = "com.maru.musiclive.clips.v20261105";
    private static final String TARGET_ACTIVITY = "com.maru.musiclive.MainActivity";
    private static final String[] CODES = {"ko","en","zh"};
    private static final String[] CATEGORIES = {"환영 인사","곡 소개","사연 소개","댓글 답변","자유 토크"};
    private Spinner languages, category;
    private TextView status, assigned;
    private SharedPreferences storage;
    private MediaPlayer playback;
    private MediaRecorder recorder;
    private File recording;
    private boolean recordingNow;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        storage = getSharedPreferences("ahwan_clips_private", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(18),dp(20),dp(18),dp(30));
        area.setBackgroundColor(0xFFF3F7F9);
        scroll.addView(area);
        setContentView(scroll);
        text(area,"아환 실제 목소리 스튜디오",25,0xFF163A58);
        button(area,"🎙 아환 LIVE 초대·토크·노래 방송실 열기", () ->
                startActivity(new Intent(this, LiveShowActivity.class)));
        text(area,"휴대폰 TTS 사용 안 함 · 실제 녹음 및 본인 음성 파일만 재생",13,0xFF324C5C);
        text(area,"언어 선택",17,0xFF163A58);
        languages = new Spinner(this);
        languages.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"한국어","English","中文"}));
        area.addView(languages);
        text(area,"멘트 종류",17,0xFF163A58);
        category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,CATEGORIES));
        area.addView(category);

        button(area,"1. 실제 목소리 녹음 시작",this::startRecording);
        button(area,"2. 녹음 종료 · 이 멘트로 저장",this::stopAndSaveRecording);
        button(area,"또는 기존 MP3/WAV/M4A 등록·교체",this::pickClip);
        button(area,"등록한 음성 미리듣기",this::listenClip);
        button(area,"MARU RADIO로 음성 보내기",this::sendClipToMaru);
        button(area,"이 멘트 등록 해제",this::removeClip);
        button(area,"재생 중지",this::stopPlayback);
        assigned = text(area,"등록된 음성 없음",14,0xFF163A58);
        status = text(area,"준비 완료 · 음성을 직접 녹음하거나 파일을 선택하세요.",14,0xFF1A5B49);
        text(area,"참고: 이 앱은 형의 실제 음성만 재생합니다. 새로운 글을 형 목소리로 "
                + "합성하는 AI 엔진은 아직 없으며 BIGO에 직접 송출하지 않습니다. "
                + "MARU 연결판에서 파일을 확인하고 바로 재생할 수 있습니다.",12,0xFF57636C);
        languages.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p,
                    android.view.View v, int position, long id) { refresh(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p,
                    android.view.View v, int position, long id) { refresh(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
        refresh();
    }

    private TextView text(LinearLayout area,String value,int size,int color) {
        TextView v=new TextView(this);
        v.setText(value);v.setTextSize(size);v.setTextColor(color);
        v.setPadding(0,dp(8),0,dp(8));area.addView(v);return v;
    }
    private void button(LinearLayout area,String label,Runnable action) {
        Button b=new Button(this); b.setAllCaps(false);b.setText(label);area.addView(b);
        b.setOnClickListener(v -> action.run());
    }
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private String key(){return "clip_"+CODES[languages.getSelectedItemPosition()]
            +"_"+category.getSelectedItemPosition();}
    private String current(){return storage.getString(key(),"");}
    private void refresh() {
        if (assigned == null || languages == null || category == null) return;
        assigned.setText(current().isEmpty()?"이 항목의 음성 파일 없음":
                "이 항목에 본인 녹음 음성 파일 등록됨");
    }
    private void pickClip() {
        if (recordingNow) { status.setText("먼저 녹음을 종료하세요.");return; }
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_AUDIO);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request!=PICK_AUDIO || result!=RESULT_OK || data==null || data.getData()==null)
            return;
        Uri uri=data.getData();
        try{
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException ignored){}
        // The document picker grants access only after explicit user selection.
        storage.edit().putString(key(),uri.toString()).apply();
        status.setText("음성 파일 등록 완료 · 미리듣기 후 MARU로 보내세요.");
        refresh();
    }

    private void startRecording() {
        if (recordingNow) {status.setText("이미 녹음 중입니다.");return;}
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
            return;
        }
        stopPlayback();
        try{
            File dir=new File(getFilesDir(),"voice_notes");
            if (!dir.exists()&&!dir.mkdirs()) throw new IOException("녹음 폴더 실패");
            recording=File.createTempFile("ahwan-", ".m4a",dir);
            recorder = Build.VERSION.SDK_INT>=Build.VERSION_CODES.S
                    ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recording.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recordingNow=true;
            status.setText("녹음 중 · 형의 실제 목소리만 녹음합니다. 종료 후 저장하세요.");
        }catch(Exception ex){
            releaseRecorder();
            if(recording!=null) recording.delete();
            status.setText("마이크 녹음 실패 · 기기 권한을 확인하세요.");
        }
    }

    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants) {
        super.onRequestPermissionsResult(request,perms,grants);
        if(request==MIC_PERMISSION) {
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)
                startRecording();
            else status.setText("마이크 권한이 없어서 녹음하지 않았습니다.");
        }
    }

    private void releaseRecorder(){
        if(recorder!=null){
            try{recorder.reset();}catch(RuntimeException ignored){}
            try{recorder.release();}catch(RuntimeException ignored){}
            recorder=null;
        }
        recordingNow=false;
    }

    private void stopAndSaveRecording(){
        if(!recordingNow||recorder==null||recording==null){
            status.setText("진행 중인 녹음이 없습니다.");return;
        }
        boolean good=true;
        try{recorder.stop();}catch(RuntimeException ex){good=false;}
        releaseRecorder();
        if(!good||recording.length()<256){
            recording.delete();status.setText("녹음 실패 · 짧거나 비어 있는 파일입니다.");return;
        }
        Uri voice=FileProvider.getUriForFile(this,
                getPackageName()+".fileprovider",recording);
        storage.edit().putString(key(),voice.toString()).apply();
        status.setText("실제 목소리 녹음 저장 완료 · 미리듣기 가능합니다.");
        refresh();
    }

    private void listenClip(){
        if(recordingNow){status.setText("먼저 녹음을 종료하세요.");return;}
        Uri uri=clip();
        if(uri==null){status.setText("먼저 녹음하거나 음성 파일을 등록하세요.");return;}
        stopPlayback();
        try{
            playback=new MediaPlayer();
            playback.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            playback.setDataSource(this,uri);
            playback.setOnPreparedListener(mp -> {mp.start();status.setText("실제 녹음 음성 재생 중");});
            playback.setOnCompletionListener(mp -> stopPlayback());
            playback.setOnErrorListener((mp,what,extra)-> {
                stopPlayback();status.setText("파일 재생 실패 · 다시 등록하세요.");return true;
            });
            playback.prepareAsync();
        }catch(Exception e){stopPlayback();status.setText("음성 파일을 열 수 없습니다.");}
    }

    private Uri clip(){
        String value=current();return value.isEmpty()?null:Uri.parse(value);
    }
    private void sendClipToMaru() {
        if(recordingNow){status.setText("녹음 종료 후 보내세요.");return;}
        Uri uri=clip();
        if(uri==null){status.setText("전송할 음성 파일을 먼저 등록하세요.");return;}
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setClassName(TARGET_PACKAGE,TARGET_ACTIVITY);
        send.setType("audio/*");
        send.putExtra(Intent.EXTRA_STREAM,uri);
        send.putExtra("com.maru.ahwanvoice.AUDIO_CLIP",true);
        send.putExtra("com.maru.ahwanvoice.LANGUAGE",CODES[languages.getSelectedItemPosition()]);
        send.setClipData(ClipData.newUri(getContentResolver(),"아환 실제 목소리",uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try{
            startActivity(send);
            status.setText("MARU 연결판으로 전달 · MARU에서 재생을 확인하세요.");
        }catch(RuntimeException e){
            status.setText("MARU 실제음성 연결판이 필요합니다. 이전 연결판과 구별하세요.");
        }
    }

    private void removeClip(){
        if(recordingNow){status.setText("녹음을 종료한 뒤 해제하세요.");return;}
        storage.edit().remove(key()).apply();refresh();
        status.setText("이 항목 연결 해제 · 휴대폰 원본 파일은 삭제하지 않았습니다.");
    }
    private void stopPlayback(){
        if(playback!=null){
            try{playback.stop();}catch(RuntimeException ignored){}
            playback.release();playback=null;
        }
    }
    @Override protected void onDestroy(){
        if(recordingNow&&recorder!=null){
            try{recorder.stop();}catch(RuntimeException ignored){}
        }
        releaseRecorder();
        stopPlayback();
        super.onDestroy();
    }
}
