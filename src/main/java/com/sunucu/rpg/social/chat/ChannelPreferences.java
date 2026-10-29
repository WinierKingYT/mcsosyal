package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.social.SocialStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyuncunun yazdığı kanal ve gizlediği kanallar (/sosyal → Kanallar). Bellekte tutulur, değişince SocialStore'a
 * yazılır; girişte load ile gelir, çıkışta unload ile bırakılır. Async sohbet thread'inden de okunur.
 */
public final class ChannelPreferences {

    private final SocialStore store;
    private final Map<UUID, String> active = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> hidden = new ConcurrentHashMap<>();

    public ChannelPreferences(SocialStore store) {
        this.store = store;
    }

    public void load(UUID player, String activeChannel, Set<String> hiddenChannels) {
        if (activeChannel != null) {
            active.put(player, activeChannel);
        }
        Set<String> set = hiddenOf(player);
        set.clear();
        set.addAll(hiddenChannels);
    }

    public void unload(UUID player) {
        active.remove(player);
        hidden.remove(player);
    }

    public String active(UUID player) {
        return active.getOrDefault(player, ChatChannel.GLOBAL);
    }

    /** Yazılan kanal gizli olamaz: seçilen kanal görünür olur. */
    public void setActive(UUID player, String channel) {
        active.put(player, channel);
        store.saveSetting(player, SocialStore.ACTIVE_CHANNEL, channel);
        if (hiddenOf(player).remove(channel)) {
            saveHidden(player);
        }
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
        boolean nowHidden = !set.remove(channel);
        if (nowHidden) {
            set.add(channel);
        }
        saveHidden(player);
        return nowHidden;
    }

    private void saveHidden(UUID player) {
        store.saveSetting(player, SocialStore.HIDDEN_CHANNELS, SocialStore.joinList(Set.copyOf(hiddenOf(player))));
    }

    private Set<String> hiddenOf(UUID player) {
        return hidden.computeIfAbsent(player, id -> ConcurrentHashMap.newKeySet());
    }
}
