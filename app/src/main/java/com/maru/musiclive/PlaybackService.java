package com.maru.musiclive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

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
    private static final long HOST_DUCK_MAX_MS = 15_000L;
    private static final long GUIDANCE_DUCK_MAX_MS = 30_000L;
    private final Runnable volumeRecovery = () -> {
        synchronized (PlaybackService.this) {
            // Music has its own timer: destroying MainActivity while BIGO is
            // foreground cannot strand the playback gain at 0% or 18%.
            applyVolume();
        }
    };
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean ownsAudioFocus;
    private boolean pausedForFocus;
    private boolean bigoCoexistenceMode;
    private float focusVolume = 1f;
    private final AudioManager.OnAudioFocusChangeListener focusListener =
            change -> main.post(() -> handleAudioFocusChange(change));

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
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(musicAttributes())
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(focusListener, main)
                    .build();
        }
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
        ducking.restoreAll();
        applyVolume();

        if (queue.isEmpty()) {
            error("재생할 노래가 없습니다.");
            return;
        }

        if (player == null) {
            // 방송 시작 직후에는 TTS 서비스를 새로 띄우지 않고 첫 곡을 바로 재생한다.
            // 제목·감사 안내는 실제 곡과 곡 사이에서만 실행한다.
            play(currentIndex < 0 ? 0 : currentIndex);
            return;
        }

        if (preparing) return;

        try {
            if (!player.isPlaying()) {
                if (!requestFocusForMusic()) {
                    error("다른 앱이 오디오 우선권을 사용하고 있습니다. 재생을 다시 눌러 주세요.");
                    return;
                }
                pausedForFocus = false;
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
            try {
                synchronized (PlaybackService.this) {
                    preparing = false;
                    if (player != mp) return;
                    if (!requestFocusForMusic()) {
                        notifyState(false);
                        error("음악 출력 대기 · 다른 앱에서 오디오를 사용 중입니다.");
                        return;
                    }
                    pausedForFocus = false;
                    applyVolume();
                    mp.start();
                    randomGuard.markStarted(
                            trackKeyAt(currentIndex),
                            System.currentTimeMillis());
                    saveRandomPlaybackState();
                }
                notifyTrack();
                notifyState(true);
                startForeground(
                        NOTIFICATION_ID,
                        notification("게임 오디오 음악만 재생 중"));
                // 중요: 제목/인사 TTS는 곡이 시작된 뒤 절대 재생하지 않는다.
            } catch (RuntimeException error) {
                synchronized (PlaybackService.this) {
                    preparing = false;
                    if (player == mp) player = null;
                    try { mp.release(); } catch (RuntimeException ignored) {}
                }
                notifyState(false);
                error("노래 재생 시작 실패: " + safeMessage(error));
            }
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
        } catch (SecurityException error) {
            preparing = false;
            if (player == next) player = null;
            try { next.release(); } catch (RuntimeException ignored) {}
            error("노래 파일 권한이 만료되었습니다. 설정에서 삭제 후 다시 추가하세요.");
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            preparing = false;
            if (player == next) player = null;
            try { next.release(); } catch (RuntimeException ignored) {}
            error("파일을 열 수 없습니다: " + safeMessage(error));
        } catch (RuntimeException error) {
            preparing = false;
            if (player == next) player = null;
            try { next.release(); } catch (RuntimeException ignored) {}
            error("재생 준비 오류: " + safeMessage(error));
        }
    }

    private String safeMessage(Throwable error) {
        if (error == null) return "알 수 없는 오류";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message.trim();
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
            try {
                manager.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL);
            } catch (RuntimeException ignored) {
                // 제조사별 오디오 정책 오류가 있어도 재생 서비스는 계속 시작한다.
            }
        }
    }

    private AudioAttributes musicAttributes() {
        AudioAttributes.Builder builder = new AudioAttributes.Builder()
                // Music should use the Android media stream, not the game channel.
                // Samsung side volume keys therefore target regular media audio.
                .setUsage(AudioAttributes.USAGE_MEDIA)
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
                pausedForFocus = false;
                player.pause();
                notifyState(false);
            } else {
                if (!requestFocusForMusic()) {
                    error("오디오 우선권을 받을 수 없습니다. 다른 앱의 음성 재생 상태를 확인하세요.");
                    return;
                }
                pausedForFocus = false;
                player.start();
                notifyState(true);
            }
        } catch (IllegalStateException error) {
            play(currentIndex < 0 ? 0 : currentIndex);
        }
    }

    public synchronized void pause() {
        pausedForFocus = false; // Explicit user pause must never auto-resume.
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
        ducking.restoreAll();
        main.removeCallbacks(volumeRecovery);
        pausedForFocus = false;
        bigoCoexistenceMode = false;
        abandonFocus();
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
        ducking.setHostTimed(value, SystemClock.elapsedRealtime(), HOST_DUCK_MAX_MS);
        applyVolume();
    }

    public synchronized void setGuidanceVolume(float value) {
        ducking.setGuidanceTimed(value, SystemClock.elapsedRealtime(), GUIDANCE_DUCK_MAX_MS);
        applyVolume();
    }

    /**
     * BIGO must capture this app's MUSIC output. During BIGO coexistence do
     * not request/steal exclusive audio focus away from the broadcast app.
     * Android may still enforce focus rules; we cannot override system audio.
     */
    public synchronized void setBigoCoexistenceMode(boolean enabled) {
        bigoCoexistenceMode = enabled;
        if (enabled) {
            abandonFocus();
            focusVolume = 1f;
            pausedForFocus = false;
            ducking.restoreAll();
            applyVolume();
        } else if (isPlaying()) {
            requestFocusForMusic();
        }
    }

    private boolean requestFocusForMusic() {
        if (bigoCoexistenceMode || audioManager == null || focusRequest == null) return true;
        if (ownsAudioFocus) return true;
        try {
            ownsAudioFocus = audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } catch (RuntimeException error) {
            ownsAudioFocus = false;
        }
        return ownsAudioFocus;
    }

    private void abandonFocus() {
        if (ownsAudioFocus && audioManager != null && focusRequest != null) {
            try { audioManager.abandonAudioFocusRequest(focusRequest); }
            catch (RuntimeException ignored) {}
        }
        ownsAudioFocus = false;
    }

    private synchronized void handleAudioFocusChange(int change) {
        if (bigoCoexistenceMode) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                ownsAudioFocus = true;
                focusVolume = 1f;
                applyVolume();
                if (pausedForFocus && player != null && !preparing) {
                    pausedForFocus = false;
                    try {
                        player.start();
                        notifyState(true);
                    } catch (IllegalStateException ignored) {
                        notifyState(false);
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                focusVolume = 0.35f;
                applyVolume();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS:
                focusVolume = 1f;
                try {
                    if (player != null && !preparing && player.isPlaying()) {
                        pausedForFocus = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                        player.pause();
                        notifyState(false);
                    }
                } catch (IllegalStateException ignored) {}
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    pausedForFocus = false;
                    ownsAudioFocus = false;
                }
                break;
            default:
                break;
        }
    }

    private void applyVolume() {
        main.removeCallbacks(volumeRecovery);
        long now = SystemClock.elapsedRealtime();
        ducking.restoreExpired(now);
        long nextExpiry = ducking.nextExpiryMs();
        if (nextExpiry > 0L)
            main.postDelayed(volumeRecovery, Math.max(1L, nextExpiry - now));
        if (player == null) return;
        try {
            float value = ducking.effective() * focusVolume;
            player.setVolume(value, value);
        } catch (IllegalStateException ignored) {}
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
            ducking.restoreAll();
            main.removeCallbacks(volumeRecovery);
            pausedForFocus = false;
            bigoCoexistenceMode = false;
            abandonFocus();
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
        main.removeCallbacks(volumeRecovery);
        abandonFocus();
        AutoGreetingService.cancel(this);
        releasePlayer();
        super.onDestroy();
    }
}
