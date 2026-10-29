package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.social.chat.ChannelPreferences;
import com.sunucu.rpg.social.chat.ChatMutes;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.friends.Friends;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Oyuncunun sosyal verisini giriş öncesi (async, ana thread beklemez) yükler, çıkışta bellekten bırakır.
 * Veri okunamazsa oyuncu girişte nazikçe geri çevrilir: varsayılanlarla girse susturması uygulanmaz ve
 * ilk ayar değişikliği kayıtlı ayarlarının üstüne yazılırdı.
 */
public final class SocialData implements Listener {

    private final SocialStore store;
    private final Logger logger;
    private final Messages messages;
    private final ChannelPreferences preferences;
    private final Notifications notifications;
    private final PrivateMessages privateMessages;
    private final ChatMutes mutes;
    private final Friends friends;
    // Verisi yüklenmiş ama henüz oyuna girmemiş oyuncular. Aynı hesapla ikinci kez girilince eski oturum atılır;
    // onun çıkışı yeni oturumun verisini silmesin (yoksa susturma kalkardı).
    private final Set<UUID> pendingLogins = ConcurrentHashMap.newKeySet();

    public SocialData(SocialStore store, Logger logger, Messages messages, ChannelPreferences preferences,
                      Notifications notifications, PrivateMessages privateMessages, ChatMutes mutes,
                      Friends friends) {
        this.store = store;
        this.logger = logger;
        this.messages = messages;
        this.preferences = preferences;
        this.notifications = notifications;
        this.privateMessages = privateMessages;
        this.mutes = mutes;
        this.friends = friends;
    }

    /** Plugin oyuncular içerdeyken açılırsa (ör. sunucu yeniden yüklenince) onları da yükle. */
    public void loadOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                apply(player.getUniqueId(), store.load(player.getUniqueId()));
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Sosyal veri yüklenemedi: " + player.getName(), e);
            }
        }
    }

    // MONITOR: başka bir plugin girişi reddettiyse (ban vb.) boşuna yükleme.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID player = event.getUniqueId();
        try {
            SocialStore.PlayerData data = store.load(player);
            pendingLogins.add(player);
            apply(player, data);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Sosyal veri yüklenemedi, giriş reddedildi: " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("data-load-failed"));
        }
    }

    // shortcut: ön girişten sonra reddedilen girişte (sunucu dolu, beyaz liste) veri bellekte kalır; oyuncu tekrar
    // girince üstüne yazılır. PlayerLoginEvent Paper'da kullanımdan kalktı; sızıntı önemli olursa yeni giriş olayına bağla.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        pendingLogins.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        if (!pendingLogins.contains(player)) {
            unload(player);
        }
    }

    private void apply(UUID player, SocialStore.PlayerData data) {
        preferences.load(player, data.activeChannel(), data.hiddenChannels());
        notifications.load(player, data.mutedNotifications());
        privateMessages.load(player, data.ignores());
        mutes.load(player, data.mutedUntil(), data.shadowMutedUntil());
        friends.load(player, data.friends(), data.incomingRequests(), data.outgoingRequests());
    }

    private void unload(UUID player) {
        preferences.unload(player);
        notifications.unload(player);
        privateMessages.unload(player);
        mutes.unload(player);
        friends.unload(player);
    }
}
