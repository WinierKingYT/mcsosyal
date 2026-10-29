package com.sunucu.rpg.social;

import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Oyuncu kartında (isme gelince) gösterilecek değerler, ör. {"level": "42", "class": "Savaşçı", "guild": "Ejder"}.
 * RPG sistemi kendi sağlayıcısını SocialModule#setStatsProvider ile verir. Hangi değerin hangi satırda
 * görüneceğini sunucu sahibi config.yml → chat.player-card ile seçer.
 * Async sohbet thread'inden çağrılır: dünyaya dokunma, önbellekten oku.
 */
@FunctionalInterface
public interface StatsProvider {

    StatsProvider NONE = player -> Map.of();

    Map<String, String> stats(Player player);
}
