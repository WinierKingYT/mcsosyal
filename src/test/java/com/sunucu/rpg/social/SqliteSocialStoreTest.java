package com.sunucu.rpg.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sunucu.rpg.core.db.Database;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSocialStoreTest {

    private static final Logger LOG = Logger.getAnonymousLogger();

    private final UUID player = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void unknownPlayerLoadsEmpty(@TempDir Path dir) throws Exception {
        try (Database database = Database.open(dir.resolve("data.db"), Logger.getAnonymousLogger())) {
            assertEquals(SocialStore.PlayerData.EMPTY, SqliteSocialStore.create(database, LOG).load(player));
        }
    }

    @Test
    void savedDataSurvivesReopen(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("data.db");
        try (Database database = Database.open(file, Logger.getAnonymousLogger())) {
            SqliteSocialStore store = SqliteSocialStore.create(database, LOG);
            store.saveSetting(player, SocialStore.ACTIVE_CHANNEL, "trade");
            store.saveSetting(player, SocialStore.HIDDEN_CHANNELS, SocialStore.joinList(Set.of("help", "local")));
            store.saveSetting(player, SocialStore.ACTIVE_CHANNEL, "help");
            store.setIgnored(player, other, true);
            store.setIgnored(player, other, true);
            store.saveMute(player, 123_456);
        }
        // Kapanınca bekleyen yazımlar bitmiş olmalı; yeniden açınca hepsi okunur.
        try (Database database = Database.open(file, Logger.getAnonymousLogger())) {
            SocialStore.PlayerData data = SqliteSocialStore.create(database, LOG).load(player);
            assertEquals("help", data.activeChannel());
            assertEquals(Set.of("help", "local"), data.hiddenChannels());
            assertEquals(Set.of(other), data.ignores());
            assertEquals(123_456, data.mutedUntil());
        }
    }

    @Test
    void friendshipsAndRequestsPersist(@TempDir Path dir) throws Exception {
        UUID third = UUID.randomUUID();
        try (Database database = Database.open(dir.resolve("data.db"), LOG)) {
            SqliteSocialStore store = SqliteSocialStore.create(database, LOG);
            store.addFriendRequest(player, other);
            store.addFriendRequest(third, player);
            store.addFriendRequest(other, player);
            // Arkadaş olunca aradaki iki yönlü istek de silinir; üçüncü kişinin isteği kalır.
            store.addFriendship(player, other);
            SocialStore.PlayerData mine = store.load(player);
            assertEquals(Set.of(other), mine.friends());
            assertEquals(Set.of(third), mine.incomingRequests());
            assertEquals(Set.of(), mine.outgoingRequests());
            assertEquals(Set.of(player), store.load(other).friends());
            assertEquals(Set.of(player), store.load(third).outgoingRequests());

            store.removeFriendship(other, player);
            store.removeFriendRequest(third, player);
            assertEquals(Set.of(), store.load(player).friends());
            assertEquals(Set.of(), store.load(other).friends());
            assertEquals(Set.of(), store.load(player).incomingRequests());
        }
    }

    @Test
    void removingDeletesRows(@TempDir Path dir) throws Exception {
        try (Database database = Database.open(dir.resolve("data.db"), Logger.getAnonymousLogger())) {
            SqliteSocialStore store = SqliteSocialStore.create(database, LOG);
            store.saveSetting(player, SocialStore.ACTIVE_CHANNEL, "trade");
            store.saveSetting(player, SocialStore.ACTIVE_CHANNEL, null);
            store.setIgnored(player, other, true);
            store.setIgnored(player, other, false);
            store.saveMute(player, 999);
            store.saveMute(player, 0);
            SocialStore.PlayerData data = store.load(player);
            assertNull(data.activeChannel());
            assertEquals(Set.of(), data.ignores());
            assertEquals(0, data.mutedUntil());
        }
    }
}
