package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.StatsProvider;
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
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long MILLIS_PER_MINUTE = 60_000;

    private final JavaPlugin plugin;
    private final Messages messages;
    private final ChatFilter filter;
    private final PrivateMessages privateMessages;
    private final Notifications notifications;
    private final Showcase showcase;
    private final ChannelPreferences preferences;
    private final PlayerCard card;
    private final ChatMutes mutes;
    private final int spamWarnAfter;
    private final int spamMuteAfter;
    private final long spamMuteMillis;
    private final String format;
    private final String localPrefix;
    private final Map<String, ChatChannel> channels = new LinkedHashMap<>();
    private final Map<String, Component> badges = new LinkedHashMap<>();
    private volatile TagProvider tagProvider = TagProvider.NONE;
    private volatile StatsProvider statsProvider = StatsProvider.NONE;
    private volatile boolean locked;

    public ChatListener(JavaPlugin plugin, ConfigurationSection chat, Messages messages, ChatFilter filter,
                        PrivateMessages privateMessages, Notifications notifications, Showcase showcase,
                        ChannelPreferences preferences, PlayerCard card, ChatMutes mutes) {
        this.plugin = plugin;
        this.messages = messages;
        this.filter = filter;
        this.privateMessages = privateMessages;
        this.notifications = notifications;
        this.showcase = showcase;
        this.preferences = preferences;
        this.card = card;
        this.mutes = mutes;
        this.spamWarnAfter = chat.getInt("spam.warn-after", 5);
        this.spamMuteAfter = chat.getInt("spam.mute-after", 10);
        this.spamMuteMillis = chat.getLong("spam.mute-minutes", 5) * MILLIS_PER_MINUTE;
        this.format = chat.getString("format", "<name>: <message>");
        this.localPrefix = chat.getString("local-prefix", "!");
        ConfigurationSection section = chat.getConfigurationSection("channels");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                channels.put(key, ChatChannel.fromConfig(key, section.getConfigurationSection(key)));
            }
        }
        channels.putIfAbsent(ChatChannel.GLOBAL, ChatChannel.fallback(ChatChannel.GLOBAL, 0));
        channels.putIfAbsent(ChatChannel.LOCAL, ChatChannel.fallback(ChatChannel.LOCAL, 64));
        // Sıra önemli: config'deki ilk eşleşen rozet gösterilir.
        for (Map<?, ?> badge : chat.getMapList("badges")) {
            badges.put(String.valueOf(badge.get("permission")), Messages.parse(String.valueOf(badge.get("tag"))));
        }
    }

    /**
     * Normal sohbet: oyuncunun /sosyal'dan seçtiği kanala, önek varsa yerel kanala gider. İmzalı sohbet korunur.
     * Hata olursa mesaj engellenir: aksi halde Paper mesajı filtresiz yayınlardı.
     */
    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        try {
            handleChat(event);
        } catch (RuntimeException e) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.SEVERE, "Sohbet mesajı işlenemedi, mesaj engellendi", e);
            event.getPlayer().sendMessage(messages.get("message-error"));
        }
    }

    private void handleChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        boolean local = text.startsWith(localPrefix) && text.length() > localPrefix.length();
        ChatChannel channel = local ? channels.get(ChatChannel.LOCAL) : activeChannel(sender);
        String filtered = prepare(sender, channel, local ? text.substring(localPrefix.length()).strip() : text);
        Showcase.Shared shared = filtered == null ? null : showcase.prepare(sender, filtered);
        if (shared == null) {
            event.setCancelled(true);
            return;
        }

        // Oyuncu metni düz metin olarak girer, MiniMessage etiketleri çalışmaz.
        event.message(Component.text(filtered));
        event.viewers().removeIf(viewer -> viewer instanceof Player player && !canSee(channel, sender, player));
        Component tags = tagProvider.tags(sender);
        Component name = nameComponent(sender);
        List<Mentions.Span> mentions = Mentions.find(filtered, onlineNames());
        // Her izleyici kendi adını vurgulu görür, bu yüzden renderer izleyiciye göre çalışır.
        ChatRenderer renderer = (source, displayName, message, viewer) ->
                render(sender, channel, tags, name, shared.apply(mentionMessage(filtered, mentions, viewer)));
        event.renderer(renderer);

        Set<Audience> viewers = Set.copyOf(event.viewers());
        // Ses ve ekran yazısı ana thread'de.
        Bukkit.getScheduler().runTask(plugin, () -> notifyMentioned(sender, mentions, viewers::contains));
    }

    /**
     * Komutla belirli bir kanala gönderim (/sc). Ana thread'den çağrılır.
     * Bu mesajlar sistem mesajı olarak gider, imzalı sohbet değildir.
     */
    public void sendToChannel(Player sender, String channelKey, String text) {
        ChatChannel channel = channels.get(channelKey);
        if (channel == null) {
            return;
        }
        String filtered = prepare(sender, channel, text);
        Showcase.Shared shared = filtered == null ? null : showcase.prepare(sender, filtered);
        if (shared == null) {
            return;
        }
        Component tags = tagProvider.tags(sender);
        Component name = nameComponent(sender);
        List<Mentions.Span> mentions = Mentions.find(filtered, onlineNames());
        Set<Player> viewers = new HashSet<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canSee(channel, sender, viewer)) {
                viewers.add(viewer);
                viewer.sendMessage(render(sender, channel, tags, name,
                        shared.apply(mentionMessage(filtered, mentions, viewer))));
            }
        }
        Bukkit.getConsoleSender().sendMessage(render(sender, channel, tags, name, shared.apply(Component.text(filtered))));
        notifyMentioned(sender, mentions, viewers::contains);
    }

    /**
     * /me: Minecraft'ın kendi /me'si filtreyi atlatıyordu. Seçili kanala "* İsim eylem" olarak gider; filtre,
     * bekleme süresi ve susturma normal sohbetle aynı. Ana thread'den çağrılır.
     */
    public void sendEmote(Player sender, String text) {
        ChatChannel channel = activeChannel(sender);
        String filtered = prepare(sender, channel, text);
        if (filtered == null) {
            return;
        }
        Component line = messages.get("emote",
                Placeholder.component("channel", channel.tag()),
                Placeholder.component("name", nameComponent(sender)),
                Placeholder.unparsed("message", filtered));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canSee(channel, sender, viewer)) {
                viewer.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
    }

    public ChatChannel channel(String key) {
        return channels.get(key);
    }

    /** /sosyal'da gösterilecek kanallar: izni olmayan kanal listelenmez. */
    public List<ChatChannel> channelsFor(Player player) {
        return channels.values().stream()
                .filter(channel -> channel.permission() == null || player.hasPermission(channel.permission()))
                .toList();
    }

    /** Seçtiği kanalın izni sonradan alındıysa global'e düşer. */
    public ChatChannel activeChannel(Player player) {
        ChatChannel channel = channels.get(preferences.active(player.getUniqueId()));
        if (channel == null || channel.permission() != null && !player.hasPermission(channel.permission())) {
            return channels.get(ChatChannel.GLOBAL);
        }
        return channel;
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
    }

    public boolean toggleLock() {
        locked = !locked;
        return locked;
    }

    public void setTagProvider(TagProvider provider) {
        this.tagProvider = provider == null ? TagProvider.NONE : provider;
    }

    public void setStatsProvider(StatsProvider provider) {
        this.statsProvider = provider == null ? StatsProvider.NONE : provider;
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
        long now = System.currentTimeMillis();
        if (isMuted(sender, now)) {
            return null;
        }
        ChatFilter.Result result = filter.check(sender.getUniqueId(), channel.key(), channel.cooldownMillis(),
                text, now);
        if (result.verdict() == ChatFilter.Verdict.OK) {
            return result.text();
        }
        // Bekleme/tekrar denemeleri sayılır: önce normal uyarı, sonra farklı renkte sert uyarı, en sonda susturma.
        if (result.strikes() >= spamMuteAfter) {
            mutes.mute(sender.getUniqueId(), now + spamMuteMillis);
            filter.clearStrikes(sender.getUniqueId());
            sender.sendMessage(messages.get("spam-muted", minutes(spamMuteMillis)));
            plugin.getLogger().info(sender.getName() + " spam yüzünden " + spamMuteMillis / MILLIS_PER_MINUTE
                    + " dakika susturuldu");
        } else if (result.strikes() >= spamWarnAfter) {
            sender.sendMessage(messages.get("spam-warning",
                    Placeholder.unparsed("left", String.valueOf(spamMuteAfter - result.strikes()))));
        } else {
            // Yukarı yuvarla: 0,4 saniye kaldıysa "1 saniye bekle" yazsın, "0" değil.
            long seconds = (result.waitMillis() + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
            sender.sendMessage(messages.get(messageKey(result.verdict()),
                    Placeholder.unparsed("seconds", String.valueOf(seconds))));
        }
        return null;
    }

    /** Susturulmuşsa oyuncuya kalan süreyi söyler. */
    private boolean isMuted(Player sender, long now) {
        long remaining = mutes.remainingMillis(sender.getUniqueId(), now);
        if (remaining <= 0) {
            return false;
        }
        sender.sendMessage(messages.get("muted", minutes(remaining)));
        return true;
    }

    static TagResolver minutes(long millis) {
        return Placeholder.unparsed("minutes", String.valueOf((millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE));
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
        if (viewer.equals(sender)) {
            return true;
        }
        if (privateMessages.isIgnoring(viewer, sender) || preferences.isHidden(viewer.getUniqueId(), channel.key())) {
            return false;
        }
        if (channel.permission() != null && !viewer.hasPermission(channel.permission())) {
            return false;
        }
        return channel.radius() <= 0 || inRange(sender, viewer, channel.radius());
    }

    /** config → chat.badges: izni olan ilk rozet. Yoksa boş. */
    private Component badgeFor(Player player) {
        for (Map.Entry<String, Component> badge : badges.entrySet()) {
            if (player.hasPermission(badge.getKey())) {
                return badge.getValue();
            }
        }
        return Component.empty();
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

    /** Ana thread'den çağrılır. Sadece "@isim" ile etiketlenen ve mesajı gören oyunculara bildirim gider. */
    private void notifyMentioned(Player sender, List<Mentions.Span> mentions, Predicate<Player> sawMessage) {
        mentions.stream()
                .filter(Mentions.Span::explicit)
                .map(span -> Bukkit.getPlayerExact(span.name()))
                .filter(player -> player != null && !player.equals(sender) && sawMessage.test(player))
                .distinct()
                .forEach(player -> notifications.mention(player, sender));
    }

    // shortcut: çevrimiçi liste async thread'den okunuyor (Paper'da kopya döner); sorun çıkarsa ana thread'de önbelleğe al.
    private static Map<String, String> onlineNames() {
        Map<String, String> names = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.put(player.getName().toLowerCase(Locale.ROOT), player.getName());
        }
        return names;
    }

    private Component render(Player sender, ChatChannel channel, Component tags, Component name, Component message) {
        return Messages.parse(format,
                Placeholder.component("channel", channel.tag()),
                Placeholder.component("badge", badgeFor(sender)),
                Placeholder.component("tags", tags),
                Placeholder.component("name", name),
                Placeholder.component("message", message));
    }

    private Component nameComponent(Player player) {
        return Component.text(player.getName())
                .hoverEvent(card.render(player.getName(), statsProvider.stats(player)))
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));
    }

    // shortcut: konum async thread'den okunuyor (yaygın ve pratikte güvenli); sorun çıkarsa konumları ana thread'de önbelleğe al.
    private static boolean inRange(Player a, Player b, double radius) {
        return a.getWorld().equals(b.getWorld())
                && a.getLocation().distanceSquared(b.getLocation()) <= radius * radius;
    }
}
