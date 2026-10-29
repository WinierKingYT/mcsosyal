package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import java.time.Duration;
import java.util.EnumMap;
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

/** Etiket ve özel mesaj bildirimleri (ses, etikette ekran yazısı); oyuncu her birini ayrı kapatabilir. */
public final class Notifications {

    public enum Type { MENTION, PRIVATE_MESSAGE }

    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400));

    private final Messages messages;
    private final long mentionCooldownMillis;
    private final Map<Type, Sound> sounds = new EnumMap<>(Type.class);
    // shortcut: tercihler bellekte, yeniden başlatınca sıfırlanır; ayarlar menüsü ve veritabanı gelince kalıcı yap.
    private final Map<Type, Set<UUID>> muted = new EnumMap<>(Type.class);
    private final Map<UUID, Long> lastMention = new ConcurrentHashMap<>();

    public Notifications(ConfigurationSection soundConfig, Messages messages, long mentionCooldownSeconds) {
        this.messages = messages;
        this.mentionCooldownMillis = mentionCooldownSeconds * 1000;
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

    public void forget(UUID player) {
        lastMention.remove(player);
    }

    private static Sound sound(ConfigurationSection config, String path, String fallback) {
        String key = config == null ? fallback : config.getString(path, fallback);
        return Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f);
    }
}
