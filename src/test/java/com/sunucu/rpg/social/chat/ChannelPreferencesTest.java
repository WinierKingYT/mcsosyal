package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunucu.rpg.social.SocialStore;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelPreferencesTest {

    private final ChannelPreferences prefs = new ChannelPreferences(SocialStore.NONE);
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
    void loadRestoresSavedChoices() {
        prefs.load(player, "trade", Set.of("help"));
        assertEquals("trade", prefs.active(player));
        assertTrue(prefs.isHidden(player, "help"));
        prefs.unload(player);
        assertEquals(ChatChannel.GLOBAL, prefs.active(player));
        assertFalse(prefs.isHidden(player, "help"));
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
