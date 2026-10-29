package com.sunucu.rpg.social.friends;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunucu.rpg.social.SocialStore;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FriendsTest {

    private final Friends friends = new Friends(SocialStore.NONE, 2);
    private final UUID ali = UUID.randomUUID();
    private final UUID ayse = UUID.randomUUID();
    private final UUID can = UUID.randomUUID();

    @BeforeEach
    void loadPlayers() {
        for (UUID player : Set.of(ali, ayse, can)) {
            friends.load(player, Set.of(), Set.of(), Set.of());
        }
    }

    @Test
    void requestThenAccept() {
        assertEquals(Friends.RequestResult.SENT, friends.request(ali, ayse));
        assertEquals(Set.of(ali), friends.requestsTo(ayse));
        assertEquals(Friends.RequestResult.ALREADY_SENT, friends.request(ali, ayse));
        assertEquals(Friends.AcceptResult.ACCEPTED, friends.accept(ayse, ali));
        assertTrue(friends.areFriends(ali, ayse));
        assertTrue(friends.areFriends(ayse, ali));
        assertEquals(Set.of(), friends.requestsTo(ayse));
        assertEquals(Friends.RequestResult.ALREADY_FRIENDS, friends.request(ayse, ali));
    }

    @Test
    void mutualRequestBecomesFriendship() {
        friends.request(ali, ayse);
        assertEquals(Friends.RequestResult.ACCEPTED, friends.request(ayse, ali));
        assertTrue(friends.areFriends(ali, ayse));
    }

    @Test
    void cannotBefriendYourself() {
        assertEquals(Friends.RequestResult.SELF, friends.request(ali, ali));
    }

    @Test
    void denyRemovesRequestOnly() {
        friends.request(ali, ayse);
        assertTrue(friends.deny(ayse, ali));
        assertFalse(friends.deny(ayse, ali));
        assertFalse(friends.areFriends(ali, ayse));
        assertEquals(Friends.AcceptResult.NO_REQUEST, friends.accept(ayse, ali));
        // Reddedilince tekrar istek gönderilebilir.
        assertEquals(Friends.RequestResult.SENT, friends.request(ali, ayse));
    }

    @Test
    void removeEndsFriendshipOnBothSides() {
        friends.request(ali, ayse);
        friends.accept(ayse, ali);
        assertTrue(friends.remove(ali, ayse));
        assertFalse(friends.areFriends(ayse, ali));
        assertFalse(friends.remove(ali, ayse));
    }

    @Test
    void respectsFriendLimit() {
        UUID dora = UUID.randomUUID();
        friends.load(dora, Set.of(), Set.of(), Set.of());
        friends.request(ali, ayse);
        friends.accept(ayse, ali);
        friends.request(ali, can);
        friends.accept(can, ali);
        assertEquals(Friends.RequestResult.YOUR_LIMIT, friends.request(ali, dora));
        assertEquals(Friends.RequestResult.THEIR_LIMIT, friends.request(dora, ali));
    }

    @Test
    void acceptWorksWhileRequesterIsOffline() {
        friends.request(ali, ayse);
        friends.unload(ali);
        assertEquals(Friends.AcceptResult.ACCEPTED, friends.accept(ayse, ali));
        assertEquals(Set.of(ali), friends.friendsOf(ayse));
    }
}
