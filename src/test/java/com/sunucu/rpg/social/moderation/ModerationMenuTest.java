package com.sunucu.rpg.social.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModerationMenuTest {

    @Test
    void wrapsOnWordBoundaries() {
        assertEquals(List.of("bir iki", "üç dört"), ModerationMenu.wrap("bir iki üç dört", 7));
    }

    @Test
    void cutsWordsLongerThanWidth() {
        assertEquals(List.of("abcde", "fghij", "k x"), ModerationMenu.wrap("abcdefghijk x", 5));
    }

    @Test
    void wrapOfBlankIsEmpty() {
        assertTrue(ModerationMenu.wrap("   ", 10).isEmpty());
    }

    @Test
    void previewShortensLongMessages() {
        assertEquals("kısa", ModerationMenu.preview("kısa"));
        String preview = ModerationMenu.preview("a".repeat(100));
        assertEquals(32, preview.length());
        assertTrue(preview.endsWith("…"));
    }
}
