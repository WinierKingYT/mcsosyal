package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatFilterTest {

    private final ChatFilter filter = new ChatFilter(2000, 6, 0.7, List.of("salak", "amk"));
    private final UUID player = UUID.randomUUID();

    @Test
    void catchesBannedWordTricks() {
        assertTrue(filter.containsBannedWord("sen SALAK mısın"));
        assertTrue(filter.containsBannedWord("s.a.l.a.k"));
        assertTrue(filter.containsBannedWord("saaalaaak"));
        assertTrue(filter.containsBannedWord("s4l4k"));
        assertTrue(filter.containsBannedWord("a m k"));
    }

    @Test
    void doesNotFlagInnocentWords() {
        assertFalse(filter.containsBannedWord("ambulans geldi"));
        assertFalse(filter.containsBannedWord("salakça değil, salatalık"));
        assertFalse(filter.containsBannedWord("a b c merhaba"));
    }

    @Test
    void normalizesTurkishLetters() {
        assertEquals("isik", ChatFilter.normalize("IŞIK"));
        assertEquals("cigoru", ChatFilter.normalize("çığörü"));
        assertEquals("istanbul", ChatFilter.normalize("İstanbul"));
    }

    @Test
    void enforcesCooldownAndDuplicates() {
        assertEquals(ChatFilter.Verdict.OK, filter.check(player, "selam", 0).verdict());
        assertEquals(ChatFilter.Verdict.COOLDOWN, filter.check(player, "naber", 1000).verdict());
        assertEquals(ChatFilter.Verdict.DUPLICATE, filter.check(player, "SELAM!!", 3000).verdict());
        assertEquals(ChatFilter.Verdict.OK, filter.check(player, "selam", 40_000).verdict());
    }

    @Test
    void lowersShoutingWithTurkishRules() {
        assertEquals("ışık geldi", filter.fixCaps("IŞIK GELDİ"));
        assertEquals("Selam GG", filter.fixCaps("Selam GG"));
    }
}
