package com.sunucu.rpg.social.chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Süreli sohbet susturması (şimdilik sadece spam yüzünden otomatik). Susturulan oyuncu sohbete ve özel mesaja
 * yazamaz. Bukkit'e bağımlı değil, test edilebilir; async sohbetten de okunur.
 */
// shortcut: susturmalar bellekte, yeniden başlatınca kalkar; moderasyon ve veritabanı gelince kalıcı yap.
public final class ChatMutes {

    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    public void mute(UUID player, long untilMillis) {
        mutedUntil.put(player, untilMillis);
    }

    /** @return true ise susturulmuştu. */
    public boolean unmute(UUID player) {
        return mutedUntil.remove(player) != null;
    }

    /** Kalan süre; susturulmamışsa ya da süresi dolduysa 0. */
    public long remainingMillis(UUID player, long now) {
        Long until = mutedUntil.get(player);
        if (until == null) {
            return 0;
        }
        if (until <= now) {
            mutedUntil.remove(player, until);
            return 0;
        }
        return until - now;
    }
}
