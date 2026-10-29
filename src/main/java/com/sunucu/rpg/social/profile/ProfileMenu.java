package com.sunucu.rpg.social.profile;

import com.sunucu.rpg.core.Durations;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import com.sunucu.rpg.social.chat.PlayerCard;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.friends.FriendActions;
import com.sunucu.rpg.social.friends.Friends;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Oyuncu profili (3 sıra): üstte kafa ve kart bilgileri, altında oynama süresi, mesaj, arkadaşlık ve engelleme.
 * Çevrimdışı oyuncunun profili de açılır (RPG değerleri o zaman yok, sadece oynama süresi).
 */
public final class ProfileMenu {

    public static final String COMMAND = "/profil";

    private static final int ROWS = 3;
    private static final int HEAD_SLOT = 4;
    private static final int PLAYTIME_SLOT = 10;
    private static final int MESSAGE_SLOT = 12;
    private static final int FRIEND_SLOT = 14;
    private static final int IGNORE_SLOT = 16;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Messages messages;
    private final Friends friends;
    private final FriendActions friendActions;
    private final PrivateMessages privateMessages;
    private final PlayerCard card;
    private final Function<Player, Map<String, String>> stats;

    public ProfileMenu(Messages messages, Friends friends, FriendActions friendActions,
                       PrivateMessages privateMessages, PlayerCard card, Function<Player, Map<String, String>> stats) {
        this.messages = messages;
        this.friends = friends;
        this.friendActions = friendActions;
        this.privateMessages = privateMessages;
        this.card = card;
        this.stats = stats;
    }

    public void open(Player viewer, OfflinePlayer target) {
        Menu menu = new Menu(ROWS, messages.get("profile-title", name(target)));
        fill(menu, viewer, target);
        menu.open(viewer);
    }

    private void fill(Menu menu, Player viewer, OfflinePlayer target) {
        String targetName = nameOf(target);
        Player online = target.getPlayer();
        Map<String, String> values = online != null ? stats.apply(online)
                : Map.of("playtime", Durations.format(Playtime.millis(target)));
        List<Component> headLore = new ArrayList<>(card.lines(targetName, values));
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            headLore.add(Component.empty());
            headLore.addAll(messages.lines("profile-own"));
        }
        menu.set(HEAD_SLOT, Icons.head(target, messages.get("profile-head", name(target)), headLore));
        menu.set(PLAYTIME_SLOT, Icons.icon(Material.CLOCK, messages.get("profile-playtime"), playtimeLore(target)));
        if (viewer.getUniqueId().equals(target.getUniqueId())) {
            return;
        }
        menu.button(MESSAGE_SLOT, Icons.icon(Material.WRITABLE_BOOK, messages.get("profile-message"),
                messages.lines(online != null ? "profile-message-lore" : "profile-offline-lore")), click -> {
            if (target.getPlayer() != null) {
                viewer.closeInventory();
                viewer.sendMessage(messages.get("friend-message-prompt", name(target))
                        .clickEvent(ClickEvent.suggestCommand("/msg " + targetName + " ")));
            }
        });
        menu.button(FRIEND_SLOT, friendIcon(viewer.getUniqueId(), target),
                click -> onFriendClick(menu, viewer, target, click));
        boolean ignored = privateMessages.isIgnoring(viewer.getUniqueId(), target.getUniqueId());
        menu.button(IGNORE_SLOT, Icons.icon(Material.BARRIER, messages.get("profile-ignore"),
                messages.lines(ignored ? "profile-ignored-lore" : "profile-not-ignored-lore"), ignored), click -> {
            boolean now = privateMessages.toggleIgnore(viewer.getUniqueId(), target.getUniqueId());
            viewer.sendMessage(messages.get(now ? "ignore-on" : "ignore-off", name(target)));
            fill(menu, viewer, target);
        });
    }

    /** Arkadaşlık butonu duruma göre değişir: arkadaş / sana istek gönderdi / istek gönderdin / ekle. */
    private ItemStack friendIcon(UUID viewer, OfflinePlayer target) {
        UUID other = target.getUniqueId();
        String loreKey;
        boolean glow = false;
        if (friends.areFriends(viewer, other)) {
            loreKey = "profile-friends-lore";
            glow = true;
        } else if (friends.isRequested(other, viewer)) {
            loreKey = "profile-incoming-lore";
            glow = true;
        } else if (friends.isRequested(viewer, other)) {
            loreKey = "profile-outgoing-lore";
        } else if (target.getPlayer() != null) {
            loreKey = "profile-add-lore";
        } else {
            loreKey = "profile-add-offline-lore";
        }
        return Icons.icon(Material.PLAYER_HEAD, messages.get("profile-friend"), messages.lines(loreKey), glow);
    }

    private void onFriendClick(Menu menu, Player viewer, OfflinePlayer target, ClickType click) {
        UUID other = target.getUniqueId();
        if (friends.areFriends(viewer.getUniqueId(), other)) {
            if (click == ClickType.SHIFT_RIGHT) {
                friendActions.remove(viewer, other);
            }
        } else if (friends.isRequested(other, viewer.getUniqueId())) {
            if (click.isLeftClick()) {
                friendActions.accept(viewer, other);
            } else if (click.isRightClick()) {
                friendActions.deny(viewer, other);
            }
        } else if (!friends.isRequested(viewer.getUniqueId(), other) && target.getPlayer() != null) {
            friendActions.request(viewer, target.getPlayer());
        }
        fill(menu, viewer, target);
    }

    private List<Component> playtimeLore(OfflinePlayer target) {
        List<Component> lore = new ArrayList<>(messages.lines("profile-playtime-total",
                Placeholder.unparsed("time", Durations.format(Playtime.millis(target)))));
        if (target.getFirstPlayed() > 0) {
            lore.addAll(messages.lines("profile-first-joined",
                    Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(target.getFirstPlayed())))));
        }
        if (target.isOnline()) {
            lore.addAll(messages.lines("profile-online-now"));
        } else if (target.getLastSeen() > 0) {
            long ago = System.currentTimeMillis() - target.getLastSeen();
            lore.addAll(messages.lines("profile-last-seen",
                    Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(target.getLastSeen()))),
                    Placeholder.unparsed("ago", Durations.format(ago))));
        }
        return lore;
    }

    private static String nameOf(OfflinePlayer player) {
        return player.getName() == null ? "?" : player.getName();
    }

    private static TagResolver name(OfflinePlayer player) {
        return Placeholder.unparsed("name", nameOf(player));
    }
}
