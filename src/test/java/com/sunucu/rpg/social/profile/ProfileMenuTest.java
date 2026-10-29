package com.sunucu.rpg.social.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProfileMenuTest {

    @Test
    void progressBarFillsTenSquares() {
        assertEquals("□□□□□□□□□□", ProfileMenu.progressBar(0));
        assertEquals("■■■■□□□□□□", ProfileMenu.progressBar(0.4));
        assertEquals("■■■■■■■■■■", ProfileMenu.progressBar(1));
        assertEquals("■■■■■■■■■■", ProfileMenu.progressBar(7), "1'den büyük taşmasın");
        assertEquals("□□□□□□□□□□", ProfileMenu.progressBar(-1));
    }
}
