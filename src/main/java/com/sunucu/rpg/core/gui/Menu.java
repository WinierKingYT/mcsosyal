package com.sunucu.rpg.core.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Sandık arayüzü. İçindeki hiçbir eşya alınamaz, konulamaz; sadece button ile eklenen yuvalar bir iş yapar.
 * Butonların işi bir sonraki tick'te ana thread'de çalışır (tıklama olayının içinde pencere açmak sorun çıkarır).
 * Ana thread'den oluştur ve aç. Hem sosyal hem RPG tarafı kullanabilir.
 */
public final class Menu implements InventoryHolder {

    private static final int ROW_SIZE = 9;

    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    public Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * ROW_SIZE, title);
    }

    public Menu(InventoryType type, Component title) {
        this.inventory = Bukkit.createInventory(this, type, title);
    }

    /** Sadece gösterilen eşya. */
    public Menu set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        actions.remove(slot);
        return this;
    }

    /** Tıklanınca action çalışır; ClickType ile sol/sağ tık ayrılabilir. */
    public Menu button(int slot, ItemStack item, Consumer<ClickType> action) {
        inventory.setItem(slot, item);
        actions.put(slot, action);
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    Consumer<ClickType> action(int slot) {
        return actions.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
