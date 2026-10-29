package com.sunucu.rpg.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    private static void assertNoEvents(Component component) {
        assertNull(component.clickEvent());
        assertNull(component.hoverEvent());
        component.children().forEach(MessagesTest::assertNoEvents);
    }
}
