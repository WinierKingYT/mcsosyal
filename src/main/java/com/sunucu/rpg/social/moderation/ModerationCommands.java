package com.sunucu.rpg.social.moderation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Players;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Yetkili: /mute /ban <oyuncu> [süre] [sebep], /kick /warn <oyuncu> [sebep], /unmute /unban, /geçmiş, /chatlog,
 * /şikayetler [kapat <no>]. Oyuncu: /şikayet <oyuncu> <sebep>. Yetkili komutları izni olmayana görünmez.
 * Süre verilmezse ceza kalıcıdır (ör. "/ban Ahmet hile"), verilirse süreli ("/ban Ahmet 7d hile").
 */
public final class ModerationCommands {

    public static final String MUTE = "rpg.moderation.mute";
    public static final String BAN = "rpg.moderation.ban";
    public static final String KICK = "rpg.moderation.kick";
    public static final String WARN = "rpg.moderation.warn";
    public static final String HISTORY = "rpg.moderation.history";
    public static final String CHATLOG = "rpg.moderation.chatlog";

    private static final int DEFAULT_CHATLOG_LINES = ModerationMenu.PAGE_SIZE;
    private static final int MAX_CHATLOG_LINES = ModerationMenu.PAGE_SIZE;

    /** Sebep ve süre: ilk kelime süreyse (ör. 7d) süre, gerisi sebep; değilse kalıcı ve hepsi sebep. */
    record Detail(long durationMillis, String reason) {

        static Detail parse(String text, String defaultReason) {
            String trimmed = text.strip();
            int space = trimmed.indexOf(' ');
            String first = space < 0 ? trimmed : trimmed.substring(0, space);
            long duration = DurationParser.parse(first);
            String reason = duration > 0 ? (space < 0 ? "" : trimmed.substring(space + 1).strip()) : trimmed;
            return new Detail(Math.max(duration, 0), reason.isEmpty() ? defaultReason : reason);
        }
    }

    private ModerationCommands() {
    }

    public static void register(JavaPlugin plugin, Moderation moderation, Reports reports, Messages messages,
                                String defaultReason) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(timed("mute", MUTE, messages, defaultReason, moderation::mute).build(),
                    "Oyuncuyu sustur (süre yoksa kalıcı)");
            commands.register(timed("gizlisustur", MUTE, messages, defaultReason, moderation::shadowMute).build(),
                    "Gizlice sustur: mesajlarını sadece kendisi görür (/unmute kaldırır)", List.of("shadowmute"));
            commands.register(timed("ban", BAN, messages, defaultReason, moderation::ban).build(),
                    "Oyuncuyu yasakla (süre yoksa kalıcı)");
            commands.register(target("unmute", MUTE, messages, moderation::unmute).build(), "Susturmayı kaldır");
            commands.register(target("unban", BAN, messages, moderation::unban).build(), "Yasağı kaldır");
            commands.register(Commands.literal("kick")
                    .requires(src -> src.getSender().hasPermission(KICK))
                    .then(Commands.argument("oyuncu", ArgumentTypes.player())
                            .executes(ctx -> kick(ctx, moderation, defaultReason))
                            .then(Commands.argument("sebep", StringArgumentType.greedyString())
                                    .executes(ctx -> kick(ctx, moderation, StringArgumentType.getString(ctx, "sebep")))))
                    .build(), "Oyuncuyu at");
            commands.register(Commands.literal("warn")
                    .requires(src -> src.getSender().hasPermission(WARN))
                    .then(onlineName(Commands.argument("oyuncu", StringArgumentType.word()))
                            .executes(ctx -> withTarget(ctx, messages, t -> moderation.warn(sender(ctx), t, defaultReason)))
                            .then(Commands.argument("sebep", StringArgumentType.greedyString())
                                    .executes(ctx -> withTarget(ctx, messages, t -> moderation.warn(sender(ctx), t,
                                            StringArgumentType.getString(ctx, "sebep"))))))
                    .build(), "Oyuncuyu uyar");
            commands.register(target("geçmiş", HISTORY, messages, moderation::showHistory).build(),
                    "Ceza geçmişi", List.of("gecmis"));
            commands.register(Commands.literal("chatlog")
                    .requires(src -> src.getSender().hasPermission(CHATLOG))
                    .then(onlineName(Commands.argument("oyuncu", StringArgumentType.word()))
                            .executes(ctx -> withTarget(ctx, messages,
                                    t -> moderation.showChatLog(sender(ctx), t, DEFAULT_CHATLOG_LINES)))
                            .then(Commands.argument("sayı", IntegerArgumentType.integer(1, MAX_CHATLOG_LINES))
                                    .executes(ctx -> withTarget(ctx, messages, t -> moderation.showChatLog(sender(ctx), t,
                                            IntegerArgumentType.getInteger(ctx, "sayı"))))))
                    .build(), "Oyuncunun son mesajları");
            commands.register(Commands.literal("şikayet")
                    .requires(src -> src.getSender() instanceof Player)
                    .then(onlineName(Commands.argument("oyuncu", StringArgumentType.word()))
                            .then(Commands.argument("sebep", StringArgumentType.greedyString())
                                    .executes(ctx -> withTarget(ctx, messages, t -> reports.report((Player) sender(ctx), t,
                                            StringArgumentType.getString(ctx, "sebep"))))))
                    .build(), "Bir oyuncuyu yetkililere şikayet et", List.of("sikayet", "report"));
            commands.register(Commands.literal("şikayetler")
                    .requires(src -> src.getSender().hasPermission(Reports.PERMISSION))
                    .executes(ctx -> {
                        reports.list(sender(ctx));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("kapat")
                            .then(Commands.argument("no", LongArgumentType.longArg(1)).executes(ctx -> {
                                reports.close(sender(ctx), LongArgumentType.getLong(ctx, "no"));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .build(), "Açık şikayetler", List.of("sikayetler", "reports"));
        });
    }

    @FunctionalInterface
    private interface Timed {
        void apply(CommandSender issuer, OfflinePlayer target, long durationMillis, String reason);
    }

    @FunctionalInterface
    private interface TargetAction {
        void apply(OfflinePlayer target) throws CommandSyntaxException;
    }

    /** /mute ve /ban: <oyuncu> [süre] [sebep]. Çevrimdışı oyuncu da (sunucuya girmiş olmalı). */
    private static LiteralArgumentBuilder<CommandSourceStack> timed(String name, String permission, Messages messages,
                                                                   String defaultReason, Timed action) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(onlineName(Commands.argument("oyuncu", StringArgumentType.word()))
                        .executes(ctx -> withTarget(ctx, messages, t -> action.apply(sender(ctx), t, 0, defaultReason)))
                        .then(Commands.argument("süre ve sebep", StringArgumentType.greedyString())
                                .executes(ctx -> withTarget(ctx, messages, t -> {
                                    Detail detail = Detail.parse(StringArgumentType.getString(ctx, "süre ve sebep"),
                                            defaultReason);
                                    action.apply(sender(ctx), t, detail.durationMillis(), detail.reason());
                                }))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> target(String name, String permission, Messages messages,
                                                                    BiConsumer<CommandSender, OfflinePlayer> action) {
        return Commands.literal(name)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(onlineName(Commands.argument("oyuncu", StringArgumentType.word()))
                        .executes(ctx -> withTarget(ctx, messages, t -> action.accept(sender(ctx), t))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> onlineName(
            RequiredArgumentBuilder<CommandSourceStack, String> argument) {
        return argument.suggests((ctx, builder) -> {
            Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
            return builder.buildFuture();
        });
    }

    private static int withTarget(CommandContext<CommandSourceStack> ctx, Messages messages, TargetAction action)
            throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "oyuncu");
        OfflinePlayer target = Players.find(name);
        if (target == null) {
            sender(ctx).sendMessage(messages.get("mod-not-found", Placeholder.unparsed("name", name)));
        } else {
            action.apply(target);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx, Moderation moderation, String reason)
            throws CommandSyntaxException {
        Player target = ctx.getArgument("oyuncu", PlayerSelectorArgumentResolver.class)
                .resolve(ctx.getSource()).getFirst();
        moderation.kick(sender(ctx), target, reason);
        return Command.SINGLE_SUCCESS;
    }

    private static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }
}
