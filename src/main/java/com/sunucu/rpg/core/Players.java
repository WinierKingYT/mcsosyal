package com.sunucu.rpg.core;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** İsimden oyuncu bulma; komutlar ortak kullanır. */
public final class Players {

    private Players() {
    }

    /** Çevrimiçi ya da sunucuya en az bir kez girmiş oyuncu; hiç girmemiş isim için ağ isteği yapılmaz, null döner. */
    public static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }

    public static String name(OfflinePlayer player) {
        return player.getName() == null ? "?" : player.getName();
    }
}
