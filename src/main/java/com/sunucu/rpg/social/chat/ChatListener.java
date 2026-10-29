package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.TagProvider;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatListener implements Listener {

    public static final String BYPASS = "rpg.social.chat.bypass";

    private final JavaPlugin plugin;
    private final Messages messages;
    private final ChatFilter filter;
    private final PrivateMessages privateMessages;
    private final Notifications notifications;
    private final String format;
    private final String localPrefix;
    private final Map<String, ChatChannel> channels = new LinkedHashMap<>();
    private volatile TagProvider tagProvider = TagProvider.NONE;
    private volatile boolean locked;

    public ChatListener(JavaPlugin plugin, ConfigurationSection chat, Messages messages, ChatFilter filter,
                        PrivateMessages privateMessages, Notifications notifications) {
        this.plugin = plugin;
        this.messages = messages;
        this.filter = filter;
        this.privateMessages = privateMessages;
        this.notifications = notifications;
        this.format = chat.getString("format", "<name>: <message>");
        this.localPrefix = chat.getString("local-prefix", "!");
        ConfigurationSection section = chat.getConfigurationSection("channels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                channels.put(key, ChatChannel.fromConfig(key, section.getConfigurationSection(key)));
            }
        }
        channels.putIfAbsent(ChatChannel.GLOBAL, new ChatChannel(ChatChannel.GLOBAL, Component.text("G"), 2000, null, 0));
        channels.putIfAbsent(ChatChannel.LOCAL, new ChatChannel(ChatChannel.LOCAL, Component.text("Y"), 2000, null, 64));
    }

    /** Normal sohbet: global, ya da önek varsa yerel. İmzalı sohbet korunur. */
    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean local = text.startsWith(localPrefix) && text.length() > localPrefix.length();
        ChatChannel channel = channels.get(local ? ChatChannel.LOCAL : ChatChannel.GLOBAL);
        String filtered = prepare(sender, channel, local ? text.substring(localPrefix.length()).strip() : text);
        if (filtered == null) {
            event.setCancelled(true);
            return;
        }

        // Oyuncu metni düz metin olarak girer, MiniMessage etiketleri çalışmaz.
        event.message(Component.text(filtered));
        event.viewers().removeIf(viewer -> viewer instanceof Player player && !canSee(channel, sender, player));
        Component tags = tagProvider.tags(sender);
        List<Mentions.Span> mentions = Mentions.find(filtered, onlineNames());
        // Her izleyici kendi adını vurgulu görür, bu yüzden renderer izleyiciye göre çalışır.
        ChatRenderer renderer = (source, displayName, message, viewer) ->
                render(sender, channel, tags, mentionMessage(filtered, mentions, viewer));
        event.renderer(renderer);

        Set<Audience> viewers = Set.copyOf(event.viewers());
        // Ses ana thread'de çalınır.
        Bukkit.getScheduler().runTask(plugin, () -> notifyMentioned(sender, mentions, viewers::contains));
    }

    /**
     * Komutla belirli bir kanala gönderim (/t, /yardım, /sc, /l). Ana thread'den çağrılır.
     * Bu mesajlar sistem mesajı olarak gider, imzalı sohbet değildir.
     */
    public void sendToChannel(Player sender, String channelKey, String text) {
        ChatChannel channel = channels.get(channelKey);
        if (channel == null) {
            return;
        }
        String filtered = prepare(sender, channel, text);
        if (filtered == null) {
            return;
        }
        Component tags = tagProvider.tags(sender);
        List<Mentions.Span> mentions = Mentions.find(filtered, onlineNames());
        Set<Player> viewers = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canSee(channel, sender, viewer)) {
                viewers.add(viewer);
                viewer.sendMessage(render(sender, channel, tags, mentionMessage(filtered, mentions, viewer)));
            }
        }
        Bukkit.getConsoleSender().sendMessage(render(sender, channel, tags, Component.text(filtered)));
        notifyMentioned(sender, mentions, viewers::contains);
    }

    public ChatChannel channel(String key) {
        return channels.get(key);
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

    /** Kilit, izin ve filtre kontrolü. Mesaj gönderilemezse oyuncuya sebebini söyler ve null döner. */
    private String prepare(Player sender, ChatChannel channel, String text) {
        boolean bypass = sender.hasPermission(BYPASS);
        if (channel.permission() != null && !sender.hasPermission(channel.permission())) {
            return null;
        }
        if (locked && !bypass && channel.permission() == null) {
            sender.sendMessage(messages.get("chat-locked"));
            return null;
        }
        if (bypass) {
            return text;
        }
        ChatFilter.Result result = filter.check(sender.getUniqueId(), channel.key(), channel.cooldownMillis(),
                text, System.currentTimeMillis());
        if (result.verdict() != ChatFilter.Verdict.OK) {
            sender.sendMessage(messages.get(messageKey(result.verdict())));
            return null;
        }
        return result.text();
    }

    static String messageKey(ChatFilter.Verdict verdict) {
        return switch (verdict) {
            case COOLDOWN -> "cooldown";
            case DUPLICATE -> "duplicate";
            case ADVERTISEMENT -> "advertisement";
            case BANNED_WORD, OK -> "banned-word";
        };
    }

    private boolean canSee(ChatChannel channel, Player sender, Player viewer) {
        if (privateMessages.isIgnoring(viewer, sender)) {
            return false;
        }
        if (channel.permission() != null && !viewer.hasPermission(channel.permission())) {
            return false;
        }
        return channel.radius() <= 0 || inRange(sender, viewer, channel.radius());
    }

    /** "@isim" herkese renkli görünür; izleyicinin kendi adı (@'li ya da @'siz) ona vurgulu görünür. */
    private Component mentionMessage(String text, List<Mentions.Span> mentions, Audience viewer) {
        if (mentions.isEmpty()) {
            return Component.text(text);
        }
        String viewerName = viewer instanceof Player player ? player.getName() : null;
        TextComponent.Builder builder = Component.text();
        int position = 0;
        for (Mentions.Span span : mentions) {
            builder.append(Component.text(text.substring(position, span.start())));
            String part = text.substring(span.start(), span.end());
            if (span.name().equals(viewerName)) {
                builder.append(messages.get("mention-self", Placeholder.unparsed("text", part)));
            } else if (span.explicit()) {
                builder.append(messages.get("mention", Placeholder.unparsed("text", part)));
            } else {
                builder.append(Component.text(part));
            }
            position = span.end();
        }
        return builder.append(Component.text(text.substring(position))).build();
    }

    /** Ana thread'den çağrılır. Sadece "@isim" ile etiketlenen ve mesajı gören oyunculara ses gider. */
    private void notifyMentioned(Player sender, List<Mentions.Span> mentions, Predicate<Player> sawMessage) {
        mentions.stream()
                .filter(Mentions.Span::explicit)
                .map(span -> Bukkit.getPlayerExact(span.name()))
                .filter(player -> player != null && !player.equals(sender) && sawMessage.test(player))
                .distinct()
                .forEach(player -> notifications.play(player, Notifications.Type.MENTION));
    }

    // shortcut: çevrimiçi liste async thread'den okunuyor (Paper'da kopya döner); sorun çıkarsa ana thread'de önbelleğe al.
    private static Map<String, String> onlineNames() {
        Map<String, String> names = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.put(player.getName().toLowerCase(Locale.ROOT), player.getName());
        }
        return names;
    }

    private Component render(Player sender, ChatChannel channel, Component tags, Component message) {
        return Messages.parse(format,
                Placeholder.component("channel", channel.tag()),
                Placeholder.component("tags", tags),
                Placeholder.component("name", nameComponent(sender)),
                Placeholder.component("message", message));
    }

    private Component nameComponent(Player player) {
        return Component.text(player.getName())
                .hoverEvent(messages.get("name-hover", Placeholder.unparsed("name", player.getName())))
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));
    }

    // shortcut: konum async thread'den okunuyor (yaygın ve pratikte güvenli); sorun çıkarsa konumları ana thread'de önbelleğe al.
    private static boolean inRange(Player a, Player b, double radius) {
        return a.getWorld().equals(b.getWorld())
                && a.getLocation().distanceSquared(b.getLocation()) <= radius * radius;
    }
}
