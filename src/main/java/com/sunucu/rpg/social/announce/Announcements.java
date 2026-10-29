package com.sunucu.rpg.social.announce;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.chat.Notifications;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Otomatik ipuçları (config → announcements.tips, sırayla, belli aralıkla; oyuncu /sosyal → Bildirimler'den kapatır)
 * ve yetkili duyurusu (/duyuru: sohbet satırı + ekran ortasında yazı + ses, herkese).
 */
public final class Announcements {

    public static final String PERMISSION = "rpg.social.announce";

    private static final long TICKS_PER_SECOND = 20;
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600));

    private final Messages messages;
    private final Notifications notifications;
    private final TipRotation tips;
    private final long intervalSeconds;
    private final Sound sound;

    public Announcements(Messages messages, Notifications notifications, ConfigurationSection config, Sound sound) {
        this.messages = messages;
        this.notifications = notifications;
        this.tips = new TipRotation(config == null ? List.of() : config.getStringList("tips"));
        this.intervalSeconds = config == null ? 0 : config.getLong("interval-seconds", 300);
        this.sound = sound;
    }

    public static Sound sound(ConfigurationSection sounds) {
        String key = sounds == null ? "block.note_block.bell" : sounds.getString("announcement", "block.note_block.bell");
        return Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, 1f);
    }

    /** interval-seconds 0 ya da ipucu yoksa zamanlayıcı kurulmaz. */
    public void start(JavaPlugin plugin) {
        if (intervalSeconds > 0 && !tips.isEmpty()) {
            long ticks = intervalSeconds * TICKS_PER_SECOND;
            Bukkit.getScheduler().runTaskTimer(plugin, this::sendNextTip, ticks, ticks);
        }
    }

    public void register(JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("duyuru")
                        .requires(src -> src.getSender().hasPermission(PERMISSION))
                        .then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(ctx -> {
                            announce(StringArgumentType.getString(ctx, "mesaj"));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(), "Herkese duyuru yap"));
    }

    /** İpuçları sunucu sahibinin config'inden: tıklanabilir bağlantı olabilir. <name> okuyanın adı. */
    private void sendNextTip() {
        Collection<? extends Player> online = Bukkit.getOnlinePlayers();
        if (online.isEmpty()) {
            return;
        }
        String tip = tips.next();
        for (Player player : online) {
            if (notifications.isEnabled(player, Notifications.Type.TIPS)) {
                player.sendMessage(messages.get("announcement-tip", Placeholder.component("message",
                        Messages.parse(tip, Placeholder.unparsed("name", player.getName()),
                                Placeholder.unparsed("online", String.valueOf(online.size()))))));
            }
        }
    }

    /** Yetkili metninde renk çalışır, tıklama/hover çalışmaz (Messages.parseFormatting). İpucu ayarına bakılmaz. */
    public void announce(String text) {
        Component message = Messages.parseFormatting(text);
        Bukkit.broadcast(messages.get("announcement-chat", Placeholder.component("message", message)));
        Title title = Title.title(messages.get("announcement-title"), message, TITLE_TIMES);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(sound);
        }
    }
}
