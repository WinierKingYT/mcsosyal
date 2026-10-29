package com.sunucu.rpg.social.moderation;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Players;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.social.chat.LiveInventoryView;
import com.sunucu.rpg.social.chat.Showcase;
import com.sunucu.rpg.social.profile.StaffButtons;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Profilde yetkiliye görünen butonlar: ceza geçmişi, sohbet kaydı, envanter (çevrimiçiyse) ve ceza ver. Ceza ver
 * pencereyi kapatıp sohbete komut butonları yazar; sebebi yetkili ekler. Her buton kendi izniyle görünür.
 */
public final class ProfileStaffButtons implements StaffButtons {

    private final JavaPlugin plugin;
    private final Moderation moderation;
    private final Messages messages;

    public ProfileStaffButtons(JavaPlugin plugin, Moderation moderation, Messages messages) {
        this.plugin = plugin;
        this.moderation = moderation;
        this.messages = messages;
    }

    @Override
    public List<Button> buttons(Player viewer, OfflinePlayer target) {
        List<Button> buttons = new ArrayList<>();
        if (viewer.hasPermission(ModerationCommands.HISTORY)) {
            buttons.add(button(Material.BOOK, "profile-staff-history",
                    () -> moderation.showHistory(viewer, target)));
        }
        if (viewer.hasPermission(ModerationCommands.CHATLOG)) {
            buttons.add(button(Material.PAPER, "profile-staff-chatlog",
                    () -> moderation.showChatLog(viewer, target, ModerationMenu.PAGE_SIZE)));
        }
        Player online = target.getPlayer();
        if (online != null && viewer.hasPermission(Showcase.EDIT_INVENTORY)) {
            buttons.add(button(Material.CHEST, "profile-staff-inventory", () -> {
                if (online.isOnline()) {
                    LiveInventoryView.open(plugin, viewer, online, messages, messages.get("view-inventory-edit-title",
                            Placeholder.unparsed("name", online.getName())));
                }
            }));
        }
        Component punish = punishLine(viewer, target);
        if (punish != null) {
            buttons.add(button(Material.IRON_SWORD, "profile-staff-punish", () -> {
                viewer.closeInventory();
                viewer.sendMessage(punish);
            }));
        }
        return buttons;
    }

    private Button button(Material material, String key, Runnable action) {
        return new Button(Icons.icon(material, messages.get(key), messages.lines(key + "-lore")), action);
    }

    /** İzni olan cezalar için tıklanabilir komutlar; hiç izni yoksa null. */
    private Component punishLine(Player viewer, OfflinePlayer target) {
        String name = Players.name(target);
        Component line = messages.get("punish-prompt", Placeholder.unparsed("name", name));
        boolean any = false;
        if (viewer.hasPermission(ModerationCommands.WARN)) {
            line = line.append(command("punish-warn", "/warn " + name + " "));
            any = true;
        }
        if (viewer.hasPermission(ModerationCommands.MUTE)) {
            line = line.append(command("punish-mute", "/mute " + name + " 1h "))
                    .append(command("punish-shadowmute", "/gizlisustur " + name + " 1h "));
            any = true;
        }
        if (viewer.hasPermission(ModerationCommands.KICK) && target.getPlayer() != null) {
            line = line.append(command("punish-kick", "/kick " + name + " "));
            any = true;
        }
        if (viewer.hasPermission(ModerationCommands.BAN)) {
            line = line.append(command("punish-ban", "/ban " + name + " 1d "));
            any = true;
        }
        return any ? line : null;
    }

    private Component command(String key, String command) {
        return Component.space().append(messages.get(key)
                .hoverEvent(messages.get("punish-hover", Placeholder.unparsed("command", command.strip())))
                .clickEvent(ClickEvent.suggestCommand(command)));
    }
}
