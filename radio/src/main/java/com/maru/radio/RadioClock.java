package com.maru.radio;

import java.util.Calendar;

/**
 * Pure Java local-clock calculations, independently testable without Android.
 * Uses the caller's device timezone, including calendar day rollover.
 */
public final class RadioClock {
    private RadioClock() {}

    public static long nextOccurrence(Calendar now, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            throw new IllegalArgumentException("시각은 00:00~23:59이어야 합니다.");
        Calendar selected = (Calendar) now.clone();
        selected.set(Calendar.HOUR_OF_DAY, hour);
        selected.set(Calendar.MINUTE, minute);
        selected.set(Calendar.SECOND, 0);
        selected.set(Calendar.MILLISECOND, 0);
        if (!selected.after(now)) selected.add(Calendar.DAY_OF_MONTH, 1);
        return selected.getTimeInMillis();
    }

    public static long endAfter(Calendar now, long startAt, int hour, int minute) {
        if (startAt <= 0L) throw new IllegalArgumentException("시작 시각이 필요합니다.");
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59)
            throw new IllegalArgumentException("시각은 00:00~23:59이어야 합니다.");
        Calendar end = (Calendar) now.clone();
        end.set(Calendar.HOUR_OF_DAY, hour);
        end.set(Calendar.MINUTE, minute);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);
        while (end.getTimeInMillis() <= startAt) {
            end.add(Calendar.DAY_OF_MONTH, 1);
        }
        return end.getTimeInMillis();
    }
}
