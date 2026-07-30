package com.maru.musiclive;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdaptiveAiStore {
    private static final String PREFS = "maru_adaptive_ai";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CONVERSATION = "conversation";
    private static final String KEY_DEFAULT_LANGUAGE = "default_language";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_RULES = "event_rules";
    private static final String KEY_REPLIES = "custom_replies";
    private static final String KEY_CANDIDATES = "candidates";
    private static final String KEY_LAST_CHAT_NAME = "last_chat_name";
    private static final String KEY_LAST_CHAT_TEXT = "last_chat_text";
    private static final String KEY_LAST_CHAT_LANGUAGE = "last_chat_language";
    private static final String KEY_HOST_NICKNAME = "host_nickname";
    private static final String KEY_SAFE_CONVERSATION_V311 = "safe_conversation_v311";
    private static final int MAX_PROFILES = 500;
    private static final int MAX_RULES = 100;
    private static final int MAX_REPLIES = 100;
    private static final int MAX_CANDIDATES = 80;

    private AdaptiveAiStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply();
    }

    public static boolean conversationEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CONVERSATION, true);
    }

    public static void setConversationEnabled(Context context, boolean value) {
        prefs(context).edit()
                .putBoolean(KEY_CONVERSATION, value)
                .putBoolean(KEY_SAFE_CONVERSATION_V311, true)
                .apply();
    }

    /** V3.1.0 forced this option off on every launch. Restore the new visual-only safe mode once. */
    public static void migrateSafeConversationV311(Context context) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean(KEY_SAFE_CONVERSATION_V311, false)) return;
        preferences.edit()
                .putBoolean(KEY_CONVERSATION, true)
                .putBoolean(KEY_SAFE_CONVERSATION_V311, true)
                .apply();
    }

    public static String defaultLanguage(Context context) {
        return GreetingLanguage.normalize(
                prefs(context).getString(KEY_DEFAULT_LANGUAGE, GreetingLanguage.ENGLISH));
    }

    public static void setDefaultLanguage(Context context, String language) {
        prefs(context).edit()
                .putString(KEY_DEFAULT_LANGUAGE, GreetingLanguage.normalize(language))
                .apply();
    }

    public static String hostNickname(Context context) {
        return prefs(context).getString(KEY_HOST_NICKNAME, "꿈에서넌");
    }

    public static void setHostNickname(Context context, String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        prefs(context).edit().putString(KEY_HOST_NICKNAME, value).apply();
    }

    public static synchronized String resolveEventLanguage(
            Context context,
            String nickname,
            String observedLanguage) {
        String remembered = storedLanguage(context, nickname, 2);
        if (!remembered.isEmpty()) return remembered;

        String script = GreetingLanguage.detectFromNickname(nickname);
        if (!GreetingLanguage.ENGLISH.equals(script)) {
            rememberLanguage(context, nickname, script, 2);
            return script;
        }

        String observed = GreetingLanguage.normalize(observedLanguage);
        // BIGO may display every system event in the host phone language.
        // For Latin nicknames, Korean/Chinese/Japanese UI text does not prove
        // the viewer speaks that language, so the user-selected fallback is safer.
        if (GreetingLanguage.KOREAN.equals(observed)
                || GreetingLanguage.CHINESE.equals(observed)
                || GreetingLanguage.JAPANESE.equals(observed)) {
            return defaultLanguage(context);
        }

        rememberLanguage(context, nickname, observed, 1);
        return observed;
    }

    public static synchronized String resolveEventLanguage(
            Context context,
            String nickname) {
        String stored = storedLanguage(context, nickname, 2);
        if (!stored.isEmpty()) return stored;

        String script = GreetingLanguage.detectFromNickname(nickname);
        if (!GreetingLanguage.ENGLISH.equals(script)) {
            rememberLanguage(context, nickname, script, 2);
            return script;
        }
        return defaultLanguage(context);
    }

    public static synchronized String resolveChatLanguage(
            Context context,
            String nickname,
            String message) {
        String detected = GreetingLanguage.detectFromText(message);
        if (!GreetingLanguage.ENGLISH.equals(detected)
                || containsStrongEnglish(message)) {
            rememberLanguage(context, nickname, detected, 2);
            return detected;
        }
        String stored = storedLanguage(context, nickname, 1);
        return stored.isEmpty() ? defaultLanguage(context) : stored;
    }

    public static synchronized void observeEvent(Context context, LiveEvent event) {
        if (context == null || event == null || event.nickname.isEmpty()) return;
        JSONObject profiles = profiles(context);
        String key = profileKey(event.nickname);
        JSONObject profile = profiles.optJSONObject(key);
        if (profile == null) profile = new JSONObject();
        try {
            profile.put("name", event.nickname);
            profile.put("language", GreetingLanguage.normalize(event.languageHint));
            profile.put("confidence", Math.max(1, profile.optInt("confidence", 0)));
            profile.put("lastSeen", event.timeMs);
            profile.put("events", profile.optInt("events", 0) + 1);
            String field = countField(event.type);
            if (!field.isEmpty()) profile.put(field, profile.optInt(field, 0) + 1);
            profiles.put(key, profile);
            trimObject(profiles, MAX_PROFILES);
            saveJson(context, KEY_PROFILES, profiles);
        } catch (JSONException ignored) {}
    }

    public static synchronized void observeChat(
            Context context,
            ChatMessage chat) {
        if (chat == null) return;
        String language = resolveChatLanguage(context, chat.nickname, chat.message);
        JSONObject profiles = profiles(context);
        String key = profileKey(chat.nickname);
        JSONObject profile = profiles.optJSONObject(key);
        if (profile == null) profile = new JSONObject();
        try {
            profile.put("name", chat.nickname);
            profile.put("language", language);
            profile.put("confidence", Math.max(2, profile.optInt("confidence", 0)));
            profile.put("lastSeen", System.currentTimeMillis());
            profile.put("chats", profile.optInt("chats", 0) + 1);
            profiles.put(key, profile);
            trimObject(profiles, MAX_PROFILES);
            saveJson(context, KEY_PROFILES, profiles);
        } catch (JSONException ignored) {}
        prefs(context).edit()
                .putString(KEY_LAST_CHAT_NAME, chat.nickname)
                .putString(KEY_LAST_CHAT_TEXT, chat.message)
                .putString(KEY_LAST_CHAT_LANGUAGE, language)
                .apply();
    }

    public static synchronized void rememberLanguage(
            Context context,
            String nickname,
            String language,
            int confidenceDelta) {
        if (nickname == null || nickname.trim().isEmpty()) return;
        JSONObject profiles = profiles(context);
        String key = profileKey(nickname);
        JSONObject profile = profiles.optJSONObject(key);
        if (profile == null) profile = new JSONObject();
        try {
            String normalized = GreetingLanguage.normalize(language);
            String old = GreetingLanguage.normalize(profile.optString("language", normalized));
            int confidence = profile.optInt("confidence", 0);
            if (!old.equals(normalized)) confidence = 0;
            profile.put("name", nickname.trim());
            profile.put("language", normalized);
            profile.put("confidence", Math.min(10, confidence + Math.max(1, confidenceDelta)));
            profile.put("lastSeen", System.currentTimeMillis());
            profiles.put(key, profile);
            trimObject(profiles, MAX_PROFILES);
            saveJson(context, KEY_PROFILES, profiles);
        } catch (JSONException ignored) {}
    }

    public static synchronized String storedLanguage(
            Context context,
            String nickname,
            int minimumConfidence) {
        JSONObject profile = profiles(context).optJSONObject(profileKey(nickname));
        if (profile == null || profile.optInt("confidence", 0) < minimumConfidence) return "";
        return GreetingLanguage.normalize(profile.optString("language", ""));
    }

    public static synchronized void addEventRule(
            Context context,
            String phrase,
            EventType type,
            String language) {
        String clean = cleanPhrase(phrase);
        if (clean.length() < 2 || type == null || type == EventType.UNKNOWN) return;
        JSONArray rules = array(context, KEY_RULES);
        JSONArray out = new JSONArray();
        try {
            JSONObject newest = new JSONObject();
            newest.put("phrase", clean);
            newest.put("type", type.name());
            newest.put("language", GreetingLanguage.normalize(language));
            newest.put("hits", 0);
            newest.put("created", System.currentTimeMillis());
            out.put(newest);
            for (int i = 0; i < rules.length() && out.length() < MAX_RULES; i++) {
                JSONObject rule = rules.optJSONObject(i);
                if (rule == null) continue;
                if (clean.equalsIgnoreCase(rule.optString("phrase"))) continue;
                out.put(rule);
            }
            saveArray(context, KEY_RULES, out);
        } catch (JSONException ignored) {}
    }

    public static synchronized List<LearnedRule> eventRules(Context context) {
        List<LearnedRule> out = new ArrayList<>();
        JSONArray rules = array(context, KEY_RULES);
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) continue;
            String phrase = rule.optString("phrase", "").trim();
            EventType type = EventType.fromStored(rule.optString("type", ""));
            if (phrase.length() >= 2 && type != EventType.UNKNOWN) {
                out.add(new LearnedRule(
                        phrase,
                        type,
                        rule.optString("language", GreetingLanguage.ENGLISH)));
            }
        }
        return out;
    }

    public static synchronized void addCustomReply(
            Context context,
            String trigger,
            String answer,
            String language) {
        String cleanTrigger = cleanPhrase(trigger);
        String cleanAnswer = answer == null ? "" : answer.trim();
        if (cleanTrigger.length() < 2 || cleanAnswer.length() < 1) return;
        JSONArray replies = array(context, KEY_REPLIES);
        JSONArray out = new JSONArray();
        try {
            JSONObject newest = new JSONObject();
            newest.put("trigger", cleanTrigger);
            newest.put("answer", cleanAnswer);
            newest.put("language", GreetingLanguage.normalize(language));
            newest.put("hits", 0);
            newest.put("created", System.currentTimeMillis());
            out.put(newest);
            for (int i = 0; i < replies.length() && out.length() < MAX_REPLIES; i++) {
                JSONObject reply = replies.optJSONObject(i);
                if (reply == null) continue;
                if (cleanTrigger.equalsIgnoreCase(reply.optString("trigger"))) continue;
                out.put(reply);
            }
            saveArray(context, KEY_REPLIES, out);
        } catch (JSONException ignored) {}
    }

    public static synchronized String customReply(
            Context context,
            String message,
            String language) {
        String clean = cleanPhrase(message).toLowerCase(Locale.ROOT);
        JSONArray replies = array(context, KEY_REPLIES);
        for (int i = 0; i < replies.length(); i++) {
            JSONObject reply = replies.optJSONObject(i);
            if (reply == null) continue;
            String trigger = reply.optString("trigger", "").toLowerCase(Locale.ROOT);
            if (trigger.length() >= 2 && clean.contains(trigger)) {
                try {
                    reply.put("hits", reply.optInt("hits", 0) + 1);
                    saveArray(context, KEY_REPLIES, replies);
                } catch (JSONException ignored) {}
                return reply.optString("answer", "");
            }
        }
        return "";
    }

    public static synchronized void recordCandidate(
            Context context,
            String nickname,
            String message,
            String language) {
        if (message == null || message.trim().isEmpty()) return;
        JSONArray candidates = array(context, KEY_CANDIDATES);
        JSONArray out = new JSONArray();
        try {
            JSONObject newest = new JSONObject();
            newest.put("nickname", nickname == null ? "" : nickname.trim());
            newest.put("message", message.trim());
            newest.put("language", GreetingLanguage.normalize(language));
            newest.put("time", System.currentTimeMillis());
            out.put(newest);
            for (int i = 0; i < candidates.length() && out.length() < MAX_CANDIDATES; i++) {
                JSONObject item = candidates.optJSONObject(i);
                if (item == null) continue;
                if (message.trim().equalsIgnoreCase(item.optString("message"))) continue;
                out.put(item);
            }
            saveArray(context, KEY_CANDIDATES, out);
        } catch (JSONException ignored) {}
    }

    public static synchronized JSONObject latestCandidate(Context context) {
        JSONObject value = array(context, KEY_CANDIDATES).optJSONObject(0);
        return value == null ? new JSONObject() : value;
    }

    public static String lastChatName(Context context) {
        return prefs(context).getString(KEY_LAST_CHAT_NAME, "");
    }

    public static String lastChatText(Context context) {
        return prefs(context).getString(KEY_LAST_CHAT_TEXT, "");
    }

    public static String lastChatLanguage(Context context) {
        return GreetingLanguage.normalize(
                prefs(context).getString(KEY_LAST_CHAT_LANGUAGE, GreetingLanguage.ENGLISH));
    }

    public static synchronized String summary(Context context) {
        JSONObject profiles = profiles(context);
        JSONArray rules = array(context, KEY_RULES);
        JSONArray replies = array(context, KEY_REPLIES);
        JSONArray candidates = array(context, KEY_CANDIDATES);
        int visits = 0;
        int likes = 0;
        int gifts = 0;
        int follows = 0;
        int chats = 0;
        JSONArray names = profiles.names();
        if (names != null) {
            for (int i = 0; i < names.length(); i++) {
                JSONObject profile = profiles.optJSONObject(names.optString(i));
                if (profile == null) continue;
                visits += profile.optInt("joins", 0);
                likes += profile.optInt("likes", 0);
                gifts += profile.optInt("gifts", 0);
                follows += profile.optInt("follows", 0);
                chats += profile.optInt("chats", 0);
            }
        }
        return "기억한 청취자 " + profiles.length() + "명\n"
                + "입장 " + visits + " · 좋아요 " + likes + " · 선물 " + gifts + " · 팔로우 " + follows + " · 대화 " + chats + "\n"
                + "학습 이벤트 문구 " + rules.length() + "개 · 학습 답변 " + replies.length() + "개\n"
                + "미분류 대화 " + candidates.length() + "개";
    }

    public static synchronized String exportJson(Context context) {
        JSONObject root = new JSONObject();
        try {
            root.put("format", "MARU_ADAPTIVE_AI_V1");
            root.put("exportedAt", System.currentTimeMillis());
            root.put("enabled", enabled(context));
            root.put("conversation", conversationEnabled(context));
            root.put("defaultLanguage", defaultLanguage(context));
            root.put("hostNickname", hostNickname(context));
            root.put("profiles", profiles(context));
            root.put("eventRules", array(context, KEY_RULES));
            root.put("customReplies", array(context, KEY_REPLIES));
            root.put("candidates", array(context, KEY_CANDIDATES));

            // Android org.json.JSONObject.toString(int) declares
            // JSONException. Keep the pretty-print operation inside the
            // protected block so the Android compiler accepts this method.
            return root.toString(2);
        } catch (JSONException ignored) {
            // Compact JSON does not declare a checked exception and preserves
            // any data already collected if pretty printing ever fails.
            return root.toString();
        }
    }

    public static synchronized boolean importJson(Context context, String text) {
        try {
            JSONObject root = new JSONObject(text);
            if (!"MARU_ADAPTIVE_AI_V1".equals(root.optString("format"))) return false;
            prefs(context).edit()
                    .putBoolean(KEY_ENABLED, root.optBoolean("enabled", true))
                    .putBoolean(KEY_CONVERSATION, root.optBoolean("conversation", false))
                    .putString(KEY_DEFAULT_LANGUAGE,
                            GreetingLanguage.normalize(root.optString("defaultLanguage", GreetingLanguage.ENGLISH)))
                    .putString(KEY_HOST_NICKNAME, root.optString("hostNickname", "꿈에서넌"))
                    .putString(KEY_PROFILES, root.optJSONObject("profiles") == null
                            ? "{}" : root.optJSONObject("profiles").toString())
                    .putString(KEY_RULES, root.optJSONArray("eventRules") == null
                            ? "[]" : root.optJSONArray("eventRules").toString())
                    .putString(KEY_REPLIES, root.optJSONArray("customReplies") == null
                            ? "[]" : root.optJSONArray("customReplies").toString())
                    .putString(KEY_CANDIDATES, root.optJSONArray("candidates") == null
                            ? "[]" : root.optJSONArray("candidates").toString())
                    .apply();
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    public static synchronized void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static JSONObject profiles(Context context) {
        try {
            return new JSONObject(prefs(context).getString(KEY_PROFILES, "{}"));
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private static JSONArray array(Context context, String key) {
        try {
            return new JSONArray(prefs(context).getString(key, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private static void saveJson(Context context, String key, JSONObject value) {
        prefs(context).edit().putString(key, value.toString()).apply();
    }

    private static void saveArray(Context context, String key, JSONArray value) {
        prefs(context).edit().putString(key, value.toString()).apply();
    }

    private static String profileKey(String nickname) {
        return nickname == null
                ? ""
                : nickname.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_-]+", "_");
    }

    private static String countField(EventType type) {
        if (type == EventType.JOIN) return "joins";
        if (type == EventType.LIKE) return "likes";
        if (type == EventType.GIFT) return "gifts";
        if (type == EventType.FOLLOW) return "follows";
        if (type == EventType.CHAT) return "chats";
        return "";
    }

    private static void trimObject(JSONObject object, int max) {
        JSONArray names = object.names();
        if (names == null || names.length() <= max) return;
        for (int i = max; i < names.length(); i++) object.remove(names.optString(i));
    }

    private static String cleanPhrase(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return clean.length() > 120 ? clean.substring(0, 120) : clean;
    }

    private static boolean containsStrongEnglish(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("hello")
                || lower.contains("thank")
                || lower.contains("song")
                || lower.contains("music")
                || lower.contains("good");
    }
}
