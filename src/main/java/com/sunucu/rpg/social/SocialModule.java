package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.social.chat.ChatCommands;
import com.sunucu.rpg.social.chat.ChatFilter;
import com.sunucu.rpg.social.chat.ChatListener;
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
        PrivateMessages privateMessages = new PrivateMessages(messages, filter);
        chat = new ChatListener(config, messages, filter, privateMessages);

        plugin.getServer().getPluginManager().registerEvents(chat, plugin);
        ChatCommands.register(plugin, chat, privateMessages, messages);
    }

    public void setTagProvider(TagProvider provider) {
        chat.setTagProvider(provider);
    }
}
