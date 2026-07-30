package com.maru.musiclive;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalize(String input) {
        if (input == null) return "";
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace('\u200B', ' ')
                .replace('\u2060', ' ')
                .replace('\r', '\n');
        value = value.replaceAll("[\\t\\f ]+", " ");
        value = value.replaceAll("\\n{3,}", "\\n\\n");
        return value.trim();
    }

    public static String sanitizeNickname(String input) {
        String value = normalize(input);
        if (value.isEmpty()) return "";

        String[] lines = value.split("\\n");
        value = lines[lines.length - 1].trim();
        value = stripEdgeSymbols(value).trim();
        value = value.replaceAll("^[\\[【(（].{0,16}?[\\]】)）]\\s*", "");
        value = value.replaceAll(
                "^(?:VIP|ADMIN|HOST|팬|관리자|방장|主播|管理員)\\s*[:：-]?\\s*",
                "");
        value = value.replaceAll("^[xX×]?\\d{1,6}\\s+", "");
        value = value.replaceAll("(?:님|さん)$", "");
        value = stripEdgeSymbols(value).replaceAll("\\s{2,}", " ").trim();

        if (value.isEmpty()) return "";
        if (value.codePointCount(0, value.length()) > 24) return "";
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("bigo live") || lower.equals("live")
                || lower.equals("system") || lower.equals("notification")) {
            return "";
        }
        if (!value.codePoints().anyMatch(Character::isLetterOrDigit)) return "";
        return value;
    }

    private static String stripEdgeSymbols(String input) {
        int start = 0;
        int end = input.length();
        while (start < end) {
            int cp = input.codePointAt(start);
            if (Character.isLetterOrDigit(cp) || isAllowed(cp)) break;
            start += Character.charCount(cp);
        }
        while (end > start) {
            int cp = input.codePointBefore(end);
            if (Character.isLetterOrDigit(cp) || isAllowed(cp)) break;
            end -= Character.charCount(cp);
        }
        return input.substring(start, end);
    }

    private static boolean isAllowed(int cp) {
        return cp == '_' || cp == '-' || cp == '.' || cp == '·'
                || Character.isSpaceChar(cp);
    }
}
