package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.social.SocialStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Süreli sohbet susturması (şimdilik sadece spam yüzünden otomatik). Susturulan oyuncu sohbete ve özel mesaja
 * yazamaz; çıkıp girmek ya da sunucu yeniden başlamak susturmayı kaldırmaz (SocialStore).
 * Bukkit'e bağımlı değil, test edilebilir; async sohbetten de okunur.
 */
public final class ChatMutes {

    private final SocialStore store;
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    public ChatMutes(SocialStore store) {
        this.store = store;
    }

    public void mute(UUID player, long untilMillis) {
        mutedUntil.put(player, untilMillis);
        store.saveMute(player, untilMillis);
    }

    /** @return true ise susturulmuştu. */
    public boolean unmute(UUID player) {
        store.saveMute(player, 0);
        return mutedUntil.remove(player) != null;
    }

    public void load(UUID player, long untilMillis) {
        if (untilMillis > 0) {
            mutedUntil.put(player, untilMillis);
        }
    }

    public void unload(UUID player) {
        mutedUntil.remove(player);
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
