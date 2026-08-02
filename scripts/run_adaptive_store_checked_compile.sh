#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/adaptive-store-checked"
SRC="$OUT/src"
CLASSES="$OUT/classes"

rm -rf "$OUT"
mkdir -p \
  "$SRC/android/content" \
  "$SRC/org/json" \
  "$SRC/com/maru/musiclive" \
  "$CLASSES"

cat > "$SRC/com/maru/musiclive/AutoReplyPolicy.java" <<'JAVA'
package com.maru.musiclive;

/** Compile-only policy constant used by EventType. */
public final class AutoReplyPolicy {
    public static final long MAX_VISUAL_REPLY_MS = 2_000L;
    private AutoReplyPolicy() {}
}
JAVA

cat > "$SRC/android/content/Context.java" <<'JAVA'
package android.content;

public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract SharedPreferences getSharedPreferences(
            String name,
            int mode);
}
JAVA

cat > "$SRC/android/content/SharedPreferences.java" <<'JAVA'
package android.content;

public interface SharedPreferences {
    boolean getBoolean(String key, boolean defaultValue);
    String getString(String key, String defaultValue);
    Editor edit();

    interface Editor {
        Editor putBoolean(String key, boolean value);
        Editor putString(String key, String value);
        Editor clear();
        void apply();
    }
}
JAVA

cat > "$SRC/org/json/JSONException.java" <<'JAVA'
package org.json;

public class JSONException extends Exception {
    public JSONException(String message) {
        super(message);
    }
}
JAVA

cat > "$SRC/org/json/JSONArray.java" <<'JAVA'
package org.json;

import java.util.ArrayList;
import java.util.List;

public class JSONArray {
    private final List<Object> values = new ArrayList<>();

    public JSONArray() {}

    public JSONArray(String text) throws JSONException {
        if (text == null) {
            throw new JSONException("null JSON");
        }
    }

    public int length() {
        return values.size();
    }

    public JSONArray put(Object value) {
        values.add(value);
        return this;
    }

    public JSONObject optJSONObject(int index) {
        if (index < 0 || index >= values.size()) return null;
        Object value = values.get(index);
        return value instanceof JSONObject
                ? (JSONObject) value
                : null;
    }

    public String optString(int index) {
        if (index < 0 || index >= values.size()) return "";
        Object value = values.get(index);
        return value == null ? "" : String.valueOf(value);
    }

    @Override public String toString() {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(',');
            out.append(JSONObject.render(values.get(i)));
        }
        return out.append(']').toString();
    }
}
JAVA

cat > "$SRC/org/json/JSONObject.java" <<'JAVA'
package org.json;

import java.util.LinkedHashMap;
import java.util.Map;

public class JSONObject {
    private final Map<String, Object> values =
            new LinkedHashMap<>();

    public JSONObject() {}

    public JSONObject(String text) throws JSONException {
        if (text == null) {
            throw new JSONException("null JSON");
        }
    }

    public JSONObject put(String key, Object value)
            throws JSONException {
        if (key == null) {
            throw new JSONException("null key");
        }
        values.put(key, value);
        return this;
    }

    public JSONObject optJSONObject(String key) {
        Object value = values.get(key);
        return value instanceof JSONObject
                ? (JSONObject) value
                : null;
    }

    public JSONArray optJSONArray(String key) {
        Object value = values.get(key);
        return value instanceof JSONArray
                ? (JSONArray) value
                : null;
    }

    public String optString(String key) {
        return optString(key, "");
    }

    public String optString(
            String key,
            String defaultValue) {
        Object value = values.get(key);
        return value == null
                ? defaultValue
                : String.valueOf(value);
    }

    public int optInt(String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Number
                ? ((Number) value).intValue()
                : defaultValue;
    }

    public boolean optBoolean(
            String key,
            boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean
                ? (Boolean) value
                : defaultValue;
    }

    public JSONArray names() {
        JSONArray names = new JSONArray();
        for (String key : values.keySet()) {
            names.put(key);
        }
        return names;
    }

    public int length() {
        return values.size();
    }

    public Object remove(String key) {
        return values.remove(key);
    }

    // Deliberately checked, matching Android org.json.
    public String toString(int indentSpaces)
            throws JSONException {
        if (indentSpaces < 0) {
            throw new JSONException("negative indent");
        }
        return toString();
    }

    @Override public String toString() {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry
                : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('"')
                    .append(escape(entry.getKey()))
                    .append('"')
                    .append(':')
                    .append(render(entry.getValue()));
        }
        return out.append('}').toString();
    }

    static String render(Object value) {
        if (value == null) return "null";
        if (value instanceof Number
                || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof JSONObject
                || value instanceof JSONArray) {
            return value.toString();
        }
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
JAVA

cat > "$SRC/AdaptiveAiStoreCheckedTest.java" <<'JAVA'
import android.content.Context;
import android.content.SharedPreferences;

import com.maru.musiclive.AdaptiveAiStore;

import java.util.HashMap;
import java.util.Map;

public final class AdaptiveAiStoreCheckedTest {
    public static void main(String[] args) {
        FakeContext context = new FakeContext();
        String exported =
                AdaptiveAiStore.exportJson(context);

        if (!exported.contains(
                "MARU_ADAPTIVE_AI_V1")) {
            throw new AssertionError(
                    "Export format marker is missing: "
                            + exported);
        }
        if (!exported.startsWith("{")
                || !exported.endsWith("}")) {
            throw new AssertionError(
                    "Export is not a JSON object: "
                            + exported);
        }

        System.out.println(
                "ADAPTIVE-STORE-CHECKED-COMPILE-TEST: PASS");
    }

    private static final class FakeContext
            extends Context {
        private final FakePreferences preferences =
                new FakePreferences();

        @Override public SharedPreferences
        getSharedPreferences(
                String name,
                int mode) {
            return preferences;
        }
    }

    private static final class FakePreferences
            implements SharedPreferences {
        private final Map<String, Object> values =
                new HashMap<>();

        @Override public boolean getBoolean(
                String key,
                boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean
                    ? (Boolean) value
                    : defaultValue;
        }

        @Override public String getString(
                String key,
                String defaultValue) {
            Object value = values.get(key);
            return value instanceof String
                    ? (String) value
                    : defaultValue;
        }

        @Override public Editor edit() {
            return new FakeEditor(values);
        }
    }

    private static final class FakeEditor
            implements SharedPreferences.Editor {
        private final Map<String, Object> values;

        FakeEditor(Map<String, Object> values) {
            this.values = values;
        }

        @Override public SharedPreferences.Editor
        putBoolean(String key, boolean value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor
        putString(String key, String value) {
            values.put(key, value);
            return this;
        }

        @Override public SharedPreferences.Editor clear() {
            values.clear();
            return this;
        }

        @Override public void apply() {}
    }
}
JAVA

javac \
  -encoding UTF-8 \
  -d "$CLASSES" \
  "$SRC/android/content/Context.java" \
  "$SRC/android/content/SharedPreferences.java" \
  "$SRC/org/json/JSONException.java" \
  "$SRC/org/json/JSONArray.java" \
  "$SRC/org/json/JSONObject.java" \
  "$SRC/com/maru/musiclive/AutoReplyPolicy.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/EventType.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/LearnedRule.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/LiveEvent.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/ChatMessage.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/GreetingLanguage.java" \
  "$ROOT/app/src/main/java/com/maru/musiclive/AdaptiveAiStore.java" \
  "$SRC/AdaptiveAiStoreCheckedTest.java"

java -cp "$CLASSES" AdaptiveAiStoreCheckedTest
