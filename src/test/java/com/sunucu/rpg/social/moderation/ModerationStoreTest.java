package com.sunucu.rpg.social.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sunucu.rpg.core.db.Database;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModerationStoreTest {

    private static final Logger LOG = Logger.getAnonymousLogger();
    private final UUID ahmet = UUID.randomUUID();
    private final UUID ayse = UUID.randomUUID();

    @Test
    void activeBanRespectsExpiryAndRevoke(@TempDir Path dir) throws Exception {
        try (Database database = Database.open(dir.resolve("data.db"), LOG)) {
            ModerationStore store = ModerationStore.create(database);
            store.addPunishment(ahmet, "Ahmet", Punishment.Type.BAN, "hile", "Eleven", 100, 1000).get();
            assertTrue(store.activeBan(ahmet, 500).get().isPresent());
            assertFalse(store.activeBan(ahmet, 1000).get().isPresent());

            store.addPunishment(ahmet, "Ahmet", Punishment.Type.BAN, "tekrar", "Eleven", 2000, 0).get();
            assertEquals("tekrar", store.activeBan(ahmet, 5000).get().orElseThrow().reason());
            assertEquals(1, store.revokeActive(ahmet, Punishment.Type.BAN, "Mod", 6000).get());
            assertFalse(store.activeBan(ahmet, 7000).get().isPresent());
            assertFalse(store.activeBan(ayse, 0).get().isPresent());
        }
    }

    @Test
    void historyIsNewestFirstAndKeepsRevoked(@TempDir Path dir) throws Exception {
        try (Database database = Database.open(dir.resolve("data.db"), LOG)) {
            ModerationStore store = ModerationStore.create(database);
            store.addPunishment(ahmet, "Ahmet", Punishment.Type.WARN, "küfür", "Eleven", 100, 0);
            store.addPunishment(ahmet, "Ahmet", Punishment.Type.MUTE, "spam", "Eleven", 200, 0);
            store.revokeActive(ahmet, Punishment.Type.MUTE, "Mod", 300);
            List<Punishment> history = store.history(ahmet, 10).get();
            assertEquals(2, history.size());
            assertEquals(Punishment.Type.MUTE, history.get(0).type());
            assertEquals(Punishment.Status.REVOKED, history.get(0).status(400));
            assertEquals("Mod", history.get(0).revokedBy());
        }
    }

    @Test
    void chatLogKeepsLatestInOrderAndPurges(@TempDir Path dir) throws Exception {
        try (Database database = Database.open(dir.resolve("data.db"), LOG)) {
            ModerationStore store = ModerationStore.create(database);
            for (int i = 1; i <= 5; i++) {
                store.logChat(ahmet, "Ahmet", "global", "mesaj " + i, i * 100L);
            }
            store.logChat(ayse, "Ayse", "global", "başkası", 150);
            List<ModerationStore.ChatLine> last = store.chatLog(ahmet, 3).get();
            assertEquals(List.of("mesaj 3", "mesaj 4", "mesaj 5"), last.stream().map(ModerationStore.ChatLine::message).toList());
            // Silme herkes için: Ahmet'in 3 eski mesajı + Ayşe'nin 150'deki mesajı.
            assertEquals(4, store.purgeChatLog(350).get());
            assertEquals(2, store.chatLog(ahmet, 10).get().size());
        }
    }

    @Test
    void reportsOpenAndClose(@TempDir Path dir) throws Exception {
        try (Database database = Database.open(dir.resolve("data.db"), LOG)) {
            ModerationStore store = ModerationStore.create(database);
            long id = store.addReport(ayse, "Ayse", ahmet, "Ahmet", "küfür etti", 100).get();
            assertEquals(1, store.countOpenReports().get());
            assertEquals("küfür etti", store.openReports(10).get().getFirst().reason());
            assertTrue(store.closeReport(id, "Eleven", 200).get());
            assertFalse(store.closeReport(id, "Eleven", 300).get());
            assertEquals(0, store.countOpenReports().get());
        }
    }
}
