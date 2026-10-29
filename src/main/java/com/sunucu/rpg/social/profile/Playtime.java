package com.sunucu.rpg.social.profile;

import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

/**
 * Oynama süresi Minecraft'ın kendi istatistiğinden (PLAY_ONE_MINUTE, tick cinsinden) gelir: ayrı sayaç yok,
 * çevrimdışı oyuncu için de okunur.
 */
// shortcut: AFK geçen süre de sayılıyor; aktif süre ya da haftalık sıralama gerekince kendi sayacını tut.
public final class Playtime {

    private static final long MILLIS_PER_TICK = 50;

    private Playtime() {
    }

    public static long millis(OfflinePlayer player) {
        if (!player.isOnline() && !player.hasPlayedBefore()) {
            return 0;
        }
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) * MILLIS_PER_TICK;
    }
}
