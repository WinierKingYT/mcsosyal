package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * [inv] penceresinin düzeni (5 sıra): 3 sıra çanta, 1 sıra hızlı çubuk, en altta ortalanmış kask, göğüslük,
 * pantolon, ayakkabı, ikinci el; kalan 4 yuva dolgu camı. Boş zırh yuvasında gri cam durur.
 * Oyuncu da yetkili de aynı düzeni görür. Yuva eşlemesi Bukkit'e bağımlı değil, test edilebilir.
 */
final class InventoryLayout {

    static final int ROWS = 5;
    static final int SIZE = ROWS * 9;
    private static final int HOTBAR_SIZE = 9;
    private static final int HOTBAR_VIEW_START = 27;
    private static final int BOTTOM_ROW_START = 36;
    private static final int ARMOR_VIEW_START = 38;
    private static final int ARMOR_PIECES = 4;
    private static final int OFFHAND_VIEW_SLOT = 42;
    private static final String[] ARMOR_NAMES = {"Kask", "Göğüslük", "Pantolon", "Ayakkabı"};

    private InventoryLayout() {
    }

    /** Oyuncu envanteri yuvası (0–8 hızlı çubuk, 9–35 çanta) → pencere yuvası. */
    static int viewSlotOfStorage(int storageSlot) {
        return storageSlot < HOTBAR_SIZE ? HOTBAR_VIEW_START + storageSlot : storageSlot - HOTBAR_SIZE;
    }

    /** Pencere yuvası → oyuncu envanteri yuvası; çanta/hızlı çubuk değilse -1. */
    static int storageSlotOfView(int viewSlot) {
        if (viewSlot < 0 || viewSlot >= BOTTOM_ROW_START) {
            return -1;
        }
        return viewSlot < HOTBAR_VIEW_START ? viewSlot + HOTBAR_SIZE : viewSlot - HOTBAR_VIEW_START;
    }

    /** 0 kask, 1 göğüslük, 2 pantolon, 3 ayakkabı; zırh yuvası değilse -1. */
    static int armorPieceOfView(int viewSlot) {
        int piece = viewSlot - ARMOR_VIEW_START;
        return piece >= 0 && piece < ARMOR_PIECES ? piece : -1;
    }

    static boolean isOffhand(int viewSlot) {
        return viewSlot == OFFHAND_VIEW_SLOT;
    }

    /** Envanterde karşılığı olmayan dolgu yuvası: kimse dokunamaz. */
    static boolean isFiller(int viewSlot) {
        return viewSlot >= BOTTOM_ROW_START && viewSlot < SIZE
                && armorPieceOfView(viewSlot) < 0 && !isOffhand(viewSlot);
    }

    /**
     * Envanterin pencere görünümü (kopyalar). placeholders null değilse, gri cam konan zırh yuvaları işaretlenir.
     */
    static ItemStack[] render(PlayerInventory inventory, Messages messages, boolean[] placeholders) {
        ItemStack[] layout = new ItemStack[SIZE];
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            layout[viewSlotOfStorage(slot)] = copy(storage[slot]);
        }
        ItemStack filler = Icons.filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = BOTTOM_ROW_START; slot < SIZE; slot++) {
            if (isFiller(slot)) {
                layout[slot] = filler;
            }
        }
        for (int piece = 0; piece < ARMOR_PIECES; piece++) {
            ItemStack worn = copy(armor(inventory, piece));
            int slot = ARMOR_VIEW_START + piece;
            if (placeholders != null) {
                placeholders[slot] = worn == null;
            }
            layout[slot] = worn != null ? worn : Icons.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    messages.get("view-empty-armor", Placeholder.unparsed("slot", ARMOR_NAMES[piece])),
                    messages.lines("view-empty-armor-lore"));
        }
        // Elinde bir şey yoksa yuva boş kalır.
        layout[OFFHAND_VIEW_SLOT] = copy(inventory.getItemInOffHand());
        return layout;
    }

    /** Pencere yuvasındaki eşyayı oyuncunun gerçek envanterine yazar (yetkili düzenlemesi). */
    static void write(PlayerInventory inventory, int viewSlot, ItemStack item) {
        int storage = storageSlotOfView(viewSlot);
        if (storage >= 0) {
            inventory.setItem(storage, item);
            return;
        }
        int piece = armorPieceOfView(viewSlot);
        if (piece >= 0) {
            switch (piece) {
                case 0 -> inventory.setHelmet(item);
                case 1 -> inventory.setChestplate(item);
                case 2 -> inventory.setLeggings(item);
                default -> inventory.setBoots(item);
            }
            return;
        }
        if (isOffhand(viewSlot)) {
            inventory.setItemInOffHand(item);
        }
    }

    private static ItemStack armor(PlayerInventory inventory, int piece) {
        return switch (piece) {
            case 0 -> inventory.getHelmet();
            case 1 -> inventory.getChestplate();
            case 2 -> inventory.getLeggings();
            default -> inventory.getBoots();
        };
    }

    static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    /** Filtre kontrolü için envanterdeki tüm eşyalar. Boş yuvalar null: List.of kullanma, null kabul etmez. */
    static List<ItemStack> contents(PlayerInventory inventory) {
        List<ItemStack> items = new ArrayList<>(Arrays.asList(inventory.getStorageContents()));
        items.addAll(Arrays.asList(inventory.getArmorContents()));
        items.add(inventory.getItemInOffHand());
        return items;
    }
}
