package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.TagProvider;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatListener implements Listener {

    public static final String BYPASS = "rpg.social.chat.bypass";

    private final Messages messages;
    private final ChatFilter filter;
    private final PrivateMessages privateMessages;
    private final String format;
    private final Component globalTag;
    private final Component localTag;
    private final String localPrefix;
    private final double localRadiusSquared;
    private volatile TagProvider tagProvider = TagProvider.NONE;
    private volatile boolean locked;

    public ChatListener(ConfigurationSection chat, Messages messages, ChatFilter filter, PrivateMessages privateMessages) {
        this.messages = messages;
        this.filter = filter;
        this.privateMessages = privateMessages;
        this.format = chat.getString("format", "<name>: <message>");
        this.globalTag = Messages.parse(chat.getString("channels.global", "G"));
        this.localTag = Messages.parse(chat.getString("channels.local", "Y"));
        this.localPrefix = chat.getString("local-prefix", "!");
        double radius = chat.getDouble("local-radius", 64);
        this.localRadiusSquared = radius * radius;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        boolean bypass = sender.hasPermission(BYPASS);
        if (locked && !bypass) {
            event.setCancelled(true);
            sender.sendMessage(messages.get("chat-locked"));
            return;
        }

        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean local = text.startsWith(localPrefix) && text.length() > localPrefix.length();
        if (local) {
            text = text.substring(localPrefix.length()).strip();
        }
        if (!bypass) {
            ChatFilter.Result result = filter.check(sender.getUniqueId(), text, System.currentTimeMillis());
            if (result.verdict() != ChatFilter.Verdict.OK) {
                event.setCancelled(true);
                sender.sendMessage(messages.get(switch (result.verdict()) {
                    case COOLDOWN -> "cooldown";
                    case DUPLICATE -> "duplicate";
                    default -> "banned-word";
                }));
                return;
            }
            text = result.text();
        }

        // Oyuncu metni düz metin olarak girer, MiniMessage etiketleri çalışmaz.
        event.message(Component.text(text));
        event.viewers().removeIf(viewer -> viewer instanceof Player player
                && (privateMessages.isIgnoring(player, sender) || local && !inRange(sender, player)));

        Component channel = local ? localTag : globalTag;
        Component tags = tagProvider.tags(sender);
        Component name = nameComponent(sender);
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> Messages.parse(format,
                Placeholder.component("channel", channel),
                Placeholder.component("tags", tags),
                Placeholder.component("name", name),
                Placeholder.component("message", message))));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            Bukkit.broadcast(messages.get("welcome", Placeholder.unparsed("name", player.getName())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        filter.forget(event.getPlayer().getUniqueId());
        privateMessages.forget(event.getPlayer().getUniqueId());
    }

    public boolean toggleLock() {
        locked = !locked;
        return locked;
    }

    public void setTagProvider(TagProvider provider) {
        this.tagProvider = provider == null ? TagProvider.NONE : provider;
    }

    private Component nameComponent(Player player) {
        return Component.text(player.getName())
                .hoverEvent(messages.get("name-hover", Placeholder.unparsed("name", player.getName())))
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));
    }

    // shortcut: konum async thread'den okunuyor (yaygın ve pratikte güvenli); sorun çıkarsa konumları ana thread'de önbelleğe al.
    private boolean inRange(Player a, Player b) {
        return a.getWorld().equals(b.getWorld())
                && a.getLocation().distanceSquared(b.getLocation()) <= localRadiusSquared;
    }
}
