package com.maru.musiclive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BigoJoinParser {
    private static final int MAX_RESULTS = 20;

    private static final Pattern ENGLISH = Pattern.compile(
            "(?iu)^([\\p{L}\\p{N}_·.\\- ]{1,40}?)\\s+"
                    + "(?:has\\s+)?(?:joined|entered|came\\s+into)"
                    + "\\s+(?:(?:the|this)\\s+)?(?:live|room|broadcast|stream)"
                    + "\\s*[.!?]?$");

    private static final Pattern INDONESIAN = Pattern.compile(
            "(?iu)^([\\p{L}\\p{N}_·.\\- ]{1,40}?)\\s+"
                    + "(?:telah\\s+)?(?:bergabung|masuk)"
                    + "(?:\\s+ke)?"
                    + "\\s+(?:siaran\\s+langsung|live|room)"
                    + "\\s*[.!?]?$");

    private static final Pattern VIETNAMESE = Pattern.compile(
            "(?iu)^([\\p{L}\\p{N}_·.\\- ]{1,40}?)\\s+"
                    + "(?:đã\\s+)?(?:tham\\s+gia|vào)"
                    + "\\s+(?:phòng\\s+live|phòng|live)"
                    + "\\s*[.!?]?$");

    private static final Pattern JAPANESE = Pattern.compile(
            "^([\\p{L}\\p{N}_·.\\- ]{1,40}?)"
                    + "(?:さん)?(?:が)?\\s*"
                    + "(?:入室しました|参加しました|"
                    + "ライブ(?:配信)?に参加しました)\\s*[。.!?]?$");

    private static final String[] KOREAN_KEYWORDS = {
            "입장", "들어왔", "들어오셨", "참여했",
            "시청을시작", "방문했"
    };

    private static final String[] CHINESE_KEYWORDS = {
            "进入了你的直播间", "进入你的直播间",
            "加入了你的直播间", "加入你的直播间",
            "进入直播间", "加入直播间", "进入直播",
            "进来了"
    };

    public List<JoinEvent> parseAll(String input) {
        String normalized = TextNormalizer.normalize(input);
        if (normalized.isEmpty()) return Collections.emptyList();

        List<String> candidates = candidateLines(normalized);
        List<JoinEvent> output = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();

        for (String candidate : candidates) {
            addKorean(candidate, output, dedupe);
            addChinese(candidate, output, dedupe);
            addRegex(candidate, ENGLISH, "en", output, dedupe);
            addRegex(candidate, JAPANESE, "ja", output, dedupe);
            addRegex(candidate, INDONESIAN, "id", output, dedupe);
            addRegex(candidate, VIETNAMESE, "vi", output, dedupe);
            if (output.size() >= MAX_RESULTS) break;
        }
        return output;
    }

    private static List<String> candidateLines(String text) {
        String[] raw = text.split("\n");
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String line = raw[i].trim();
            if (!line.isEmpty()) candidates.add(line);

            if (i + 1 < raw.length) {
                String next = raw[i + 1].trim();
                if (!line.isEmpty() && !next.isEmpty()
                        && standaloneJoinPhrase(next)) {
                    candidates.add(line + " " + next);
                }
            }
            if (i + 2 < raw.length) {
                String next = raw[i + 1].trim();
                String third = raw[i + 2].trim();
                String phrase = next + " " + third;
                if (!line.isEmpty() && !next.isEmpty() && !third.isEmpty()
                        && standaloneJoinPhrase(phrase)) {
                    candidates.add(line + " " + phrase);
                }
            }
        }
        if (candidates.isEmpty()) candidates.add(text);
        return candidates;
    }

    private static boolean standaloneJoinPhrase(String value) {
        String compact = TextNormalizer.normalize(value).trim();
        String lower = compact.toLowerCase(Locale.ROOT);
        if (lower.matches("^(?:has\\s+)?(?:joined|entered|came\\s+into)"
                + "\\s+(?:(?:the|this)\\s+)?(?:live|room|broadcast|stream)[.!?]?$")) {
            return true;
        }
        if (lower.matches("^(?:telah\\s+)?(?:bergabung|masuk)(?:\\s+ke)?"
                + "\\s+(?:siaran\\s+langsung|live|room)[.!?]?$")) {
            return true;
        }
        if (lower.matches("^(?:đã\\s+)?(?:tham\\s+gia|vào)"
                + "\\s+(?:phòng\\s+live|phòng|live)[.!?]?$")) {
            return true;
        }
        String noSpace = compact.replaceAll("\\s+", "");
        if (noSpace.matches("^(?:님(?:이|께서)?)?"
                + "(?:입장(?:했습니다|하였습니다|하셨습니다)|"
                + "들어왔습니다|들어오셨습니다|참여했습니다|"
                + "시청을시작했습니다|방문했습니다)[.!?。！？]?$")) {
            return true;
        }
        for (String keyword : CHINESE_KEYWORDS) {
            if (noSpace.equals(keyword)) return true;
        }
        return noSpace.matches("^(?:さん)?(?:が)?"
                + "(?:入室しました|参加しました|ライブ(?:配信)?に参加しました)"
                + "[。.!?]?$");
    }

    private static void addKorean(
            String line,
            List<JoinEvent> output,
            Set<String> dedupe) {
        String compact = line.replaceAll("\\s+", "");
        int marker = compact.indexOf("님");
        if (marker < 1) return;

        String after = compact.substring(marker + 1);
        boolean joined = false;
        for (String keyword : KOREAN_KEYWORDS) {
            if (after.contains(keyword)) {
                joined = true;
                break;
            }
        }
        if (!joined) return;

        int sourceMarker = indexOfKoreanNim(line);
        if (sourceMarker < 1) return;

        String nickname = tailNickname(
                line.substring(0, sourceMarker));
        addEvent(
                nickname,
                line,
                "ko",
                output,
                dedupe);
    }

    private static int indexOfKoreanNim(String line) {
        Matcher matcher = Pattern.compile(
                "님(?:이|께서)?",
                Pattern.UNICODE_CASE).matcher(line);
        return matcher.find() ? matcher.start() : -1;
    }

    private static void addChinese(
            String line,
            List<JoinEvent> output,
            Set<String> dedupe) {
        CompactLine compactLine = CompactLine.from(line);
        String compact = compactLine.text;
        for (String keyword : CHINESE_KEYWORDS) {
            int index = compact.indexOf(keyword);
            if (index <= 0) continue;

            // Match the event phrase with whitespace removed, but take the
            // nickname from the original OCR line. This keeps legitimate
            // spacing in names such as "小 雨" while still recognizing
            // "进 入 直 播 间" and full-width spaces.
            int originalTriggerStart = compactLine.originalOffset(index);
            String nickname = tailNickname(
                    line.substring(0, originalTriggerStart));
            addEvent(
                    nickname,
                    line,
                    "zh",
                    output,
                    dedupe);
            return;
        }
    }

    private static final class CompactLine {
        final String text;
        final int[] originalOffsets;

        private CompactLine(String text, int[] originalOffsets) {
            this.text = text;
            this.originalOffsets = originalOffsets;
        }

        static CompactLine from(String value) {
            String source = value == null ? "" : value;
            StringBuilder compact = new StringBuilder(source.length());
            int[] offsets = new int[source.length()];
            int count = 0;
            for (int i = 0; i < source.length();) {
                int codePoint = source.codePointAt(i);
                int charCount = Character.charCount(codePoint);
                if (!Character.isWhitespace(codePoint)
                        && !Character.isSpaceChar(codePoint)) {
                    for (int j = 0; j < charCount; j++) {
                        compact.append(source.charAt(i + j));
                        offsets[count++] = i + j;
                    }
                }
                i += charCount;
            }
            int[] used = new int[count];
            System.arraycopy(offsets, 0, used, 0, count);
            return new CompactLine(compact.toString(), used);
        }

        int originalOffset(int compactIndex) {
            if (compactIndex < 0 || compactIndex >= originalOffsets.length) {
                return 0;
            }
            return originalOffsets[compactIndex];
        }
    }

    private static void addRegex(
            String line,
            Pattern pattern,
            String language,
            List<JoinEvent> output,
            Set<String> dedupe) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find() && output.size() < MAX_RESULTS) {
            String nickname = tailNickname(matcher.group(1));
            addEvent(
                    nickname,
                    matcher.group(0),
                    language,
                    output,
                    dedupe);
        }
    }

    private static String tailNickname(String prefix) {
        String value = TextNormalizer.normalize(prefix);
        if (value.isEmpty()) return "";

        String[] pieces = value.split(
                "[\\n|:：>▶►]+");
        value = pieces[pieces.length - 1].trim();

        value = value.replaceAll(
                "^(?:알림|입장|시스템|notification|system)\\s*",
                "");
        value = value.replaceAll(
                "^[^\\p{L}\\p{N}_]+",
                "");

        if (value.length() > 48) {
            value = value.substring(value.length() - 48);
        }
        return TextNormalizer.sanitizeNickname(value);
    }

    private static void addEvent(
            String nickname,
            String raw,
            String language,
            List<JoinEvent> output,
            Set<String> dedupe) {
        if (nickname == null || nickname.isEmpty()) return;
        String lowerName = nickname.toLowerCase(Locale.ROOT);
        if (lowerName.equals("i") || lowerName.equals("we")
                || lowerName.equals("you") || lowerName.equals("he")
                || lowerName.equals("she") || lowerName.equals("나")
                || lowerName.equals("저")) return;
        if (output.size() >= MAX_RESULTS) return;

        String key = lowerName
                + "|" + language;
        if (!dedupe.add(key)) return;

        output.add(new JoinEvent(
                nickname,
                raw == null ? "" : raw.trim(),
                language));
    }
}
