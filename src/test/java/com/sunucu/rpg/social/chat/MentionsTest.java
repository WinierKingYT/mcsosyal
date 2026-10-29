package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MentionsTest {

    private static final Map<String, String> ONLINE = Map.of("ahmet", "Ahmet", "ayse_42", "Ayse_42");

    @Test
    void findsExplicitMentionCaseInsensitive() {
        List<Mentions.Span> spans = Mentions.find("@ahmet gel buraya!", ONLINE);
        assertEquals(List.of(new Mentions.Span(0, 6, "Ahmet", true)), spans);
    }

    @Test
    void findsPlainNameAsNonExplicit() {
        List<Mentions.Span> spans = Mentions.find("Ahmet nerede, @Ayse_42?", ONLINE);
        assertEquals(List.of(
                new Mentions.Span(0, 5, "Ahmet", false),
                new Mentions.Span(14, 22, "Ayse_42", true)), spans);
    }

    @Test
    void ignoresPartialNamesOfflinePlayersAndEmails() {
        assertTrue(Mentions.find("@ahm ahmetcan xahmet", ONLINE).isEmpty());
        assertTrue(Mentions.find("@Mehmet çevrimdışı", ONLINE).isEmpty());
        assertTrue(Mentions.find("mail@ahmet.com", ONLINE).isEmpty());
    }
}
