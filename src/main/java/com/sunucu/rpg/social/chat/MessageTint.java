package com.sunucu.rpg.social.chat;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

/**
 * İzleyiciye göre mesaj rengi: parti üyeleri birbirinin mesajını partinin renginde görür. Renk yoksa null.
 * Async sohbet thread'inden çağrılır.
 */
@FunctionalInterface
public interface MessageTint {

    MessageTint NONE = (sender, viewer) -> null;

    TextColor tint(Player sender, Player viewer);
}
