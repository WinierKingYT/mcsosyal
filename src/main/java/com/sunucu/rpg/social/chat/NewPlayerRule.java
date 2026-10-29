package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.social.profile.Playtime;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

/**
 * Yeni oyuncu kısıtlaması (config → moderation.new-player-minutes): oynama süresi bundan azsa bağlantı paylaşamaz
 * ve yetkili olmayanlara özel mesaj atamaz. Reklam botları ilk dakikalarda yazar. millis 0: kapalı.
 */
public record NewPlayerRule(long millis) {

    public static final NewPlayerRule NONE = new NewPlayerRule(0);
    private static final long MILLIS_PER_MINUTE = 60_000;

    // shortcut: oynama süresi async sohbet thread'inden okunuyor (sadece okuma); sorun çıkarsa ana thread'de önbelleğe al.
    public boolean applies(Player player) {
        return millis > 0 && !player.hasPermission(ChatListener.BYPASS) && Playtime.millis(player) < millis;
    }

    public TagResolver minutes() {
        return Placeholder.unparsed("minutes", String.valueOf(millis / MILLIS_PER_MINUTE));
    }
}
