package com.sunucu.rpg.social;

import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Profildeki RPG yuvaları: ustalıklar (kazma, kürek, balta, çapa), pet ve binek. RPG sistemi kendi sağlayıcısını
 * SocialModule#setProfileProvider ile verir; verilmezse yuvalar "Yakında" yazar. Ana thread'den çağrılır.
 * Ustalık anahtarları: ProfileProvider.MINING, EXCAVATION, FARMING, WOODCUTTING.
 */
public interface ProfileProvider {

    String MINING = "mining";
    String EXCAVATION = "excavation";
    String FARMING = "farming";
    String WOODCUTTING = "woodcutting";

    /** progress: sonraki seviyeye ilerleme, 0 ile 1 arası. details: lore'a eklenen ek satırlar. */
    record Mastery(int level, double progress, List<Component> details) {
    }

    ProfileProvider NONE = new ProfileProvider() {
    };

    /** Oyuncunun bu ustalıktaki durumu; yoksa (ya da bilinmiyorsa) boş. Çevrimdışı oyuncu için de istenir. */
    default Optional<Mastery> mastery(OfflinePlayer player, String skill) {
        return Optional.empty();
    }

    /** Ustalık ikonuna tıklanınca: RPG'nin ustalık paneli açılır. @return false ise panel yok. */
    default boolean openMastery(Player viewer, OfflinePlayer target, String skill) {
        return false;
    }

    /** Takılı petin ikonu (ismi ve açıklamasıyla hazır); takılı pet yoksa boş. */
    default Optional<ItemStack> pet(OfflinePlayer player) {
        return Optional.empty();
    }

    /** Pet yuvasına tıklanınca (pet takılı olsun olmasın): RPG'nin pet menüsü. @return false ise menü yok. */
    default boolean openPets(Player viewer, OfflinePlayer target) {
        return false;
    }

    /** Takılı bineğin ikonu; yoksa boş. */
    default Optional<ItemStack> mount(OfflinePlayer player) {
        return Optional.empty();
    }

    /** Binek yuvasına tıklanınca: RPG'nin binek menüsü. @return false ise menü yok. */
    default boolean openMounts(Player viewer, OfflinePlayer target) {
        return false;
    }
}
