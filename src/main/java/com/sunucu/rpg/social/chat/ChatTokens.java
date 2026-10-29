package com.sunucu.rpg.social.chat;

import java.util.regex.Pattern;

/**
 * Sohbette özel etiketler. Bukkit'e bağımlı değil, test edilebilir; görünümleri ChatListener'da.
 * [item] / [eşya]: eldeki eşya. RPG eşyaları da ItemStack olduğu için isim, lore ve özellikleri otomatik görünür.
 * [konum]: oyuncunun koordinatları, tıklayınca kopyalanır.
 */
final class ChatTokens {

    static final Pattern ITEM = Pattern.compile("\\[(item|eşya|esya)]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    static final Pattern LOCATION = Pattern.compile("\\[(konum|loc|location)]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ChatTokens() {
    }

    static boolean has(Pattern token, String text) {
        return token.matcher(text).find();
    }
}
