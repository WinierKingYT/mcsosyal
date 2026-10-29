package com.sunucu.rpg.social.friends;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * Arkadaşlar paneli (6 sıra): üstte en fazla 45 arkadaş (çevrimiçiler önce), en altta geri, istekler ve bilgi.
 * İstekler paneli: sol tık kabul, sağ tık reddet.
 */
public final class FriendsMenu {

    private static final int ROWS = 6;
    private static final int LIST_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int REQUESTS_SLOT = 49;
    private static final int INFO_SLOT = 53;

    private final Friends friends;
    private final FriendActions actions;
    private final Messages messages;
    private final Consumer<Player> openParent;

    public FriendsMenu(Friends friends, FriendActions actions, Messages messages, Consumer<Player> openParent) {
        this.friends = friends;
        this.actions = actions;
        this.messages = messages;
        this.openParent = openParent;
    }

    public void open(Player player) {
        Menu menu = new Menu(ROWS, messages.get("friends-title", count(player)));
        fill(menu, player);
        menu.open(player);
    }

    private void fill(Menu menu, Player player) {
        List<UUID> list = sorted(friends.friendsOf(player.getUniqueId()));
        for (int slot = 0; slot < LIST_SIZE; slot++) {
            if (slot >= list.size()) {
                menu.set(slot, null);
                continue;
            }
            UUID friendId = list.get(slot);
            Player online = Bukkit.getPlayer(friendId);
            String name = FriendActions.nameOf(friendId);
            List<Component> lore = new ArrayList<>(messages.lines(online != null ? "friend-online" : "friend-offline"));
            lore.add(Component.empty());
            if (online != null) {
                lore.addAll(messages.lines("friend-click-message"));
            }
            lore.addAll(messages.lines("friend-click-remove"));
            menu.button(slot, Icons.head(Bukkit.getOfflinePlayer(friendId),
                            messages.get("friend-head", Placeholder.unparsed("name", name)), lore),
                    click -> onFriendClick(menu, player, friendId, name, click));
        }
        menu.button(BACK_SLOT, Icons.icon(Material.ARROW, messages.get("menu-back"), messages.lines("menu-back-lore")),
                click -> openParent.accept(player));
        int waiting = friends.requestsTo(player.getUniqueId()).size();
        menu.button(REQUESTS_SLOT, Icons.icon(Material.WRITABLE_BOOK,
                        messages.get("friend-requests", Placeholder.unparsed("count", String.valueOf(waiting))),
                        messages.lines("friend-requests-lore"), waiting > 0),
                click -> openRequests(player));
        menu.set(INFO_SLOT, Icons.icon(Material.OAK_SIGN, messages.get("friend-info"),
                messages.lines("friend-info-lore", Placeholder.unparsed("max", String.valueOf(friends.maxFriends())))));
    }

    /** Sol tık: mesaj yaz (çevrimiçiyse). Shift + sağ tık: arkadaşlıktan çıkar (yanlışlıkla silinmesin). */
    private void onFriendClick(Menu menu, Player player, UUID friendId, String name, ClickType click) {
        if (click == ClickType.SHIFT_RIGHT) {
            actions.remove(player, friendId);
            fill(menu, player);
        } else if (click.isLeftClick() && Bukkit.getPlayer(friendId) != null) {
            player.closeInventory();
            player.sendMessage(messages.get("friend-message-prompt", Placeholder.unparsed("name", name))
                    .clickEvent(ClickEvent.suggestCommand("/msg " + name + " ")));
        }
    }

    private void openRequests(Player player) {
        Menu menu = new Menu(ROWS, messages.get("friend-requests-title"));
        fillRequests(menu, player);
        menu.open(player);
    }

    private void fillRequests(Menu menu, Player player) {
        List<UUID> list = sorted(friends.requestsTo(player.getUniqueId()));
        for (int slot = 0; slot < LIST_SIZE; slot++) {
            if (slot >= list.size()) {
                menu.set(slot, null);
                continue;
            }
            UUID requester = list.get(slot);
            String name = FriendActions.nameOf(requester);
            menu.button(slot, Icons.head(Bukkit.getOfflinePlayer(requester),
                            messages.get("friend-head", Placeholder.unparsed("name", name)),
                            messages.lines("friend-request-lore")),
                    click -> {
                        if (click.isLeftClick()) {
                            actions.accept(player, requester);
                        } else if (click.isRightClick()) {
                            actions.deny(player, requester);
                        }
                        fillRequests(menu, player);
                    });
        }
        menu.button(BACK_SLOT, Icons.icon(Material.ARROW, messages.get("menu-back"),
                messages.lines("friend-requests-back-lore")), click -> open(player));
    }

    /** Çevrimiçiler önce, sonra isim sırası. */
    private static List<UUID> sorted(Set<UUID> ids) {
        return ids.stream()
                .sorted(Comparator.comparing((UUID id) -> Bukkit.getPlayer(id) == null)
                        .thenComparing(id -> FriendActions.nameOf(id).toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** Başlıkta "Arkadaşlar (3/45)". */
    private TagResolver count(Player player) {
        return TagResolver.resolver(
                Placeholder.unparsed("count", String.valueOf(friends.friendsOf(player.getUniqueId()).size())),
                Placeholder.unparsed("max", String.valueOf(friends.maxFriends())));
    }
}
