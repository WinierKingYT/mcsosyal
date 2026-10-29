package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ItemShareTest {

    @Test
    void detectsItemTokensInBothLanguages() {
        assertTrue(ItemShare.hasToken("bak ne düştü [item]"));
        assertTrue(ItemShare.hasToken("[ITEM] satılık"));
        assertTrue(ItemShare.hasToken("yeni [eşya] geldi"));
        assertTrue(ItemShare.hasToken("[EŞYA]"));
        assertTrue(ItemShare.hasToken("[esya]"));
    }

    @Test
    void ignoresSimilarText() {
        assertFalse(ItemShare.hasToken("item satıyorum"));
        assertFalse(ItemShare.hasToken("[items]"));
        assertFalse(ItemShare.hasToken("[ item ]"));
    }
}
