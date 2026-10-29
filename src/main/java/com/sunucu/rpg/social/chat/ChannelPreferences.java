package com.sunucu.rpg.social.chat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyuncunun yazdığı kanal ve gizlediği kanallar (/sosyal → Kanallar). Bukkit'e bağımlı değil, test edilebilir.
 * Async sohbet thread'inden de okunur.
 */
// shortcut: tercihler bellekte, yeniden başlatınca herkes global'e döner; veritabanı gelince kalıcı yap.
public final class ChannelPreferences {

    private final Map<UUID, String> active = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> hidden = new ConcurrentHashMap<>();

    public String active(UUID player) {
        return active.getOrDefault(player, ChatChannel.GLOBAL);
    }

    /** Yazılan kanal gizli olamaz: seçilen kanal görünür olur. */
    public void setActive(UUID player, String channel) {
        active.put(player, channel);
        hiddenOf(player).remove(channel);
    }

    public boolean isHidden(UUID player, String channel) {
        return hidden.getOrDefault(player, Set.of()).contains(channel);
    }

    /** @return true ise kanal artık gizli. Yazılan kanal gizlenemez: false döner, değişmez. */
    public boolean toggleHidden(UUID player, String channel) {
        if (channel.equals(active(player))) {
            return false;
        }
        Set<String> set = hiddenOf(player);
        if (set.remove(channel)) {
            return false;
        }
        set.add(channel);
        return true;
    }

    private Set<String> hiddenOf(UUID player) {
        return hidden.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet());
    }
}
