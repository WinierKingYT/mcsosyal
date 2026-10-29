package com.sunucu.rpg.core.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Menü eşyaları. Minecraft isim ve lore'u varsayılan italik gösterir; burada kapatılır. */
public final class Icons {

    private Icons() {
    }

    public static ItemStack icon(Material material, Component name, List<Component> lore) {
        return icon(material, name, lore, false);
    }

    /** glow: seçili olanı parlatmak için (büyülü eşya parıltısı). */
    public static ItemStack icon(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(plain(name));
            meta.lore(lore.stream().map(Icons::plain).toList());
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
        });
        return item;
    }

    /** Sadece boşluk doldurur: üstüne gelince hiçbir yazı göstermez. */
    public static ItemStack filler(Material material) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }

    private static Component plain(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
