package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.social.chat.ChatCommands;
import com.sunucu.rpg.social.chat.ChatFilter;
import com.sunucu.rpg.social.chat.ChatListener;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PrivateMessages;
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
        Messages messages = new Messages(plugin.getConfig().getConfigurationSection("messages"));
        ChatFilter filter = ChatFilter.fromConfig(config);
        Notifications notifications = new Notifications(config.getConfigurationSection("sounds"));
        AfkTracker afk = new AfkTracker(messages, config.getLong("afk-after-seconds", 300));
        PrivateMessages privateMessages = new PrivateMessages(messages, filter, notifications, afk);
        chat = new ChatListener(plugin, config, messages, filter, privateMessages, notifications);

        plugin.getServer().getPluginManager().registerEvents(chat, plugin);
        plugin.getServer().getPluginManager().registerEvents(afk, plugin);
        afk.start(plugin);
        ChatCommands.register(plugin, chat, privateMessages, notifications, afk, messages);
    }

    public void setTagProvider(TagProvider provider) {
        chat.setTagProvider(provider);
    }
}
