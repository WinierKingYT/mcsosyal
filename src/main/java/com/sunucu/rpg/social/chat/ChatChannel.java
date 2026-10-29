package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml → chat.channels altındaki bir kanal.
 * permission boş değilse kanala sadece o izne sahip olanlar yazar ve okur (yetkili kanalı gibi).
 * radius 0'dan büyükse sadece o mesafedeki oyuncular görür (yerel kanal).
 */
public record ChatChannel(String key, Component tag, long cooldownMillis, String permission, double radius) {

    public static final String GLOBAL = "global";
    public static final String LOCAL = "local";

    public static ChatChannel fromConfig(String key, ConfigurationSection section) {
        String permission = section.getString("permission", "");
        return new ChatChannel(
                key,
                Messages.parse(section.getString("tag", key)),
                section.getLong("cooldown-millis", 2000),
                permission.isBlank() ? null : permission,
                section.getDouble("radius", 0));
    }
}
