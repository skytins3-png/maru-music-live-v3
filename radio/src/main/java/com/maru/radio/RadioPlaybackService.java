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
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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
    public static final String ACTION_DJ_SAY = "com.maru.radio.DJ_SAY";
    public static final String ACTION_DJ_GREETING = "com.maru.radio.DJ_GREETING";
    public static final String ACTION_DJ_ANNOUNCE_NEXT = "com.maru.radio.DJ_ANNOUNCE_NEXT";
    public static final String ACTION_DJ_REQUEST = "com.maru.radio.DJ_REQUEST";
    public static final String EXTRA_DJ_TEXT = "dj_speech";
    public static final String ACTION_APPLY_AUDIO_COEXIST = "com.maru.radio.APPLY_AUDIO_COEXIST";
    public static final String ACTION_SET_TIMER = "com.maru.radio.SET_TIMER";
    public static final String ACTION_CANCEL_TIMER = "com.maru.radio.CANCEL_TIMER";
    public static final String ACTION_CANCEL_WALL_END = "com.maru.radio.CANCEL_WALL_END";
    public static final String ACTION_SET_WALL_END = "com.maru.radio.SET_WALL_END";
    public static final String EXTRA_END_AT = "end_epoch_millis";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String EXTRA_HASH = "hash";

    private static final String CHANNEL = "maru_radio_playback";
    private static final int NOTIFICATION_ID = 101;
    private final IBinder binder = new LocalBinder();
    private final List<Originals.Track> queue = new ArrayList<>();

    private MediaPlayer player;
    private RadioDjVoice djVoice;
    private String requestedDjSpeech = "";
    private int announcementCount;
    private boolean openingSpoken;
    private boolean djHoldingTrack;
    private AudioManager audio;
    private AudioFocusRequest focusRequest;
    private int currentIndex = -1;
    private boolean ready;
    private boolean loading;
    private boolean foreground;
    private String error = "";
    private int generation;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    // Independent timers: a chosen duration must never overwrite the chosen clock time.
    private long minutesDeadline;
    private long wallEndAt;

    private boolean deadlineReached() {
        return (minutesDeadline > 0L && SystemClock.elapsedRealtime() >= minutesDeadline)
                || (wallEndAt > 0L && System.currentTimeMillis() >= wallEndAt);
    }

    private void rescheduleTimerCheck() {
        timerHandler.removeCallbacks(timerCheck);
        // An idle service must not wake the process every second before playback starts.
        if ((minutesDeadline > 0L || wallEndAt > 0L)
                && (!queue.isEmpty() && (player != null || loading || ready))) {
            timerHandler.post(timerCheck);
        }
    }

    private void syncWallEndFromSchedule() {
        // All playback paths use a selected end clock, not only scheduled BIGO handoff.
        // The preference getter returns zero after the deadline passes: never erase
        // an already-expired deadline before checking it (e.g. user presses Next
        // while the inexact Android alarm has not fired).
        long scheduled = RadioSchedule.endAt(this);
        if (scheduled > 0L) wallEndAt = scheduled;
        else if (wallEndAt > System.currentTimeMillis()) wallEndAt = 0L;
        rescheduleTimerCheck();
    }

    private final Runnable timerCheck = new Runnable() {
        @Override public void run() {
            if (minutesDeadline == 0L && wallEndAt == 0L) return;
            if (deadlineReached()) {
                // Only MARU music stops. BIGO live still requires manual termination.
                stopPlayback();
                return;
            }
            timerHandler.postDelayed(this, 1000L);
        }
    };

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
                    // Owner-opted coexist mode deliberately forgoes MARU's own
                    // audio focus claim when BIGO is open. Android hardware and
                    // BIGO policies may STILL mute sound or prevent live routing.
                    if (!RadioProgram.concurrentAudio(this)
                            && (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) {
                        pauseOnly();
                    }
                }).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            return START_NOT_STICKY;
        }
        if (ACTION_DJ_SAY.equals(action) || ACTION_DJ_GREETING.equals(action)
                || ACTION_DJ_ANNOUNCE_NEXT.equals(action)) {
            final boolean greeting = ACTION_DJ_GREETING.equals(action);
            final boolean nextIntro = ACTION_DJ_ANNOUNCE_NEXT.equals(action);
            String following = queue.isEmpty() ? ""
                    : queue.get((currentIndex + 1 + queue.size()) % queue.size()).title;
            int language = RadioDjSettings.language(this);
            final String text = greeting ? RadioDjBrain.opening(announcementCount++, language)
                    : nextIntro ? RadioDjBrain.nextSong(following, announcementCount++,
                            language)
                    : intent.getStringExtra(EXTRA_DJ_TEXT);
            if (text == null || text.trim().isEmpty()) { if (queue.isEmpty()) stopSelf();
                return START_NOT_STICKY; }
            // Manual announcer speech can replace an in-progress auto intro.
            // Preserve the intent to start the prepared song afterwards.
            boolean resume = isPlaying() || djHoldingTrack;
            djHoldingTrack = false;
            int ticket = generation;
            if (resume) pauseOnly();
            Runnable afterward = () -> {
                if (ticket == generation && resume && player != null && ready
                        && !deadlineReached()) {
                    try { if (requestFocus()) player.start(); }
                    catch (IllegalStateException ignored) {}
                    updateNotification();
                }
                if (queue.isEmpty()) stopSelf();
            };
            if (greeting) voice().greeting(text, afterward);
            else voice().say(text, afterward);
            return START_NOT_STICKY;
        }
        if (ACTION_APPLY_AUDIO_COEXIST.equals(action)) {
            // Stop competing for exclusive media focus in opt-in coexist mode.
            // Does not affect volume, microphone permission, or BIGO input.
            if (RadioProgram.concurrentAudio(this)
                    && audio != null && focusRequest != null) {
                audio.abandonAudioFocusRequest(focusRequest);
            }
            updateNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL_TIMER.equals(action)) {
            minutesDeadline = 0L; // Do not cancel an independently scheduled clock end.
            rescheduleTimerCheck();
            updateNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL_WALL_END.equals(action)) {
            wallEndAt = 0L; // Do not cancel an independently selected minute timer.
            rescheduleTimerCheck();
            updateNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_SET_WALL_END.equals(action)) {
            long endAt = intent.getLongExtra(EXTRA_END_AT, 0L);
            wallEndAt = endAt > System.currentTimeMillis() ? endAt : 0L;
            rescheduleTimerCheck();
            updateNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_SET_TIMER.equals(action)) {
            int minutes = intent.getIntExtra(EXTRA_MINUTES, 0);
            if (minutes <= 0) {
                minutesDeadline = 0L;
                rescheduleTimerCheck();
                updateNotification();
                return START_NOT_STICKY;
            }
            if (queue.isEmpty() || (!isPlaying() && !loading && !ready)) {
                error = "먼저 음악을 재생한 뒤 방송 시간을 설정해 주세요.";
                updateNotification();
                return START_NOT_STICKY;
            }
            minutes = Math.max(5, Math.min(720, minutes));
            minutesDeadline = SystemClock.elapsedRealtime() + minutes * 60000L;
            rescheduleTimerCheck();
            updateNotification();
            return START_NOT_STICKY;
        }
        if (ACTION_PLAY.equals(action) || ACTION_DJ_REQUEST.equals(action)
                || ACTION_NEXT.equals(action)
                || ACTION_PREVIOUS.equals(action) || ACTION_TOGGLE.equals(action)) {
            syncWallEndFromSchedule();
            if (deadlineReached()) {
                stopPlayback();
                return START_NOT_STICKY;
            }
        }
        if (ACTION_PLAY.equals(action) || ACTION_DJ_REQUEST.equals(action)) {
            requestedDjSpeech = ACTION_DJ_REQUEST.equals(action)
                    ? intent.getStringExtra(EXTRA_DJ_TEXT) : "";
            refreshQueue(null);
            String hash = intent.getStringExtra(EXTRA_HASH);
            boolean requestedFound = false;
            if (hash != null) for (int i = 0; i < queue.size(); i++) {
                if (hash.equals(queue.get(i).hash)) {
                    currentIndex = i;
                    requestedFound = true;
                    break;
                }
            }
            if (hash != null && !requestedFound && ACTION_DJ_REQUEST.equals(action)) {
                // One requested song may be outside the rotating genre. Play once,
                // then return to the original, unchanged category on completion.
                for (Originals.Track candidate : Originals.list(this)) {
                    if (hash.equals(candidate.hash)) {
                        queue.add(0, candidate);
                        currentIndex = 0;
                        requestedFound = true;
                        break;
                    }
                }
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
        int category = RadioProgram.active(this);
        List<Originals.Track> tracks = Originals.list(this);
        if (category == 5) {
            // Interleave a song from each available group:
            // KR → CN → Western → Instrumental, then the next song of each.
            List<List<Originals.Track>> groups = new ArrayList<>();
            for (int genre = 1; genre <= 4; genre++) {
                List<Originals.Track> bucket = new ArrayList<>();
                for (Originals.Track track : tracks)
                    if (RadioProgram.category(this, track) == genre) bucket.add(track);
                groups.add(bucket);
            }
            int max = 0;
            for (List<Originals.Track> bucket : groups)
                max = Math.max(max, bucket.size());
            for (int index = 0; index < max; index++)
                for (List<Originals.Track> bucket : groups)
                    if (index < bucket.size()) queue.add(bucket.get(index));
        } else {
            for (Originals.Track track : tracks)
                if (category == 0 || RadioProgram.category(this, track) == category) queue.add(track);
        }
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
        // Android audio focus LOSS used to pause MARU whenever BIGO acquired
        // its own audio session. User opt-in can keep local audio running without
        // actively competing for focus, subject to Android/OEM mute policies.
        // This does NOT send any audio data to BIGO's livestream microphone.
        if (RadioProgram.concurrentAudio(this)) {
            if (audio != null && focusRequest != null)
                audio.abandonAudioFocusRequest(focusRequest);
            error = "";
            return true;
        }
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
        if (djVoice != null) djVoice.cancel();
        djHoldingTrack = false;
        ++generation;
        final int ticket = generation;
        releasePlayer();
        currentIndex = index;
        error = "";
        ready = false;
        loading = true;
        rescheduleTimerCheck();
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
                if (deadlineReached()) {
                    stopPlayback();
                    return;
                }
                loading = false;
                ready = true;
                p.setVolume(1f, 1f); // original gain; system volume remains OS-controlled
                final String explicitSpeech = requestedDjSpeech;
                requestedDjSpeech = "";
                boolean doOpening = RadioDjSettings.opening(this) && !openingSpoken;
                if (doOpening) openingSpoken = true;
                boolean doNext = RadioDjSettings.interSong(this);
                if ((explicitSpeech != null && !explicitSpeech.isEmpty())
                        || doOpening || doNext) {
                    djHoldingTrack = true;
                    final Runnable play = () -> {
                        if (ticket != generation || player != p) return;
                        djHoldingTrack = false;
                        if (deadlineReached()) { stopPlayback(); return; }
                        try { p.start(); } catch (IllegalStateException ignored) {}
                        updateNotification();
                    };
                    final Runnable next = () -> {
                        if (ticket != generation || player != p) return;
                        if (doNext) {
                            voice().say(RadioDjBrain.nextSong(t.title,
                                    announcementCount++, RadioDjSettings.language(this)), play);
                        } else play.run();
                    };
                    if (explicitSpeech != null && !explicitSpeech.isEmpty()) {
                        voice().say(explicitSpeech, play);
                    } else if (doOpening) {
                        int language = RadioDjSettings.language(this);
                        if (language == 0) {
                            voice().greeting(RadioDjBrain.opening(announcementCount++), next);
                        } else {
                            voice().say(RadioDjBrain.opening(announcementCount++, language), next);
                        }
                    } else next.run();
                } else {
                    p.start();
                    updateNotification();
                }
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
        if (deadlineReached()) {
            stopPlayback();
            return;
        }
        String hash = currentIndex >= 0 && currentIndex < queue.size()
                ? queue.get(currentIndex).hash : null;
        refreshQueue(hash);
        if (queue.isEmpty()) { stopSelf(); return; }
        currentIndex = (currentIndex + 1 + queue.size()) % queue.size();
        playIndex(currentIndex);
    }

    private RadioDjVoice voice() {
        if (djVoice == null) djVoice = new RadioDjVoice(this);
        return djVoice;
    }

    private void clearTimer() {
        minutesDeadline = 0L;
        wallEndAt = 0L;
        timerHandler.removeCallbacks(timerCheck);
    }

    public long timerRemainingMs() {
        long durationRemaining = minutesDeadline == 0L ? Long.MAX_VALUE
                : Math.max(0L, minutesDeadline - SystemClock.elapsedRealtime());
        long clockRemaining = wallEndAt == 0L ? Long.MAX_VALUE
                : Math.max(0L, wallEndAt - System.currentTimeMillis());
        long nearest = Math.min(durationRemaining, clockRemaining);
        return nearest == Long.MAX_VALUE ? 0L : nearest;
    }

    private void stopPlayback() {
        if (djVoice != null) djVoice.cancel();
        requestedDjSpeech = "";
        openingSpoken = false;
        djHoldingTrack = false;
        clearTimer();
        ++generation;
        releasePlayer();
        queue.clear();
        currentIndex = -1;
        loading = false;
        error = "";
        if (audio != null && focusRequest != null)
            audio.abandonAudioFocusRequest(focusRequest);
        if (foreground) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else stopForeground(true);
        }
        foreground = false;
        stopSelf();
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
                .setContentText(currentTitle() + (timerRemainingMs() > 0 ? " · 자동 종료 예약" : ""))
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
        if (djVoice != null) { djVoice.close(); djVoice = null; }
        clearTimer();
        ++generation;
        releasePlayer();
        if (audio != null && focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        foreground = false;
        super.onDestroy();
    }
}
