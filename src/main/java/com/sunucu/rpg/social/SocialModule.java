package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.social.chat.ChatCommands;
import com.sunucu.rpg.social.chat.ChatFilter;
import com.sunucu.rpg.social.chat.ChatListener;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.chat.Showcase;
import com.sunucu.rpg.world.Regions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class SocialModule implements Module {

    private final JavaPlugin plugin;
    private ChatListener chat;

    public SocialModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("chat");
        if (config == null) {
            throw new IllegalStateException(
                    "config.yml okunamadı ya da 'chat' bölümü yok; yukarıdaki YAML hatasına bak ve dosyayı düzelt");
        }
        Messages messages = new Messages(plugin.getConfig().getConfigurationSection("messages"));
        ChatFilter filter = ChatFilter.fromConfig(config);
        Notifications notifications = new Notifications(config.getConfigurationSection("sounds"), messages,
                config.getLong("mention-cooldown-seconds", 10));
        AfkTracker afk = new AfkTracker(messages, config.getLong("afk-after-seconds", 300));
        PrivateMessages privateMessages = new PrivateMessages(messages, filter, notifications, afk);
        // shortcut: bölgeler şimdilik sadece [konum] için burada yükleniyor; bölge giriş mesajları gelince world modülüne taşı.
        Regions regions = Regions.fromConfig(plugin.getConfig().getMapList("regions"), plugin.getLogger()::warning);
        Showcase showcase = new Showcase(config, messages, filter, regions);
        chat = new ChatListener(plugin, config, messages, filter, privateMessages, notifications, showcase);

        plugin.getServer().getPluginManager().registerEvents(chat, plugin);
        plugin.getServer().getPluginManager().registerEvents(showcase, plugin);
        plugin.getServer().getPluginManager().registerEvents(afk, plugin);
        afk.start(plugin);
        ChatCommands.register(plugin, chat, privateMessages, notifications, afk, messages);
    }

    public void setTagProvider(TagProvider provider) {
        chat.setTagProvider(provider);
    }
}
