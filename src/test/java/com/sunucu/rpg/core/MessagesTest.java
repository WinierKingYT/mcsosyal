package com.sunucu.rpg.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    @Test
    void formattingOnlyKeepsColors() {
        Component text = Messages.parseFormatting("<red>Bakım 10 dakika sonra");
        assertEquals(NamedTextColor.RED, text.children().isEmpty() ? text.color() : text.children().getFirst().color());
        assertEquals("Bakım 10 dakika sonra", PlainTextComponentSerializer.plainText().serialize(text));
    }

    @Test
    void formattingOnlyIgnoresClickAndHover() {
        Component text = Messages.parseFormatting(
                "<click:run_command:'/op Hacker'>Ödül al</click> <hover:show_text:'x'>bak</hover>");
        assertNoEvents(text);
        // Etiket çalışmaz, yazı olarak kalır: ne olduğu görünür.
        assertEquals("<click:run_command:'/op Hacker'>Ödül al</click> <hover:show_text:'x'>bak</hover>",
                PlainTextComponentSerializer.plainText().serialize(text));
    }

    @Test
    void newKeysComeFromDefaults() {
        // Güncellemeden sonraki ilk açılış: anahtar dosyada yok, sadece plugin'in varsayılanında.
        YamlConfiguration config = new YamlConfiguration();
        config.set("messages.old", "eski");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("messages.fresh", "yeni <count>");
        config.setDefaults(defaults);
        config.options().copyDefaults(true); // RpgPlugin de böyle açıyor
        Messages messages = new Messages(config.getConfigurationSection("messages"));
        assertEquals("yeni 3", messages.plain("fresh", Placeholder.unparsed("count", "3")));
        assertEquals("eski", messages.plain("old"));
    }

    @Test
    void ownerTextKeepsClick() {
        Component text = Messages.parse("Yaz <click:run_command:/sosyal><white><u>/sosyal</u></white></click> lütfen");
        assertTrue(hasClick(text), "ipucundaki tıklama kayboldu");
    }

    private static boolean hasClick(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(MessagesTest::hasClick);
    }

    private static void assertNoEvents(Component component) {
        assertNull(component.clickEvent());
        assertNull(component.hoverEvent());
        component.children().forEach(MessagesTest::assertNoEvents);
    }
}
