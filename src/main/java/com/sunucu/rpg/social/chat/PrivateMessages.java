package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** /msg, /r ve /ignore. */
public final class PrivateMessages {

    private final Messages messages;
    private final ChatFilter filter;
    private final Notifications notifications;
    // shortcut: engellemeler bellekte, yeniden başlatınca silinir; veritabanı katmanı gelince kalıcı yap.
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public PrivateMessages(Messages messages, ChatFilter filter, Notifications notifications) {
        this.messages = messages;
        this.filter = filter;
        this.notifications = notifications;
    }

    public boolean isIgnoring(Player who, Player target) {
        return ignores.getOrDefault(who.getUniqueId(), Set.of()).contains(target.getUniqueId());
    }

    /** @return true ise artık engelleniyor. */
    public boolean toggleIgnore(Player who, Player target) {
        Set<UUID> set = ignores.computeIfAbsent(who.getUniqueId(), id -> ConcurrentHashMap.newKeySet());
        if (set.remove(target.getUniqueId())) {
            return false;
        }
        set.add(target.getUniqueId());
        return true;
    }

    public void send(Player from, Player to, String text) {
        if (from.equals(to)) {
            from.sendMessage(messages.get("msg-self"));
            return;
        }
        if (isIgnoring(to, from)) {
            from.sendMessage(messages.get("msg-ignored"));
            return;
        }
        ChatFilter.Verdict verdict = filter.checkContent(text);
        if (!from.hasPermission(ChatListener.BYPASS) && verdict != ChatFilter.Verdict.OK) {
            from.sendMessage(messages.get(ChatListener.messageKey(verdict)));
            return;
        }
        from.sendMessage(messages.get("msg-to",
                Placeholder.unparsed("name", to.getName()), Placeholder.unparsed("message", text)));
        to.sendMessage(messages.get("msg-from",
                Placeholder.unparsed("name", from.getName()), Placeholder.unparsed("message", text)));
        notifications.play(to, Notifications.Type.PRIVATE_MESSAGE);
        lastPartner.put(from.getUniqueId(), to.getUniqueId());
        lastPartner.put(to.getUniqueId(), from.getUniqueId());
    }

    public void reply(Player from, String text) {
        UUID partnerId = lastPartner.get(from.getUniqueId());
        if (partnerId == null) {
            from.sendMessage(messages.get("reply-none"));
            return;
        }
        Player partner = Bukkit.getPlayer(partnerId);
        if (partner == null) {
            from.sendMessage(messages.get("reply-offline"));
            return;
        }
        send(from, partner, text);
    }

    public void forget(UUID player) {
        lastPartner.remove(player);
    }
}
