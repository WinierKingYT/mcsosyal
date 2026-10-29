package com.sunucu.rpg.social.chat;

import org.bukkit.entity.Player;

/**
 * Üyeliğe bağlı kanal (ör. parti, ileride lonca): kim yazabilir, kim görür. İzin ve mesafe kontrolüne ek olarak
 * çalışır. Async sohbet thread'inden çağrılır: dünyaya dokunma.
 */
public interface ChannelGate {

    boolean canSend(Player sender);

    boolean canSee(Player sender, Player viewer);

    /** canSend false ise gönderene gösterilen mesajın anahtarı. */
    String deniedKey();
}
