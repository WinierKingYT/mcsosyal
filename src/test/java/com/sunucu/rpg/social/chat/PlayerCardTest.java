package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sunucu.rpg.core.Messages;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlayerCardTest {

    private final PlayerCard card = new PlayerCard(List.of(
            Map.of("stat", "class", "line", "Sınıf: <value>"),
            Map.of("stat", "level", "line", "Seviye: <value>")), messages());

    private static Messages messages() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("player-card-header", "<name>");
        config.set("name-hover", "Tıkla: mesaj");
        return new Messages(config);
    }

    private String plain(Map<String, String> stats) {
        return PlainTextComponentSerializer.plainText().serialize(card.render("Eleven", stats));
    }

    @Test
    void showsConfiguredStatsInOrder() {
        assertEquals("Eleven\nSınıf: Savaşçı\nSeviye: 42\nTıkla: mesaj",
                plain(Map.of("level", "42", "class", "Savaşçı")));
    }

    @Test
    void skipsLinesWithoutValue() {
        assertEquals("Eleven\nSeviye: 7\nTıkla: mesaj", plain(Map.of("level", "7")));
        assertEquals("Eleven\nTıkla: mesaj", plain(Map.of()));
    }

    @Test
    void valuesAreNotParsedAsTags() {
        assertEquals("Eleven\nSınıf: <red>Hile\nTıkla: mesaj", plain(Map.of("class", "<red>Hile")));
    }
}
