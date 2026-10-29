package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.core.db.Database;
import com.sunucu.rpg.social.chat.ChannelPreferences;
import com.sunucu.rpg.social.chat.ChatCommands;
import com.sunucu.rpg.social.chat.ChatFilter;
import com.sunucu.rpg.social.chat.ChatListener;
import com.sunucu.rpg.social.chat.ChatMutes;
import com.sunucu.rpg.social.chat.LiveInventoryView;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PlayerCard;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.chat.Showcase;
import com.sunucu.rpg.social.chat.VanillaCommandGuard;
import com.sunucu.rpg.social.friends.FriendActions;
import com.sunucu.rpg.social.friends.FriendCommands;
import com.sunucu.rpg.social.friends.Friends;
import com.sunucu.rpg.world.Regions;
import java.sql.SQLException;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class SocialModule implements Module {

    // Arkadaş paneli tek sayfa: 45 kafa sığıyor.
    private static final int MAX_FRIENDS = 45;

    private final JavaPlugin plugin;
    private final Database database;
    private ChatListener chat;

    /** database null ise (açılamadıysa) sosyal veriler sadece bellekte tutulur. */
    public SocialModule(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public void enable() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("chat");
        if (config == null) {
            throw new IllegalStateException(
                    "config.yml okunamadı ya da 'chat' bölümü yok; yukarıdaki YAML hatasına bak ve dosyayı düzelt");
        }
        Messages messages = new Messages(plugin.getConfig().getConfigurationSection("messages"));
        SocialStore store = openStore();
        ChatFilter filter = ChatFilter.fromConfig(config);
        Notifications notifications = new Notifications(config.getConfigurationSection("sounds"), messages, store,
                config.getLong("mention-cooldown-seconds", 10));
        AfkTracker afk = new AfkTracker(messages, config.getLong("afk-after-seconds", 300));
        ChatMutes mutes = new ChatMutes(store);
        PrivateMessages privateMessages = new PrivateMessages(messages, filter, notifications, afk, mutes, store);
        // shortcut: bölgeler şimdilik sadece [konum] için burada yükleniyor; bölge giriş mesajları gelince world modülüne taşı.
        Regions regions = Regions.fromConfig(plugin.getConfig().getMapList("regions"), plugin.getLogger()::warning);
        Showcase showcase = new Showcase(plugin, config, messages, filter, regions);
        ChannelPreferences preferences = new ChannelPreferences(store);
        PlayerCard card = new PlayerCard(config.getMapList("player-card"), messages);
        chat = new ChatListener(plugin, config, messages, filter, privateMessages, notifications, showcase,
                preferences, card, mutes);
        Friends friends = new Friends(store, Math.min(config.getInt("friends.max", 45), MAX_FRIENDS));
        FriendActions friendActions = new FriendActions(friends, messages, notifications);
        SocialMenu menu = new SocialMenu(messages, chat, preferences, notifications, friends, friendActions);
        SocialData data = new SocialData(store, plugin.getLogger(), messages, preferences, notifications,
                privateMessages, mutes, friends);

        plugin.getServer().getPluginManager().registerEvents(data, plugin);
        data.loadOnline();
        plugin.getServer().getPluginManager().registerEvents(chat, plugin);
        plugin.getServer().getPluginManager().registerEvents(new VanillaCommandGuard(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(afk, plugin);
        plugin.getServer().getPluginManager().registerEvents(friendActions, plugin);
        FriendCommands.register(plugin, friends, friendActions, menu.friendsMenu(), messages);
        plugin.getServer().getPluginManager().registerEvents(new LiveInventoryView.Protection(plugin), plugin);
        afk.start(plugin);
        ChatCommands.register(plugin, chat, privateMessages, afk, menu, mutes, messages);
    }

    private SocialStore openStore() {
        if (database == null) {
            return SocialStore.NONE;
        }
        try {
            return SqliteSocialStore.create(database, plugin.getLogger());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Sosyal tablolar kurulamadı! Ayarlar bu oturumda kaydedilmeyecek", e);
            return SocialStore.NONE;
        }
    }

    public void setTagProvider(TagProvider provider) {
        chat.setTagProvider(provider);
    }

    /** RPG tarafı oyuncu kartındaki değerleri (seviye, sınıf, lonca...) buradan verir. */
    public void setStatsProvider(StatsProvider provider) {
        chat.setStatsProvider(provider);
    }
}
