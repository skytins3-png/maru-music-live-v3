package com.maru.musiclive;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GreetingLanguage {
    public static final String KOREAN = "ko";
    public static final String ENGLISH = "en";
    public static final String CHINESE = "zh";
    public static final String JAPANESE = "ja";
    public static final String SPANISH = "es";
    public static final String FRENCH = "fr";
    public static final String GERMAN = "de";
    public static final String ITALIAN = "it";
    public static final String PORTUGUESE = "pt";
    public static final String RUSSIAN = "ru";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(KOREAN, "한국어 음성");
        LABELS.put(ENGLISH, "영어 음성");
        LABELS.put(CHINESE, "중국어 음성");
        LABELS.put(JAPANESE, "일본어 음성");
        LABELS.put(SPANISH, "스페인어 음성");
        LABELS.put(FRENCH, "프랑스어 음성");
        LABELS.put(GERMAN, "독일어 음성");
        LABELS.put(ITALIAN, "이탈리아어 음성");
        LABELS.put(PORTUGUESE, "포르투갈어 음성");
        LABELS.put(RUSSIAN, "러시아어 음성");
    }

    private GreetingLanguage() {}

    public static String normalize(String value) {
        // Empty/blank input means no language was detected. The broadcast's
        // safe fallback is Korean; unsupported non-empty codes still fall
        // back to English below.
        if (value == null || value.trim().isEmpty()) return KOREAN;
        String language = value.trim().toLowerCase(Locale.ROOT);
        for (String code : LABELS.keySet()) {
            if (language.startsWith(code)) return code;
        }
        return ENGLISH;
    }

    public static String[] supportedCodes() {
        return LABELS.keySet().toArray(new String[0]);
    }

    public static String[] supportedLabels() {
        return LABELS.values().toArray(new String[0]);
    }

    public static Locale locale(String value) {
        switch (normalize(value)) {
            case KOREAN: return Locale.KOREA;
            case CHINESE: return Locale.SIMPLIFIED_CHINESE;
            case JAPANESE: return Locale.JAPAN;
            case SPANISH: return Locale.forLanguageTag("es-ES");
            case FRENCH: return Locale.FRANCE;
            case GERMAN: return Locale.GERMANY;
            case ITALIAN: return Locale.ITALY;
            case PORTUGUESE: return Locale.forLanguageTag("pt-BR");
            case RUSSIAN: return Locale.forLanguageTag("ru-RU");
            default: return Locale.US;
        }
    }

    public static String koreanLabel(String value) {
        return LABELS.getOrDefault(normalize(value), "영어 음성");
    }

    public static String detectFromNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return ENGLISH;
        boolean hangul = false;
        boolean kana = false;
        boolean cjk = false;
        boolean cyrillic = false;
        for (int offset = 0; offset < nickname.length();) {
            int cp = nickname.codePointAt(offset);
            offset += Character.charCount(cp);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            if (block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HANGUL_JAMO
                    || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                hangul = true;
            } else if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS) {
                kana = true;
            } else if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
                cjk = true;
            } else if (block == Character.UnicodeBlock.CYRILLIC
                    || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY) {
                cyrillic = true;
            }
        }
        if (hangul) return KOREAN;
        if (kana) return JAPANESE;
        if (cjk) return CHINESE;
        if (cyrillic) return RUSSIAN;
        return ENGLISH;
    }

    public static String detectFromText(String text) {
        if (text == null || text.trim().isEmpty()) return ENGLISH;
        String script = detectFromNickname(text);
        if (!ENGLISH.equals(script)) return script;

        String normalized = stripAccents(text).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "hola", "gracias", "regalo", "siguio", "entro", "me gusta", "buenas")) return SPANISH;
        if (containsAny(normalized, "bonjour", "merci", "cadeau", "a rejoint", "abonne", "j'aime")) return FRENCH;
        if (containsAny(normalized, "hallo", "danke", "geschenk", "beigetreten", "folgt dir", "gefallt")) return GERMAN;
        if (containsAny(normalized, "ciao", "grazie", "regalo", "entrato", "seguito", "mi piace")) return ITALIAN;
        if (containsAny(normalized, "ola", "obrigado", "obrigada", "presente", "entrou", "seguiu", "curtiu")) return PORTUGUESE;
        return ENGLISH;
    }

    public static boolean isLatinEuropean(String language) {
        String value = normalize(language);
        return SPANISH.equals(value)
                || FRENCH.equals(value)
                || GERMAN.equals(value)
                || ITALIAN.equals(value)
                || PORTUGUESE.equals(value);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private static String stripAccents(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "");
    }
}
