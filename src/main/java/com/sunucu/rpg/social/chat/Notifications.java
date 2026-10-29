package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.SocialStore;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Etiket, özel mesaj ve arkadaş bildirimleri (ses, etikette ekran yazısı); oyuncu her birini ayrı kapatabilir. */
public final class Notifications {

    /** TIPS: otomatik ipuçları; sesi yok, sadece açılıp kapanır. */
    public enum Type { MENTION, PRIVATE_MESSAGE, FRIEND, TIPS }

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400));

    private final Messages messages;
    private final SocialStore store;
    private final long mentionCooldownMillis;
    private final Map<Type, Sound> sounds = new EnumMap<>(Type.class);
    // Bellekte; değişince SocialStore'a yazılır, girişte load ile gelir.
    private final Map<Type, Set<UUID>> muted = new EnumMap<>(Type.class);
    private final Map<UUID, Long> lastMention = new ConcurrentHashMap<>();

    public Notifications(ConfigurationSection soundConfig, Messages messages, SocialStore store,
                         long mentionCooldownSeconds) {
        this.messages = messages;
        this.store = store;
        this.mentionCooldownMillis = mentionCooldownSeconds * 1000;
        sounds.put(Type.MENTION, sound(soundConfig, "mention", "entity.experience_orb.pickup"));
        sounds.put(Type.PRIVATE_MESSAGE, sound(soundConfig, "private-message", "block.note_block.pling"));
        sounds.put(Type.FRIEND, sound(soundConfig, "friend", "block.note_block.chime"));
        for (Type type : Type.values()) {
            muted.put(type, ConcurrentHashMap.newKeySet());
        }
    }

    /** @return true ise bildirim artık açık. */
    public boolean toggle(Player player, Type type) {
        Set<UUID> set = muted.get(type);
        boolean enabled = set.remove(player.getUniqueId());
        if (!enabled) {
            set.add(player.getUniqueId());
        }
        Set<String> off = new HashSet<>();
        for (Type each : Type.values()) {
            if (muted.get(each).contains(player.getUniqueId())) {
                off.add(each.name());
            }
        }
        store.saveSetting(player.getUniqueId(), SocialStore.MUTED_NOTIFICATIONS, SocialStore.joinList(off));
        return enabled;
    }

    /** Kayıtta bilinmeyen tür adı (eski sürümden kalma) atlanır. */
    public void load(UUID player, Set<String> mutedTypes) {
        for (Type type : Type.values()) {
            if (mutedTypes.contains(type.name())) {
                muted.get(type).add(player);
            } else {
                muted.get(type).remove(player);
            }
        }
    }

    public boolean isEnabled(Player player, Type type) {
        return !muted.get(type).contains(player.getUniqueId());
    }

    public void play(Player player, Type type) {
        Sound sound = sounds.get(type);
        if (sound != null && !muted.get(type).contains(player.getUniqueId())) {
            player.playSound(sound);
        }
    }

    /**
     * Etiketlenen oyuncuya ses ve ekranın ortasında "X senden bahsetti" yazısı. Aynı oyuncuya bekleme süresi
     * dolmadan tekrar gitmez; art arda etiketleyerek ekranı kilitlemek mümkün olmasın. Ana thread'den çağrılır.
     */
    public void mention(Player target, Player sender) {
        if (muted.get(Type.MENTION).contains(target.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastMention.get(target.getUniqueId());
        if (last != null && now - last < mentionCooldownMillis) {
            return;
        }
        lastMention.put(target.getUniqueId(), now);
        target.playSound(sounds.get(Type.MENTION));
        target.showTitle(Title.title(
                messages.get("mention-title", Placeholder.unparsed("name", sender.getName())),
                messages.get("mention-subtitle", Placeholder.unparsed("name", sender.getName())),
                TITLE_TIMES));
    }

    public void unload(UUID player) {
        lastMention.remove(player);
        muted.values().forEach(set -> set.remove(player));
    }

    private static Sound sound(ConfigurationSection config, String path, String fallback) {
        String key = config == null ? fallback : config.getString(path, fallback);
        return Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f);
    }
}
