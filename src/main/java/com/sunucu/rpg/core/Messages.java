package com.sunucu.rpg.core;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml'deki "messages" bölümü. Açılışta bir kez okunur, böylece async
 * sohbet olayından da güvenle kullanılır.
 * Oyuncu metnini her zaman Placeholder.unparsed ya da Placeholder.component ile ver.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, String> templates = new HashMap<>();

    public Messages(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            templates.put(key, section.getString(key, ""));
        }
    }

    public Component get(String key, TagResolver... resolvers) {
        return parse(templates.getOrDefault(key, "<red>eksik mesaj: " + key), resolvers);
    }

    public static Component parse(String template, TagResolver... resolvers) {
        return MINI.deserialize(template, resolvers);
    }
}
