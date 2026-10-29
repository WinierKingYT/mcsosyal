package com.sunucu.rpg.social.profile;

import com.sunucu.rpg.core.Durations;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import com.sunucu.rpg.social.ProfileProvider;
import com.sunucu.rpg.social.chat.PlayerCard;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.friends.FriendActions;
import com.sunucu.rpg.social.friends.Friends;
import com.sunucu.rpg.social.party.Parties;
import com.sunucu.rpg.social.party.PartyActions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Oyuncu profili (6 sıra, sahibin çizimi). Ortada giydikleri (kask, göğüslük, pantolon, ayakkabı), göğüslüğün
 * solunda birinci el, sağında ikinci el; boş zırh kırmızı, boş birinci el turuncu, ikinci el sarı cam. Üstte kafa
 * (kart bilgileri) ve yanında mesaj, arkadaşlık, engelle, partiye davet. Solda pet ve altında binek (açık mavi cam), sağda ayarlar
 * (sadece kendi profilinde) ve altında saat. En altta ustalıklar. RPG yuvalarını ProfileProvider doldurur.
 * Yetkili butonları ayakkabının iki yanında (StaffButtons). Ekipman sadece oyuncu çevrimiçiyken görünür.
 */
public final class ProfileMenu {

    public static final String COMMAND = "/profil";

    private static final int ROWS = 6;
    private static final int ROW_SIZE = 9;
    private static final int HEAD_SLOT = 4;
    private static final int MESSAGE_SLOT = 2;
    private static final int FRIEND_SLOT = 3;
    private static final int IGNORE_SLOT = 5;
    private static final int PARTY_SLOT = 6;
    private static final int[] DARK_SLOTS = {2, 3, 5, 6, 49};
    private static final int HELMET_SLOT = 13;
    private static final int MAIN_HAND_SLOT = 21;
    private static final int CHEST_SLOT = 22;
    private static final int OFF_HAND_SLOT = 23;
    private static final int LEGS_SLOT = 31;
    private static final int BOOTS_SLOT = 40;
    private static final int PET_SLOT = 19;
    private static final int MOUNT_SLOT = 28;
    private static final int SETTINGS_SLOT = 25;
    private static final int STATS_SLOT = 34;
    private static final int[] STAFF_SLOTS = {38, 39, 41, 42};
    private static final double CM_PER_KM = 100_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    /** Ustalık yuvaları, en alt sıra: kazma, kürek, (koyu cam), balta, çapa. */
    private record MasterySlot(int slot, String skill, Material icon) {
    }

    private static final List<MasterySlot> MASTERIES = List.of(
            new MasterySlot(47, ProfileProvider.MINING, Material.DIAMOND_PICKAXE),
            new MasterySlot(48, ProfileProvider.EXCAVATION, Material.DIAMOND_SHOVEL),
            new MasterySlot(50, ProfileProvider.WOODCUTTING, Material.DIAMOND_AXE),
            new MasterySlot(51, ProfileProvider.FARMING, Material.DIAMOND_HOE));

    private final Messages messages;
    private final Friends friends;
    private final FriendActions friendActions;
    private final PrivateMessages privateMessages;
    private final PlayerCard card;
    private final Function<Player, Map<String, String>> stats;
    private final Parties parties;
    private final PartyActions partyActions;
    private StaffButtons staffButtons = StaffButtons.NONE;
    private ProfileProvider provider = ProfileProvider.NONE;
    private Consumer<Player> settings = player -> {
    };

    public ProfileMenu(Messages messages, Friends friends, FriendActions friendActions,
                       PrivateMessages privateMessages, PlayerCard card, Function<Player, Map<String, String>> stats,
                       Parties parties, PartyActions partyActions) {
        this.messages = messages;
        this.friends = friends;
        this.friendActions = friendActions;
        this.privateMessages = privateMessages;
        this.card = card;
        this.stats = stats;
        this.parties = parties;
        this.partyActions = partyActions;
    }

    /** Moderasyon bağlar; veritabanı yoksa bağlanmaz ve yetkili butonları çıkmaz. */
    public void setStaffButtons(StaffButtons buttons) {
        this.staffButtons = buttons == null ? StaffButtons.NONE : buttons;
    }

    /** Kendi profilindeki ayarlar butonu (sosyal ana menü). */
    public void setSettings(Consumer<Player> settings) {
        this.settings = settings;
    }

    /** RPG tarafı bağlar: ustalıklar, pet ve binek. */
    public void setProvider(ProfileProvider provider) {
        this.provider = provider == null ? ProfileProvider.NONE : provider;
    }

    public void open(Player viewer, OfflinePlayer target) {
        Menu menu = new Menu(ROWS, messages.get("profile-title", name(target)));
        frame(menu);
        fill(menu, viewer, target);
        menu.open(viewer);
    }

    /** Çerçeve beyaz (kenar sütunlar, üst ve alt köşeler), iç açık gri, kafanın yanları ve alt orta koyu gri. Yazısız. */
    private static void frame(Menu menu) {
        for (int slot = 0; slot < ROWS * ROW_SIZE; slot++) {
            int row = slot / ROW_SIZE;
            int column = slot % ROW_SIZE;
            boolean edge = column == 0 || column == ROW_SIZE - 1;
            boolean corner = (row == 0 || row == ROWS - 1) && (column == 1 || column == ROW_SIZE - 2);
            menu.set(slot, Icons.filler(edge || corner
                    ? Material.WHITE_STAINED_GLASS_PANE : Material.LIGHT_GRAY_STAINED_GLASS_PANE));
        }
        for (int slot : DARK_SLOTS) {
            menu.set(slot, Icons.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private void fill(Menu menu, Player viewer, OfflinePlayer target) {
        boolean own = viewer.getUniqueId().equals(target.getUniqueId());
        menu.set(HEAD_SLOT, head(target, own));
        equipment(menu, target.getPlayer());
        menu.button(PET_SLOT, companion(provider.pet(target).orElse(null), "profile-pet", "profile-pet-none", own),
                click -> provider.openPets(viewer, target));
        menu.button(MOUNT_SLOT, companion(provider.mount(target).orElse(null), "profile-mount", "profile-mount-none",
                own), click -> provider.openMounts(viewer, target));
        if (own) {
            menu.button(SETTINGS_SLOT, Icons.icon(Material.WRITABLE_BOOK, messages.get("profile-settings"),
                    messages.lines("profile-settings-lore")), click -> settings.accept(viewer));
        }
        menu.set(STATS_SLOT, Icons.icon(Material.CLOCK, messages.get("profile-playtime"), statsLore(target)));
        for (MasterySlot mastery : MASTERIES) {
            menu.button(mastery.slot(), masteryIcon(target, mastery),
                    click -> provider.openMastery(viewer, target, mastery.skill()));
        }
        if (own) {
            return;
        }
        socialButtons(menu, viewer, target);
        List<StaffButtons.Button> staff = staffButtons.buttons(viewer, target);
        for (int i = 0; i < Math.min(staff.size(), STAFF_SLOTS.length); i++) {
            StaffButtons.Button button = staff.get(i);
            menu.button(STAFF_SLOTS[i], button.icon(), click -> button.action().run());
        }
    }

    private ItemStack head(OfflinePlayer target, boolean own) {
        Player online = target.getPlayer();
        Map<String, String> values = online != null ? stats.apply(online)
                : Map.of("playtime", Durations.format(Playtime.millis(target)));
        List<Component> lore = new ArrayList<>(card.lines(nameOf(target), values));
        if (own) {
            lore.add(Component.empty());
            lore.addAll(messages.lines("profile-own"));
        }
        return Icons.head(target, messages.get("profile-head", name(target)), lore);
    }

    /** Gerçek eşyanın kopyası: üstüne gelince eşyanın kendi bilgisi görünür. Boşsa ya da çevrimdışıysa kırmızı cam. */
    // shortcut: eşya isimleri ve lore'u filtreden geçmiyor ([i]'deki gibi); profilde reklam görülürse Showcase filtresini kullan.
    private void equipment(Menu menu, Player online) {
        PlayerInventory inventory = online == null ? null : online.getInventory();
        Material red = Material.RED_STAINED_GLASS_PANE;
        menu.set(HELMET_SLOT, worn(inventory == null ? null : inventory.getHelmet(), "profile-helmet", red, online));
        menu.set(CHEST_SLOT, worn(inventory == null ? null : inventory.getChestplate(), "profile-chestplate", red, online));
        menu.set(LEGS_SLOT, worn(inventory == null ? null : inventory.getLeggings(), "profile-leggings", red, online));
        menu.set(BOOTS_SLOT, worn(inventory == null ? null : inventory.getBoots(), "profile-boots", red, online));
        menu.set(MAIN_HAND_SLOT, worn(inventory == null ? null : inventory.getItemInMainHand(), "profile-main-hand",
                Material.ORANGE_STAINED_GLASS_PANE, online));
        menu.set(OFF_HAND_SLOT, worn(inventory == null ? null : inventory.getItemInOffHand(), "profile-off-hand",
                Material.YELLOW_STAINED_GLASS_PANE, online));
    }

    /** Boş yuva renkli cam: zırh kırmızı, birinci el turuncu, ikinci el sarı. */
    private ItemStack worn(ItemStack item, String key, Material empty, Player online) {
        if (item != null && !item.isEmpty()) {
            return item.clone();
        }
        return Icons.icon(empty, messages.get(key),
                messages.lines(online == null ? "profile-slot-offline" : "profile-slot-empty"));
    }

    /** Takılıysa RPG'nin verdiği ikon, değilse açık mavi cam. Kendi profilinde tıklayınca menüye gideceği yazar. */
    private ItemStack companion(ItemStack equipped, String nameKey, String noneKey, boolean own) {
        if (equipped != null) {
            return equipped.clone();
        }
        String loreKey = provider == ProfileProvider.NONE ? "profile-soon" : noneKey;
        List<Component> lore = new ArrayList<>(messages.lines(loreKey));
        if (own && provider != ProfileProvider.NONE) {
            lore.addAll(messages.lines(nameKey + "-click"));
        }
        return Icons.icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, messages.get(nameKey), lore);
    }

    private ItemStack masteryIcon(OfflinePlayer target, MasterySlot slot) {
        Component name = messages.get("profile-mastery-" + slot.skill());
        List<Component> lore = new ArrayList<>();
        provider.mastery(target, slot.skill()).ifPresentOrElse(mastery -> {
            double progress = Math.clamp(mastery.progress(), 0, 1);
            lore.addAll(messages.lines("profile-mastery-lore",
                    Placeholder.unparsed("level", String.valueOf(mastery.level())),
                    Placeholder.unparsed("bar", progressBar(progress)),
                    Placeholder.unparsed("percent", String.valueOf((int) Math.floor(progress * 100)))));
            lore.addAll(mastery.details());
            lore.addAll(messages.lines("profile-mastery-click"));
        }, () -> lore.addAll(messages.lines("profile-soon")));
        return Icons.hideAttributes(Icons.icon(slot.icon(), name, lore));
    }

    /** 10 kare: dolu kısım ■, kalan □. */
    static String progressBar(double progress) {
        int filled = (int) Math.round(Math.clamp(progress, 0, 1) * 10);
        return "■".repeat(filled) + "□".repeat(10 - filled);
    }

    private void socialButtons(Menu menu, Player viewer, OfflinePlayer target) {
        Player online = target.getPlayer();
        menu.button(MESSAGE_SLOT, Icons.icon(Material.WRITABLE_BOOK, messages.get("profile-message"),
                messages.lines(online != null ? "profile-message-lore" : "profile-offline-lore")), click -> {
            if (target.getPlayer() != null) {
                viewer.closeInventory();
                viewer.sendMessage(messages.get("friend-message-prompt", name(target))
                        .clickEvent(ClickEvent.suggestCommand("/msg " + nameOf(target) + " ")));
            }
        });
        menu.button(FRIEND_SLOT, friendIcon(viewer.getUniqueId(), target),
                click -> onFriendClick(menu, viewer, target, click));
        boolean ignored = privateMessages.isIgnoring(viewer.getUniqueId(), target.getUniqueId());
        menu.button(IGNORE_SLOT, Icons.icon(Material.BARRIER, messages.get("profile-ignore"),
                messages.lines(ignored ? "profile-ignored-lore" : "profile-not-ignored-lore"), ignored), click -> {
            boolean now = privateMessages.toggleIgnore(viewer.getUniqueId(), target.getUniqueId());
            viewer.sendMessage(messages.get(now ? "ignore-on" : "ignore-off", name(target)));
            socialButtons(menu, viewer, target);
        });
        boolean together = parties.sameParty(viewer.getUniqueId(), target.getUniqueId());
        menu.button(PARTY_SLOT, Icons.icon(Material.CAKE, messages.get("profile-party"), messages.lines(together
                ? "profile-party-together" : online != null ? "profile-party-invite" : "profile-offline-lore"),
                together), click -> {
            Player now = target.getPlayer();
            if (!together && now != null) {
                partyActions.invite(viewer, now);
                viewer.closeInventory();
            }
        });
    }

    /** Arkadaşlık butonu duruma göre değişir: arkadaş / sana istek gönderdi / istek gönderdin / ekle. */
    private ItemStack friendIcon(UUID viewer, OfflinePlayer target) {
        UUID other = target.getUniqueId();
        String loreKey;
        boolean glow = false;
        if (friends.areFriends(viewer, other)) {
            loreKey = "profile-friends-lore";
            glow = true;
        } else if (friends.isRequested(other, viewer)) {
            loreKey = "profile-incoming-lore";
            glow = true;
        } else if (friends.isRequested(viewer, other)) {
            loreKey = "profile-outgoing-lore";
        } else if (target.getPlayer() != null) {
            loreKey = "profile-add-lore";
        } else {
            loreKey = "profile-add-offline-lore";
        }
        return Icons.icon(Material.PLAYER_HEAD, messages.get("profile-friend"), messages.lines(loreKey), glow);
    }

    private void onFriendClick(Menu menu, Player viewer, OfflinePlayer target, ClickType click) {
        UUID other = target.getUniqueId();
        if (friends.areFriends(viewer.getUniqueId(), other)) {
            if (click == ClickType.SHIFT_RIGHT) {
                friendActions.remove(viewer, other);
            }
        } else if (friends.isRequested(other, viewer.getUniqueId())) {
            if (click.isLeftClick()) {
                friendActions.accept(viewer, other);
            } else if (click.isRightClick()) {
                friendActions.deny(viewer, other);
            }
        } else if (!friends.isRequested(viewer.getUniqueId(), other) && target.getPlayer() != null) {
            friendActions.request(viewer, target.getPlayer());
        }
        socialButtons(menu, viewer, target);
    }

    /** Saat: oynama süresi, ilk giriş, son görülme ve Minecraft istatistikleri (çevrimdışı da okunur). */
    private List<Component> statsLore(OfflinePlayer target) {
        List<Component> lore = new ArrayList<>(messages.lines("profile-playtime-total",
                Placeholder.unparsed("time", Durations.format(Playtime.millis(target)))));
        if (target.getFirstPlayed() > 0) {
            lore.addAll(messages.lines("profile-first-joined",
                    Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(target.getFirstPlayed())))));
        }
        if (target.isOnline()) {
            lore.addAll(messages.lines("profile-online-now"));
        } else if (target.getLastSeen() > 0) {
            long ago = System.currentTimeMillis() - target.getLastSeen();
            lore.addAll(messages.lines("profile-last-seen",
                    Placeholder.unparsed("date", DATE.format(Instant.ofEpochMilli(target.getLastSeen()))),
                    Placeholder.unparsed("ago", Durations.format(ago))));
        }
        if (target.isOnline() || target.hasPlayedBefore()) {
            double km = (target.getStatistic(Statistic.WALK_ONE_CM) + target.getStatistic(Statistic.SPRINT_ONE_CM))
                    / CM_PER_KM;
            lore.add(Component.empty());
            lore.addAll(messages.lines("profile-stats",
                    Placeholder.unparsed("deaths", String.valueOf(target.getStatistic(Statistic.DEATHS))),
                    Placeholder.unparsed("mobs", String.valueOf(target.getStatistic(Statistic.MOB_KILLS))),
                    Placeholder.unparsed("players", String.valueOf(target.getStatistic(Statistic.PLAYER_KILLS))),
                    Placeholder.unparsed("km", String.format(Locale.ROOT, "%.1f", km))));
        }
        return lore;
    }

    private static String nameOf(OfflinePlayer player) {
        return player.getName() == null ? "?" : player.getName();
    }

    private static TagResolver name(OfflinePlayer player) {
        return Placeholder.unparsed("name", nameOf(player));
    }
}
