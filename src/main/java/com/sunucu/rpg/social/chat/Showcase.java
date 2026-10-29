package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.world.Regions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Sohbette [i], [inv] ve [konum] paylaşımları. Eşya ve envantere tıklayınca salt okunur bir pencere açılır;
 * pencere paylaşım anının kopyasını gösterir, içinden eşya alınamaz.
 */
public final class Showcase implements Listener {

    /** Bir mesajdaki paylaşımlar; olmayanlar null. */
    public record Shared(Component item, Component inventory, Component location) {

        // Mesaj başına her etiketin sadece ilki değiştirilir, gerisi düz metin kalır (hover spam'ine karşı).
        public Component apply(Component message) {
            Component result = replaceOnce(message, ChatTokens.ITEM, item);
            result = replaceOnce(result, ChatTokens.INVENTORY, inventory);
            return replaceOnce(result, ChatTokens.LOCATION, location);
        }
    }

    private static final int MAX_ITEM_DEPTH = 2;
    private static final Duration VIEW_LIFETIME = Duration.ofMinutes(10);
    private static final int ITEM_VIEW_SIZE = 27;
    private static final int ITEM_VIEW_SLOT = 13;
    private static final int INVENTORY_VIEW_SIZE = 54;
    private static final int HOTBAR_SIZE = 9;
    private static final int HOTBAR_VIEW_START = 27;
    private static final int ARMOR_VIEW_START = 45;
    private static final int OFFHAND_VIEW_SLOT = 50;

    private final Messages messages;
    private final ChatFilter filter;
    private final Regions regions;
    private final Map<String, String> worldNames = new HashMap<>();

    public Showcase(ConfigurationSection chat, Messages messages, ChatFilter filter, Regions regions) {
        this.messages = messages;
        this.filter = filter;
        this.regions = regions;
        ConfigurationSection worlds = chat.getConfigurationSection("world-names");
        if (worlds != null) {
            worlds.getKeys(false).forEach(world -> worldNames.put(world, worlds.getString(world)));
        }
    }

    /**
     * Mesajdaki etiketlere göre paylaşımları hazırlar. Bir eşya filtreye takılırsa oyuncuya sebebi söylenir
     * ve null döner; mesaj gönderilmez.
     */
    // shortcut: envanter ve konum async sohbet thread'inden okunup kopyalanıyor (sohbet plugin'lerinde yaygın);
    // sorun çıkarsa ana thread'e taşı.
    public Shared prepare(Player sender, String text) {
        Component item = null;
        Component inventory = null;
        if (ChatTokens.has(ChatTokens.ITEM, text)) {
            ItemStack hand = sender.getInventory().getItemInMainHand().clone();
            if (!passesFilter(sender, List.of(hand))) {
                return null;
            }
            item = itemComponent(sender, hand);
        }
        if (ChatTokens.has(ChatTokens.INVENTORY, text)) {
            ItemStack[] layout = inventoryLayout(sender.getInventory());
            if (!passesFilter(sender, List.of(layout))) {
                return null;
            }
            inventory = inventoryComponent(sender, layout);
        }
        Component location = ChatTokens.has(ChatTokens.LOCATION, text) ? locationComponent(sender) : null;
        return new Shared(item, inventory, location);
    }

    private Component itemComponent(Player sender, ItemStack hand) {
        if (hand.isEmpty()) {
            return messages.get("item-empty");
        }
        ItemStack[] slots = new ItemStack[ITEM_VIEW_SIZE];
        slots[ITEM_VIEW_SLOT] = hand;
        Component title = messages.get("view-item-title", Placeholder.unparsed("name", sender.getName()));
        return messages.get(hand.getAmount() > 1 ? "item-many" : "item",
                        Placeholder.component("item", hand.displayName()),
                        Placeholder.unparsed("amount", String.valueOf(hand.getAmount())))
                .clickEvent(openView(title, slots));
    }

    private Component inventoryComponent(Player sender, ItemStack[] layout) {
        Component title = messages.get("view-inventory-title", Placeholder.unparsed("name", sender.getName()));
        return messages.get("inventory", Placeholder.unparsed("name", sender.getName()))
                .hoverEvent(messages.get("inventory-hover"))
                .clickEvent(openView(title, layout));
    }

    /** 54'lük pencere: 3 sıra çanta, 1 sıra hızlı çubuk, en altta zırh ve ikinci el. */
    private static ItemStack[] inventoryLayout(PlayerInventory inventory) {
        ItemStack[] layout = new ItemStack[INVENTORY_VIEW_SIZE];
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            int viewSlot = slot < HOTBAR_SIZE ? HOTBAR_VIEW_START + slot : slot - HOTBAR_SIZE;
            layout[viewSlot] = copy(storage[slot]);
        }
        // getArmorContents: botlar, pantolon, göğüslük, kask. Pencerede kasktan başlar.
        ItemStack[] armor = inventory.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            layout[ARMOR_VIEW_START + i] = copy(armor[armor.length - 1 - i]);
        }
        layout[OFFHAND_VIEW_SLOT] = copy(inventory.getItemInOffHand());
        return layout;
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private ClickEvent openView(Component title, ItemStack[] slots) {
        return ClickEvent.callback(audience -> {
            if (audience instanceof Player viewer) {
                View view = new View();
                view.inventory = Bukkit.createInventory(view, slots.length, title);
                for (int slot = 0; slot < slots.length; slot++) {
                    if (slots[slot] != null) {
                        view.inventory.setItem(slot, slots[slot].clone());
                    }
                }
                viewer.openInventory(view.inventory);
            }
        }, options -> options.uses(ClickCallback.UNLIMITED_USES).lifetime(VIEW_LIFETIME));
    }

    // Salt okunur pencere: içinden eşya almak, koymak ya da sürüklemek yok.
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) {
            event.setCancelled(true);
        }
    }

    private static final class View implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private Component locationComponent(Player sender) {
        Location location = sender.getLocation();
        String worldName = location.getWorld().getName();
        String world = worldNames.getOrDefault(worldName, worldName);
        String place = regions.at(worldName, location.getBlockX(), location.getBlockZ())
                .map(Regions.Region::name).orElse(world);
        String coordinates = location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
        return messages.get("location",
                        Placeholder.unparsed("place", place),
                        Placeholder.unparsed("world", world),
                        Placeholder.unparsed("x", String.valueOf(location.getBlockX())),
                        Placeholder.unparsed("y", String.valueOf(location.getBlockY())),
                        Placeholder.unparsed("z", String.valueOf(location.getBlockZ())))
                .hoverEvent(messages.get("location-hover"))
                .clickEvent(ClickEvent.copyToClipboard(coordinates));
    }

    /** Paylaşılan eşyalardaki oyuncu metni filtreden geçmeli; geçmezse oyuncuya sebebi söylenir. */
    private boolean passesFilter(Player sender, List<ItemStack> items) {
        if (sender.hasPermission(ChatListener.BYPASS)) {
            return true;
        }
        List<String> texts = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                texts.addAll(hoverTexts(item, 0));
            }
        }
        ChatFilter.Verdict verdict = filter.checkContent(String.join("\n", texts));
        if (verdict != ChatFilter.Verdict.OK) {
            sender.sendMessage(messages.get(ChatListener.messageKey(verdict)));
            return false;
        }
        return true;
    }

    /**
     * Eşya kutusunda görünen ve oyuncunun yazabildiği metinler: isim, lore, kitap başlığı ve yazarı,
     * shulker kutusu ve bohça içindeki eşyalar. Hepsi filtreden geçmeli, yoksa reklam hover'a saklanır.
     */
    private static List<String> hoverTexts(ItemStack item, int depth) {
        List<String> texts = new ArrayList<>();
        if (!item.hasItemMeta()) {
            return texts;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomName()) {
            texts.add(plain(meta.customName()));
        }
        if (meta.hasItemName()) {
            texts.add(plain(meta.itemName()));
        }
        if (meta.hasLore()) {
            meta.lore().forEach(line -> texts.add(plain(line)));
        }
        if (meta instanceof BookMeta book) {
            if (book.hasTitle()) {
                texts.add(plain(book.title()));
            }
            if (book.hasAuthor()) {
                texts.add(plain(book.author()));
            }
        }
        // Bohça içinde bohça olabilir; derinlik sınırı sonsuz döngüye ve ağır eşyalara karşı.
        if (depth < MAX_ITEM_DEPTH) {
            if (meta instanceof BundleMeta bundle) {
                bundle.getItems().forEach(inner -> texts.addAll(hoverTexts(inner, depth + 1)));
            }
            if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()
                    && blockMeta.getBlockState() instanceof Container container) {
                for (ItemStack inner : container.getInventory().getContents()) {
                    if (inner != null) {
                        texts.addAll(hoverTexts(inner, depth + 1));
                    }
                }
            }
        }
        return texts;
    }

    private static String plain(Component component) {
        return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static Component replaceOnce(Component message, Pattern token, Component replacement) {
        if (replacement == null) {
            return message;
        }
        return message.replaceText(config -> config.match(token).once().replacement(replacement));
    }
}
