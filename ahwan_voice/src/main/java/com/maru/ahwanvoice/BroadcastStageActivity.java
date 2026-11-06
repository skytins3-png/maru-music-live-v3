package com.maru.ahwanvoice;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Full-screen, camera-free portrait video/audio RADIO PREVIEW, not a BIGO
 * live video publisher. An external streaming mode must officially support
 * importing/capturing this screen before distant listeners can view it.
 *
 * The 6 cue audio queue is locally played, visual MP4s loop muted (to avoid
 * duplicate sound), and each cue can show its own video/image or a default
 * approved image. Keeps display awake while visible. Does not bypass Android
 * screen capture consent, BIGO's media track permissions or screen locks.
 */
public final class BroadcastStageActivity extends Activity
        implements TextureView.SurfaceTextureListener {
    public static final String EXTRA_SINGER = "com.maru.ahwanvoice.STAGE_SINGER";
    public static final String PREF_NAME = "ahwan_live_show_v1";
    public static final String DEFAULT_VISUAL_KEY = "stage_default_visual_uri";
    public static final String DEFAULT_VISUAL_MIME = "stage_default_visual_mime";

    private SharedPreferences prefs;
    private FrameLayout stage;
    private ImageView background;
    private TextureView videoView;
    private TextView title, overlayStatus, disclosure;
    private LinearLayout controls;
    private MediaPlayer soundPlayer, videoPlayer;
    private Surface videoSurface;
    private int singer, currentCue = -1, completed;
    private int visualEpoch;
    private boolean running = true, paused;
    private String videoToLoad;
    private String fallbackLabel = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        singer = Math.min(Math.max(0,getIntent().getIntExtra(EXTRA_SINGER,0)),
                LiveShowActivity.SINGERS.length-1);
        prefs=getSharedPreferences(PREF_NAME,MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildStage();
        hideBars();
        showVisual(-1);
        // Starts only after user opens the stage from the explicit start button.
        playNextFrom(0);
    }

    private int dp(int n) {
        return Math.round(n*getResources().getDisplayMetrics().density);
    }

    private void buildStage() {
        stage=new FrameLayout(this);
        stage.setBackgroundColor(Color.BLACK);
        setContentView(stage);

        background=new ImageView(this);
        background.setBackgroundColor(Color.BLACK);
        background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stage.addView(background,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        videoView=new TextureView(this);
        videoView.setOpaque(false);
        videoView.setSurfaceTextureListener(this);
        videoView.setVisibility(View.GONE);
        stage.addView(videoView,new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout legend=new LinearLayout(this);
        legend.setOrientation(LinearLayout.VERTICAL);
        legend.setBackgroundColor(0x8B090D16);
        legend.setPadding(dp(14),dp(14),dp(14),dp(10));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP);
        stage.addView(legend,lp);
        title=new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setText("아환 LIVE STUDIO");
        title.setGravity(Gravity.CENTER);
        legend.addView(title);
        disclosure=new TextView(this);
        disclosure.setGravity(Gravity.CENTER);
        disclosure.setTextSize(12);
        disclosure.setTextColor(0xFFE7EEF4);
        disclosure.setText("가상 AI 가수 · 로컬 방송 화면 · BIGO 송출 미검증");
        legend.addView(disclosure);

        TextView empty=new TextView(this);
        empty.setTextColor(0xFFD8E5F3);
        empty.setTextSize(18);
        empty.setGravity(Gravity.CENTER);
        empty.setText("AHWAN LIVE STUDIO\n\n방송 배경·토크 영상 미등록");
        empty.setTag("fallback_message");
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.CENTER);
        fp.leftMargin=dp(18);
        fp.rightMargin=dp(18);
        stage.addView(empty,fp);
        fallbackLabel="AHWAN LIVE STUDIO";
        empty.setVisibility(View.VISIBLE);

        controls=new LinearLayout(this);
        controls.setTag("stage_controls");
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(10),dp(5),dp(10),dp(15));
        controls.setBackgroundColor(0xB9090C12);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM);
        stage.addView(controls,cp);
        overlayStatus=new TextView(this);
        overlayStatus.setTextColor(Color.WHITE);
        overlayStatus.setTextSize(14);
        overlayStatus.setText("방송 화면 준비");
        overlayStatus.setGravity(Gravity.CENTER);
        controls.addView(overlayStatus);
        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        controls.addView(actions);
        addButton(actions,"이전/다음",()->nextCue());
        addButton(actions,"일시정지",()->pauseOrResume());
        addButton(actions,"종료",()->endStage());
        addButton(controls,"화면 조작 버튼 숨기기",()->controls.setVisibility(View.GONE));
        stage.setOnClickListener(v->{
            if(controls.getVisibility()!=View.VISIBLE){
                controls.setVisibility(View.VISIBLE);
                hideBars();
            }
        });
    }

    private void addButton(LinearLayout parent,String label,Runnable action) {
        Button b=new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
        if(parent==controls){
            lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        parent.addView(b,lp);
        b.setOnClickListener(v->action.run());
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private boolean[] ready() {
        boolean[] flags=new boolean[ShowQueue.CUE_COUNT];
        for(int i=0;i<flags.length;i++){
            flags[i]=!audio(i).isEmpty();
        }
        return flags;
    }
    private String audio(int cue) {
        return prefs.getString("s"+singer+"_c"+cue+"_uri","");
    }

    private void playNextFrom(int from) {
        if(!running) return;
        int next=ShowQueue.nextAvailable(ready(),from);
        if(next<0){
            currentCue=-1;
            clearSound();
            showVisual(-1);
            overlayStatus.setText(completed>0?
                    "방송 종료 · "+completed+"개 코너 완료":
                    "방송 파일 미등록 · 음성과 노래를 먼저 준비하세요");
            title.setText("아환 LIVE STUDIO · 방송 종료");
            return;
        }
        clearSound();
        currentCue=next;
        paused=false;
        showVisual(next);
        overlayStatus.setText("음성 준비 중 · "+LiveShowActivity.CUE_NAMES[next]);
        MediaPlayer mp=new MediaPlayer();
        soundPlayer=mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(next==2||next==4?
                            AudioAttributes.CONTENT_TYPE_MUSIC:
                            AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(this,Uri.parse(audio(next)));
            mp.setOnPreparedListener(p->{
                if(soundPlayer!=p||!running)return;
                try {
                    p.start();
                    overlayStatus.setText("재생 · "+LiveShowActivity.CUE_NAMES[next]);
                }catch(RuntimeException e){soundError(p,next);}
            });
            mp.setOnCompletionListener(p->{
                if(soundPlayer!=p||!running)return;
                completed++;
                playNextFrom(next+1);
            });
            mp.setOnErrorListener((p,a,b)->{
                if(soundPlayer==p)soundError(p,next);
                return true;
            });
            mp.prepareAsync();
        }catch(Exception e){soundError(mp,next);}
    }

    private void soundError(MediaPlayer mp,int cue) {
        if(soundPlayer!=mp)return;
        clearSound();
        overlayStatus.setText("오디오 오류 · 다음 승인 코너로 이동");
        playNextFrom(cue+1);
    }

    private void nextCue() {
        int next=currentCue<0?0:currentCue+1;
        clearSound();
        playNextFrom(next);
    }
    private void pauseOrResume(){
        if(soundPlayer==null){
            overlayStatus.setText("현재 재생 중인 오디오가 없습니다.");
            return;
        }
        try{
            if(paused){
                soundPlayer.start();
                paused=false;
                overlayStatus.setText("방송 다시 재생");
            }else if(soundPlayer.isPlaying()){
                soundPlayer.pause();
                paused=true;
                overlayStatus.setText("방송 일시정지");
            }
        }catch(RuntimeException e){
            overlayStatus.setText("재생 실패 · 중지합니다.");
            clearSound();
        }
    }

    private void showVisual(int cue) {
        final int epoch=++visualEpoch;
        releaseVideo();
        videoToLoad=null;
        background.setImageDrawable(null);
        videoView.setVisibility(View.GONE);
        TextView fallback=(TextView)stage.findViewWithTag("fallback_message");
        fallback.setVisibility(View.VISIBLE);
        String key=cue<0?DEFAULT_VISUAL_KEY:"stage_s"+singer+"_c"+cue+"_visual_uri";
        String mimeKey=cue<0?DEFAULT_VISUAL_MIME:"stage_s"+singer+"_c"+cue+"_visual_mime";
        String source=prefs.getString(key,"");
        String mime=prefs.getString(mimeKey,"");
        if(source.isEmpty()&&cue>=0){
            source=prefs.getString(DEFAULT_VISUAL_KEY,"");
            mime=prefs.getString(DEFAULT_VISUAL_MIME,"");
        }
        title.setText(cue<0?"아환 LIVE STUDIO":
                LiveShowActivity.CUE_NAMES[cue]+" · "+LiveShowActivity.SINGERS[singer]);
        if(source.isEmpty()){
            fallback.setText(cue<0?"AHWAN LIVE STUDIO\n기본 방송 화면":
                    "AHWAN LIVE STUDIO\n"+LiveShowActivity.CUE_NAMES[cue]
                    +"\n\n토크 영상·배경 미등록");
            return;
        }
        Uri uri=Uri.parse(source);
        if(mime.startsWith("image/")){
            try{
                background.setImageURI(uri);
                if(background.getDrawable()!=null) fallback.setVisibility(View.GONE);
                else fallback.setText("배경을 열 수 없습니다 · 새 이미지로 교체하세요.");
            }catch(RuntimeException error){
                fallback.setText("이미지 파일 접근 실패 · 재등록 필요");
            }
            return;
        }
        if(mime.startsWith("video/")){
            fallback.setVisibility(View.GONE);
            videoView.setVisibility(View.VISIBLE);
            videoToLoad=source;
            if(videoView.isAvailable()) openVideo(epoch);
        }else{
            fallback.setText("지원되지 않는 화면 자료");
        }
    }

    private void openVideo(int epoch) {
        if(epoch!=visualEpoch||videoToLoad==null||!videoView.isAvailable())return;
        releaseVideo();
        MediaPlayer mp=new MediaPlayer();
        videoPlayer=mp;
        try {
            videoSurface=new Surface(videoView.getSurfaceTexture());
            mp.setSurface(videoSurface);
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            // The talk/song audio plays from the approved cue, not from a looped video.
            mp.setVolume(0f,0f);
            mp.setLooping(true);
            mp.setDataSource(this,Uri.parse(videoToLoad));
            mp.setOnVideoSizeChangedListener((p,w,h)->{
                if(p==videoPlayer)scaleToFill(w,h);
            });
            mp.setOnPreparedListener(p->{
                if(videoPlayer!=p || epoch!=visualEpoch) return;
                try{
                    p.start();
                }catch(RuntimeException e){videoFailure(epoch);}
            });
            mp.setOnErrorListener((p,a,b)->{
                if(videoPlayer==p)videoFailure(epoch);
                return true;
            });
            mp.prepareAsync();
        }catch(Exception e){videoFailure(epoch);}
    }

    private void scaleToFill(int videoW,int videoH){
        if(videoW<=0||videoH<=0)return;
        int w=videoView.getWidth(),h=videoView.getHeight();
        if(w<=0||h<=0)return;
        float scale=Math.max((float)w/videoW,(float)h/videoH);
        float sx=videoW*scale/w,sy=videoH*scale/h;
        Matrix matrix=new Matrix();
        matrix.setScale(sx,sy,w/2f,h/2f);
        videoView.setTransform(matrix);
    }

    private void videoFailure(int epoch){
        if(epoch!=visualEpoch)return;
        releaseVideo();
        videoToLoad=null;
        videoView.setVisibility(View.GONE);
        TextView fallback=(TextView)stage.findViewWithTag("fallback_message");
        fallback.setVisibility(View.VISIBLE);
        fallback.setText("방송 영상 파일을 열 수 없습니다.\n다른 MP4를 선택해 주세요.");
    }
    private void clearSound(){
        MediaPlayer old=soundPlayer;
        soundPlayer=null;
        if(old!=null){
            try{old.reset();}catch(RuntimeException ignored){}
            try{old.release();}catch(RuntimeException ignored){}
        }
    }
    private void releaseVideo(){
        MediaPlayer old=videoPlayer;
        videoPlayer=null;
        if(old!=null){
            try{old.reset();}catch(RuntimeException ignored){}
            try{old.release();}catch(RuntimeException ignored){}
        }
        if(videoSurface!=null){videoSurface.release();videoSurface=null;}
    }

    private void endStage(){
        running=false;
        clearSound();
        ++visualEpoch;
        releaseVideo();
        finish();
    }
    @Override public void onBackPressed(){
        // A Back press during broadcast cannot accidentally stop a show.
        new android.app.AlertDialog.Builder(this)
                .setTitle("아환 방송 화면 종료")
                .setMessage("방송 리허설을 멈추고 관리 화면으로 돌아갈까요? "
                        +"이 앱은 BIGO 화면 공유를 직접 제어하지 않습니다.")
                .setPositiveButton("방송 화면 종료",(d,w)->endStage())
                .setNegativeButton("계속 방송",null).show();
    }
    @Override protected void onDestroy(){
        running=false;
        clearSound();
        releaseVideo();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){
        if(videoToLoad!=null)openVideo(visualEpoch);
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int w,int h){
        if(videoPlayer!=null)scaleToFill(videoPlayer.getVideoWidth(),videoPlayer.getVideoHeight());
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){
        releaseVideo();
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface){}

    /** Verify the emulator has decoded blue test pixels, not only opened a view. */
    public boolean videoFrameRenderedForTest() {
        if(videoView == null || videoView.getVisibility()!=View.VISIBLE
                || !videoView.isAvailable())return false;
        android.graphics.Bitmap frame=videoView.getBitmap(16,16);
        if(frame==null)return false;
        int pixel=frame.getPixel(8,8);
        frame.recycle();
        return Color.blue(pixel) > Color.red(pixel)+35
                && Color.blue(pixel) > Color.green(pixel)+35;
    }
    public int currentCueForTest(){return currentCue;}
    public int completedForTest(){return completed;}
    public boolean screenOnForTest(){
        return (getWindow().getAttributes().flags &
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;
    }
    public int videoVisibilityForTest(){return videoView.getVisibility();}
    public String overlayForTest(){return overlayStatus.getText().toString();}
}
