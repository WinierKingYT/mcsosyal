package com.sunucu.rpg.social.moderation;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Players;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Şikayetler: oyuncu /şikayet ile bildirir (bekleme süreli), çevrimiçi yetkililere butonlu bildirim gider,
 * yetkili /şikayetler ile açıkları görür ve kapatır. Giren yetkiliye açık şikayet sayısı söylenir.
 */
public final class Reports implements Listener {

    public static final String PERMISSION = "rpg.moderation.reports";

    private static final int LIST_LIMIT = 10;

    private final Moderation moderation;
    private final ModerationStore store;
    private final Messages messages;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();

    public Reports(Moderation moderation, ModerationStore store, Messages messages, long cooldownSeconds) {
        this.moderation = moderation;
        this.store = store;
        this.messages = messages;
        this.cooldownMillis = cooldownSeconds * 1000;
    }

    public void report(Player reporter, OfflinePlayer target, String reason) {
        if (reporter.getUniqueId().equals(target.getUniqueId())) {
            reporter.sendMessage(messages.get("report-self"));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastReport.get(reporter.getUniqueId());
        if (last != null && now - last < cooldownMillis) {
            long seconds = (cooldownMillis - (now - last) + 999) / 1000;
            reporter.sendMessage(messages.get("report-cooldown", Placeholder.unparsed("seconds", String.valueOf(seconds))));
            return;
        }
        lastReport.put(reporter.getUniqueId(), now);
        String targetName = Players.name(target);
        moderation.then(reporter, store.addReport(reporter.getUniqueId(), reporter.getName(), target.getUniqueId(),
                targetName, reason, now), id -> {
            reporter.sendMessage(messages.get("report-sent", Placeholder.unparsed("name", targetName)));
            Component notice = line(id, reporter.getName(), targetName, reason);
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission(PERMISSION)) {
                    staff.sendMessage(notice);
                }
            }
            Bukkit.getConsoleSender().sendMessage(notice);
        });
    }

    public void list(CommandSender viewer) {
        moderation.then(viewer, store.openReports(LIST_LIMIT), reports -> {
            if (reports.isEmpty()) {
                viewer.sendMessage(messages.get("reports-empty"));
                return;
            }
            viewer.sendMessage(messages.get("reports-header", Placeholder.unparsed("count", String.valueOf(reports.size()))));
            for (ModerationStore.Report report : reports) {
                viewer.sendMessage(line(report.id(), report.reporterName(), report.targetName(), report.reason()));
            }
        });
    }

    public void close(CommandSender viewer, long id) {
        moderation.then(viewer, store.closeReport(id, viewer.getName(), System.currentTimeMillis()), closed ->
                viewer.sendMessage(messages.get(closed ? "report-closed" : "report-not-found",
                        Placeholder.unparsed("id", String.valueOf(id)))));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission(PERMISSION)) {
            return;
        }
        moderation.then(player, store.countOpenReports(), count -> {
            if (count > 0 && player.isOnline()) {
                player.sendMessage(messages.get("reports-waiting", Placeholder.unparsed("count", String.valueOf(count)))
                        .clickEvent(ClickEvent.runCommand("/şikayetler")));
            }
        });
    }

    /** Şikayet satırı ve yetkili butonları: geçmiş, sohbet kaydı, kapat. */
    private Component line(long id, String reporter, String target, String reason) {
        return messages.get("report-line",
                        Placeholder.unparsed("id", String.valueOf(id)),
                        Placeholder.unparsed("reporter", reporter),
                        Placeholder.unparsed("target", target),
                        Placeholder.unparsed("reason", reason))
                .append(Component.space())
                .append(messages.get("report-button-history").clickEvent(ClickEvent.runCommand("/geçmiş " + target)))
                .append(Component.space())
                .append(messages.get("report-button-chatlog").clickEvent(ClickEvent.runCommand("/chatlog " + target)))
                .append(Component.space())
                .append(messages.get("report-button-close").clickEvent(ClickEvent.runCommand("/şikayetler kapat " + id)));
    }
}
