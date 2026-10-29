package com.sunucu.rpg.social.chat;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Menu;
import com.sunucu.rpg.world.Regions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Sohbette [i], [inv] ve [konum] paylaşımları. Eşyanın bilgisi üstüne gelince görünür. Envantere tıklayınca
 * salt okunur bir pencere açılır; pencere paylaşım anının kopyasını gösterir, içinden eşya alınamaz. Envanteri
 * düzenleme izni olan yetkili tıklarsa aynı düzen canlı ve düzenlenebilir açılır (LiveInventoryView).
 */
public final class Showcase {

    public static final String EDIT_INVENTORY = "rpg.social.inventory.edit";

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

    private final JavaPlugin plugin;
    private final Messages messages;
    private final ChatFilter filter;
    private final Regions regions;
    private final Map<String, String> worldNames = new HashMap<>();

    public Showcase(JavaPlugin plugin, ConfigurationSection chat, Messages messages, ChatFilter filter,
                    Regions regions) {
        this.plugin = plugin;
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
            item = itemComponent(hand);
        }
        if (ChatTokens.has(ChatTokens.INVENTORY, text)) {
            PlayerInventory live = sender.getInventory();
            if (!passesFilter(sender, InventoryLayout.contents(live))) {
                return null;
            }
            // Pencere paylaşım anını gösterir: sonradan çantaya konan eşya filtreden geçmemiş olurdu.
            inventory = inventoryComponent(sender, InventoryLayout.render(live, messages, null));
        }
        Component location = ChatTokens.has(ChatTokens.LOCATION, text) ? locationComponent(sender) : null;
        return new Shared(item, inventory, location);
    }

    /** Üstüne gelince Minecraft'ın kendi eşya kutusu tüm bilgileri gösterir; tıklama penceresi yok. */
    private Component itemComponent(ItemStack hand) {
        if (hand.isEmpty()) {
            return messages.get("item-empty");
        }
        return messages.get(hand.getAmount() > 1 ? "item-many" : "item",
                Placeholder.component("item", hand.displayName()),
                Placeholder.unparsed("amount", String.valueOf(hand.getAmount())));
    }

    private Component inventoryComponent(Player sender, ItemStack[] layout) {
        Component title = messages.get("view-inventory-title", Placeholder.unparsed("name", sender.getName()));
        UUID ownerId = sender.getUniqueId();
        ClickEvent open = ClickEvent.callback(audience -> {
            if (!(audience instanceof Player viewer)) {
                return;
            }
            // İzin tıklama anında kontrol edilir: bağlantıya kim tıklarsa onun yetkisi geçerli.
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null && !owner.equals(viewer) && viewer.hasPermission(EDIT_INVENTORY)) {
                LiveInventoryView.open(plugin, viewer, owner, messages,
                        messages.get("view-inventory-edit-title", Placeholder.unparsed("name", owner.getName())));
                return;
            }
            Menu menu = new Menu(InventoryLayout.ROWS, title);
            for (int slot = 0; slot < layout.length; slot++) {
                if (layout[slot] != null) {
                    menu.set(slot, layout[slot].clone());
                }
            }
            menu.open(viewer);
        }, options -> options.uses(ClickCallback.UNLIMITED_USES).lifetime(VIEW_LIFETIME));
        return messages.get("inventory", Placeholder.unparsed("name", sender.getName()))
                .hoverEvent(messages.get("inventory-hover"))
                .clickEvent(open);
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
