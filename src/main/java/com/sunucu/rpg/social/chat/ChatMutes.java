package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.social.SocialStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sohbet susturması. Susturulan oyuncu sohbete ve özel mesaja yazamaz. Gizli susturulan yazabilir ama mesajını
 * sadece kendisi (ve yetkililer) görür. Çıkıp girmek ya da sunucu yeniden başlamak susturmayı kaldırmaz (SocialStore).
 * Bukkit'e bağımlı değil, test edilebilir; async sohbetten de okunur.
 */
public final class ChatMutes {

    /** Kalıcı susturmanın bitiş zamanı. */
    public static final long PERMANENT = Long.MAX_VALUE;

    private final SocialStore store;
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> shadowUntil = new ConcurrentHashMap<>();

    public ChatMutes(SocialStore store) {
        this.store = store;
    }

    public void mute(UUID player, long untilMillis) {
        mutedUntil.put(player, untilMillis);
        store.saveMute(player, untilMillis);
    }

    public void shadowMute(UUID player, long untilMillis) {
        shadowUntil.put(player, untilMillis);
        store.saveSetting(player, SocialStore.SHADOW_MUTED_UNTIL, String.valueOf(untilMillis));
    }

    /** Normal ve gizli susturmanın ikisini de kaldırır. @return true ise susturulmuştu. */
    public boolean unmute(UUID player) {
        store.saveMute(player, 0);
        store.saveSetting(player, SocialStore.SHADOW_MUTED_UNTIL, null);
        boolean muted = mutedUntil.remove(player) != null;
        return shadowUntil.remove(player) != null || muted;
    }

    public void load(UUID player, long untilMillis, long shadowUntilMillis) {
        if (untilMillis > 0) {
            mutedUntil.put(player, untilMillis);
        }
        if (shadowUntilMillis > 0) {
            shadowUntil.put(player, shadowUntilMillis);
        }
    }

    public void unload(UUID player) {
        mutedUntil.remove(player);
        shadowUntil.remove(player);
    }

    public boolean isShadowMuted(UUID player, long now) {
        Long until = shadowUntil.get(player);
        if (until == null) {
            return false;
        }
        if (until <= now) {
            if (shadowUntil.remove(player, until)) {
                store.saveSetting(player, SocialStore.SHADOW_MUTED_UNTIL, null);
            }
            return false;
        }
        return true;
    }

    /** Kalan süre; susturulmamışsa ya da süresi dolduysa 0. */
    public long remainingMillis(UUID player, long now) {
        Long until = mutedUntil.get(player);
        if (until == null) {
            return 0;
        }
        if (until <= now) {
            if (mutedUntil.remove(player, until)) {
                store.saveMute(player, 0);
            }
            return 0;
        }
        return until - now;
    }
}
