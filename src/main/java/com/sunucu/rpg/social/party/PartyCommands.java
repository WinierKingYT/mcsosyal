package com.sunucu.rpg.social.party;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.friends.FriendActions;
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
 * /parti: lobi ya da parti paneli. /parti kur, /parti katıl <lider> (açık parti), /parti ayarlar,
 * /parti etiket [etiket], /parti açıklama [metin] (boş: siler), /parti admin (rpg.party.admin: tüm partiler),
 * /parti davet|kabul|reddet|at|lider <oyuncu>, /parti ayrıl. kabul ve reddet sohbetteki
 * butonlardan da çalışır. Parti sohbeti /p (ChatCommands) ya da /sosyal'dan parti kanalı.
 */
public final class PartyCommands {

    private PartyCommands() {
    }

    public static void register(JavaPlugin plugin, Parties parties, PartyActions actions, PartyMenu menu,
                                Messages messages) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal("parti")
                        .requires(src -> src.getSender() instanceof Player)
                        .executes(ctx -> {
                            menu.open(player(ctx));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("kur").executes(ctx -> {
                            actions.create(player(ctx));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("ayarlar").executes(ctx -> {
                            menu.openSettings(player(ctx));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("katıl").then(openParty(parties, actions, messages)))
                        .then(Commands.literal("katil").then(openParty(parties, actions, messages)))
                        .then(Commands.literal("etiket")
                                .executes(ctx -> own(ctx, parties, messages, (p, id) -> actions.setTag(p, id, null)))
                                .then(Commands.argument("etiket", StringArgumentType.word()).executes(ctx -> own(ctx,
                                        parties, messages, (p, id) -> actions.setTag(p, id,
                                                StringArgumentType.getString(ctx, "etiket"))))))
                        .then(description("açıklama", parties, actions, messages))
                        .then(description("aciklama", parties, actions, messages))
                        .then(Commands.literal("admin")
                                .requires(src -> src.getSender().hasPermission(PartyActions.ADMIN))
                                .executes(ctx -> {
                                    menu.openAdmin(player(ctx));
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("davet")
                                .then(Commands.argument("oyuncu", ArgumentTypes.player()).executes(ctx -> {
                                    actions.invite(player(ctx), ctx.getArgument("oyuncu",
                                            PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst());
                                    return Command.SINGLE_SUCCESS;
                                })))
                        .then(Commands.literal("kabul").then(byName(messages,
                                id -> parties.invitesTo(id, System.currentTimeMillis()), actions::accept)))
                        .then(Commands.literal("reddet").then(byName(messages,
                                id -> parties.invitesTo(id, System.currentTimeMillis()), actions::deny)))
                        .then(Commands.literal("at").then(byName(messages, id -> members(parties, id), actions::kick)))
                        .then(Commands.literal("lider").then(byName(messages, id -> members(parties, id),
                                actions::promote)))
                        .then(Commands.literal("ayrıl").executes(ctx -> leave(ctx, actions)))
                        .then(Commands.literal("ayril").executes(ctx -> leave(ctx, actions)))
                        .build(), "Parti", List.of("party")));
    }

    /** /parti açıklama <metin>; metinsiz: siler. */
    private static LiteralArgumentBuilder<CommandSourceStack> description(String literal, Parties parties,
                                                                         PartyActions actions, Messages messages) {
        return Commands.literal(literal)
                .executes(ctx -> own(ctx, parties, messages, (p, id) -> actions.setDescription(p, id, null)))
                .then(Commands.argument("metin", StringArgumentType.greedyString()).executes(ctx -> own(ctx, parties,
                        messages, (p, id) -> actions.setDescription(p, id, StringArgumentType.getString(ctx, "metin")))));
    }

    /** Oyuncunun kendi partisi üzerinde iş; partisi yoksa söylenir. */
    private static int own(CommandContext<CommandSourceStack> ctx, Parties parties, Messages messages,
                           BiConsumer<Player, Long> action) throws CommandSyntaxException {
        Player player = player(ctx);
        parties.party(player.getUniqueId()).ifPresentOrElse(party -> action.accept(player, party.id()),
                () -> player.sendMessage(messages.get("party-none")));
        return Command.SINGLE_SUCCESS;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx, PartyActions actions)
            throws CommandSyntaxException {
        actions.leave(player(ctx));
        return Command.SINGLE_SUCCESS;
    }

    /** Açık partiye liderinin adıyla katıl; Tab açık partilerin liderlerini önerir. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> openParty(Parties parties, PartyActions actions,
                                                                             Messages messages) {
        return Commands.argument("lider", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    parties.openParties().forEach(party -> builder.suggest(FriendActions.nameOf(party.leader())));
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    Player player = player(ctx);
                    String name = StringArgumentType.getString(ctx, "lider");
                    parties.openParties().stream()
                            .filter(party -> FriendActions.nameOf(party.leader()).equalsIgnoreCase(name))
                            .findFirst()
                            .ifPresentOrElse(party -> actions.joinOpen(player, party.id()),
                                    () -> player.sendMessage(messages.get("party-unknown",
                                            Placeholder.unparsed("name", name))));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static Set<UUID> members(Parties parties, UUID player) {
        return parties.party(player).map(party -> Set.copyOf(party.members())).orElse(Set.of());
    }

    /** İsim argümanı: davet edenler ya da parti üyeleri arasından seçilir, Tab ile tamamlanır. */
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
                            () -> player.sendMessage(messages.get("party-unknown", Placeholder.unparsed("name", name))));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static Player player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return (Player) ctx.getSource().getSender();
    }
}
