package com.maru.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User-started, foreground music playback of already imported Suno files.
 * Android media output only: not an injection into BIGO microphone / stream.
 */
public final class RadioPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.maru.radio.PLAY_TRACK";
    public static final String ACTION_TOGGLE = "com.maru.radio.TOGGLE";
    public static final String ACTION_PREVIOUS = "com.maru.radio.PREVIOUS";
    public static final String ACTION_NEXT = "com.maru.radio.NEXT";
    public static final String ACTION_STOP = "com.maru.radio.STOP";
    public static final String EXTRA_HASH = "hash";

    private static final String CHANNEL = "maru_radio_playback";
    private static final int NOTIFICATION_ID = 101;
    private final IBinder binder = new LocalBinder();
    private final List<Originals.Track> queue = new ArrayList<>();

    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private int currentIndex = -1;
    private boolean ready;
    private boolean loading;
    private boolean foreground;
    private String error = "";
    private int generation;

    public final class LocalBinder extends Binder {
        public RadioPlaybackService getService() { return RadioPlaybackService.this; }
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "MARU RADIO 원곡 재생", NotificationManager.IMPORTANCE_LOW));
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        pauseOnly();
                    }
                }).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            ++generation;
            releasePlayer();
            queue.clear();
            currentIndex = -1;
            loading = false;
            error = "";
            if (audio != null && focusRequest != null)
                audio.abandonAudioFocusRequest(focusRequest);
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            foreground = false;
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action)) {
            refreshQueue(null);
            String hash = intent.getStringExtra(EXTRA_HASH);
            if (hash != null) for (int i = 0; i < queue.size(); i++) {
                if (hash.equals(queue.get(i).hash)) { currentIndex = i; break; }
            }
            if (currentIndex < 0 && !queue.isEmpty()) currentIndex = 0;
            if (currentIndex >= 0) playIndex(currentIndex);
            else stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_NEXT.equals(action) || ACTION_PREVIOUS.equals(action)) {
            String previousHash = currentIndex >= 0 && currentIndex < queue.size()
                    ? queue.get(currentIndex).hash : null;
            refreshQueue(previousHash);
            if (queue.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
            int step = ACTION_NEXT.equals(action) ? 1 : -1;
            if (currentIndex < 0) currentIndex = 0;
            else currentIndex = (currentIndex + step + queue.size()) % queue.size();
            playIndex(currentIndex);
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            if (loading) return START_NOT_STICKY;
            if (player != null && ready && player.isPlaying()) {
                pauseOnly();
            } else if (player != null && ready) {
                if (requestFocus()) {
                    ensureForeground();
                    player.start();
                    updateNotification();
                }
            } else {
                refreshQueue(null);
                if (!queue.isEmpty()) {
                    if (currentIndex < 0) currentIndex = 0;
                    playIndex(currentIndex);
                } else stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void refreshQueue(String restoreHash) {
        queue.clear();
        queue.addAll(Originals.list(this));
        if (restoreHash == null) {
            if (currentIndex >= queue.size()) currentIndex = -1;
            return;
        }
        currentIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (restoreHash.equals(queue.get(i).hash)) { currentIndex = i; break; }
        }
    }

    private boolean requestFocus() {
        if (audio == null || focusRequest == null) return false;
        int granted = audio.requestAudioFocus(focusRequest);
        if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            error = "다른 앱이 오디오 사용 중이라 재생을 시작하지 못했습니다.";
            return false;
        }
        error = "";
        return true;
    }

    private void playIndex(int index) {
        if (index < 0 || index >= queue.size()) return;
        ++generation;
        final int ticket = generation;
        releasePlayer();
        currentIndex = index;
        error = "";
        ready = false;
        loading = true;
        ensureForeground(); // foreground within 5 seconds of startForegroundService
        if (!requestFocus()) {
            loading = false;
            updateNotification();
            return;
        }
        Originals.Track t = queue.get(index);
        try {
            MediaPlayer m = new MediaPlayer();
            m.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            if (t.isDownloadReference()) m.setDataSource(this, t.sourceUri());
            else m.setDataSource(t.path);
            m.setOnPreparedListener(p -> {
                if (ticket != generation || player != p) return;
                loading = false;
                ready = true;
                p.setVolume(1f, 1f); // original gain; system volume remains OS-controlled
                p.start();
                updateNotification();
            });
            m.setOnCompletionListener(p -> {
                if (ticket != generation || player != p) return;
                advanceOnCompletion();
            });
            m.setOnErrorListener((p, what, extra) -> {
                if (ticket == generation) {
                    ready = false;
                    loading = false;
                    error = "이 곡을 재생할 수 없어요. 다른 곡을 선택해 주세요.";
                    updateNotification();
                }
                return true;
            });
            player = m;
            m.prepareAsync();
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            loading = false;
            ready = false;
            error = "파일을 열 수 없어요. Download의 원곡이 있는지 확인해 주세요.";
            releasePlayer();
            updateNotification();
        }
    }

    private void advanceOnCompletion() {
        String hash = currentIndex >= 0 && currentIndex < queue.size()
                ? queue.get(currentIndex).hash : null;
        refreshQueue(hash);
        if (queue.isEmpty()) { stopSelf(); return; }
        currentIndex = (currentIndex + 1 + queue.size()) % queue.size();
        playIndex(currentIndex);
    }

    private void pauseOnly() {
        if (player != null && ready && player.isPlaying()) {
            try { player.pause(); } catch (IllegalStateException ignored) {}
        }
        updateNotification();
    }

    private void releasePlayer() {
        if (player != null) {
            player.reset();
            player.release();
            player = null;
        }
        ready = false;
    }

    public String currentHash() {
        return currentIndex >= 0 && currentIndex < queue.size()
                ? queue.get(currentIndex).hash : "";
    }

    public String currentTitle() {
        if (!error.isEmpty()) return error;
        return currentIndex >= 0 && currentIndex < queue.size()
                ? queue.get(currentIndex).title : "재생할 원곡을 선택하세요";
    }

    public boolean isPlaying() {
        try { return player != null && ready && player.isPlaying(); }
        catch (IllegalStateException e) { return false; }
    }

    public boolean isLoading() { return loading; }
    public int positionMs() {
        try { return player != null && ready ? player.getCurrentPosition() : 0; }
        catch (IllegalStateException e) { return 0; }
    }
    public int durationMs() {
        try { return player != null && ready ? player.getDuration() : 0; }
        catch (IllegalStateException e) { return 0; }
    }
    public boolean seekTo(int ms) {
        if (player == null || !ready) return false;
        try { player.seekTo(Math.min(Math.max(0, ms), durationMs())); return true; }
        catch (IllegalStateException e) { return false; }
    }

    private PendingIntent control(String action, int number) {
        Intent intent = new Intent(this, RadioPlaybackService.class).setAction(action);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getService(this, number, intent, flags);
    }

    private Notification makeNotification() {
        Intent launch = new Intent(this, RadioActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 400, launch,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle("MARU RADIO · 원곡 재생")
                .setContentText(currentTitle())
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying() || loading)
                .addAction(android.R.drawable.ic_media_previous, "이전", control(ACTION_PREVIOUS, 1))
                .addAction(isPlaying() ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play,
                        isPlaying() ? "일시정지" : "재생", control(ACTION_TOGGLE, 2))
                .addAction(android.R.drawable.ic_media_next, "다음", control(ACTION_NEXT, 3))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "종료", control(ACTION_STOP, 4))
                .build();
    }

    private void ensureForeground() {
        Notification n = makeNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else startForeground(NOTIFICATION_ID, n);
        foreground = true;
    }

    private void updateNotification() {
        // Updating an existing foreground-service notification does not require
        // requesting broad POST_NOTIFICATIONS permission on Android 13+.
        if (foreground) ensureForeground();
    }

    @Override public void onDestroy() {
        ++generation;
        releasePlayer();
        if (audio != null && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        foreground = false;
        super.onDestroy();
    }
}
