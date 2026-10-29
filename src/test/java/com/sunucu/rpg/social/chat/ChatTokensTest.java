package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChatTokensTest {

    @Test
    void detectsItemTokensInBothLanguages() {
        assertTrue(ChatTokens.has(ChatTokens.ITEM, "bak ne düştü [item]"));
        assertTrue(ChatTokens.has(ChatTokens.ITEM, "[ITEM] satılık"));
        assertTrue(ChatTokens.has(ChatTokens.ITEM, "yeni [eşya] geldi"));
        assertTrue(ChatTokens.has(ChatTokens.ITEM, "[EŞYA]"));
        assertTrue(ChatTokens.has(ChatTokens.ITEM, "[esya]"));
    }

    @Test
    void detectsLocationTokens() {
        assertTrue(ChatTokens.has(ChatTokens.LOCATION, "buraya gel [konum]"));
        assertTrue(ChatTokens.has(ChatTokens.LOCATION, "[KONUM]"));
        assertTrue(ChatTokens.has(ChatTokens.LOCATION, "[loc]"));
    }

    @Test
    void ignoresSimilarText() {
        assertFalse(ChatTokens.has(ChatTokens.ITEM, "item satıyorum"));
        assertFalse(ChatTokens.has(ChatTokens.ITEM, "[items]"));
        assertFalse(ChatTokens.has(ChatTokens.ITEM, "[ item ]"));
        assertFalse(ChatTokens.has(ChatTokens.LOCATION, "konum neresi"));
    }
}
