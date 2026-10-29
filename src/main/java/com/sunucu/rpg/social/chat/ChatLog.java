package com.sunucu.rpg.social.chat;

import org.bukkit.entity.Player;

/**
 * Gönderilen her sohbet, kanal ve özel mesajın kaydı (moderasyon: /chatlog, şikayetler). Async sohbet
 * thread'inden de çağrılır; bekletmemeli (veritabanı yazımı arka planda).
 */
@FunctionalInterface
public interface ChatLog {

    ChatLog NONE = (player, channel, message) -> {
    };

    void record(Player player, String channel, String message);
}
