package com.sunucu.rpg.social.friends;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.chat.Notifications;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Arkadaşlık işlemlerinin oyuncuya görünen tarafı: komut, panel ve sohbet butonları hepsi bunu kullanır, mesajlar
 * tek yerde. Giriş/çıkış bildirimlerini de gönderir. Ana thread'den kullanılır.
 */
public final class FriendActions implements Listener {

    public static final String COMMAND = "/arkadaş";

    private final Friends friends;
    private final Messages messages;
    private final Notifications notifications;

    public FriendActions(Friends friends, Messages messages, Notifications notifications) {
        this.friends = friends;
        this.messages = messages;
        this.notifications = notifications;
    }

    public void request(Player from, Player to) {
        switch (friends.request(from.getUniqueId(), to.getUniqueId())) {
            case SENT -> {
                from.sendMessage(messages.get("friend-request-sent", name(to)));
                to.sendMessage(messages.get("friend-request-received", name(from))
                        .append(Component.space())
                        .append(button("friend-accept-button", "kabul", from.getName()))
                        .append(Component.space())
                        .append(button("friend-deny-button", "reddet", from.getName())));
                notifications.play(to, Notifications.Type.FRIEND);
            }
            case ACCEPTED -> becameFriends(from, from.getUniqueId(), to.getName(), to.getUniqueId());
            case SELF -> from.sendMessage(messages.get("friend-self"));
            case ALREADY_FRIENDS -> from.sendMessage(messages.get("friend-already", name(to)));
            case ALREADY_SENT -> from.sendMessage(messages.get("friend-already-sent", name(to)));
            case YOUR_LIMIT -> from.sendMessage(messages.get("friend-your-limit", limit()));
            case THEIR_LIMIT -> from.sendMessage(messages.get("friend-their-limit", name(to)));
        }
    }

    public void accept(Player player, UUID requester) {
        String requesterName = nameOf(requester);
        switch (friends.accept(player.getUniqueId(), requester)) {
            case ACCEPTED -> becameFriends(player, player.getUniqueId(), requesterName, requester);
            case NO_REQUEST -> player.sendMessage(messages.get("friend-no-request",
                    Placeholder.unparsed("name", requesterName)));
            case YOUR_LIMIT -> player.sendMessage(messages.get("friend-your-limit", limit()));
        }
    }

    public void deny(Player player, UUID requester) {
        String requesterName = nameOf(requester);
        if (friends.deny(player.getUniqueId(), requester)) {
            player.sendMessage(messages.get("friend-denied", Placeholder.unparsed("name", requesterName)));
        } else {
            player.sendMessage(messages.get("friend-no-request", Placeholder.unparsed("name", requesterName)));
        }
    }

    public void remove(Player player, UUID friend) {
        String friendName = nameOf(friend);
        if (!friends.remove(player.getUniqueId(), friend)) {
            player.sendMessage(messages.get("friend-not-friends", Placeholder.unparsed("name", friendName)));
            return;
        }
        player.sendMessage(messages.get("friend-removed", Placeholder.unparsed("name", friendName)));
    }

    /** İsimden oyuncu bul: sadece verilen kümede (arkadaşlar ya da gelen istekler) arar, büyük/küçük harf fark etmez. */
    public static Optional<UUID> find(Set<UUID> candidates, String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(id -> nameOf(id).toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst();
    }

    public static String nameOf(UUID player) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
        return offline.getName() == null ? "?" : offline.getName();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        announce(player, "friend-joined", true);
        int waiting = friends.requestsTo(player.getUniqueId()).size();
        if (waiting > 0) {
            player.sendMessage(messages.get("friend-requests-waiting",
                            Placeholder.unparsed("count", String.valueOf(waiting)))
                    .clickEvent(ClickEvent.runCommand(COMMAND)));
        }
    }

    // HIGH: veri MONITOR'da (SocialData) bellekten bırakılmadan önce arkadaşlara haber ver.
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        announce(event.getPlayer(), "friend-left", false);
    }

    private void announce(Player player, String key, boolean sound) {
        for (UUID friendId : friends.friendsOf(player.getUniqueId())) {
            Player friend = Bukkit.getPlayer(friendId);
            if (friend != null && notifications.isEnabled(friend, Notifications.Type.FRIEND)) {
                friend.sendMessage(messages.get(key, name(player)));
                if (sound) {
                    notifications.play(friend, Notifications.Type.FRIEND);
                }
            }
        }
    }

    private void becameFriends(Player actor, UUID actorId, String otherName, UUID otherId) {
        actor.sendMessage(messages.get("friend-added", Placeholder.unparsed("name", otherName)));
        Player other = Bukkit.getPlayer(otherId);
        if (other != null) {
            other.sendMessage(messages.get("friend-added", Placeholder.unparsed("name", nameOf(actorId))));
            notifications.play(other, Notifications.Type.FRIEND);
        }
    }

    private Component button(String key, String action, String requesterName) {
        return messages.get(key).clickEvent(ClickEvent.runCommand(COMMAND + " " + action + " " + requesterName));
    }

    private TagResolver limit() {
        return Placeholder.unparsed("max", String.valueOf(friends.maxFriends()));
    }

    private static TagResolver name(Player player) {
        return Placeholder.unparsed("name", player.getName());
    }
}
