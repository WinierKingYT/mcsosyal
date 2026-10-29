package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import com.sunucu.rpg.social.chat.ChannelPreferences;
import com.sunucu.rpg.social.chat.ChatChannel;
import com.sunucu.rpg.social.chat.ChatListener;
import com.sunucu.rpg.social.chat.Notifications;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * /sosyal: sosyal sistemlerin ana paneli. Her ayar kendi paneli, alt panellerden ana panele dönülür.
 * Yeni bir sosyal ayar gelince ana panele bir buton ve buraya bir open... metodu eklenir.
 */
public final class SocialMenu {

    private static final int ROWS = 3;
    private static final int MAIN_CHANNELS_SLOT = 11;
    private static final int MAIN_NOTIFICATIONS_SLOT = 15;
    private static final int FIRST_CHANNEL_SLOT = 10;
    private static final int MAX_CHANNELS = 7;
    private static final int MENTION_SLOT = 11;
    private static final int PRIVATE_MESSAGE_SLOT = 15;
    private static final int BACK_SLOT = 18;

    private final Messages messages;
    private final ChatListener chat;
    private final ChannelPreferences preferences;
    private final Notifications notifications;

    public SocialMenu(Messages messages, ChatListener chat, ChannelPreferences preferences, Notifications notifications) {
        this.messages = messages;
        this.chat = chat;
        this.preferences = preferences;
        this.notifications = notifications;
    }

    public void openMain(Player player) {
        new Menu(ROWS, messages.get("menu-title"))
                .button(MAIN_CHANNELS_SLOT, Icons.icon(Material.WRITABLE_BOOK, messages.get("menu-channels"),
                        messages.lines("menu-channels-lore")), click -> openChannels(player))
                .button(MAIN_NOTIFICATIONS_SLOT, Icons.icon(Material.BELL, messages.get("menu-notifications"),
                        messages.lines("menu-notifications-lore")), click -> openNotifications(player))
                .open(player);
    }

    /** Sol tık: bu kanala yaz. Sağ tık: kanalı gizle / göster. */
    private void openChannels(Player player) {
        Menu menu = new Menu(ROWS, messages.get("channels-title"));
        fillChannels(menu, player);
        menu.open(player);
    }

    private void fillChannels(Menu menu, Player player) {
        List<ChatChannel> channels = chat.channelsFor(player);
        String active = chat.activeChannel(player).key();
        for (int i = 0; i < Math.min(channels.size(), MAX_CHANNELS); i++) {
            ChatChannel channel = channels.get(i);
            boolean isActive = channel.key().equals(active);
            boolean hidden = preferences.isHidden(player.getUniqueId(), channel.key());
            List<Component> lore = new ArrayList<>(channel.description());
            if (!lore.isEmpty()) {
                lore.add(Component.empty());
            }
            lore.addAll(messages.lines(isActive ? "channel-active" : "channel-select"));
            lore.addAll(messages.lines(hidden ? "channel-hidden" : "channel-visible"));
            menu.button(FIRST_CHANNEL_SLOT + i, Icons.icon(channel.icon(), channel.name(), lore, isActive),
                    click -> onChannelClick(menu, player, channel, click));
        }
        menu.button(BACK_SLOT, back(), click -> openMain(player));
    }

    private void onChannelClick(Menu menu, Player player, ChatChannel channel, ClickType click) {
        if (click.isRightClick()) {
            if (channel.key().equals(chat.activeChannel(player).key())) {
                player.sendMessage(messages.get("channel-cannot-hide-active"));
            } else {
                boolean hidden = preferences.toggleHidden(player.getUniqueId(), channel.key());
                player.sendMessage(messages.get(hidden ? "channel-now-hidden" : "channel-now-visible",
                        Placeholder.component("channel", channel.name())));
            }
        } else {
            preferences.setActive(player.getUniqueId(), channel.key());
            player.sendMessage(messages.get("channel-now-active", Placeholder.component("channel", channel.name())));
        }
        fillChannels(menu, player);
    }

    private void openNotifications(Player player) {
        Menu menu = new Menu(ROWS, messages.get("notifications-title"));
        fillNotifications(menu, player);
        menu.open(player);
    }

    private void fillNotifications(Menu menu, Player player) {
        menu.button(MENTION_SLOT, toggleIcon(player, Notifications.Type.MENTION, Material.NAME_TAG,
                        messages.get("notify-mention"), messages.lines("notify-mention-lore")),
                click -> toggle(menu, player, Notifications.Type.MENTION));
        menu.button(PRIVATE_MESSAGE_SLOT, toggleIcon(player, Notifications.Type.PRIVATE_MESSAGE, Material.PAPER,
                        messages.get("notify-private-message"), messages.lines("notify-private-message-lore")),
                click -> toggle(menu, player, Notifications.Type.PRIVATE_MESSAGE));
        menu.button(BACK_SLOT, back(), click -> openMain(player));
    }

    private void toggle(Menu menu, Player player, Notifications.Type type) {
        notifications.toggle(player, type);
        fillNotifications(menu, player);
    }

    /** Açıklama, boş satır, sonra açık/kapalı durumu. Açıkken parlar. */
    private ItemStack toggleIcon(Player player, Notifications.Type type, Material material, Component name,
                                 List<Component> description) {
        boolean enabled = notifications.isEnabled(player, type);
        List<Component> lore = new ArrayList<>(description);
        lore.add(Component.empty());
        lore.addAll(messages.lines(enabled ? "notify-on" : "notify-off"));
        return Icons.icon(material, name, lore, enabled);
    }

    private ItemStack back() {
        return Icons.icon(Material.ARROW, messages.get("menu-back"), messages.lines("menu-back-lore"));
    }
}
