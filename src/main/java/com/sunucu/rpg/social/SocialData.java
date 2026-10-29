package com.sunucu.rpg.social;

import com.sunucu.rpg.social.chat.ChannelPreferences;
import com.sunucu.rpg.social.chat.ChatMutes;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PrivateMessages;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Oyuncunun sosyal verisini giriş öncesi (async, ana thread beklemez) yükler, çıkışta bellekten bırakır.
 * Veritabanı okunamazsa oyuncu yine girer, sadece varsayılan ayarlarla; hata loglanır.
 */
public final class SocialData implements Listener {

    private final SocialStore store;
    private final Logger logger;
    private final ChannelPreferences preferences;
    private final Notifications notifications;
    private final PrivateMessages privateMessages;
    private final ChatMutes mutes;

    public SocialData(SocialStore store, Logger logger, ChannelPreferences preferences, Notifications notifications,
                      PrivateMessages privateMessages, ChatMutes mutes) {
        this.store = store;
        this.logger = logger;
        this.preferences = preferences;
        this.notifications = notifications;
        this.privateMessages = privateMessages;
        this.mutes = mutes;
    }

    /** Plugin oyuncular içerdeyken açılırsa (ör. sunucu yeniden yüklenince) onları da yükle. */
    public void loadOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player.getUniqueId());
        }
    }

    // MONITOR: başka bir plugin girişi reddettiyse (ban vb.) boşuna yükleme.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            load(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        preferences.unload(player);
        notifications.unload(player);
        privateMessages.unload(player);
        mutes.unload(player);
    }

    private void load(UUID player) {
        SocialStore.PlayerData data;
        try {
            data = store.load(player);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Sosyal veri yüklenemedi, varsayılan ayarlar kullanılıyor: " + player, e);
            data = SocialStore.PlayerData.EMPTY;
        }
        preferences.load(player, data.activeChannel(), data.hiddenChannels());
        notifications.load(player, data.mutedNotifications());
        privateMessages.load(player, data.ignores());
        mutes.load(player, data.mutedUntil());
    }
}
