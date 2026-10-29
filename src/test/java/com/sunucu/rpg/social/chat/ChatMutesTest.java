package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunucu.rpg.social.SocialStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatMutesTest {

    private final ChatMutes mutes = new ChatMutes(SocialStore.NONE);
    private final UUID player = UUID.randomUUID();

    @Test
    void muteExpires() {
        mutes.mute(player, 1000);
        assertEquals(600, mutes.remainingMillis(player, 400));
        assertEquals(0, mutes.remainingMillis(player, 1000));
        assertFalse(mutes.unmute(player));
    }

    @Test
    void unmuteLiftsEarly() {
        mutes.mute(player, 10_000);
        assertTrue(mutes.unmute(player));
        assertEquals(0, mutes.remainingMillis(player, 0));
    }
}
