package com.sunucu.rpg.social.profile;

import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Profilin yetkili sırası. Moderasyon doldurur; profil moderasyonu tanımaz. Yetkisi olmayan boş liste alır ve
 * profil 3 sıra kalır. Ana thread'den çağrılır.
 */
public interface StaffButtons {

    record Button(ItemStack icon, Runnable action) {
    }

    StaffButtons NONE = (viewer, target) -> List.of();

    List<Button> buttons(Player viewer, OfflinePlayer target);
}
