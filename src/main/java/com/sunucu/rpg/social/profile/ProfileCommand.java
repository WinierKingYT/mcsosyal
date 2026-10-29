package com.sunucu.rpg.social.profile;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sunucu.rpg.core.Messages;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** /profil: kendi profilin. /profil <isim>: başkasının (çevrimdışı olabilir, sunucuya en az bir kez girmiş olmalı). */
public final class ProfileCommand {

    private ProfileCommand() {
    }

    public static void register(JavaPlugin plugin, ProfileMenu menu, Messages messages) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("profil")
                        .requires(src -> src.getSender() instanceof Player)
                        .executes(ctx -> {
                            Player viewer = (Player) ctx.getSource().getSender();
                            menu.open(viewer, viewer);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("oyuncu", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    Player viewer = (Player) ctx.getSource().getSender();
                                    String name = StringArgumentType.getString(ctx, "oyuncu");
                                    OfflinePlayer target = find(name);
                                    if (target == null) {
                                        viewer.sendMessage(messages.get("profile-unknown",
                                                Placeholder.unparsed("name", name)));
                                    } else {
                                        menu.open(viewer, target);
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "Oyuncu profili"));
    }

    /** Çevrimiçi ya da sunucunun önbelleğindeki oyuncu; hiç girmemiş isim için ağ isteği yapılmaz. */
    private static OfflinePlayer find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached != null && cached.hasPlayedBefore() ? cached : null;
    }
}
