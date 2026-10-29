package com.sunucu.rpg.social.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.AfkTracker;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** /chat clear|lock, /msg, /r, /ignore, /bildirim, /socialspy, /afk ve kanal komutları (/l, /t, /yardım, /sc). */
public final class ChatCommands {

    public static final String MODERATE = "rpg.social.chat.moderate";
    private static final int CLEAR_LINES = 100;

    private ChatCommands() {
    }

    public static void register(JavaPlugin plugin, ChatListener chat, PrivateMessages pm,
                                Notifications notifications, AfkTracker afk, Messages messages) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(Commands.literal("chat")
                    .requires(src -> src.getSender().hasPermission(MODERATE))
                    .then(Commands.literal("clear").executes(ctx -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (!player.hasPermission(ChatListener.BYPASS)) {
                                for (int i = 0; i < CLEAR_LINES; i++) {
                                    player.sendMessage(Component.empty());
                                }
                            }
                        }
                        Bukkit.broadcast(messages.get("cleared", byName(ctx)));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("lock").executes(ctx -> {
                        Bukkit.broadcast(messages.get(chat.toggleLock() ? "lock-on" : "lock-off", byName(ctx)));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .build(), "Sohbeti temizle ya da kilitle");

            commands.register(Commands.literal("msg")
                    .requires(src -> src.getSender() instanceof Player)
                    .then(Commands.argument("oyuncu", ArgumentTypes.player())
                            .then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(ctx -> {
                                pm.send((Player) ctx.getSource().getSender(), target(ctx),
                                        StringArgumentType.getString(ctx, "mesaj"));
                                return Command.SINGLE_SUCCESS;
                            })))
                    .build(), "Özel mesaj gönder", List.of("tell", "w", "m"));

            commands.register(Commands.literal("r")
                    .requires(src -> src.getSender() instanceof Player)
                    .then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(ctx -> {
                        pm.reply((Player) ctx.getSource().getSender(), StringArgumentType.getString(ctx, "mesaj"));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .build(), "Son özel mesaja cevap ver", List.of("reply"));

            commands.register(Commands.literal("ignore")
                    .requires(src -> src.getSender() instanceof Player)
                    .then(Commands.argument("oyuncu", ArgumentTypes.player()).executes(ctx -> {
                        Player self = (Player) ctx.getSource().getSender();
                        Player target = target(ctx);
                        boolean now = pm.toggleIgnore(self, target);
                        self.sendMessage(messages.get(now ? "ignore-on" : "ignore-off",
                                Placeholder.unparsed("name", target.getName())));
                        return Command.SINGLE_SUCCESS;
                    }))
                    .build(), "Bir oyuncuyu engelle ya da engeli kaldır", List.of("engelle"));

            commands.register(Commands.literal("bildirim")
                    .requires(src -> src.getSender() instanceof Player)
                    .then(soundToggle("etiket", Notifications.Type.MENTION, "Etiket", notifications, messages))
                    .then(soundToggle("mesaj", Notifications.Type.PRIVATE_MESSAGE, "Özel mesaj", notifications, messages))
                    .build(), "Bildirim seslerini aç ya da kapat");

            commands.register(Commands.literal("socialspy")
                    .requires(src -> src.getSender() instanceof Player player && player.hasPermission(PrivateMessages.SPY))
                    .executes(ctx -> {
                        Player player = (Player) ctx.getSource().getSender();
                        player.sendMessage(messages.get(pm.toggleSpy(player) ? "spy-on" : "spy-off"));
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(), "Başkalarının özel mesajlarını gör", List.of("spy"));

            commands.register(Commands.literal("afk")
                    .requires(src -> src.getSender() instanceof Player)
                    .executes(ctx -> {
                        afk.toggle((Player) ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(), "AFK ol ya da AFK'dan çık");

            registerChannel(commands, chat, "l", ChatChannel.LOCAL, "Yerel kanala yaz", List.of());
            registerChannel(commands, chat, "t", "trade", "Ticaret kanalına yaz", List.of("ticaret"));
            registerChannel(commands, chat, "yardım", "help", "Yardım kanalına yaz", List.of("yardim"));
            registerChannel(commands, chat, "sc", "staff", "Yetkili kanalına yaz", List.of());
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> soundToggle(
            String name, Notifications.Type type, String label, Notifications notifications, Messages messages) {
        return Commands.literal(name).executes(ctx -> {
            Player player = (Player) ctx.getSource().getSender();
            boolean enabled = notifications.toggle(player, type);
            player.sendMessage(messages.get(enabled ? "sound-on" : "sound-off", Placeholder.unparsed("type", label)));
            return Command.SINGLE_SUCCESS;
        });
    }

    private static void registerChannel(Commands commands, ChatListener chat, String name, String channelKey,
                                        String description, List<String> aliases) {
        ChatChannel channel = chat.channel(channelKey);
        if (channel == null) {
            return; // config'den silinmiş kanalın komutu da olmaz
        }
        commands.register(Commands.literal(name)
                .requires(src -> src.getSender() instanceof Player player
                        && (channel.permission() == null || player.hasPermission(channel.permission())))
                .then(Commands.argument("mesaj", StringArgumentType.greedyString()).executes(ctx -> {
                    chat.sendToChannel((Player) ctx.getSource().getSender(), channelKey,
                            StringArgumentType.getString(ctx, "mesaj"));
                    return Command.SINGLE_SUCCESS;
                }))
                .build(), description, aliases);
    }

    private static Player target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("oyuncu", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    private static TagResolver byName(CommandContext<CommandSourceStack> ctx) {
        return Placeholder.unparsed("name", ctx.getSource().getSender().getName());
    }
}
