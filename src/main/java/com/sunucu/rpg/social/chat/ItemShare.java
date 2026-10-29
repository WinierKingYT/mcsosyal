package com.sunucu.rpg.social.chat;

import java.util.regex.Pattern;

/**
 * Sohbette [item] / [eşya]: eldeki eşya gösterilir, üstüne gelince özellikleri çıkar.
 * RPG eşyaları da normal ItemStack olduğu için isim, lore ve özellikleri otomatik görünür.
 * Bukkit'e bağımlı değil, test edilebilir; görünüm ChatListener'da.
 */
final class ItemShare {

    static final Pattern TOKEN = Pattern.compile("\\[(item|eşya|esya)]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private ItemShare() {
    }

    static boolean hasToken(String text) {
        return TOKEN.matcher(text).find();
    }
}
