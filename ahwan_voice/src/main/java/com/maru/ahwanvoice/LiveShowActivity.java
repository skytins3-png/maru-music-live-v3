package com.maru.ahwanvoice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * AHwan guest-interview + singing show, offline-first.
 * A cue contains an operator-approved REAL recorded audio file.
 * Absolutely no TextToSpeech, fake live audience, automatic Suno scraping,
 * voice cloning, or BIGO capture is implemented.
 */
public final class LiveShowActivity extends Activity {
    private static final int PICK_CUE = 401;
    private static final int PICK_VISUAL = 402;
    private static final long MAX_VISUAL = 300L * 1024L * 1024L;
    private static final String PREFS = "ahwan_live_show_v1";
    private static final long MAX_FILE = 160L * 1024L * 1024L;
    public static final String[] SINGERS = {
        "강태준","민도윤","윤시후","한도겸","차이준","서건우","백유찬","강서율","류하준","남지후",
        "오태린","임선우","정하온","유도현","문이안","신재온","권도하","장유건","홍시안","송태오",
        "서하린","윤채린","한세아","차나린","백소윤","류서빈","남예린","오하늬","임다온","정유라"
    };
    private static final String[] CUE_NAMES = {
        "① 아환 환영 인사", "② 오늘의 가수 초대·근황 대화",
        "③ 가수의 첫 번째 노래", "④ 청취자 사연·답변",
        "⑤ 가수의 두 번째 노래", "⑥ 아환 마무리 인사"
    };

    private SharedPreferences pref;
    private Spinner singerSelect;
    private TextView status, counts, disclosure;
    private Button[] cueButtons = new Button[ShowQueue.CUE_COUNT];
    private MediaPlayer player;
    private int activeSinger = 0, selectedCue = -1, playingCue = -1;
    private int visualSelectedSinger = -1, visualSelectedCue = -1;
    private int lastFinishedCueCount = 0;
    private boolean autoAdvance, paused;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pref = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildView();
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView label(LinearLayout column, String title, int size, int color) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(3),dp(8),dp(3),dp(8));
        column.addView(t);
        return t;
    }

    private Button button(LinearLayout column, String title, View.OnClickListener callback) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        column.addView(b);
        b.setOnClickListener(callback);
        return b;
    }

    private void buildView() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16),dp(18),dp(16),dp(36));
        column.setBackgroundColor(0xFFF5F8FB);
        scroll.addView(column);
        setContentView(scroll);

        label(column,"아환 LIVE STUDIO",25,0xFF173A58);
        disclosure = label(column,
            "가상 AI 가수 초대 음악 토크 방송 · 녹음된 실제 오디오만 사용 · TTS 사용 안 함",14,0xFF314757);
        label(column,"오늘의 초대 가수 (남성 20명 · 여성 10명)",17,0xFF173A58);
        singerSelect = new Spinner(this);
        String[] options = new String[SINGERS.length];
        for (int i=0; i<SINGERS.length; i++) {
            options[i] = String.format(java.util.Locale.KOREA,"%02d · %s · 가상 AI 가수",
                    i+1, SINGERS[i]);
        }
        singerSelect.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options));
        column.addView(singerSelect);
        singerSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
                if (activeSinger != index) {
                    stop();
                    activeSinger=index;
                    pref.edit().putInt("last_singer",index).apply();
                }
                updateUi();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        button(column,"🧠 AI 토크 작가 · 생활 이야기·개그·학습",v -> {
            Intent writer=new Intent(this,TalkWriterActivity.class);
            writer.putExtra(TalkWriterActivity.EXTRA_SINGER_INDEX,activeSinger);
            startActivity(writer);
        });
        label(column,"토크 작가에서 대본을 승인한 뒤 실제 음성으로 녹음하여 방송실의 토크 코너에 등록하세요.",12,0xFF596474);
        button(column,"🎬 방송 영상·표지 관리 (등록·교체·삭제)",v -> chooseVisualSource());
        button(column,"▶ 전체화면 방송 화면 시작 · 영상+음악",v -> {
            stop();
            Intent show=new Intent(this,BroadcastStageActivity.class);
            show.putExtra(BroadcastStageActivity.EXTRA_SINGER,activeSinger);
            startActivity(show);
        });
        label(column,"세로 화면을 검은 여백 없이 채우는 로컬 영상 무대입니다. "
                +"화면은 방송 처음부터 끝까지 계속 보이고, 토크/노래 코너마다 지정한 "
                +"영상 또는 이미지를 표시합니다. BIGO 오디오 LIVE 내부 화면 공유는 별도 확인이 필요합니다.",
                12,0xFF596474);
        label(column,"초대 → 대화 → 노래 → 사연 → 노래 → 마무리",17,0xFF173A58);
        for (int i=0; i<ShowQueue.CUE_COUNT; i++) {
            final int slot=i;
            cueButtons[i]=button(column,CUE_NAMES[i],v -> chooseCue(slot));
        }
        counts=label(column,"등록 파일 0/6",14,0xFF35516C);
        button(column,"▶ 전체 방송 순차 재생",v -> playAll());
        button(column,"▶ 선택 코너 듣기",v -> {
            if (selectedCue<0) { status.setText("먼저 코너를 선택해 주세요.");return; }
            playOne(selectedCue);
        });
        button(column,"⏭ 다음 코너",v -> nextCue());
        button(column,"⏸ 일시정지 / 계속",v -> pauseResume());
        button(column,"■ 긴급 중단",v -> {
            stop();
            status.setText("방송 재생을 중단했습니다.");
        });
        status=label(column,"방송 준비 · 가수와 음성·음악 파일을 선택해 주세요.",15,0xFF16634F);
        label(column,"운영 원칙: 방송 권한이 확인된 자작곡과 승인 녹음만 등록하세요. "
                +"각 코너를 누르면 파일 등록·교체·삭제가 가능합니다. "
                +"빈 코너는 자동으로 건너뜁니다. 가상 AI 가수의 설정은 사실이 아님을 알립니다. "
                +"이 단계는 로컬 방송 리허설이며 BIGO 청취자 송출·실시간 AI 발화는 검증되지 않았습니다.",
                12,0xFF596474);
        singerSelect.setSelection(Math.min(SINGERS.length-1,
                Math.max(0,pref.getInt("last_singer",0))));
        updateUi();
    }

    private String uriKey(int singer, int cue) { return "s"+singer+"_c"+cue+"_uri"; }
    private String titleKey(int singer, int cue) { return "s"+singer+"_c"+cue+"_title"; }
    private String media(int cue) { return pref.getString(uriKey(activeSinger,cue),""); }
    private boolean[] readiness() {
        boolean[] ready = new boolean[ShowQueue.CUE_COUNT];
        for (int i=0; i<ready.length; i++) ready[i]=!media(i).isEmpty();
        return ready;
    }
    private void updateUi() {
        if (counts == null) return;
        for (int i=0;i<cueButtons.length;i++) {
            if (cueButtons[i]==null) continue;
            String name=pref.getString(titleKey(activeSinger,i),"");
            cueButtons[i].setText(CUE_NAMES[i]+(name.isEmpty()?" · 미등록":" · "+trim(name,25)));
        }
        counts.setText("등록 파일 "+ShowQueue.registeredCount(readiness())+"/6 · "
                +SINGERS[activeSinger]+" (가상 AI 가수) · 실제 음원만 재생");
    }
    private String trim(String source, int max) {
        return source.length()>max?source.substring(0,max)+"…":source;
    }

    private void chooseVisualSource() {
        String[] options=new String[ShowQueue.CUE_COUNT+1];
        options[0]="기본 방송 배경 (모든 코너 대체)";
        for(int i=0;i<ShowQueue.CUE_COUNT;i++)options[i+1]=CUE_NAMES[i]+" 화면";
        new AlertDialog.Builder(this)
                .setTitle("방송 영상·표지 관리")
                .setItems(options,(dialog,which)->visualActions(which-1))
                .setNegativeButton("취소",null).show();
    }
    private String visualKey(int which,int whichSinger) {
        return which<0?BroadcastStageActivity.DEFAULT_VISUAL_KEY:
                "stage_s"+whichSinger+"_c"+which+"_visual_uri";
    }
    private String mimeKey(int which,int whichSinger) {
        return which<0?BroadcastStageActivity.DEFAULT_VISUAL_MIME:
                "stage_s"+whichSinger+"_c"+which+"_visual_mime";
    }
    private void visualActions(int chosenCue) {
        int snapshotSinger=activeSinger;
        boolean saved=!pref.getString(visualKey(chosenCue,snapshotSinger),"").isEmpty();
        new AlertDialog.Builder(this)
                .setTitle(chosenCue<0?"기본 방송 배경":CUE_NAMES[chosenCue]+" 화면")
                .setMessage(saved?"등록된 이미지·동영상이 있습니다. 교체나 해제가 가능합니다.":
                        "이 화면의 영상 또는 표지가 아직 없습니다.")
                .setPositiveButton(saved?"교체":"영상·이미지 선택",(d,w)->
                        confirmVisualRights(chosenCue,snapshotSinger))
                .setNeutralButton(saved?"등록 해제":"닫기",(d,w)->{
                    if(saved) {
                        pref.edit()
                          .remove(visualKey(chosenCue,snapshotSinger))
                          .remove(mimeKey(chosenCue,snapshotSinger)).apply();
                        status.setText("이 화면의 영상 연결 해제 · 원본 파일은 보존했습니다.");
                    }
                })
                .setNegativeButton("취소",null).show();
    }
    private void confirmVisualRights(int chosenCue,int chosenSinger) {
        new AlertDialog.Builder(this)
            .setTitle("방송 영상·초상권 확인")
            .setMessage("이 영상이나 사진에 대한 BIGO 방송 사용 허락이 있나요? "
                    +"실존 인물의 얼굴·음성을 포함한다면 공개 동의도 확인해야 합니다. "
                    +"가상 AI 인물은 화면에서 가상임을 표시합니다.")
            .setPositiveButton("권리 확인 후 선택",(d,w)->{
                visualSelectedCue=chosenCue;
                visualSelectedSinger=chosenSinger;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"video/*","image/*"});
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        |Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,PICK_VISUAL);
            })
            .setNegativeButton("취소",null).show();
    }
    private void importVisual(Intent data){
        if(data==null||data.getData()==null||visualSelectedSinger<0)return;
        Uri uri=data.getData();
        if(!"content".equals(uri.getScheme())) {
            status.setText("안전한 파일 선택기를 통해 이미지 또는 영상을 선택하세요.");
            return;
        }
        String mime=getContentResolver().getType(uri);
        if(mime==null || !(mime.startsWith("video/") || mime.startsWith("image/"))) {
            status.setText("이미지와 동영상만 등록할 수 있습니다.");
            return;
        }
        long limit=mime.startsWith("video/")?MAX_VISUAL:30L*1024L*1024L;
        try(Cursor c=getContentResolver().query(uri,
                new String[]{OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()) {
                int index=c.getColumnIndex(OpenableColumns.SIZE);
                if(index>=0&&!c.isNull(index)&&c.getLong(index)>limit){
                    status.setText("영상은 300MB, 이미지는 30MB 이하로 선택하세요.");
                    return;
                }
            }
        }catch(RuntimeException ignored){}
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }catch(SecurityException ex){
            status.setText("장기 접근 권한 없음 · 다운로드 폴더에서 다시 선택하세요.");
            return;
        }
        pref.edit().putString(visualKey(visualSelectedCue,visualSelectedSinger),uri.toString())
                .putString(mimeKey(visualSelectedCue,visualSelectedSinger),mime)
                .apply();
        status.setText("영상/이미지 등록 완료 · 전체화면 방송 시작에서 확인하세요.");
        visualSelectedSinger=-1;
        visualSelectedCue=-1;
    }

    private void chooseCue(int index) {
        if (player!=null) stop();
        selectedCue=index;
        new AlertDialog.Builder(this)
            .setTitle(CUE_NAMES[index])
            .setItems(new String[]{"MP3/WAV/M4A 등록 또는 교체","이 코너 재생","등록 해제",
                    "돌아가기"}, (dialog,which) -> {
                if (which==0) confirmFileRights(index);
                else if (which==1) playOne(index);
                else if (which==2) clearCue(index);
            }).show();
    }

    private void confirmFileRights(int cue) {
        new AlertDialog.Builder(this)
            .setTitle("방송 파일 권리 확인")
            .setMessage("이 파일은 내가 제작했거나 방송·공연에 필요한 사용 허락을 받은 "
                    +"음원/음성입니까?\n\n다른 가수의 목소리, 실존 손님의 녹음, "
                    +"청취자의 사연은 별도 동의가 있어야 합니다. "
                    +"이 등록 동의는 실제 권리 보유의 증명이 아닙니다.")
            .setPositiveButton("권리 확인 후 선택", (d,w)->{
                selectedCue=cue;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("audio/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,PICK_CUE);
            })
            .setNegativeButton("취소",null).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_VISUAL){
            if(result==RESULT_OK)importVisual(data);
            return;
        }
        if (request!=PICK_CUE || result!=RESULT_OK || data==null
                || data.getData()==null || selectedCue<0) return;
        Uri uri=data.getData();
        if (!"content".equals(uri.getScheme())) {
            status.setText("안전한 시스템 파일 선택기로 선택한 오디오만 등록할 수 있어요.");
            return;
        }
        String mime=getContentResolver().getType(uri);
        if (mime!=null && !mime.startsWith("audio/") && !mime.equals("application/ogg")) {
            status.setText("오디오 파일만 등록할 수 있어요.");
            return;
        }
        long size=-1;
        String name="승인한 오디오";
        try(Cursor c=getContentResolver().query(uri,new String[]{
                OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(c!=null && c.moveToFirst()) {
                int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s=c.getColumnIndex(OpenableColumns.SIZE);
                if(n>=0 && !c.isNull(n)) name=c.getString(n);
                if(s>=0 && !c.isNull(s)) size=c.getLong(s);
            }
        }catch(Exception ignored){}
        if(size>MAX_FILE) {status.setText("160MB 이하의 오디오 파일만 등록해 주세요.");return;}
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(SecurityException ex) {
            status.setText("이 파일을 계속 읽을 권한이 없습니다. 다운로드 폴더에서 다시 선택해 주세요.");
            return;
        }
        pref.edit().putString(uriKey(activeSinger,selectedCue),uri.toString())
            .putString(titleKey(activeSinger,selectedCue),name).apply();
        status.setText(CUE_NAMES[selectedCue]+" 등록 완료 · "+trim(name,35));
        updateUi();
    }

    private void clearCue(int cue) {
        stop();
        pref.edit().remove(uriKey(activeSinger,cue))
                .remove(titleKey(activeSinger,cue)).apply();
        updateUi();
        status.setText(CUE_NAMES[cue]+" 등록 해제 · 원본 파일은 삭제하지 않았습니다.");
    }

    private void playOne(int cue) {
        stop();
        autoAdvance=false;
        if (cue<0 || cue>=ShowQueue.CUE_COUNT || media(cue).isEmpty()) {
            status.setText("이 코너에 승인된 음성이 없습니다. 파일을 먼저 등록하세요.");
            return;
        }
        playCue(cue);
    }

    private void playAll() {
        stop();
        autoAdvance=true;
        lastFinishedCueCount=0;
        playNextFrom(0);
    }

    private void nextCue() {
        int from=playingCue<0 ? 0 : playingCue+1;
        boolean keepAuto=autoAdvance;
        stopPlayer();
        autoAdvance=keepAuto;
        playNextFrom(from);
    }

    private void playNextFrom(int from) {
        int next=ShowQueue.nextAvailable(readiness(),from);
        if(next<0) {
            stopPlayer();
            autoAdvance=false;
            status.setText(lastFinishedCueCount>0
                    ?"오늘의 초대 방송 리허설 종료 · "+lastFinishedCueCount+"개 코너 완료"
                    :"등록된 다음 코너가 없습니다. 음성·노래를 먼저 등록하세요.");
            return;
        }
        playCue(next);
    }

    private void playCue(int cue) {
        String source=media(cue);
        if(source.isEmpty()) {
            if (autoAdvance) playNextFrom(cue+1);
            else status.setText("이 코너 파일이 없습니다.");
            return;
        }
        stopPlayer();
        playingCue=cue;
        paused=false;
        final Uri uri=Uri.parse(source);
        MediaPlayer mp=new MediaPlayer();
        player=mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(cue==2||cue==4
                            ? AudioAttributes.CONTENT_TYPE_MUSIC
                            : AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(this,uri);
            mp.setOnPreparedListener(prepared -> {
                if(player!=prepared) return;
                try {
                    prepared.start();
                    status.setText("▶ "+SINGERS[activeSinger]+" · "+CUE_NAMES[cue]
                            +" (실제 녹음 파일)");
                }catch(RuntimeException ex) { handlePlaybackError(prepared,cue); }
            });
            mp.setOnCompletionListener(done -> {
                if(player!=done) return;
                lastFinishedCueCount++;
                stopPlayer();
                if(autoAdvance) playNextFrom(cue+1);
                else status.setText(CUE_NAMES[cue]+" 재생 완료");
            });
            mp.setOnErrorListener((bad,what,extra)-> {
                if(player==bad) handlePlaybackError(bad,cue);
                return true;
            });
            mp.prepareAsync();
        } catch(Exception ex) {
            handlePlaybackError(mp,cue);
        }
    }

    private void handlePlaybackError(MediaPlayer mp,int cue) {
        if(player!=mp)return;
        boolean continuation=autoAdvance;
        stopPlayer();
        status.setText("오디오 파일 재생 실패: "+CUE_NAMES[cue]+" · 파일 교체 필요");
        if(continuation) {autoAdvance=true;playNextFrom(cue+1);}
    }

    private void pauseResume() {
        if(player==null) {status.setText("재생 중인 코너가 없습니다.");return;}
        try {
            if(paused) {
                player.start();
                paused=false;
                status.setText("▶ 다시 재생 · "+CUE_NAMES[playingCue]);
            } else if(player.isPlaying()) {
                player.pause();
                paused=true;
                status.setText("⏸ 일시정지 · "+CUE_NAMES[playingCue]);
            }
        }catch(RuntimeException e){stop();status.setText("재생 상태 오류 · 재시작해 주세요.");}
    }

    private void stopPlayer() {
        MediaPlayer old=player;
        player=null;
        playingCue=-1;
        paused=false;
        if(old!=null) {
            try{old.reset();}catch(RuntimeException ignored){}
            try{old.release();}catch(RuntimeException ignored){}
        }
    }
    private void stop() {
        autoAdvance=false;
        stopPlayer();
    }

    /** Visible to the on-device instrumentation test; no external or remote access. */
    public int finishedCueCountForTest() { return lastFinishedCueCount; }
    public String statusForTest() {return status==null?"":status.getText().toString();}

    @Override protected void onDestroy() {
        stop();
        super.onDestroy();
    }
}
