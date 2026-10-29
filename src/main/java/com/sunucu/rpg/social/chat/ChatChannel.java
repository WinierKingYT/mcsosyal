package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml → chat.channels altındaki bir kanal. name ve icon /sosyal panelinde, tag sohbet satırında görünür.
 * permission boş değilse kanala sadece o izne sahip olanlar yazar ve okur (yetkili kanalı gibi).
 * radius 0'dan büyükse sadece o mesafedeki oyuncular görür (yerel kanal).
 */
public record ChatChannel(String key, Component name, Component tag, Material icon, long cooldownMillis,
                          String permission, double radius) {

    public static final String GLOBAL = "global";
    public static final String LOCAL = "local";

    public static ChatChannel fromConfig(String key, ConfigurationSection section) {
        String permission = section.getString("permission", "");
        Material icon = Material.matchMaterial(section.getString("icon", "PAPER"));
        return new ChatChannel(
                key,
                Messages.parse(section.getString("name", key)),
                Messages.parse(section.getString("tag", key)),
                icon == null ? Material.PAPER : icon,
                section.getLong("cooldown-millis", 2000),
                permission.isBlank() ? null : permission,
                section.getDouble("radius", 0));
    }

    /** config'de olmasa bile her zaman var olan kanallar. */
    static ChatChannel fallback(String key, double radius) {
        return new ChatChannel(key, Component.text(key), Component.text(key), Material.PAPER, 2000, null, radius);
    }
}
