package com.sunucu.rpg.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationsTest {

    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    void showsTwoLargestUnits() {
        assertEquals("2 gün 3 saat", Durations.format(2 * DAY + 3 * HOUR + 40 * MINUTE));
        assertEquals("3 saat 12 dk", Durations.format(3 * HOUR + 12 * MINUTE + 30_000));
        assertEquals("45 dk", Durations.format(45 * MINUTE));
    }

    @Test
    void dropsZeroSecondUnit() {
        assertEquals("1 gün", Durations.format(DAY + 5 * MINUTE));
        assertEquals("2 saat", Durations.format(2 * HOUR));
    }

    @Test
    void underAMinute() {
        assertEquals("1 dk'dan az", Durations.format(0));
        assertEquals("1 dk'dan az", Durations.format(59_999));
        assertEquals("1 dk", Durations.format(MINUTE));
    }
}
