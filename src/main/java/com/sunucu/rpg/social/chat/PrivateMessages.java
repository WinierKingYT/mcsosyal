package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.AfkTracker;
import com.sunucu.rpg.social.SocialStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** /msg, /r, /ignore ve /socialspy. */
public final class PrivateMessages {

    public static final String SPY = "rpg.social.chat.spy";
    private static final String STAFF = "rpg.social.chat.staff";

    private final Messages messages;
    private final ChatFilter filter;
    private final Notifications notifications;
    private final AfkTracker afk;
    private final ChatMutes mutes;
    private volatile ChatLog chatLog = ChatLog.NONE;
    private volatile NewPlayerRule newPlayers = NewPlayerRule.NONE;
    // shortcut: spy listesi bellekte, yeniden başlatınca kapanır (güvenli varsayılan).
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
    private final SocialStore store;
    // Bellekte; değişince SocialStore'a yazılır, girişte load ile gelir.
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();

    public PrivateMessages(Messages messages, ChatFilter filter, Notifications notifications, AfkTracker afk,
                           ChatMutes mutes, SocialStore store) {
        this.messages = messages;
        this.filter = filter;
        this.notifications = notifications;
        this.afk = afk;
        this.mutes = mutes;
        this.store = store;
    }

    public boolean isIgnoring(Player who, Player target) {
        return isIgnoring(who.getUniqueId(), target.getUniqueId());
    }

    public boolean isIgnoring(UUID who, UUID target) {
        return ignores.getOrDefault(who, Set.of()).contains(target);
    }

    /** @return true ise artık engelleniyor. */
    public boolean toggleIgnore(Player who, Player target) {
        return toggleIgnore(who.getUniqueId(), target.getUniqueId());
    }

    /** Engellenen çevrimdışı olabilir (profil panelinden). */
    public boolean toggleIgnore(UUID who, UUID target) {
        Set<UUID> set = ignores.computeIfAbsent(who, id -> ConcurrentHashMap.newKeySet());
        boolean nowIgnored = !set.remove(target);
        if (nowIgnored) {
            set.add(target);
        }
        store.setIgnored(who, target, nowIgnored);
        return nowIgnored;
    }

    public void load(UUID player, Set<UUID> ignored) {
        Set<UUID> set = ConcurrentHashMap.newKeySet();
        set.addAll(ignored);
        ignores.put(player, set);
    }

    public void send(Player from, Player to, String text) {
        long muted = mutes.remainingMillis(from.getUniqueId(), System.currentTimeMillis());
        if (muted > 0 && !from.hasPermission(ChatListener.BYPASS)) {
            from.sendMessage(ChatListener.mutedMessage(messages, muted));
            return;
        }
        if (from.equals(to)) {
            from.sendMessage(messages.get("msg-self"));
            return;
        }
        if (isIgnoring(to, from)) {
            from.sendMessage(messages.get("msg-ignored"));
            return;
        }
        // Yeni oyuncu yetkililere yazabilir: yardım isteyebilsin.
        if (newPlayers.applies(from) && !to.hasPermission(STAFF)) {
            from.sendMessage(messages.get("new-player-pm", newPlayers.minutes()));
            return;
        }
        ChatFilter.Verdict verdict = filter.checkContent(text);
        if (!from.hasPermission(ChatListener.BYPASS) && verdict != ChatFilter.Verdict.OK) {
            from.sendMessage(messages.get(ChatListener.messageKey(verdict)));
            return;
        }
        from.sendMessage(messages.get("msg-to",
                Placeholder.unparsed("name", to.getName()), Placeholder.unparsed("message", text)));
        // Gizli susturulan gönderdiğini sanır; mesaj karşıya gitmez, kayda ve spy'a gider.
        if (mutes.isShadowMuted(from.getUniqueId(), System.currentTimeMillis())) {
            spy(from, to, text);
            chatLog.record(from, "msg>" + to.getName() + " (gizli)", text);
            return;
        }
        to.sendMessage(messages.get("msg-from",
                Placeholder.unparsed("name", from.getName()), Placeholder.unparsed("message", text)));
        notifications.play(to, Notifications.Type.PRIVATE_MESSAGE);
        if (afk.isAfk(to)) {
            from.sendMessage(messages.get("msg-afk", Placeholder.unparsed("name", to.getName())));
        }
        lastPartner.put(from.getUniqueId(), to.getUniqueId());
        lastPartner.put(to.getUniqueId(), from.getUniqueId());
        spy(from, to, text);
        chatLog.record(from, "msg>" + to.getName(), text);
    }

    public void setNewPlayerRule(NewPlayerRule rule) {
        this.newPlayers = rule;
    }

    /** Moderasyon bağlar: özel mesajlar da kaydedilir (taciz şikayetleri için). */
    public void setChatLog(ChatLog log) {
        this.chatLog = log == null ? ChatLog.NONE : log;
    }

    /** @return true ise social spy artık açık. */
    public boolean toggleSpy(Player player) {
        if (spies.remove(player.getUniqueId())) {
            return false;
        }
        spies.add(player.getUniqueId());
        return true;
    }

    /** Taciz şikayetleri için: izni olan spy'lar ve konsol, taraf olmadıkları özel mesajları görür. */
    private void spy(Player from, Player to, String text) {
        Component line = messages.get("spy",
                Placeholder.unparsed("from", from.getName()),
                Placeholder.unparsed("to", to.getName()),
                Placeholder.unparsed("message", text));
        for (UUID spyId : spies) {
            Player spy = Bukkit.getPlayer(spyId);
            // İzni sonradan alınan yetkili artık görmez.
            if (spy != null && !spy.equals(from) && !spy.equals(to) && spy.hasPermission(SPY)) {
                spy.sendMessage(line);
            }
        }
        Bukkit.getConsoleSender().sendMessage(line);
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

    public void unload(UUID player) {
        lastPartner.remove(player);
        ignores.remove(player);
        spies.remove(player);
    }
}
