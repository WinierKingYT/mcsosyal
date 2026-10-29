package com.sunucu.rpg.social.party;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

/**
 * Partinin seçebileceği renkler (sohbette okunaklı olanlar). Ayarlar menüsünde boya ile gösterilir, tıkladıkça
 * sıradakine geçer. Türkçe adları config → messages.party-color-<anahtar>.
 */
public enum PartyColor {
    AQUA(NamedTextColor.AQUA, Material.LIGHT_BLUE_DYE),
    DARK_AQUA(NamedTextColor.DARK_AQUA, Material.CYAN_DYE),
    GREEN(NamedTextColor.GREEN, Material.LIME_DYE),
    YELLOW(NamedTextColor.YELLOW, Material.YELLOW_DYE),
    GOLD(NamedTextColor.GOLD, Material.ORANGE_DYE),
    RED(NamedTextColor.RED, Material.RED_DYE),
    LIGHT_PURPLE(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_DYE),
    WHITE(NamedTextColor.WHITE, Material.WHITE_DYE);

    private final NamedTextColor color;
    private final Material dye;

    PartyColor(NamedTextColor color, Material dye) {
        this.color = color;
        this.dye = dye;
    }

    public NamedTextColor color() {
        return color;
    }

    public Material dye() {
        return dye;
    }

    /** "aqua", "light_purple" gibi; config ve Parties bu adı tutar. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public PartyColor next(boolean forward) {
        PartyColor[] all = values();
        return all[Math.floorMod(ordinal() + (forward ? 1 : -1), all.length)];
    }

    public static Optional<PartyColor> from(String key) {
        return key == null ? Optional.empty()
                : Arrays.stream(values()).filter(color -> color.key().equals(key.toLowerCase(Locale.ROOT))).findFirst();
    }
}
