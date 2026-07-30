package com.maru.musiclive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LyricsCore {
    public static final long DEFAULT_FINAL_LINE_HOLD_MS = 6_000L;

    public static final class Line {
        private final long timeMs;
        private final String text;

        public Line(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }

        public long timeMs() {
            return timeMs;
        }

        public String text() {
            return text;
        }
    }

    private static final Pattern TIME =
            Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LyricsCore() {}

    public static List<Line> parse(InputStream input) throws IOException {
        List<Line> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String row;
            while ((row = reader.readLine()) != null) {
                Matcher matcher = TIME.matcher(row);
                List<Long> times = new ArrayList<>();
                int lastEnd = 0;
                while (matcher.find()) {
                    long min = Long.parseLong(matcher.group(1));
                    long sec = Long.parseLong(matcher.group(2));
                    String frac = matcher.group(3);
                    long ms = 0;
                    if (frac != null) {
                        if (frac.length() == 1) {
                            ms = Long.parseLong(frac) * 100;
                        } else if (frac.length() == 2) {
                            ms = Long.parseLong(frac) * 10;
                        } else {
                            ms = Long.parseLong(frac.substring(0, 3));
                        }
                    }
                    times.add((min * 60 + sec) * 1000 + ms);
                    lastEnd = matcher.end();
                }
                String text = row.substring(
                        Math.min(lastEnd, row.length())).trim();
                for (Long time : times) {
                    out.add(new Line(time, text));
                }
            }
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }

    public static String twoLines(
            List<Line> lines,
            long positionMs) {
        return twoLines(
                lines,
                positionMs,
                DEFAULT_FINAL_LINE_HOLD_MS);
    }

    public static String twoLines(
            List<Line> lines,
            long positionMs,
            long finalLineHoldMs) {
        if (lines == null || lines.isEmpty()) return "";

        int found = latestLineIndex(lines, positionMs);
        if (found < 0) return "";

        Line current = lines.get(found);
        boolean isFinalLine = found == lines.size() - 1;
        if (isFinalLine
                && positionMs - current.timeMs()
                > Math.max(0L, finalLineHoldMs)) {
            return "";
        }

        String first = current.text().trim();
        if (first.isEmpty()) return "";

        String second = "";
        if (!isFinalLine) {
            long untilNext = lines.get(found + 1).timeMs() - positionMs;
            if (untilNext <= 1_500L) {
                second = lines.get(found + 1).text().trim();
            }
        }

        return second.isEmpty()
                ? first
                : first + "\n" + second;
    }

    private static int latestLineIndex(
            List<Line> lines,
            long positionMs) {
        int found = -1;
        int lo = 0;
        int hi = lines.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }
}
