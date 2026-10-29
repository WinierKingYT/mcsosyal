package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hareketsiz oyuncuyu AFK işaretler, tab listesinde soluk gösterir. Olaylar sadece zaman damgası yazar;
 * durum değişikliği saniyede bir ana thread'de yapılır (async sohbetten de güvenli).
 */
public final class AfkTracker implements Listener {

    private static final long TICKS_PER_SECOND = 20;

    private final Messages messages;
    private final long afkAfterMillis;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSince = new ConcurrentHashMap<>();

    public AfkTracker(Messages messages, long afkAfterSeconds) {
        this.messages = messages;
        this.afkAfterMillis = afkAfterSeconds * 1000;
    }

    public void start(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICKS_PER_SECOND, TICKS_PER_SECOND);
    }

    public boolean isAfk(Player player) {
        return afkSince.containsKey(player.getUniqueId());
    }

    /** /afk: AFK ise çıkarır, değilse hemen AFK yapar. */
    public void toggle(Player player) {
        if (isAfk(player)) {
            clear(player);
        } else {
            mark(player, System.currentTimeMillis());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            long last = lastActive.getOrDefault(player.getUniqueId(), now);
            Long since = afkSince.get(player.getUniqueId());
            if (since == null && now - last >= afkAfterMillis) {
                mark(player, now);
            } else if (since != null && last > since) {
                clear(player);
            }
        }
    }

    // shortcut: tab ismini doğrudan değiştiriyor; RPG tarafı da tab ismi yazarsa ortak bir tab yöneticisine taşı.
    private void mark(Player player, long now) {
        afkSince.put(player.getUniqueId(), now);
        player.playerListName(messages.get("afk-tab", Placeholder.unparsed("name", player.getName())));
        player.sendMessage(messages.get("afk-on"));
    }

    private void clear(Player player) {
        afkSince.remove(player.getUniqueId());
        player.playerListName(null);
        player.sendMessage(messages.get("afk-off"));
    }

    private void touch(Player player) {
        lastActive.put(player.getUniqueId(), System.currentTimeMillis());
    }

    // shortcut: suyla itilmek de hareket sayılır; AFK'ya ödül/ceza bağlanırsa sadece bakış yönü değişimine bak.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() || event.hasChangedOrientation()) {
            touch(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        // /afk'nın kendisi oyuncuyu hemen geri çıkarmasın.
        if (!event.getMessage().toLowerCase(Locale.ROOT).startsWith("/afk")) {
            touch(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastActive.remove(event.getPlayer().getUniqueId());
        afkSince.remove(event.getPlayer().getUniqueId());
    }
}
