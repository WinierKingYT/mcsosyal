package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VanillaCommandGuardTest {

    @Test
    void blocksNamespacedVanillaChatCommands() {
        assertTrue(VanillaCommandGuard.isBlocked("/minecraft:me dans ediyor"));
        assertTrue(VanillaCommandGuard.isBlocked("/Minecraft:MSG Ahmet selam"));
        assertTrue(VanillaCommandGuard.isBlocked("/minecraft:tell Ahmet x"));
        assertTrue(VanillaCommandGuard.isBlocked("/minecraft:w Ahmet x"));
        assertTrue(VanillaCommandGuard.isBlocked("/minecraft:teammsg x"));
        assertTrue(VanillaCommandGuard.isBlocked("/minecraft:tm"));
    }

    @Test
    void allowsOurCommandsAndOtherVanillaOnes() {
        assertFalse(VanillaCommandGuard.isBlocked("/me dans ediyor"));
        assertFalse(VanillaCommandGuard.isBlocked("/msg Ahmet selam"));
        assertFalse(VanillaCommandGuard.isBlocked("/rpg:msg Ahmet selam"));
        assertFalse(VanillaCommandGuard.isBlocked("/minecraft:help"));
        assertFalse(VanillaCommandGuard.isBlocked("/minecraft:memory"));
    }
}
