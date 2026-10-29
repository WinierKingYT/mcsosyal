package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import java.util.Locale;
import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * /me, /msg gibi komutları bizimkiyle değiştirdik ama Minecraft'ın kendileri "minecraft:" önekiyle hâlâ çalışıyor
 * ve sohbet filtresini atlatıyordu. Oyuncu bu etiketleri yazınca komut çalışmadan engellenir.
 */
public final class VanillaCommandGuard implements Listener {

    private static final Set<String> BLOCKED = Set.of(
            "minecraft:me", "minecraft:msg", "minecraft:tell", "minecraft:w",
            "minecraft:teammsg", "minecraft:tm");

    private final Messages messages;

    public VanillaCommandGuard(Messages messages) {
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (isBlocked(event.getMessage())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("command-disabled"));
        }
    }

    /** "/Minecraft:MSG Ahmet selam" gibi büyük/küçük harf ve baştaki boşluk farklarını da yakalar. */
    static boolean isBlocked(String commandLine) {
        String line = commandLine.strip();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        int space = line.indexOf(' ');
        String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        return BLOCKED.contains(label);
    }
}
