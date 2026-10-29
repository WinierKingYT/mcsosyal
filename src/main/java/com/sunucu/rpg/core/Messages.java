package com.sunucu.rpg.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

/**
 * config.yml'deki "messages" bölümü. Açılışta bir kez okunur, böylece async
 * sohbet olayından da güvenle kullanılır.
 * Oyuncu metnini her zaman Placeholder.unparsed ya da Placeholder.component ile ver.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    // Sadece renk ve biçim; tıklama, hover, komut gibi etiketler düz yazı kalır.
    private static final MiniMessage FORMATTING_ONLY = MiniMessage.builder()
            .tags(TagResolver.resolver(StandardTags.color(), StandardTags.decorations(), StandardTags.gradient(),
                    StandardTags.rainbow(), StandardTags.reset(), StandardTags.newline()))
            .build();

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

    /** Mesajın düz metni (renksiz); veritabanına yazılacak metinler için. */
    public String plain(String key, TagResolver... resolvers) {
        return PlainTextComponentSerializer.plainText().serialize(get(key, resolvers));
    }

    /** Çok satırlı metin (menü açıklamaları): config'de satırlar "\n" ile ayrılır. */
    public List<Component> lines(String key, TagResolver... resolvers) {
        String template = templates.getOrDefault(key, "<red>eksik mesaj: " + key);
        return Arrays.stream(template.split("\n")).map(line -> parse(line, resolvers)).toList();
    }

    public static Component parse(String template, TagResolver... resolvers) {
        return MINI.deserialize(template, resolvers);
    }

    /**
     * Yetkilinin yazdığı metin için (ör. /duyuru): renk ve biçim çalışır, tıklama ve hover çalışmaz. Böylece
     * ele geçirilmiş bir yetkili hesabı herkese komut çalıştırtan bir duyuru atamaz. Oyuncu metni için değil.
     */
    public static Component parseFormatting(String text) {
        return FORMATTING_ONLY.deserialize(text);
    }
}
