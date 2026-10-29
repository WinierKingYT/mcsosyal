package com.sunucu.rpg.social.chat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Etiket ve özel mesaj bildirim sesleri; oyuncu her birini ayrı kapatabilir. */
public final class Notifications {

    public enum Type { MENTION, PRIVATE_MESSAGE }

    private final Map<Type, Sound> sounds = new EnumMap<>(Type.class);
    // shortcut: tercihler bellekte, yeniden başlatınca sıfırlanır; ayarlar menüsü ve veritabanı gelince kalıcı yap.
    private final Map<Type, Set<UUID>> muted = new EnumMap<>(Type.class);

    public Notifications(ConfigurationSection soundConfig) {
        sounds.put(Type.MENTION, sound(soundConfig, "mention", "entity.experience_orb.pickup"));
        sounds.put(Type.PRIVATE_MESSAGE, sound(soundConfig, "private-message", "block.note_block.pling"));
        for (Type type : Type.values()) {
            muted.put(type, ConcurrentHashMap.newKeySet());
        }
    }

    /** @return true ise ses artık açık. */
    public boolean toggle(Player player, Type type) {
        Set<UUID> set = muted.get(type);
        if (set.remove(player.getUniqueId())) {
            return true;
        }
        set.add(player.getUniqueId());
        return false;
    }

    public void play(Player player, Type type) {
        if (!muted.get(type).contains(player.getUniqueId())) {
            player.playSound(sounds.get(type));
        }
    }

    private static Sound sound(ConfigurationSection config, String path, String fallback) {
        String key = config == null ? fallback : config.getString(path, fallback);
        return Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f);
    }
}
