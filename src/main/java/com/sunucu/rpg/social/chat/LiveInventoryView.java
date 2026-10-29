package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Yetkilinin [inv] penceresi: oyuncunun gördüğü düzenle aynı, ama eşyalar düzenlenebilir ve her değişiklik
 * oyuncunun gerçek envanterine yazılır. Dolgu camlarına ve boş zırh yuvasındaki gri cama dokunulamaz; gri cama
 * elindeki eşyayla tıklamak o eşyayı giydirir. Pencere oyuncunun envanterinden sürekli tazelenir.
 */
public final class LiveInventoryView implements InventoryHolder {

    private static final long REFRESH_TICKS = 2;

    private final Player owner;
    private final Messages messages;
    private final Inventory inventory;
    private final boolean[] placeholders = new boolean[InventoryLayout.SIZE];
    private BukkitTask refresher;
    // Yetkilinin dokunduğu, henüz envantere yazılmamış yuvalar. Boş değilken tazeleme yapılmaz; yoksa değişiklik
    // silinir ve eşya kaybolur. Aynı tick'teki tüm tıklamalar tek seferde yazılır. Sadece ana thread.
    private final Set<Integer> pendingSlots = new HashSet<>();

    private LiveInventoryView(Player owner, Messages messages, Component title) {
        this.owner = owner;
        this.messages = messages;
        this.inventory = Bukkit.createInventory(this, InventoryLayout.SIZE, title);
    }

    /** Ana thread'den çağrılır. */
    public static void open(JavaPlugin plugin, Player admin, Player owner, Messages messages, Component title) {
        LiveInventoryView view = new LiveInventoryView(owner, messages, title);
        view.render();
        view.refresher = Bukkit.getScheduler().runTaskTimer(plugin, view::refresh, REFRESH_TICKS, REFRESH_TICKS);
        admin.openInventory(view.inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void refresh() {
        if (!owner.isOnline()) {
            // Oyuncu çıktı: pencereyi kapat, eski kopya üzerinde düzenleme yapılmasın.
            for (var viewer : Set.copyOf(inventory.getViewers())) {
                viewer.closeInventory();
            }
            return;
        }
        if (pendingSlots.isEmpty()) {
            render();
        }
    }

    /** Sadece değişen yuvaları günceller; yetkilinin elindeki eşya ve titreme sorunu olmasın. */
    private void render() {
        ItemStack[] layout = InventoryLayout.render(owner.getInventory(), messages, placeholders);
        for (int slot = 0; slot < layout.length; slot++) {
            if (!Objects.equals(inventory.getItem(slot), layout[slot])) {
                inventory.setItem(slot, layout[slot]);
            }
        }
    }

    /** Sadece yetkilinin dokunduğu yuvalar yazılır: oyuncunun o arada yaptığı başka değişiklikler ezilmez. */
    private void writeBack() {
        if (owner.isOnline()) {
            for (int slot : pendingSlots) {
                InventoryLayout.write(owner.getInventory(), slot, InventoryLayout.copy(inventory.getItem(slot)));
            }
        }
        pendingSlots.clear();
        render();
    }

    /** Değişiklik olay bittikten sonra pencerede görünür; bir sonraki tick'te envantere yazılır. */
    private void scheduleWrite(JavaPlugin plugin, Set<Integer> slots) {
        boolean scheduled = !pendingSlots.isEmpty();
        pendingSlots.addAll(slots);
        if (!scheduled) {
            Bukkit.getScheduler().runTask(plugin, this::writeBack);
        }
    }

    private boolean isLocked(int slot) {
        return InventoryLayout.isFiller(slot) || placeholders[slot];
    }

    public static final class Protection implements Listener {

        private final JavaPlugin plugin;

        public Protection(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @EventHandler(ignoreCancelled = true)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof LiveInventoryView view)) {
                return;
            }
            int slot = event.getRawSlot();
            InventoryAction action = event.getAction();
            // Çift tıklayıp toplama camları da toplayabilir; alttan shift-click hedefi belirsiz. İkisi de kapalı.
            if (!view.owner.isOnline() || action == InventoryAction.COLLECT_TO_CURSOR
                    || slot >= InventoryLayout.SIZE && action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                return;
            }
            if (slot < 0 || slot >= InventoryLayout.SIZE) {
                return; // yetkilinin kendi envanteri
            }
            if (InventoryLayout.isFiller(slot)) {
                event.setCancelled(true);
                return;
            }
            if (view.placeholders[slot]) {
                event.setCancelled(true);
                ItemStack cursor = event.getCursor();
                if (cursor != null && !cursor.isEmpty() && event.getWhoClicked() instanceof Player admin) {
                    InventoryLayout.write(view.owner.getInventory(), slot, cursor.clone());
                    admin.setItemOnCursor(null);
                    // Bekleyen yazımlar varsa onlarla birlikte tazelensin.
                    view.scheduleWrite(plugin, Set.of());
                }
                return;
            }
            view.scheduleWrite(plugin, Set.of(slot));
        }

        @EventHandler(ignoreCancelled = true)
        public void onDrag(InventoryDragEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof LiveInventoryView view)) {
                return;
            }
            Set<Integer> touched = event.getRawSlots().stream()
                    .filter(slot -> slot < InventoryLayout.SIZE)
                    .collect(Collectors.toSet());
            if (touched.stream().anyMatch(view::isLocked)) {
                event.setCancelled(true);
            } else if (!touched.isEmpty()) {
                view.scheduleWrite(plugin, touched);
            }
        }

        @EventHandler
        public void onClose(InventoryCloseEvent event) {
            if (event.getInventory().getHolder() instanceof LiveInventoryView view
                    && view.inventory.getViewers().size() <= 1 && view.refresher != null) {
                view.refresher.cancel();
            }
        }
    }
}
