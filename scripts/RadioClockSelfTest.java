package com.maru.radio;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** Runs with plain javac/java 17 in GitHub Actions, no Android emulator needed. */
public final class RadioClockSelfTest {
    private static final TimeZone KST = TimeZone.getTimeZone("Asia/Seoul");

    private static Calendar at(int year, int month, int day, int hh, int mm) {
        Calendar c = new GregorianCalendar(KST);
        c.clear();
        c.set(year, month - 1, day, hh, mm);
        return c;
    }

    private static void assertAt(String name, long actual, Calendar expected) {
        if (actual != expected.getTimeInMillis()) {
            throw new AssertionError(name + ": expected " + expected.getTime()
                    + " got " + new java.util.Date(actual));
        }
        System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        Calendar morning = at(2026, 10, 16, 8, 15);
        assertAt("future same day", RadioClock.nextOccurrence(morning, 17, 0),
                at(2026, 10, 16, 17, 0));
        assertAt("past time goes tomorrow", RadioClock.nextOccurrence(morning, 6, 0),
                at(2026, 10, 17, 6, 0));
        assertAt("exact minute rolls tomorrow", RadioClock.nextOccurrence(morning, 8, 15),
                at(2026, 10, 17, 8, 15));
        Calendar late = at(2026, 10, 16, 23, 50);
        long start = RadioClock.nextOccurrence(late, 23, 55);
        assertAt("cross midnight end", RadioClock.endAfter(late, start, 0, 40),
                at(2026, 10, 17, 0, 40));
        assertAt("same clock end rolls next day", RadioClock.endAfter(morning,
                        RadioClock.nextOccurrence(morning, 17, 0), 17, 0),
                at(2026, 10, 17, 17, 0));
        assertAt("next morning start earlier end", RadioClock.endAfter(morning,
                        RadioClock.nextOccurrence(morning, 6, 0), 6, 30),
                at(2026, 10, 17, 6, 30));
        try {
            RadioClock.nextOccurrence(morning, 24, 0);
            throw new AssertionError("out-of-range time did not throw");
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: rejects invalid hour");
        }
        System.out.println("MARU clock rollover self-test: ALL PASS");
    }
}
