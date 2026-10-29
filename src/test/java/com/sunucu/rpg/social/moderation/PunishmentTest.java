package com.sunucu.rpg.social.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PunishmentTest {

    private static Punishment of(Punishment.Type type, long expires, long revokedAt) {
        return new Punishment(1, UUID.randomUUID(), "Ahmet", type, "sebep", "Eleven", 100, expires, revokedAt, null);
    }

    @Test
    void timedBanExpires() {
        Punishment ban = of(Punishment.Type.BAN, 1000, 0);
        assertEquals(Punishment.Status.ACTIVE, ban.status(500));
        assertEquals(500, ban.remaining(500));
        assertEquals(Punishment.Status.EXPIRED, ban.status(1000));
        assertEquals(0, ban.remaining(1000));
    }

    @Test
    void permanentAndRevoked() {
        assertEquals(Long.MAX_VALUE, of(Punishment.Type.MUTE, 0, 0).remaining(1_000_000));
        assertEquals(Punishment.Status.REVOKED, of(Punishment.Type.BAN, 0, 200).status(300));
    }

    @Test
    void kickAndWarnAreNeverActive() {
        assertEquals(Punishment.Status.DONE, of(Punishment.Type.KICK, 0, 0).status(0));
        assertEquals(Punishment.Status.DONE, of(Punishment.Type.WARN, 0, 0).status(0));
    }
}
