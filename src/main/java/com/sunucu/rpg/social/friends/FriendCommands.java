package com.sunucu.rpg.social.friends;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sunucu.rpg.core.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /arkadaş: paneli açar. /arkadaş ekle|sil|kabul|reddet <oyuncu>. kabul ve reddet sohbetteki butonlardan da
 * çalışır. sil, kabul ve reddet isteği gönderen çevrimdışı olsa da isimle çalışır.
 */
public final class FriendCommands {

    private FriendCommands() {
    }

    public static void register(JavaPlugin plugin, Friends friends, FriendActions actions, FriendsMenu menu,
                                Messages messages) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("arkadaş")
                    .requires(src -> src.getSender() instanceof Player)
                    .executes(ctx -> {
                        menu.open(player(ctx));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("ekle")
                            .then(Commands.argument("oyuncu", ArgumentTypes.player()).executes(ctx -> {
                                actions.request(player(ctx), ctx.getArgument("oyuncu",
                                        PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst());
                                return Command.SINGLE_SUCCESS;
                            })))
                    .then(Commands.literal("sil")
                            .then(byName(messages, id -> friends.friendsOf(id), actions::remove)))
                    .then(Commands.literal("kabul")
                            .then(byName(messages, id -> friends.requestsTo(id), actions::accept)))
                    .then(Commands.literal("reddet")
                            .then(byName(messages, id -> friends.requestsTo(id), actions::deny)))
                    .build(), "Arkadaşlar", List.of("arkadas"));
        });
    }

    /** İsim argümanı: arkadaşlar ya da gelen istekler arasından seçilir, Tab ile tamamlanır. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> byName(
            Messages messages, Function<UUID, Set<UUID>> candidates, BiConsumer<Player, UUID> action) {
        return Commands.argument("oyuncu", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        candidates.apply(player.getUniqueId())
                                .forEach(id -> builder.suggest(FriendActions.nameOf(id)));
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    Player player = player(ctx);
                    String name = StringArgumentType.getString(ctx, "oyuncu");
                    FriendActions.find(candidates.apply(player.getUniqueId()), name).ifPresentOrElse(
                            id -> action.accept(player, id),
                            () -> player.sendMessage(messages.get("friend-unknown", Placeholder.unparsed("name", name))));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return (Player) ctx.getSource().getSender();
    }
}
