package com.sunucu.rpg.social.party;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.chat.ChatFilter;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.friends.FriendActions;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Parti işlemlerinin oyuncuya görünen tarafı: komut, panel, profil ve sohbet butonları bunu kullanır, mesajlar
 * tek yerde. Üyelere katılma, ayrılma, giriş ve çıkış haberini verir; dost ateşi kapalıysa parti içi hasarı
 * engeller. Ana thread'den kullanılır.
 */
public final class PartyActions implements Listener {

    public static final String COMMAND = "/parti";
    /** Sunucudaki tüm partileri görür ve düzenler (/parti admin). */
    public static final String ADMIN = "rpg.party.admin";
    /** VIP: rpg.party.size.<n> ile n kişilik partiye kadar büyütür. */
    public static final String SIZE_PERMISSION = "rpg.party.size.";

    private final Parties parties;
    private final Messages messages;
    private final Notifications notifications;
    private final PrivateMessages privateMessages;
    private final ChatFilter filter;
    private final int sizeLimit;

    /** sizeLimit: normal oyuncunun ayarlayabileceği en büyük parti boyutu. */
    public PartyActions(Parties parties, Messages messages, Notifications notifications,
                        PrivateMessages privateMessages, ChatFilter filter, int sizeLimit) {
        this.parties = parties;
        this.messages = messages;
        this.notifications = notifications;
        this.privateMessages = privateMessages;
        this.filter = filter;
        this.sizeLimit = Math.clamp(sizeLimit, Parties.MIN_SIZE, Parties.MAX_SIZE);
    }

    /** Listede ve panelde görünen ad: partinin renginde "[ETİKET] Eleven Partisi". */
    public Component displayName(Parties.Party party) {
        Component tag = party.tag() == null ? Component.empty()
                : messages.get("party-tag", Placeholder.unparsed("tag", party.tag()));
        return messages.get("party-display", Placeholder.unparsed("party", party.name()),
                Placeholder.component("tag", tag), Placeholder.styling("pcolor", color(party).color()));
    }

    public static PartyColor color(Parties.Party party) {
        return PartyColor.from(party.color()).orElse(PartyColor.AQUA);
    }

    /** Lider kendi partisini, admin her partiyi düzenler. */
    public boolean canEdit(Player actor, Parties.Party party) {
        return party.leader().equals(actor.getUniqueId()) || actor.hasPermission(ADMIN);
    }

    /**
     * Bu oyuncunun ayarlayabileceği en büyük parti boyutu: config → party.size-limit; VIP için rpg.party.size.<n>
     * (ör. rpg.party.size.10) daha yükseğe çıkarır; admin her zaman Parties.MAX_SIZE.
     */
    public int sizeLimit(Player player) {
        if (player.hasPermission(ADMIN)) {
            return Parties.MAX_SIZE;
        }
        for (int size = Parties.MAX_SIZE; size > sizeLimit; size--) {
            if (player.hasPermission(SIZE_PERMISSION + size)) {
                return size;
            }
        }
        return sizeLimit;
    }

    /** Etiket ve açıklama oyuncu metni: sohbet filtresinden (küfür, reklam) geçmeli. null: sil. */
    public void setTag(Player actor, long partyId, String tag) {
        if (tag != null && filter.checkContent(tag) != ChatFilter.Verdict.OK) {
            actor.sendMessage(messages.get("party-text-blocked"));
            return;
        }
        edit(actor, partyId, () -> parties.setTag(partyId, tag), "party-tag-set", "party-tag-cleared",
                "party-tag-invalid");
    }

    public void setDescription(Player actor, long partyId, String description) {
        if (description != null && filter.checkContent(description) != ChatFilter.Verdict.OK) {
            actor.sendMessage(messages.get("party-text-blocked"));
            return;
        }
        edit(actor, partyId, () -> parties.setDescription(partyId, description), "party-description-set",
                "party-description-cleared", "party-description-invalid");
    }

    /** Sol tık sıradaki renk, sağ tık önceki. */
    public void cycleColor(Player actor, long partyId, boolean forward) {
        edit(actor, partyId, () -> parties.byId(partyId).map(party -> parties.setColor(partyId,
                color(party).next(forward).key())).orElse(Parties.EditResult.GONE), "party-color-set", null, null);
    }

    /** Sol tık +1, sağ tık -1; düzenleyenin sınırını (VIP, admin) aşamaz. */
    public void changeSize(Player actor, long partyId, int delta) {
        Parties.Party party = parties.byId(partyId).orElse(null);
        if (party == null) {
            actor.sendMessage(messages.get("party-gone"));
            return;
        }
        int size = party.size() + delta;
        if (delta > 0 && size > sizeLimit(actor)) {
            actor.sendMessage(messages.get("party-size-limit", Placeholder.unparsed("max",
                    String.valueOf(sizeLimit(actor)))));
            return;
        }
        edit(actor, partyId, () -> parties.setSize(partyId, size), "party-size-set", null, "party-size-invalid");
    }

    /** Uygunsuz etiket ve açıklamayı admin temizler. */
    public void clearTexts(Player admin, long partyId) {
        if (!admin.hasPermission(ADMIN)) {
            return;
        }
        parties.setTag(partyId, null);
        parties.setDescription(partyId, null);
        admin.sendMessage(messages.get("party-admin-cleared"));
    }

    private void edit(Player actor, long partyId, Supplier<Parties.EditResult> change, String setKey,
                      String clearedKey, String invalidKey) {
        Parties.Party before = parties.byId(partyId).orElse(null);
        if (before == null) {
            actor.sendMessage(messages.get("party-gone"));
            return;
        }
        if (!canEdit(actor, before)) {
            actor.sendMessage(messages.get("party-not-leader"));
            return;
        }
        switch (change.get()) {
            case SET -> parties.byId(partyId).ifPresent(party -> tellMembers(party, null, messages.get(setKey,
                    Placeholder.component("party", displayName(party)),
                    Placeholder.unparsed("text", party.description() == null ? "" : party.description()),
                    Placeholder.unparsed("size", String.valueOf(party.size())),
                    Placeholder.component("color", messages.get("party-color-" + color(party).key())))));
            case CLEARED -> tellMembers(before, null, messages.get(clearedKey));
            case INVALID -> actor.sendMessage(messages.get(invalidKey,
                    Placeholder.unparsed("max", String.valueOf(Parties.MAX_DESCRIPTION)),
                    Placeholder.unparsed("min", String.valueOf(Math.max(Parties.MIN_SIZE, before.members().size())))));
            case TAKEN -> actor.sendMessage(messages.get("party-tag-taken"));
            case GONE -> actor.sendMessage(messages.get("party-gone"));
        }
    }

    /** Parti adı kurucunun adıyla kurulur (config → party-name); lider değişse de ad kalır. */
    public void create(Player player) {
        switch (parties.create(player.getUniqueId(), messages.plain("party-name", name(player)))) {
            case CREATED -> player.sendMessage(messages.get("party-created"));
            case ALREADY_IN_PARTY -> player.sendMessage(messages.get("party-already-in-party"));
        }
    }

    public void invite(Player from, Player to) {
        // Engelleyen oyuncuya davet gitmez; gönderene söylenmez (engellendiğini öğrenmesin).
        boolean ignored = privateMessages.isIgnoring(to, from);
        Parties.InviteResult result = ignored && parties.party(from.getUniqueId()).isPresent()
                ? Parties.InviteResult.SENT : parties.invite(from.getUniqueId(), to.getUniqueId(),
                System.currentTimeMillis());
        switch (result) {
            case SENT -> {
                from.sendMessage(messages.get("party-invite-sent", name(to)));
                if (!ignored) {
                    to.sendMessage(messages.get("party-invite-received", name(from), partyName(from.getUniqueId()))
                            .append(Component.space()).append(button("party-accept-button", "kabul", from.getName()))
                            .append(Component.space()).append(button("party-deny-button", "reddet", from.getName())));
                    notifications.play(to, Notifications.Type.FRIEND);
                }
            }
            case SELF -> from.sendMessage(messages.get("party-self"));
            case NOT_IN_PARTY -> from.sendMessage(messages.get("party-none"));
            case NO_PERMISSION -> from.sendMessage(messages.get("party-not-leader"));
            case ALREADY_IN_PARTY -> from.sendMessage(messages.get("party-target-in-party", name(to)));
            case ALREADY_INVITED -> from.sendMessage(messages.get("party-already-invited", name(to)));
            case FULL -> from.sendMessage(messages.get("party-full", size(from.getUniqueId())));
        }
    }

    public void accept(Player player, UUID inviter) {
        joined(player, parties.accept(player.getUniqueId(), inviter, System.currentTimeMillis()), nameOf(inviter));
    }

    /** Açık partiye listeden ya da /parti katıl ile. */
    public void joinOpen(Player player, long partyId) {
        joined(player, parties.joinOpen(player.getUniqueId(), partyId), Placeholder.unparsed("name", "?"));
    }

    private void joined(Player player, Parties.JoinResult result, TagResolver inviter) {
        switch (result) {
            case JOINED -> tellParty(player.getUniqueId(), messages.get("party-joined", name(player)));
            case NO_INVITE -> player.sendMessage(messages.get("party-no-invite", inviter));
            case GONE -> player.sendMessage(messages.get("party-gone"));
            case CLOSED -> player.sendMessage(messages.get("party-closed"));
            case ALREADY_IN_PARTY -> player.sendMessage(messages.get("party-already-in-party"));
            case FULL -> player.sendMessage(messages.get("party-full"));
        }
    }

    public void deny(Player player, UUID inviter) {
        if (!parties.deny(player.getUniqueId(), inviter)) {
            player.sendMessage(messages.get("party-no-invite", nameOf(inviter)));
            return;
        }
        player.sendMessage(messages.get("party-denied", nameOf(inviter)));
        Player from = Bukkit.getPlayer(inviter);
        if (from != null) {
            from.sendMessage(messages.get("party-invite-denied", name(player)));
        }
    }

    public void leave(Player player) {
        Parties.Party before = parties.party(player.getUniqueId()).orElse(null);
        Parties.LeaveResult result = parties.leave(player.getUniqueId());
        if (result == Parties.LeaveResult.NOT_IN_PARTY) {
            player.sendMessage(messages.get("party-none"));
            return;
        }
        player.sendMessage(messages.get(result == Parties.LeaveResult.DISBANDED ? "party-disbanded" : "party-you-left"));
        tellMembers(before, player.getUniqueId(), messages.get("party-left", name(player)));
        if (result == Parties.LeaveResult.LEFT_NEW_LEADER) {
            before.members().stream().filter(id -> !id.equals(player.getUniqueId())).findFirst()
                    .flatMap(parties::party).ifPresent(party ->
                    tellParty(party.leader(), messages.get("party-new-leader", nameOf(party.leader()))));
        }
    }

    /** Lider kendi partisini, admin her partiyi kapatır. */
    public void disband(Player actor, long partyId) {
        Parties.Party before = parties.byId(partyId).orElse(null);
        if (before == null) {
            actor.sendMessage(messages.get("party-gone"));
            return;
        }
        if (!canEdit(actor, before)) {
            actor.sendMessage(messages.get("party-not-leader"));
            return;
        }
        if (parties.disband(partyId)) {
            tellMembers(before, null, messages.get("party-disbanded"));
            if (!before.members().contains(actor.getUniqueId())) {
                actor.sendMessage(messages.get("party-disbanded"));
            }
        }
    }

    public void kick(Player leader, UUID target) {
        switch (parties.kick(leader.getUniqueId(), target)) {
            case KICKED -> {
                Player kicked = Bukkit.getPlayer(target);
                if (kicked != null) {
                    kicked.sendMessage(messages.get("party-you-kicked", name(leader)));
                }
                tellParty(leader.getUniqueId(), messages.get("party-kicked", nameOf(target)));
            }
            case NOT_LEADER -> leader.sendMessage(messages.get("party-not-leader"));
            case NOT_MEMBER -> leader.sendMessage(messages.get("party-not-member", nameOf(target)));
            case SELF -> leader.sendMessage(messages.get("party-self"));
        }
    }

    public void promote(Player leader, UUID target) {
        switch (parties.promote(leader.getUniqueId(), target)) {
            case PROMOTED -> tellParty(target, messages.get("party-new-leader", nameOf(target)));
            case NOT_LEADER -> leader.sendMessage(messages.get("party-not-leader"));
            case NOT_MEMBER -> leader.sendMessage(messages.get("party-not-member", nameOf(target)));
            case SELF -> leader.sendMessage(messages.get("party-self"));
        }
    }

    /** Ayar değişince partideki herkese duyurulur. */
    public void toggle(Player actor, long partyId, Parties.Setting setting) {
        Parties.Party before = parties.byId(partyId).orElse(null);
        if (before == null) {
            actor.sendMessage(messages.get("party-gone"));
            return;
        }
        if (!canEdit(actor, before)) {
            actor.sendMessage(messages.get("party-not-leader"));
            return;
        }
        Parties.ToggleResult result = parties.toggle(partyId, setting);
        if (result == Parties.ToggleResult.GONE) {
            actor.sendMessage(messages.get("party-gone"));
            return;
        }
        tellMembers(before, null, messages.get("party-setting-changed",
                Placeholder.component("setting", messages.get(settingKey(setting))),
                Placeholder.component("value", messages.get(result == Parties.ToggleResult.ON
                        ? "party-setting-on" : "party-setting-off"))));
    }

    static String settingKey(Parties.Setting setting) {
        return switch (setting) {
            case OPEN -> "party-setting-open";
            case MEMBERS_INVITE -> "party-setting-members-invite";
            case FRIENDLY_FIRE -> "party-setting-friendly-fire";
        };
    }

    // shortcut: sadece oyuncu ve oyuncunun attığı mermi (ok, kartopu...); büyü/iksir bulutu gibi dolaylı hasar
    // RPG savaş sistemine kalır (Parties#protects ile aynı kontrolü yapabilir).
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && attacker(event.getDamager()) instanceof Player attacker
                && !attacker.equals(victim) && parties.protects(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private static Entity attacker(Entity damager) {
        return damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter
                ? shooter : damager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tellOthers(player.getUniqueId(), messages.get("party-member-online", name(player)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        tellOthers(player.getUniqueId(), messages.get("party-member-offline", name(player)));
    }

    /** Partideki herkese (kendisi dahil). */
    private void tellParty(UUID member, Component message) {
        parties.party(member).ifPresent(party -> tellMembers(party, null, message));
    }

    private void tellOthers(UUID member, Component message) {
        parties.party(member).ifPresent(party -> tellMembers(party, member, message));
    }

    private static void tellMembers(Parties.Party party, UUID except, Component message) {
        if (party == null) {
            return;
        }
        for (UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && !id.equals(except)) {
                member.sendMessage(message);
            }
        }
    }

    private TagResolver partyName(UUID member) {
        return Placeholder.unparsed("party", parties.party(member).map(Parties.Party::name).orElse("?"));
    }

    private Component button(String key, String action, String inviterName) {
        return messages.get(key).clickEvent(ClickEvent.runCommand(COMMAND + " " + action + " " + inviterName));
    }

    private TagResolver size(UUID member) {
        return Placeholder.unparsed("max", parties.party(member).map(party -> String.valueOf(party.size())).orElse("?"));
    }

    private static TagResolver name(Player player) {
        return Placeholder.unparsed("name", player.getName());
    }

    private static TagResolver nameOf(UUID player) {
        return Placeholder.unparsed("name", FriendActions.nameOf(player));
    }
}
