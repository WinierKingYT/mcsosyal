package com.sunucu.rpg.social.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DetailTest {

    private static final String DEFAULT = "Sebep belirtilmedi";

    @Test
    void durationThenReason() {
        ModerationCommands.Detail detail = ModerationCommands.Detail.parse("7d hile yaptı", DEFAULT);
        assertEquals(7L * 86_400_000, detail.durationMillis());
        assertEquals("hile yaptı", detail.reason());
    }

    @Test
    void noDurationMeansPermanent() {
        ModerationCommands.Detail detail = ModerationCommands.Detail.parse("hile yaptı", DEFAULT);
        assertEquals(0, detail.durationMillis());
        assertEquals("hile yaptı", detail.reason());
    }

    @Test
    void onlyDurationUsesDefaultReason() {
        ModerationCommands.Detail detail = ModerationCommands.Detail.parse("  2h  ", DEFAULT);
        assertEquals(2L * 3_600_000, detail.durationMillis());
        assertEquals(DEFAULT, detail.reason());
    }
}
