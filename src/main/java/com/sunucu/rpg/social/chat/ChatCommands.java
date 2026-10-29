package com.sunucu.rpg.social.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sunucu.rpg.core.Messages;
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

/** /chat clear|lock, /msg, /r, /ignore. */
public final class ChatCommands {

    public static final String MODERATE = "rpg.social.chat.moderate";
    private static final int CLEAR_LINES = 100;

    private ChatCommands() {
    }

    public static void register(JavaPlugin plugin, ChatListener chat, PrivateMessages pm, Messages messages) {
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
        });
    }

    private static Player target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getArgument("oyuncu", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
    }

    private static TagResolver byName(CommandContext<CommandSourceStack> ctx) {
        return Placeholder.unparsed("name", ctx.getSource().getSender().getName());
    }
}
