package com.maru.musiclive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.audio.AudioFileSource;
import com.pedro.encoder.input.sources.video.BitmapSource;
import com.pedro.library.rtmp.RtmpStream;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * OBS-free native encoder: fixed cover image + actual Android audio source.
 * Only an account-authorized BIGO RTMP(S) endpoint is accepted. This does NOT
 * programmatically enter BIGO's "Audio LIVE" UI and a connected RTMP socket
 * is NOT proof that a separate BIGO listener received audio.
 */
public final class BigoDirectRtmpService extends Service implements ConnectChecker {
    public static final String ACTION_START="com.maru.musiclive.rtmp.START";
    public static final String ACTION_STOP="com.maru.musiclive.rtmp.STOP";
    public static final String EXTRA_SONGS="songs";
    public static final String PREFS="maru_rtmp_connection_status";
    public static final String STATUS="status";
    private static final String CHANNEL="maru_rtmbroadcast_v1";
    private static final int NOTICE_ID=47921;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Uri> songUris=new ArrayList<>();
    private RtmpStream stream;
    private volatile boolean stopped=true;
    private int trackIndex=0, sampleRate=44100;
    private boolean stereo=true;

    @Nullable @Override public IBinder onBind(Intent intent) {return null;}

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "MARU 휴대폰 직접 송출",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent command,int flags,int startId) {
        if(command!=null && ACTION_STOP.equals(command.getAction())){
            stopSelf();return START_NOT_STICKY;
        }
        if(command==null || !ACTION_START.equals(command.getAction()))
            return START_NOT_STICKY;
        ArrayList<String> supplied=command.getStringArrayListExtra(EXTRA_SONGS);
        if(supplied==null || supplied.isEmpty()){
            record("시작 실패 · 선택된 음악 없음");stopSelf();return START_NOT_STICKY;
        }
        if(!stopped){
            record("이미 직접송출 시도 중 · 중지 후 다시 시작");return START_NOT_STICKY;
        }
        stopped=false;trackIndex=0;songUris.clear();
        for(String raw:supplied){
            if(songUris.size()>=200)break;
            try{
                Uri parsed=Uri.parse(raw);
                if("content".equals(parsed.getScheme()))songUris.add(parsed);
            }catch(RuntimeException ignored){}
        }
        if(songUris.isEmpty()){
            record("시작 실패 · 공유 권한이 있는 휴대폰 음원 없음");
            stopped=true;stopSelf();return START_NOT_STICKY;
        }
        startForeground(NOTICE_ID,notification("외부 송출 준비 중"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        record("휴대폰 직접송출 준비 중 · BIGO 실제 수신 미확인");
        worker.execute(this::startEncoder);
        return START_NOT_STICKY;
    }

    private Notification notification(String text) {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,CHANNEL)
                .setContentTitle("MARU 직접송출 · OBS 미사용")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setContentIntent(pending).build();
    }

    private void record(String message) {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString(STATUS,message).putLong("updated",System.currentTimeMillis()).apply();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null && !stopped)nm.notify(NOTICE_ID,notification(message));
    }

    private static final class AudioInfo {
        final int hz;
        final boolean stereo;
        AudioInfo(int hz,boolean stereo){this.hz=hz;this.stereo=stereo;}
    }

    private AudioInfo inspect(Uri uri) throws Exception {
        MediaExtractor probe=new MediaExtractor();
        try {
            probe.setDataSource(this,uri,null);
            for(int i=0;i<probe.getTrackCount();i++){
                MediaFormat fmt=probe.getTrackFormat(i);
                String mime=fmt.getString(MediaFormat.KEY_MIME);
                if(mime!=null && mime.startsWith("audio/")){
                    int rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(rate>=16000 && rate<=48000 && (channels==1 || channels==2))
                        return new AudioInfo(rate,channels==2);
                }
            }
            throw new IllegalArgumentException("Unsupported audio format");
        }finally{probe.release();}
    }

    private Bitmap makeCover() {
        Bitmap img=Bitmap.createBitmap(360,640,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(img);
        c.drawColor(Color.rgb(20,28,56));
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.rgb(205,169,255));
        p.setTextSize(39);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText("MARU RADIO",180,252,p);
        p.setTextSize(26);
        p.setColor(Color.WHITE);
        c.drawText("Original Music",180,323,p);
        p.setTextSize(23);
        c.drawText("雲上居者",180,390,p);
        p.setTextSize(18);
        p.setColor(Color.LTGRAY);
        c.drawText("Audio + Static Cover",180,475,p);
        return img;
    }

    private AudioFileSource audioSource(Uri uri) {
        return new AudioFileSource(this,uri,false,new Function1<Boolean, Unit>(){
            @Override public Unit invoke(Boolean didLoop) {
                if(!stopped)main.post(()->advanceTrack());
                return Unit.INSTANCE;
            }
        });
    }

    private void startEncoder(){
        try {
            String server=BigoRtmpCredentialVault.server(this);
            String key=BigoRtmpCredentialVault.loadKey(this);
            // Never persist/log/send credentials to any third party.
            String endpoint=BigoRtmpEndpointPolicy.join(server,key);
            AudioInfo info=inspect(songUris.get(0));
            sampleRate=info.hz;stereo=info.stereo;
            RtmpStream candidate=new RtmpStream(this,this,
                    new BitmapSource(makeCover()),audioSource(songUris.get(0)));
            if(!candidate.prepareVideo(360,640,480_000,15,2,0))
                throw new IllegalStateException("Video encoder init failed");
            if(!candidate.prepareAudio(sampleRate,stereo,96000,false,false))
                throw new IllegalStateException("Audio encoder init failed");
            if(stopped) {candidate.release();return;}
            stream=candidate;
            candidate.startStream(endpoint);
            record("RTMP 서버 연결 중 · BIGO 청취 확인 전");
        }catch(Exception ex){
            record("직접송출 준비 실패 · 서버/키/음원 형식 확인");
            stopped=true;stopSelf();
        }
    }

    private void advanceTrack() {
        if(stopped || stream==null || songUris.size()<2)return;
        worker.execute(()->{
            if(stopped || stream==null)return;
            for(int i=0;i<songUris.size();i++){
                trackIndex=(trackIndex+1)%songUris.size();
                Uri next=songUris.get(trackIndex);
                try{
                    AudioInfo info=inspect(next);
                    if(info.hz!=sampleRate || info.stereo!=stereo)continue;
                    stream.changeAudioSource(audioSource(next));
                    record("재생목록 다음 곡 · RTMP 서버 전송 중 · 시청자 수신 미검증");
                    return;
                }catch(Exception ignored){}
            }
            record("다음 곡 전환 불가 · 샘플레이트 동일 음원이 필요");
        });
    }

    @Override public void onConnectionStarted(String url) {
        // Do not log the URL: it can contain the private stream key.
        record("RTMP 연결 시작 · 서버 수락 확인 전");
    }
    @Override public void onConnectionSuccess() {
        record("RTMP 서버 연결됨 · BIGO 청취자 수신은 아직 미검증");
    }
    @Override public void onConnectionFailed(String reason) {
        record("RTMP 연결 실패 · 계정 외부송출 권한/서버 상태 확인");
        stopped=true;stopSelf();
    }
    @Override public void onDisconnect() {
        record("RTMP 송출 연결 종료");
        stopped=true;stopSelf();
    }
    @Override public void onAuthError() {
        record("RTMP 인증 실패 · 공식 BIGO 방송키 다시 확인");
        stopped=true;stopSelf();
    }
    @Override public void onAuthSuccess() {
        record("RTMP 인증 진행 · 외부 청취 확인 전");
    }

    @Override public void onDestroy() {
        stopped=true;
        RtmpStream current=stream;stream=null;
        if(current!=null){
            try{if(current.isStreaming())current.stopStream();}
            catch(Exception ignored){}
            try{current.release();}catch(Exception ignored){}
        }
        worker.shutdownNow();
        record("직접송출 중지됨 · 기존 음악 파일 보존");
        super.onDestroy();
    }
}
