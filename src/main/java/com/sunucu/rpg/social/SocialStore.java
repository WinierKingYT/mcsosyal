package com.sunucu.rpg.social;

import java.util.Set;
import java.util.UUID;

/**
 * Sosyal verilerin kalıcı hali. Oyun sırasında her şey bellekte durur; bu sadece giriş öncesi yükler ve
 * değişiklikleri arka planda yazar. Testler ve veritabanı yokken NONE kullanılır.
 */
public interface SocialStore {

    String ACTIVE_CHANNEL = "channel.active";
    String HIDDEN_CHANNELS = "channel.hidden";
    String MUTED_NOTIFICATIONS = "notify.off";

    /** activeChannel null ise varsayılan kanal; mutedUntil 0 ise susturulmamış. */
    record PlayerData(String activeChannel, Set<String> hiddenChannels, Set<String> mutedNotifications,
                      Set<UUID> ignores, long mutedUntil) {

        public static final PlayerData EMPTY = new PlayerData(null, Set.of(), Set.of(), Set.of(), 0);
    }

    /** Liste ayarları için: virgülle birleştirir, boşsa null (ayar silinir). */
    static String joinList(Set<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }

    /** Bekler: ana thread'den çağırma (giriş öncesi async olayda çağrılır). */
    PlayerData load(UUID player);

    /** value null ya da boşsa ayar silinir. */
    void saveSetting(UUID player, String key, String value);

    void setIgnored(UUID player, UUID target, boolean ignored);

    /** untilMillis 0 ise susturma silinir. */
    void saveMute(UUID player, long untilMillis);

    SocialStore NONE = new SocialStore() {
        @Override
        public PlayerData load(UUID player) {
            return PlayerData.EMPTY;
        }

        @Override
        public void saveSetting(UUID player, String key, String value) {
        }

        @Override
        public void setIgnored(UUID player, UUID target, boolean ignored) {
        }

        @Override
        public void saveMute(UUID player, long untilMillis) {
        }
    };
}
