package com.maru.musiclive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PlaybackService extends Service {
    public interface Listener {
        void onTrackChanged(int index);
        void onStateChanged(boolean playing);
        void onError(String message);
    }

    public final class LocalBinder extends Binder {
        public PlaybackService service() {
            return PlaybackService.this;
        }
    }

    public static final String ACTION_GREETING_DUCK =
            "com.maru.musiclive.PLAYBACK_GREETING_DUCK";
    public static final String ACTION_GREETING_RESTORE =
            "com.maru.musiclive.PLAYBACK_GREETING_RESTORE";
    public static final String ACTION_PLAY_AFTER_ANNOUNCEMENT =
            "com.maru.musiclive.PLAY_AFTER_ANNOUNCEMENT";
    public static final String ACTION_TOGGLE =
            "com.maru.musiclive.PLAYBACK_TOGGLE";
    public static final String ACTION_NEXT =
            "com.maru.musiclive.PLAYBACK_NEXT";
    public static final String ACTION_STOP_ALL =
            "com.maru.musiclive.PLAYBACK_STOP_ALL";
    public static final String EXTRA_TRACK_INDEX = "track_index";

    private static final String CHANNEL_ID = "maru_playback";
    private static final int NOTIFICATION_ID = 273;

    private final IBinder binder = new LocalBinder();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> queue = new ArrayList<>();
    private final List<String> trackKeys = new ArrayList<>();
    private final Random random = new Random();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RandomPlaybackGuard randomGuard = new RandomPlaybackGuard();
    private final VolumeDucking ducking = new VolumeDucking();

    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean repeatAll = true;
    private boolean randomMode;
    private boolean preparing;
    private boolean intermissionPending;
    private boolean randomRetryScheduled;

    private final Runnable randomRetry = () -> {
        synchronized (PlaybackService.this) {
            randomRetryScheduled = false;
            if (!randomMode || player != null || preparing || intermissionPending || queue.isEmpty()) {
                return;
            }
            transitionToNext();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        allowPlaybackCapture();
        randomGuard.restore(
                AppStorage.loadRandomHistory(this),
                AppStorage.loadRandomCycle(this));
        createChannel();
    }

    @Override public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification("대기 중"));

        String action = intent == null ? null : intent.getAction();
        if (ACTION_PLAY_AFTER_ANNOUNCEMENT.equals(action)) {
            int index = intent.getIntExtra(EXTRA_TRACK_INDEX, -1);
            synchronized (this) {
                intermissionPending = false;
                if (index >= 0) playInternal(index);
            }
        } else if (ACTION_TOGGLE.equals(action)) {
            toggle();
            startForeground(NOTIFICATION_ID, notification(isPlaying() ? "게임 오디오 음악만 재생 중" : "일시정지"));
        } else if (ACTION_NEXT.equals(action)) {
            next();
        } else if (ACTION_STOP_ALL.equals(action)) {
            stopAllServices();
            return START_NOT_STICKY;
        } else if (ACTION_GREETING_DUCK.equals(action)) {
            // 이전 버전 호환용. V3.1.1 이후 자동 안내는 곡 사이에서만 나와 이 경로를 사용하지 않는다.
            setGuidanceVolume(0.18f);
        } else if (ACTION_GREETING_RESTORE.equals(action)) {
            setGuidanceVolume(1f);
        }
        return START_STICKY;
    }

    public void addListener(Listener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized void setQueue(List<String> uris) {
        cancelRandomRetry();
        queue.clear();
        trackKeys.clear();
        if (uris != null) {
            for (String uri : uris) {
                if (uri == null || uri.trim().isEmpty()) continue;
                queue.add(uri);
                trackKeys.add(stableTrackKey(uri));
            }
        }
        randomGuard.pruneToQueue(trackKeys, System.currentTimeMillis());
        saveRandomPlaybackState();
        if (currentIndex >= queue.size()) {
            currentIndex = -1;
            stopPlayback();
        }
    }

    public synchronized void setRepeatAll(boolean value) {
        repeatAll = value;
    }

    public synchronized void setRandomMode(boolean value) {
        randomMode = value;
        if (!value) cancelRandomRetry();
    }

    public synchronized void prepareForBroadcast() {
        if (intermissionPending) return;
        ducking.setGuidance(1f);
        ducking.setHost(1f);
        applyVolume();

        if (queue.isEmpty()) {
            error("재생할 노래가 없습니다.");
            return;
        }

        if (player == null) {
            if (currentIndex < 0 && AppStorage.songTitleTts(this)) {
                startIntermissionBefore(0);
            } else {
                play(currentIndex < 0 ? 0 : currentIndex);
            }
            return;
        }

        if (preparing) return;

        try {
            if (!player.isPlaying()) {
                player.start();
                notifyState(true);
                startForeground(NOTIFICATION_ID, notification("게임 오디오 음악만 재생 중"));
            }
        } catch (IllegalStateException error) {
            play(currentIndex < 0 ? 0 : currentIndex);
        }
    }

    /** 사용자가 직접 곡을 선택하면 대기 중 안내를 취소하고 해당 곡을 즉시 재생한다. */
    public synchronized void play(int index) {
        cancelRandomRetry();
        AutoGreetingService.cancel(this);
        intermissionPending = false;
        playInternal(index);
    }

    private void playInternal(int index) {
        if (queue.isEmpty()) {
            error("재생할 노래가 없습니다.");
            return;
        }
        if (index < 0 || index >= queue.size()) index = 0;

        releasePlayer();
        currentIndex = index;
        preparing = true;
        intermissionPending = false;

        MediaPlayer next = new MediaPlayer();
        next.setAudioAttributes(musicAttributes());
        next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        next.setOnPreparedListener(mp -> {
            synchronized (PlaybackService.this) {
                preparing = false;
                applyVolume();
                mp.start();
                randomGuard.markStarted(
                        trackKeyAt(currentIndex),
                        System.currentTimeMillis());
                saveRandomPlaybackState();
            }
            notifyTrack();
            notifyState(true);
            startForeground(NOTIFICATION_ID, notification("게임 오디오 음악만 재생 중"));
            // 중요: 제목/인사 TTS는 곡이 시작된 뒤 절대 재생하지 않는다.
        });
        next.setOnCompletionListener(mp -> {
            synchronized (PlaybackService.this) {
                transitionToNext();
            }
        });
        next.setOnErrorListener((mp, what, extra) -> {
            synchronized (PlaybackService.this) {
                preparing = false;
                intermissionPending = false;
            }
            error("재생 오류: " + what + "/" + extra);
            return true;
        });

        try {
            player = next;
            next.setDataSource(this, Uri.parse(queue.get(currentIndex)));
            next.prepareAsync();
        } catch (IOException | IllegalArgumentException error) {
            preparing = false;
            player = null;
            next.release();
            error("파일을 열 수 없습니다: " + error.getMessage());
        }
    }

    private void transitionToNext() {
        if (queue.isEmpty() || intermissionPending) return;
        int nextIndex = calculateNextIndex();
        if (nextIndex < 0) {
            releasePlayer();
            notifyState(false);
            if (randomMode && randomGuard.lastChoiceBlockedByCooldown()) {
                scheduleRandomRetry(randomGuard.lastRequiredWaitMs());
                return;
            }
            startForeground(NOTIFICATION_ID, notification("재생 완료"));
            return;
        }

        if (!AppStorage.songTitleTts(this)) {
            playInternal(nextIndex);
            return;
        }

        startIntermissionBefore(nextIndex);
    }

    private void startIntermissionBefore(int nextIndex) {
        if (nextIndex < 0 || nextIndex >= queue.size() || intermissionPending) return;
        String title = SongTitleResolver.resolve(this, queue.get(nextIndex));
        IntermissionStore.Snapshot snapshot = IntermissionStore.takeSnapshot(this);
        // 첫 곡 전 또는 현재 곡 종료 후: 음악이 없는 상태에서만 안내한다.
        releasePlayer();
        preparing = false;
        intermissionPending = true;
        notifyState(false);
        startForeground(NOTIFICATION_ID, notification("곡 사이 통합 안내 중"));

        boolean started = AutoGreetingService.announceIntermission(
                this,
                title,
                snapshot.giftNames,
                snapshot.followNames,
                nextIndex);
        if (!started) {
            intermissionPending = false;
            playInternal(nextIndex);
        }
    }

    private int calculateNextIndex() {
        if (queue.isEmpty()) return -1;
        if (randomMode) {
            int nextIndex = randomGuard.chooseNext(
                    trackKeys,
                    currentIndex,
                    random,
                    System.currentTimeMillis());
            if (nextIndex >= 0) return nextIndex;
            if (randomGuard.lastChoiceBlockedByCooldown()) return -1;
        }
        int nextIndex = currentIndex + 1;
        if (nextIndex >= queue.size()) {
            if (!repeatAll) return -1;
            nextIndex = 0;
        }
        return Math.max(0, nextIndex);
    }

    private String stableTrackKey(String uri) {
        String title;
        try {
            title = SongTitleFormatter.clean(SongTitleResolver.resolve(this, uri));
        } catch (RuntimeException ignored) {
            title = "";
        }
        String normalizedTitle = title == null ? "" : title
                .replaceAll("(?i)\\s*\\((?:copy|복사본|\\d+)\\)$", "")
                .replaceAll("(?i)\\s*[-_]\\s*(?:copy|복사본)$", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!normalizedTitle.isEmpty()
                && !"음악".equals(normalizedTitle)
                && !"music".equals(normalizedTitle)) {
            return "title:" + normalizedTitle;
        }
        return "uri:" + uri.trim();
    }

    private String trackKeyAt(int index) {
        if (index >= 0 && index < trackKeys.size()) return trackKeys.get(index);
        if (index >= 0 && index < queue.size()) return stableTrackKey(queue.get(index));
        return "";
    }

    private void scheduleRandomRetry(long waitMs) {
        cancelRandomRetry();
        long safeWait = Math.max(1_000L, Math.min(RandomPlaybackGuard.DEFAULT_COOLDOWN_MS, waitMs));
        long seconds = (safeWait + 999L) / 1_000L;
        AutoGreetingStore.setStatus(
                this,
                "같은 곡 20분 중복 방지 중 · " + seconds + "초 후 자동 재개");
        startForeground(
                NOTIFICATION_ID,
                notification("랜덤 중복 방지 대기 · " + seconds + "초"));
        randomRetryScheduled = true;
        main.postDelayed(randomRetry, safeWait);
    }

    private void cancelRandomRetry() {
        if (!randomRetryScheduled) return;
        main.removeCallbacks(randomRetry);
        randomRetryScheduled = false;
    }

    private void saveRandomPlaybackState() {
        AppStorage.saveRandomPlaybackState(
                this,
                randomGuard.historySnapshot(),
                randomGuard.cycleSnapshot());
    }

    private void allowPlaybackCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        AudioManager manager = getSystemService(AudioManager.class);
        if (manager != null) {
            manager.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
        }
    }

    private AudioAttributes musicAttributes() {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
        }
        return builder.build();
    }

    public synchronized void toggle() {
        if (intermissionPending) return;
        if (player == null) {
            play(currentIndex < 0 ? 0 : currentIndex);
            return;
        }
        if (preparing) return;

        try {
            if (player.isPlaying()) {
                player.pause();
                notifyState(false);
            } else {
                player.start();
                notifyState(true);
            }
        } catch (IllegalStateException error) {
            play(currentIndex < 0 ? 0 : currentIndex);
        }
    }

    public synchronized void pause() {
        if (player == null || preparing) return;
        try {
            if (player.isPlaying()) {
                player.pause();
                notifyState(false);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void stopPlayback() {
        cancelRandomRetry();
        AutoGreetingService.cancel(this);
        intermissionPending = false;
        releasePlayer();
        currentIndex = -1;
        notifyState(false);
        startForeground(NOTIFICATION_ID, notification("대기 중"));
    }

    public synchronized void next() {
        transitionToNext();
    }

    public synchronized void previous() {
        cancelRandomRetry();
        if (queue.isEmpty()) return;
        int index = currentIndex <= 0 ? queue.size() - 1 : currentIndex - 1;
        play(index);
    }

    public synchronized int position() {
        try {
            return player == null ? 0 : player.getCurrentPosition();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    public synchronized int duration() {
        try {
            return player == null ? 0 : player.getDuration();
        } catch (IllegalStateException ignored) {
            return 0;
        }
    }

    public synchronized boolean isPlaying() {
        try {
            return player != null && !preparing && player.isPlaying();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    public synchronized boolean isPreparing() {
        return preparing;
    }

    public synchronized int currentIndex() {
        return currentIndex;
    }

    public synchronized void seekTo(int positionMs) {
        if (player == null || preparing) return;
        try {
            player.seekTo(Math.max(0, positionMs));
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void setHostVolume(float value) {
        ducking.setHost(value);
        applyVolume();
    }

    public synchronized void setGuidanceVolume(float value) {
        ducking.setGuidance(value);
        applyVolume();
    }

    private void applyVolume() {
        if (player == null) return;
        try {
            float value = ducking.effective();
            player.setVolume(value, value);
        } catch (IllegalStateException ignored) {
        }
    }

    private void notifyTrack() {
        for (Listener listener : listeners) listener.onTrackChanged(currentIndex);
    }

    private void notifyState(boolean playing) {
        for (Listener listener : listeners) listener.onStateChanged(playing);
    }

    private void error(String message) {
        for (Listener listener : listeners) listener.onError(message);
    }

    private void releasePlayer() {
        preparing = false;
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
    }


    private void stopAllServices() {
        synchronized (this) {
            cancelRandomRetry();
            intermissionPending = false;
            releasePlayer();
            currentIndex = -1;
            notifyState(false);
        }
        ScreenOcrGreetingService.stop(this);
        AutoGreetingService.cancel(this);
        AutoGreetingStore.setRunningMode(this, "");
        AutoGreetingStore.setStatus(this, "완전 종료됨");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.channel_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(
                this,
                100,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle = serviceAction(ACTION_TOGGLE, 101);
        PendingIntent next = serviceAction(ACTION_NEXT, 102);
        PendingIntent stop = serviceAction(ACTION_STOP_ALL, 103);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .addAction(0, isPlaying() ? "일시정지" : "재생", toggle)
                .addAction(0, "다음 곡", next)
                .addAction(0, "완전 종료", stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private PendingIntent serviceAction(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override public void onDestroy() {
        AutoGreetingService.cancel(this);
        releasePlayer();
        super.onDestroy();
    }
}
