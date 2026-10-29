package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelPreferencesTest {

    private final ChannelPreferences prefs = new ChannelPreferences();
    private final UUID player = UUID.randomUUID();

    @Test
    void defaultsToGlobal() {
        assertEquals(ChatChannel.GLOBAL, prefs.active(player));
    }

    @Test
    void hidesAndShowsChannels() {
        assertTrue(prefs.toggleHidden(player, "trade"));
        assertTrue(prefs.isHidden(player, "trade"));
        assertFalse(prefs.toggleHidden(player, "trade"));
        assertFalse(prefs.isHidden(player, "trade"));
    }

    @Test
    void activeChannelCannotBeHiddenAndSelectingUnhides() {
        prefs.toggleHidden(player, "help");
        prefs.setActive(player, "help");
        assertFalse(prefs.isHidden(player, "help"));
        assertFalse(prefs.toggleHidden(player, "help"));
        assertFalse(prefs.isHidden(player, "help"));
    }
}
