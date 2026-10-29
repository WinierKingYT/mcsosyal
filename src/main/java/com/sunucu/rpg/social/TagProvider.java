package com.sunucu.rpg.social;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Sohbette ismin önündeki etiketler ([Savaşçı Lv.42] gibi). RPG sistemi kendi
 * sağlayıcısını SocialModule#setTagProvider ile verir.
 * Async sohbet thread'inden çağrılır: dünyaya dokunma, önbellekten oku.
 */
@FunctionalInterface
public interface TagProvider {

    TagProvider NONE = player -> Component.empty();

    Component tags(Player player);
}
