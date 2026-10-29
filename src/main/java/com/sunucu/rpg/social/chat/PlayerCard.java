package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Sohbette ismin üstüne gelince çıkan oyuncu kartı: başlık, config'deki satırlar (chat.player-card), ipucu.
 * Her satır tek bir değere bağlıdır; değer yoksa (RPG tarafı vermediyse) satır hiç gösterilmez.
 */
public final class PlayerCard {

    record Line(String stat, String template) {
    }

    private final Messages messages;
    private final List<Line> lines = new ArrayList<>();

    public PlayerCard(List<Map<?, ?>> config, Messages messages) {
        this.messages = messages;
        for (Map<?, ?> entry : config) {
            Object stat = entry.get("stat");
            Object line = entry.get("line");
            if (stat != null && line != null) {
                lines.add(new Line(stat.toString(), line.toString()));
            }
        }
    }

    /** Sohbette isim hover'ı: başlık, satırlar, ipucu. */
    public Component render(String name, Map<String, String> stats) {
        TextComponent.Builder card = Component.text()
                .append(messages.get("player-card-header", Placeholder.unparsed("name", name)));
        for (Component line : lines(name, stats)) {
            card.appendNewline().append(line);
        }
        return card.appendNewline()
                .append(messages.get("name-hover", Placeholder.unparsed("name", name)))
                .build();
    }

    /** Sadece değer satırları (profil panelinde açıklama olarak da kullanılır). Değerler düz metin eklenir. */
    public List<Component> lines(String name, Map<String, String> stats) {
        List<Component> result = new ArrayList<>();
        for (Line line : lines) {
            String value = stats.get(line.stat());
            if (value != null) {
                result.add(Messages.parse(line.template(),
                        Placeholder.unparsed("value", value), Placeholder.unparsed("name", name)));
            }
        }
        return result;
    }
}
