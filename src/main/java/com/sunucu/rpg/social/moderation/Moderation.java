package com.sunucu.rpg.social.moderation;

import com.sunucu.rpg.core.Durations;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Players;
import com.sunucu.rpg.social.chat.ChatMutes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cezalar: mute, ban (kalıcı ya da süreli), kick, warn ve kaldırma. Her ceza geçmişe yazılır, yetkililere duyurulur.
 * Ban girişten önce kontrol edilir. Mute'un uygulanması ChatMutes'ta (spam susturmasıyla aynı yer).
 * Ana thread'den çağrılır; veritabanı sonuçları ana thread'e döner.
 */
public final class Moderation implements Listener {

    public static final String EXEMPT = "rpg.moderation.exempt";
    public static final String NOTIFY = "rpg.moderation.notify";

    private static final long LOGIN_CHECK_TIMEOUT_SECONDS = 5;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ModerationStore store;
    private final ChatMutes mutes;
    private final Messages messages;
    private final ModerationMenu menu;
    private final WarnEscalation escalation;
    private final long warnWindowMillis;

    public Moderation(JavaPlugin plugin, ModerationStore store, ChatMutes mutes, Messages messages,
                      WarnEscalation escalation, long warnWindowMillis) {
        this.plugin = plugin;
        this.store = store;
        this.mutes = mutes;
        this.messages = messages;
        this.menu = new ModerationMenu(messages);
        this.escalation = escalation;
        this.warnWindowMillis = warnWindowMillis;
    }

    /** durationMillis 0: kalıcı. */
    public void mute(CommandSender issuer, OfflinePlayer target, long durationMillis, String reason) {
        if (allowed(issuer, target)) {
            applyMute(target, durationMillis, reason, issuer.getName());
        }
    }

    private void applyMute(OfflinePlayer target, long durationMillis, String reason, String issuer) {
        long now = System.currentTimeMillis();
        mutes.mute(target.getUniqueId(), durationMillis == 0 ? ChatMutes.PERMANENT : now + durationMillis);
        record(target, Punishment.Type.MUTE, reason, issuer, now, durationMillis);
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(messages.get("mod-muted-target", reason(reason), time(durationMillis)));
        }
        notifyStaff("mod-notice-mute", issuer, target, reason, durationMillis);
    }

    /**
     * Gizli susturma: oyuncu yazmaya devam eder, mesajını sadece kendisi ve susturma yetkisi olanlar görür.
     * Oyuncuya söylenmez; trol fark etmeden yavaşlasın. /unmute ikisini de kaldırır.
     */
    public void shadowMute(CommandSender issuer, OfflinePlayer target, long durationMillis, String reason) {
        if (!allowed(issuer, target)) {
            return;
        }
        long now = System.currentTimeMillis();
        mutes.shadowMute(target.getUniqueId(), durationMillis == 0 ? ChatMutes.PERMANENT : now + durationMillis);
        record(target, Punishment.Type.SHADOW_MUTE, reason, issuer.getName(), now, durationMillis);
        notifyStaff("mod-notice-shadowmute", issuer.getName(), target, reason, durationMillis);
    }

    /** Spam susturması da geçmişe yazılsın (ChatListener çağırır). */
    public void recordAutoMute(Player target, long durationMillis) {
        record(target, Punishment.Type.MUTE, messages.plain("mod-auto-spam-reason"),
                messages.plain("mod-auto-issuer"), System.currentTimeMillis(), durationMillis);
    }

    /** Çevrimdışı oyuncunun susturması bellekte değil, veritabanında: ikisinden biri yeterli. */
    public void unmute(CommandSender issuer, OfflinePlayer target) {
        boolean inMemory = mutes.unmute(target.getUniqueId());
        long now = System.currentTimeMillis();
        CompletableFuture<Integer> revokes = store.revokeActive(target.getUniqueId(), Punishment.Type.MUTE,
                issuer.getName(), now).thenCombine(store.revokeActive(target.getUniqueId(),
                Punishment.Type.SHADOW_MUTE, issuer.getName(), now), Integer::sum);
        then(issuer, revokes, revoked -> {
            boolean was = inMemory || revoked > 0;
            issuer.sendMessage(messages.get(was ? "unmuted" : "not-muted", name(target)));
            if (was) {
                notifyStaff("mod-notice-unmute", issuer.getName(), target, "", 0);
            }
        });
    }

    public void ban(CommandSender issuer, OfflinePlayer target, long durationMillis, String reason) {
        if (allowed(issuer, target)) {
            applyBan(target, durationMillis, reason, issuer.getName());
        }
    }

    private void applyBan(OfflinePlayer target, long durationMillis, String reason, String issuer) {
        record(target, Punishment.Type.BAN, reason, issuer, System.currentTimeMillis(), durationMillis);
        Player online = target.getPlayer();
        if (online != null) {
            online.kick(banScreen(reason, durationMillis == 0 ? Long.MAX_VALUE : durationMillis, issuer));
        }
        notifyStaff("mod-notice-ban", issuer, target, reason, durationMillis);
    }

    public void unban(CommandSender issuer, OfflinePlayer target) {
        then(issuer, store.revokeActive(target.getUniqueId(), Punishment.Type.BAN, issuer.getName(),
                System.currentTimeMillis()), revoked -> {
            issuer.sendMessage(messages.get(revoked > 0 ? "mod-unbanned" : "mod-not-banned", name(target)));
            if (revoked > 0) {
                notifyStaff("mod-notice-unban", issuer.getName(), target, "", 0);
            }
        });
    }

    public void kick(CommandSender issuer, Player target, String reason) {
        if (allowed(issuer, target)) {
            applyKick(target, reason, issuer.getName());
        }
    }

    private void applyKick(Player target, String reason, String issuer) {
        record(target, Punishment.Type.KICK, reason, issuer, System.currentTimeMillis(), 0);
        target.kick(messages.get("mod-kick-screen", reason(reason)));
        notifyStaff("mod-notice-kick", issuer, target, reason, 0);
    }

    public void warn(CommandSender issuer, OfflinePlayer target, String reason) {
        if (!allowed(issuer, target)) {
            return;
        }
        long now = System.currentTimeMillis();
        record(target, Punishment.Type.WARN, reason, issuer.getName(), now, 0);
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage(messages.get("mod-warned-target", reason(reason)));
        }
        notifyStaff("mod-notice-warn", issuer.getName(), target, reason, 0);
        if (!escalation.isEmpty()) {
            // Veritabanı tek thread: sayım az önceki uyarının yazılmasından sonra çalışır, onu da sayar.
            then(issuer, store.countSince(target.getUniqueId(), Punishment.Type.WARN, now - warnWindowMillis),
                    count -> escalation.at(count).ifPresent(rule -> escalate(target, rule)));
        }
    }

    private void escalate(OfflinePlayer target, WarnEscalation.Rule rule) {
        String issuer = messages.plain("mod-auto-issuer");
        String reason = messages.plain("mod-auto-warn-reason",
                Placeholder.unparsed("count", String.valueOf(rule.warnings())));
        switch (rule.action()) {
            case MUTE -> applyMute(target, rule.durationMillis(), reason, issuer);
            case BAN -> applyBan(target, rule.durationMillis(), reason, issuer);
            case KICK -> {
                Player online = target.getPlayer();
                if (online != null) {
                    applyKick(online, reason, issuer);
                }
            }
            default -> plugin.getLogger().warning("Otomatik cezada desteklenmeyen tür: " + rule.action());
        }
    }

    /** Oyuncuya pencere, konsola sohbet satırları. */
    public void showHistory(CommandSender viewer, OfflinePlayer target) {
        then(viewer, store.history(target.getUniqueId(), ModerationMenu.PAGE_SIZE), history -> {
            if (history.isEmpty()) {
                viewer.sendMessage(messages.get("mod-history-empty", name(target)));
                return;
            }
            if (viewer instanceof Player player) {
                menu.history(player, target, history, player.hasPermission(ModerationCommands.CHATLOG)
                        ? () -> showChatLog(player, target, ModerationMenu.PAGE_SIZE) : null);
                return;
            }
            viewer.sendMessage(messages.get("mod-history-header", name(target),
                    Placeholder.unparsed("count", String.valueOf(history.size()))));
            long now = System.currentTimeMillis();
            for (Punishment entry : history) {
                viewer.sendMessage(messages.get("mod-history-line",
                        Placeholder.unparsed("id", String.valueOf(entry.id())),
                        Placeholder.component("type", messages.get("mod-type-" + entry.type().name().toLowerCase(Locale.ROOT))),
                        Placeholder.component("time", entry.isTimed()
                                ? timeComponent(entry.isPermanent() ? 0 : entry.expires() - entry.created())
                                : Component.empty()),
                        reason(entry.reason()),
                        Placeholder.unparsed("issuer", entry.issuer()),
                        Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(entry.created()))),
                        Placeholder.component("status", messages.get("mod-status-"
                                + entry.status(now).name().toLowerCase(Locale.ROOT)))));
            }
        });
    }

    /** /chatlog: son mesajlar, eskiden yeniye; özel mesajlar "msg>Alıcı" kanalıyla görünür. */
    public void showChatLog(CommandSender viewer, OfflinePlayer target, int limit) {
        then(viewer, store.chatLog(target.getUniqueId(), limit), lines -> {
            if (lines.isEmpty()) {
                viewer.sendMessage(messages.get("mod-chatlog-empty", name(target)));
                return;
            }
            if (viewer instanceof Player player) {
                menu.chatLog(player, target, lines, player.hasPermission(ModerationCommands.HISTORY)
                        ? () -> showHistory(player, target) : null);
                return;
            }
            viewer.sendMessage(messages.get("mod-chatlog-header", name(target),
                    Placeholder.unparsed("count", String.valueOf(lines.size()))));
            for (ModerationStore.ChatLine line : lines) {
                viewer.sendMessage(messages.get("mod-chatlog-line",
                        Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(line.time()))),
                        Placeholder.unparsed("channel", line.channel()),
                        Placeholder.unparsed("message", line.message())));
            }
        });
    }

    // HIGH: SocialData (MONITOR) yasaklı oyuncunun verisini boşuna yüklemesin.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            Optional<Punishment> ban = store.activeBan(event.getUniqueId(), now)
                    .get(LOGIN_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ban.ifPresent(found -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    banScreen(found.reason(), found.remaining(now), found.issuer())));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Ban kontrol edilemezse içeri alma: yasaklı biri veritabanı hatasıyla giremesin.
            plugin.getLogger().log(Level.SEVERE, "Ban kontrolü yapılamadı, giriş reddedildi: " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("data-load-failed"));
        }
    }

    /** remainingMillis Long.MAX_VALUE: kalıcı. */
    private Component banScreen(String reason, long remainingMillis, String issuer) {
        return messages.get("mod-ban-screen", reason(reason),
                time(remainingMillis == Long.MAX_VALUE ? 0 : remainingMillis),
                Placeholder.unparsed("issuer", issuer));
    }

    /** Kendini cezalandırma yok; muaf yetkiliyi (ör. admin) sadece konsol cezalandırabilir. */
    private boolean allowed(CommandSender issuer, OfflinePlayer target) {
        if (issuer instanceof Player player && player.getUniqueId().equals(target.getUniqueId())) {
            issuer.sendMessage(messages.get("mod-self"));
            return false;
        }
        // Çevrimdışı hedefin izinleri okunamaz; muafiyet varsayılan olarak op'tan geldiği için op'lar da korunur.
        // shortcut: izin plugin'iyle (LuckPerms) op olmayan birine muafiyet verilirse, o kişi çevrimdışıyken korunmaz;
        // o zaman muafiyeti izin plugin'inin API'sinden oku.
        Player online = target.getPlayer();
        boolean exempt = online != null ? online.hasPermission(EXEMPT) : target.isOp();
        if (issuer instanceof Player && exempt) {
            issuer.sendMessage(messages.get("mod-exempt", name(target)));
            return false;
        }
        return true;
    }

    private void record(OfflinePlayer target, Punishment.Type type, String reason, String issuer, long now,
                        long durationMillis) {
        store.addPunishment(target.getUniqueId(), Players.name(target), type, reason, issuer, now,
                durationMillis == 0 ? 0 : now + durationMillis).whenComplete((id, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Ceza geçmişe yazılamadı: " + type + " " + Players.name(target), error);
            }
        });
    }

    private void notifyStaff(String key, String issuer, OfflinePlayer target, String reason, long durationMillis) {
        Component notice = messages.get(key, Placeholder.unparsed("issuer", issuer),
                Placeholder.unparsed("target", Players.name(target)), reason(reason), time(durationMillis));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(NOTIFY)) {
                staff.sendMessage(notice);
            }
        }
        Bukkit.getConsoleSender().sendMessage(notice);
    }

    /** Veritabanı sonucu ana thread'de işlenir; hata olursa isteyene söylenir ve loglanır. */
    <T> void then(CommandSender viewer, CompletableFuture<T> future, Consumer<T> action) {
        future.whenComplete((value, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Moderasyon veritabanı hatası", error);
                viewer.sendMessage(messages.get("mod-error"));
            } else {
                action.accept(value);
            }
        }));
    }

    private Component timeComponent(long durationMillis) {
        return durationMillis == 0 ? messages.get("mod-permanent") : Component.text(Durations.format(durationMillis));
    }

    private TagResolver time(long durationMillis) {
        return Placeholder.component("time", timeComponent(durationMillis));
    }

    /** Sebep yetkiliden gelir ama oyunculara gösterilir: düz metin. */
    private static TagResolver reason(String reason) {
        return Placeholder.unparsed("reason", reason);
    }

    private static TagResolver name(OfflinePlayer player) {
        return Placeholder.unparsed("name", Players.name(player));
    }
}
