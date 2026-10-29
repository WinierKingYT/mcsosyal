package com.sunucu.rpg.social.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationParserTest {

    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    void parsesSingleUnits() {
        assertEquals(30_000, DurationParser.parse("30s"));
        assertEquals(10 * MINUTE, DurationParser.parse("10m"));
        assertEquals(2 * HOUR, DurationParser.parse("2h"));
        assertEquals(7 * DAY, DurationParser.parse("7d"));
        assertEquals(14 * DAY, DurationParser.parse("2w"));
    }

    @Test
    void parsesTurkishAndCombined() {
        assertEquals(10 * MINUTE, DurationParser.parse("10dk"));
        assertEquals(2 * HOUR, DurationParser.parse("2sa"));
        assertEquals(3 * DAY, DurationParser.parse("3g"));
        assertEquals(DAY + 12 * HOUR, DurationParser.parse("1d12h"));
        assertEquals(DAY + 12 * HOUR, DurationParser.parse("1G12SA"));
    }

    @Test
    void rejectsNonDurations() {
        assertEquals(-1, DurationParser.parse("hile"));
        assertEquals(-1, DurationParser.parse("10"));
        assertEquals(-1, DurationParser.parse("10x"));
        assertEquals(-1, DurationParser.parse("d10"));
        assertEquals(-1, DurationParser.parse("1d hile"));
        assertEquals(-1, DurationParser.parse("0m"));
        assertEquals(-1, DurationParser.parse(""));
    }

    @Test
    void rejectsAbsurdlyLong() {
        assertEquals(-1, DurationParser.parse("999999w"));
    }
}
