package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatFilterTest {

    private final ChatFilter filter = new ChatFilter(3000, 6, 0.7, List.of("salak", "amk"), List.of("youtube.com"));
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
        assertEquals(ChatFilter.Verdict.OK, check("global", 2000, "selam", 0));
        assertEquals(ChatFilter.Verdict.COOLDOWN, check("global", 2000, "naber", 1000));
        assertEquals(ChatFilter.Verdict.DUPLICATE, check("global", 2000, "SELAM!!", 2500));
        assertEquals(ChatFilter.Verdict.OK, check("global", 2000, "selam", 3000));
    }

    @Test
    void reportsRemainingWaitTime() {
        filter.check(player, "global", 2000, "selam", 0);
        assertEquals(1500, filter.check(player, "global", 2000, "naber", 500).waitMillis());
        assertEquals(500, filter.check(player, "global", 2000, "selam", 2500).waitMillis());
    }

    @Test
    void cooldownIsPerChannel() {
        assertEquals(ChatFilter.Verdict.OK, check("trade", 45_000, "kılıç satıyorum", 0));
        assertEquals(ChatFilter.Verdict.OK, check("global", 2000, "selam", 1000));
        assertEquals(ChatFilter.Verdict.COOLDOWN, check("trade", 45_000, "kalkan da var", 30_000));
        assertEquals(ChatFilter.Verdict.OK, check("trade", 45_000, "kalkan da var", 46_000));
    }

    @Test
    void catchesAdvertisements() {
        assertTrue(filter.containsAdvertisement("gelin 192.168.1.20 çok iyi"));
        assertTrue(filter.containsAdvertisement("123 , 45 , 67 , 89"));
        assertTrue(filter.containsAdvertisement("başkasunucu.com'a gelin"));
        assertTrue(filter.containsAdvertisement("mc.baskasunucu . net"));
        assertTrue(filter.containsAdvertisement("baskasunucu nokta com"));
        assertTrue(filter.containsAdvertisement("baskasunucu(dot)gg"));
        assertEquals(ChatFilter.Verdict.ADVERTISEMENT, filter.checkContent("play.baska.net"));
    }

    @Test
    void allowsNormalTextAndWhitelistedDomains() {
        assertFalse(filter.containsAdvertisement("videom youtube.com'da"));
        assertFalse(filter.containsAdvertisement("sürüm 1.21.11 çıktı"));
        assertFalse(filter.containsAdvertisement("koordinat 100 64 -200"));
        assertFalse(filter.containsAdvertisement("tamam. com da olur"));
        assertFalse(filter.containsAdvertisement("fiyat 2.5 altın"));
    }

    @Test
    void lowersShoutingWithTurkishRules() {
        assertEquals("ışık geldi", filter.fixCaps("IŞIK GELDİ"));
        assertEquals("Selam GG", filter.fixCaps("Selam GG"));
    }

    private ChatFilter.Verdict check(String channel, long cooldown, String text, long now) {
        return filter.check(player, channel, cooldown, text, now).verdict();
    }
}
