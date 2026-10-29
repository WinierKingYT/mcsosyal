package com.sunucu.rpg.core.gui;

import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Tüm Menu pencerelerini korur ve butonları çalıştırır. RpgPlugin bir kez kaydeder. */
public final class MenuListener implements Listener {

    private final JavaPlugin plugin;

    public MenuListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        // Menü açıkken kendi envanterine yapılan tıklama da iptal: shift-click ile eşya içeri giremesin.
        event.setCancelled(true);
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Consumer<ClickType> action = menu.action(event.getRawSlot());
        if (action != null) {
            ClickType click = event.getClick();
            Bukkit.getScheduler().runTask(plugin, () -> action.accept(click));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
