package com.sunucu.rpg.social.moderation;

import com.sunucu.rpg.core.Durations;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Players;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Yetkili pencereleri: ceza geçmişi (her ceza bir eşya) ve sohbet kaydı (her mesaj bir kağıt). Üstüne gelince
 * ayrıntı görünür. Alt sırada oyuncunun kafası ve diğer pencereye geçiş. Tek sayfa: en yeni PAGE_SIZE kayıt.
 */
final class ModerationMenu {

    static final int PAGE_SIZE = 45;

    private static final int ROWS = 6;
    private static final int HEAD_SLOT = 49;
    private static final int SWITCH_SLOT = 53;
    private static final int PREVIEW_LENGTH = 32;
    private static final int LORE_WIDTH = 40;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Map<Punishment.Type, Material> TYPE_ICONS = Map.of(
            Punishment.Type.BAN, Material.BARRIER,
            Punishment.Type.MUTE, Material.COBWEB,
            Punishment.Type.SHADOW_MUTE, Material.GRAY_DYE,
            Punishment.Type.KICK, Material.LEATHER_BOOTS,
            Punishment.Type.WARN, Material.BELL);

    private final Messages messages;

    ModerationMenu(Messages messages) {
        this.messages = messages;
    }

    /** history en yeniden eskiye gelir. other: sohbet kaydına geçiş, yetkisi yoksa null. */
    void history(Player viewer, OfflinePlayer target, List<Punishment> history, Runnable other) {
        Menu menu = frame(target, "mod-gui-history-title", history.size(), "mod-gui-to-chatlog", Material.PAPER, other);
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < Math.min(history.size(), PAGE_SIZE); slot++) {
            menu.set(slot, punishmentIcon(history.get(slot), now));
        }
        menu.open(viewer);
    }

    /** lines eskiden yeniye gelir; pencerede en yeni en başta. other: ceza geçmişine geçiş, yetkisi yoksa null. */
    void chatLog(Player viewer, OfflinePlayer target, List<ModerationStore.ChatLine> lines, Runnable other) {
        Menu menu = frame(target, "mod-gui-chatlog-title", lines.size(), "mod-gui-to-history", Material.BOOK, other);
        List<ModerationStore.ChatLine> newestFirst = lines.reversed();
        for (int slot = 0; slot < Math.min(newestFirst.size(), PAGE_SIZE); slot++) {
            menu.set(slot, messageIcon(newestFirst.get(slot)));
        }
        menu.open(viewer);
    }

    private Menu frame(OfflinePlayer target, String titleKey, int count, String switchKey, Material switchIcon,
                       Runnable other) {
        TagResolver name = Placeholder.unparsed("name", Players.name(target));
        Menu menu = new Menu(ROWS, messages.get(titleKey, name));
        for (int slot = PAGE_SIZE; slot < ROWS * 9; slot++) {
            menu.set(slot, Icons.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        menu.set(HEAD_SLOT, Icons.head(target, messages.get("mod-gui-head", name),
                messages.lines("mod-gui-head-lore", Placeholder.unparsed("count", String.valueOf(count)))));
        if (other != null) {
            menu.button(SWITCH_SLOT, Icons.icon(switchIcon, messages.get(switchKey), messages.lines(switchKey + "-lore")),
                    click -> other.run());
        }
        return menu;
    }

    private ItemStack punishmentIcon(Punishment entry, long now) {
        Punishment.Status status = entry.status(now);
        TagResolver[] resolvers = {
                Placeholder.unparsed("id", String.valueOf(entry.id())),
                Placeholder.component("type", messages.get("mod-type-" + entry.type().name().toLowerCase(Locale.ROOT))),
                Placeholder.component("time", entry.isTimed() ? duration(entry) : messages.get("mod-gui-no-time")),
                Placeholder.unparsed("issuer", entry.issuer()),
                Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(entry.created()))),
                Placeholder.component("status", messages.get("mod-status-" + status.name().toLowerCase(Locale.ROOT)))};
        List<Component> lore = new ArrayList<>(messages.lines("mod-gui-entry-lore", resolvers));
        lore.add(messages.get("mod-gui-reason"));
        wrap(entry.reason(), LORE_WIDTH).forEach(line ->
                lore.add(messages.get("mod-gui-text-line", Placeholder.unparsed("text", line))));
        if (status == Punishment.Status.REVOKED) {
            lore.add(messages.get("mod-gui-revoked",
                    Placeholder.unparsed("issuer", entry.revokedBy() == null ? "?" : entry.revokedBy()),
                    Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(entry.revokedAt())))));
        }
        return Icons.icon(TYPE_ICONS.get(entry.type()), messages.get("mod-gui-entry", resolvers), lore,
                status == Punishment.Status.ACTIVE);
    }

    private Component duration(Punishment entry) {
        return entry.isPermanent() ? messages.get("mod-permanent")
                : Component.text(Durations.format(entry.expires() - entry.created()));
    }

    private ItemStack messageIcon(ModerationStore.ChatLine line) {
        List<Component> lore = new ArrayList<>(messages.lines("mod-gui-message-lore",
                Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(line.time()))),
                Placeholder.unparsed("channel", line.channel())));
        wrap(line.message(), LORE_WIDTH).forEach(text ->
                lore.add(messages.get("mod-gui-text-line", Placeholder.unparsed("text", text))));
        return Icons.icon(Material.PAPER, messages.get("mod-gui-message",
                Placeholder.unparsed("text", preview(line.message()))), lore);
    }

    static String preview(String text) {
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH - 1).stripTrailing() + "…";
    }

    /** Uzun metni kelime sınırından satırlara böler; tek başına uzun kelime satır genişliğinde kesilir. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            while (word.length() > width) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (!current.isEmpty() && current.length() + 1 + word.length() > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (!word.isEmpty()) {
                current.append(current.isEmpty() ? "" : " ").append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
