package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppStorage {
    private static final String PREFS = "maru_music_live";
    private static final String KEY_SONGS = "songs";
    private static final String KEY_IMAGES = "images";
    private static final String KEY_LYRICS = "lyrics";
    private static final String KEY_REPEAT = "repeat";
    private static final String KEY_RANDOM = "random";
    private static final String KEY_MODE = "broadcast_mode";
    private static final String KEY_SONG_TITLE_TTS = "song_title_tts";
    private static final String KEY_RADIO_CONVERSATION = "radio_conversation_v1";
    private static final String KEY_DAILY_RADIO = "daily_radio_v1";
    private static final String KEY_LONG_GUEST_TALK = "radio_long_guest_talk_v1";
    private static final String KEY_ENGLISH_TALK = "english_human_talk_v1";
    private static final String KEY_TRI_INTRO = "trilingual_song_story_intros_v1";
    private static final String KEY_DJ_TTS_VOICE = "dj_tts_selected_voice_";
    private static final String KEY_SUNO_PLAYLIST = "suno_playlist_url_v1";
    private static final String KEY_TRACK_ARTIST = "song_artist_v1_";
    private static final String KEY_RANDOM_HISTORY = "random_history_v1";
    private static final String KEY_RANDOM_CYCLE = "random_cycle_v1";
    private static final String KEY_FILL_BROADCAST_IMAGE = "fill_broadcast_image";
    private static final String KEY_POPUP_SINGLE_MEDIA = "popup_single_media_uri_v1";
    private static final String KEY_OWN_VOICE_ORIGINAL = "own_voice_original_uri_v1";
    private static final String KEY_DEFAULT_BROADCAST_VIDEO = "default_broadcast_video_uri_v1";
    private static final String KEY_AHWAN_GREETING_VISUAL = "ahwan_greeting_video_uri_v1";
    private static final String KEY_RADIO_TALK_VISUAL = "radio_talk_video_uri_v1";

    private AppStorage() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String encode(List<String> items) {
        return String.join("\n", items);
    }

    private static List<String> decode(String value) {
        List<String> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return out;
        for (String line : value.split("\n")) {
            if (!line.trim().isEmpty()) out.add(line);
        }
        return out;
    }

    public static List<String> loadSongs(Context c) { return decode(prefs(c).getString(KEY_SONGS, "")); }
    public static List<String> loadImages(Context c) { return decode(prefs(c).getString(KEY_IMAGES, "")); }
    public static List<String> loadLyrics(Context c) { return decode(prefs(c).getString(KEY_LYRICS, "")); }

    public static void saveSongs(Context c, List<String> v) { prefs(c).edit().putString(KEY_SONGS, encode(v)).apply(); }
    public static void saveImages(Context c, List<String> v) { prefs(c).edit().putString(KEY_IMAGES, encode(v)).apply(); }
    public static void saveLyrics(Context c, List<String> v) { prefs(c).edit().putString(KEY_LYRICS, encode(v)).apply(); }

    /** Only the user's explicitly selected Storage Access Framework URI is retained. */
    public static String popupMediaUri(Context c) {
        return prefs(c).getString(KEY_POPUP_SINGLE_MEDIA, "");
    }

    public static void setPopupMediaUri(Context c, String uri) {
        String safe = SingleMediaPreviewPolicy.isLocalUri(uri) ? uri : "";
        prefs(c).edit().putString(KEY_POPUP_SINGLE_MEDIA, safe).apply();
    }

    /** Last operator-approved original recording for local before/after playback only. */
    public static String ownVoiceOriginalUri(Context c) {
        return prefs(c).getString(KEY_OWN_VOICE_ORIGINAL, "");
    }
    public static void setOwnVoiceOriginalUri(Context c, String uri) {
        prefs(c).edit().putString(KEY_OWN_VOICE_ORIGINAL,
                SingleMediaPreviewPolicy.isLocalUri(uri) ? uri : "").apply();
    }

    /** User-selected fallback image or silent clip for scenes without visuals. */
    public static String defaultBroadcastVideo(Context c) {
        return prefs(c).getString(KEY_DEFAULT_BROADCAST_VIDEO,"");
    }
    public static void setDefaultBroadcastVideo(Context c,String uri) {
        String safe=SingleMediaPreviewPolicy.isLocalUri(uri)?uri:"";
        prefs(c).edit().putString(KEY_DEFAULT_BROADCAST_VIDEO,safe).apply();
    }

    /** Operator-selected local videos; only the image/video is shown, TTS is independent. */
    public static String ahwanGreetingVideo(Context c) {
        return prefs(c).getString(KEY_AHWAN_GREETING_VISUAL, "");
    }
    public static void setAhwanGreetingVideo(Context c, String uri) {
        prefs(c).edit().putString(KEY_AHWAN_GREETING_VISUAL,
                SingleMediaPreviewPolicy.isLocalUri(uri) ? uri : "").apply();
    }
    public static String radioTalkVideo(Context c) {
        return prefs(c).getString(KEY_RADIO_TALK_VISUAL, "");
    }
    public static void setRadioTalkVideo(Context c, String uri) {
        prefs(c).edit().putString(KEY_RADIO_TALK_VISUAL,
                SingleMediaPreviewPolicy.isLocalUri(uri) ? uri : "").apply();
    }

    public static boolean repeatAll(Context c) { return prefs(c).getBoolean(KEY_REPEAT, true); }
    public static boolean random(Context c) { return prefs(c).getBoolean(KEY_RANDOM, false); }
    public static void setRepeatAll(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_REPEAT, v).apply(); }
    public static void setRandom(Context c, boolean v) { prefs(c).edit().putBoolean(KEY_RANDOM, v).apply(); }

    /** Split-screen BIGO use: crop the song image slightly so the top pane has no wide side bars. */
    public static boolean fillBroadcastImage(Context c) {
        return prefs(c).getBoolean(KEY_FILL_BROADCAST_IMAGE, false);
    }

    public static void setFillBroadcastImage(Context c, boolean value) {
        prefs(c).edit().putBoolean(KEY_FILL_BROADCAST_IMAGE, value).apply();
    }

    public static boolean songTitleTts(Context c) {
        return prefs(c).getBoolean(KEY_SONG_TITLE_TTS, true);
    }

    public static void setSongTitleTts(Context c, boolean value) {
        prefs(c).edit()
                .putBoolean(KEY_SONG_TITLE_TTS, value)
                .apply();
    }

    /** Radio dialogue replaces the legacy five-language track intermission. */
    public static boolean radioConversationEnabled(Context c) {
        return prefs(c).getBoolean(KEY_RADIO_CONVERSATION, true);
    }

    public static void setRadioConversationEnabled(Context c, boolean value) {
        prefs(c).edit().putBoolean(KEY_RADIO_CONVERSATION, value).apply();
    }

    /** Daily topics rotate based on the phone's local date; no install/network required. */
    public static String sunoPlaylistUrl(Context c) {
        return prefs(c).getString(KEY_SUNO_PLAYLIST, "");
    }

    public static void setSunoPlaylistUrl(Context c, String url) {
        prefs(c).edit().putString(KEY_SUNO_PLAYLIST, url == null ? "" : url).apply();
    }

    /** Explicitly auditioned and confirmed TTS voice by ISO language code.
     * Android Voice exposes no reliable gender field; never infer gender. */
    public static String selectedDjVoice(Context c, String language) {
        String code = GreetingLanguage.normalize(language);
        return prefs(c).getString(KEY_DJ_TTS_VOICE + code, "");
    }
    public static void setSelectedDjVoice(Context c, String language, String voiceName) {
        String code = GreetingLanguage.normalize(language);
        String clean = voiceName == null ? "" : voiceName.trim();
        prefs(c).edit().putString(KEY_DJ_TTS_VOICE + code, clean).apply();
    }

    /** User-configured English -> Korean -> Mandarin intros; enabled by default. */
    public static boolean trilingualIntroEnabled(Context c) {
        return prefs(c).getBoolean(KEY_TRI_INTRO, true);
    }
    public static void setTrilingualIntroEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_TRI_INTRO, enabled).apply();
    }

    public static boolean englishRadioEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENGLISH_TALK, true);
    }

    public static void setEnglishRadioEnabled(Context c, boolean yes) {
        prefs(c).edit().putBoolean(KEY_ENGLISH_TALK, yes).apply();
    }

    public static boolean longGuestTalkEnabled(Context c) {
        return prefs(c).getBoolean(KEY_LONG_GUEST_TALK, false);
    }

    public static void setLongGuestTalkEnabled(Context c, boolean value) {
        prefs(c).edit().putBoolean(KEY_LONG_GUEST_TALK, value).apply();
    }

    public static boolean dailyRadioEnabled(Context c) {
        return prefs(c).getBoolean(KEY_DAILY_RADIO, true);
    }

    public static void setDailyRadioEnabled(Context c, boolean value) {
        prefs(c).edit().putBoolean(KEY_DAILY_RADIO, value).apply();
    }

    /** User-selected artist attribution is metadata, NOT proof of AI singing. */
    public static int artistForSong(Context c, String uri) {
        if (uri == null || uri.trim().isEmpty()) return 0;
        int stored = prefs(c).getInt(KEY_TRACK_ARTIST
                + encodeKey(uri), 0);
        return stored >= 1 && stored <= SunoArtistProductionCatalog.count() ? stored : 0;
    }

    public static void setArtistForSong(Context c, String uri, int artistId) {
        if (uri == null || uri.trim().isEmpty()) return;
        if (artistId < 0 || artistId > SunoArtistProductionCatalog.count())
            throw new IllegalArgumentException("Invalid artist id");
        String key = KEY_TRACK_ARTIST + encodeKey(uri);
        SharedPreferences.Editor editor = prefs(c).edit();
        if (artistId == 0) editor.remove(key);
        else editor.putInt(key, artistId);
        editor.apply();
    }

    public static Map<String, Long> loadRandomHistory(Context c) {
        Map<String, Long> out = new LinkedHashMap<>();
        String raw = prefs(c).getString(KEY_RANDOM_HISTORY, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String line : raw.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0 || tab >= line.length() - 1) continue;
            try {
                long time = Long.parseLong(line.substring(0, tab));
                String key = decodeKey(line.substring(tab + 1));
                if (!key.isEmpty()) out.put(key, time);
            } catch (RuntimeException ignored) {}
        }
        return out;
    }

    public static Set<String> loadRandomCycle(Context c) {
        Set<String> out = new HashSet<>();
        String raw = prefs(c).getString(KEY_RANDOM_CYCLE, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String line : raw.split("\n")) {
            try {
                String key = decodeKey(line);
                if (!key.isEmpty()) out.add(key);
            } catch (RuntimeException ignored) {}
        }
        return out;
    }

    public static void saveRandomPlaybackState(
            Context c,
            Map<String, Long> history,
            Set<String> cycle) {
        StringBuilder historyText = new StringBuilder();
        if (history != null) {
            for (Map.Entry<String, Long> entry : history.entrySet()) {
                String key = entry.getKey();
                Long time = entry.getValue();
                if (key == null || key.trim().isEmpty() || time == null) continue;
                if (historyText.length() > 0) historyText.append('\n');
                historyText.append(Math.max(0L, time))
                        .append('\t')
                        .append(encodeKey(key));
            }
        }
        StringBuilder cycleText = new StringBuilder();
        if (cycle != null) {
            for (String key : cycle) {
                if (key == null || key.trim().isEmpty()) continue;
                if (cycleText.length() > 0) cycleText.append('\n');
                cycleText.append(encodeKey(key));
            }
        }
        prefs(c).edit()
                .putString(KEY_RANDOM_HISTORY, historyText.toString())
                .putString(KEY_RANDOM_CYCLE, cycleText.toString())
                .apply();
    }

    private static String encodeKey(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeKey(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        return new String(
                Base64.getUrlDecoder().decode(value.trim()),
                StandardCharsets.UTF_8);
    }

    public static String broadcastMode(Context c) {
        return prefs(c).getString(KEY_MODE, BroadcastMode.PORTRAIT_9_16.name());
    }
    public static void setBroadcastMode(Context c, String v) {
        prefs(c).edit().putString(KEY_MODE, v).apply();
    }
}
