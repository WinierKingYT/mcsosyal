package com.sunucu.rpg.social.moderation;

import java.util.UUID;

/**
 * Tek bir ceza kaydı. expires 0 ise kalıcı; revokedAt 0 değilse yetkili kaldırmış. KICK ve WARN anlıktır, hiç
 * "aktif" olmaz; geçmişte görünür.
 */
public record Punishment(long id, UUID player, String playerName, Type type, String reason, String issuer,
                         long created, long expires, long revokedAt, String revokedBy) {

    public enum Type { BAN, MUTE, SHADOW_MUTE, KICK, WARN }

    public enum Status { ACTIVE, EXPIRED, REVOKED, DONE }

    /** Süresi olan cezalar: ban ve susturmalar. */
    public boolean isTimed() {
        return type == Type.BAN || type == Type.MUTE || type == Type.SHADOW_MUTE;
    }

    public boolean isPermanent() {
        return expires == 0;
    }

    public Status status(long now) {
        if (revokedAt > 0) {
            return Status.REVOKED;
        }
        if (type == Type.KICK || type == Type.WARN) {
            return Status.DONE;
        }
        return isPermanent() || expires > now ? Status.ACTIVE : Status.EXPIRED;
    }

    /** Kalan süre; kalıcıysa Long.MAX_VALUE, aktif değilse 0. */
    public long remaining(long now) {
        if (status(now) != Status.ACTIVE) {
            return 0;
        }
        return isPermanent() ? Long.MAX_VALUE : expires - now;
    }
}
