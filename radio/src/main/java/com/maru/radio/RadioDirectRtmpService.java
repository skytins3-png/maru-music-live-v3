package com.maru.radio;

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
import android.graphics.Typeface;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Build;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.video.BitmapSource;
import com.pedro.library.generic.GenericStream;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional DIRECT, consented audio-to-RTMP export.
 *
 * The decoded Suno music is never played through AudioTrack or the speaker:
 * AudioFileSource -> hardware AAC -> RTMP, accompanied by a static H264 card
 * for RTMP servers that require video. Thus STREAM_MUSIC volume = 0 does NOT
 * mute these PCM frames. This is not an injection into the BIGO app's mic.
 * Requires an OWNER-AUTHORIZED BIGO OBS server/key; not the regular BIGO Audio
 * LIVE mixer, and the account may not have RTMP privilege.
 *
 * This MVP broadcasts MUSIC only; existing Ahwan TTS/chat remain LOCAL until
 * a PCM voice mixing bridge is separately implemented and receiver-tested.
 */
public final class RadioDirectRtmpService extends Service implements ConnectChecker {
    public static final String ACTION_START = "com.maru.radio.DIRECT_RTMP_START";
    public static final String ACTION_STOP = "com.maru.radio.DIRECT_RTMP_STOP";
    public static final String EXTRA_SERVER = "rtmp_server_private";
    public static final String EXTRA_KEY = "rtmp_key_private";

    private static final String CHANNEL = "maru_direct_rtmp_status";
    private static final int NOTIFICATION_ID = 202;
    private static volatile String publicStatus = "독립 방송 대기 · 연결 안 됨";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Originals.Track> queue = new ArrayList<>();
    private GenericStream stream;
    private RadioRtmpLevelAudioSource source;
    private String endpoint;
    private int generation;
    private int currentIndex = -1;
    private int sampleRate;
    private boolean stereo;
    private boolean active;

    public static String status() { return publicStatus; }

    private void state(String s) {
        publicStatus = s; // Never include server URL, host, keys, or private details.
        if (active) updateNotification();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "MARU 독립 RTMP 음악 송출", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopAndClose("독립 RTMP 송출 종료됨");
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) return START_NOT_STICKY;
        if (active) {
            state("이미 독립 송출 중 · 먼저 종료해 주세요.");
            return START_NOT_STICKY;
        }
        String server = intent.getStringExtra(EXTRA_SERVER);
        String key = intent.getStringExtra(EXTRA_KEY);
        intent.removeExtra(EXTRA_SERVER);
        intent.removeExtra(EXTRA_KEY);
        if (!valid(server, key)) {
            state("서버 URL·스트림 키가 올바르지 않음 · BIGO 공식 설정 확인");
            stopSelf();
            return START_NOT_STICKY;
        }
        queue.clear();
        queue.addAll(Originals.list(this));
        queue.removeIf(t -> !RadioProgram.isInActiveCategory(this, t));
        if (queue.isEmpty()) {
            state("송출할 Suno 원곡 없음 · MARU에 먼저 곡을 가져와 주세요.");
            stopSelf();
            return START_NOT_STICKY;
        }
        endpoint = server.trim().replaceAll("/+$", "") + "/" + key.trim();
        currentIndex = 0;
        active = true;
        foreground();
        int id = ++generation;
        // Encoder initialization is isolated from Android's volume slider.
        main.post(() -> {
            if (active && generation == id) prepareAndStart(id);
        });
        return START_NOT_STICKY;
    }

    private static boolean valid(String server, String key) {
        if (server == null || key == null) return false;
        String url = server.trim();
        String secret = key.trim();
        if (url.length() > 440 || url.length() < 10 || secret.isEmpty()
                || secret.length() > 360 || url.contains("@")
                || url.matches(".*\\s+.*") || secret.matches(".*[\\r\\n].*"))
            return false;
        Uri uri = Uri.parse(url);
        return ("rtmp".equalsIgnoreCase(uri.getScheme())
                || "rtmps".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && !uri.getHost().isEmpty()
                && !url.contains("?") && !url.contains("#");
    }

    private void foreground() {
        Notification note = notification();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(NOTIFICATION_ID, note,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTIFICATION_ID, note);
    }

    private Notification notification() {
        Intent intent = new Intent(this, RadioDirectRtmpActivity.class);
        PendingIntent show = PendingIntent.getActivity(this, 901, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, RadioDirectRtmpService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 902, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle("MARU · 독립 RTMP 송출 (실험)")
                .setContentText(publicStatus)
                .setContentIntent(show)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "송출 종료", stop).build())
                .setOngoing(active).build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification());
    }

    private static Bitmap card() {
        Bitmap bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(23, 20, 39));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(220, 207, 255));
        paint.setTextSize(56);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        canvas.drawText("MARU RADIO", 115, 170, paint);
        paint.setTextSize(26);
        paint.setColor(Color.rgb(179, 175, 220));
        canvas.drawText("Original music · Ahwan", 160, 220, paint);
        return bitmap;
    }

    private static final class Format {
        final int rate;
        final boolean stereo;
        Format(int rate, boolean stereo) {
            this.rate = rate; this.stereo = stereo;
        }
    }

    private Format format(Originals.Track item) throws Exception {
        Uri sourceUri = item.isDownloadReference()
                ? item.sourceUri() : Uri.fromFile(item.file());
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(this, sourceUri, null);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")
                        && fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                        && fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    int hz = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (hz >= 16000 && hz <= 96000 && (channels == 1 || channels == 2))
                        return new Format(hz, channels == 2);
                }
            }
            throw new IllegalArgumentException("Audio format unsupported");
        } finally {
            extractor.release();
        }
    }

    private Uri uri(Originals.Track item) {
        return item.isDownloadReference() ? item.sourceUri() : Uri.fromFile(item.file());
    }

    private void prepareAndStart(int ticket) {
        if (!active || ticket != generation || queue.isEmpty()) return;
        try {
            Originals.Track first = queue.get(currentIndex);
            Format f = format(first);
            sampleRate = f.rate;
            stereo = f.stereo;
            source = new RadioRtmpLevelAudioSource(this, uri(first), isLoop -> {
                main.post(() -> nextSong(ticket));
                return Unit.INSTANCE;
            });
            // Silent local output: the decoder is routed to AAC, not AudioTrack.
            // Every source PCM block gets gain-riding and a -0.75 dBFS peak limiter.
            stream = new GenericStream(this, this, new BitmapSource(card()), source);
            if (!stream.prepareVideo(640, 360, 320000, 10, 2, 0,
                    -1, -1, 0, 0, 320000)
                    || !stream.prepareAudio(sampleRate, stereo, 128000, false, false)) {
                stopAndClose("기기 인코더가 음성/정지화면을 지원하지 않아요.");
                return;
            }
            state("RTMP 서버 연결 시도 · 스피커 음소거 독립 오디오 보정 · 아환 실시간 음성 미연결");
            stream.startStream(endpoint);
        } catch (Exception ex) {
            stopAndClose("원곡 디코더 또는 송출 시작 실패 · 다른 파일로 시험해 주세요.");
        }
    }

    private void nextSong(int ticket) {
        if (!active || ticket != generation || queue.isEmpty()) return;
        int candidate = currentIndex;
        for (int i = 0; i < queue.size(); i++) {
            candidate = (candidate + 1) % queue.size();
            Originals.Track song = queue.get(candidate);
            try {
                Format fmt = format(song);
                if (fmt.rate != sampleRate || fmt.stereo != stereo) continue;
                if (source == null) break;
                source.replaceFile(this, uri(song));
                currentIndex = candidate;
                state("RTMP 음악 송출 중 · " + (currentIndex + 1) + "/" + queue.size()
                        + "곡 (청취자 기기에서 소리 확인 필요)");
                return;
            } catch (Exception ignored) {
                // Skip incompatible/broken song; keep stream connection alive.
            }
        }
        // Clearly report instead of silently turning off audio mid-LIVE.
        stopAndClose("재생할 호환 음악이 없어요 (샘플레이트·채널 확인 필요).");
    }

    private void stopAndClose(String why) {
        active = false;
        ++generation;
        try { if (stream != null) stream.release(); }
        catch (RuntimeException ignored) {}
        stream = null;
        source = null;
        endpoint = null;
        queue.clear();
        currentIndex = -1;
        state(why);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override public void onConnectionStarted(String url) {
        main.post(() -> { if (active) state("RTMP 연결 대기 · URL/키는 표시하지 않음"); });
    }

    @Override public void onConnectionSuccess() {
        main.post(() -> { if (active) state("RTMP 서버에 연결됨 · 실제 BIGO 시청 소리 확인 필요"); });
    }

    @Override public void onConnectionFailed(String reason) {
        main.post(() -> { if (active) stopAndClose("RTMP 연결 실패 · BIGO 스트림 권한/설정 확인"); });
    }

    @Override public void onDisconnect() {
        main.post(() -> { if (active) stopAndClose("RTMP 연결 끊김 · 종료 처리"); });
    }

    @Override public void onAuthError() {
        main.post(() -> { if (active) stopAndClose("RTMP 방송 키 인증 실패"); });
    }

    @Override public void onAuthSuccess() {
        main.post(() -> { if (active) state("RTMP 서버 인증 성공 · 음악 송출/청취자 확인 필요"); });
    }

    @Override public void onNewBitrate(long bitrate) {
        // Deliberately do not publish user endpoint, song title or credentials.
    }

    @Override public void onDestroy() {
        if (active) stopAndClose("Android 서비스 종료 · RTMP 방송 종료");
        super.onDestroy();
    }
}
