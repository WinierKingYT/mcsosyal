package com.sunucu.rpg.social.party;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import com.sunucu.rpg.social.friends.FriendActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * /parti. Partide değilse lobi: Parti Kur, Mevcut Partiler (açık partilere katıl) ve gelen davetler. Partideyse
 * parti paneli (6 sıra): bilgi kitabı, üyelerin kafaları (sol tık profil; lider için sağ tık lider yap, shift + sağ
 * tık at; boş yer davet), kapat/ayrıl ve ayarlar. Ayarlar: açık/gizli, davet, dost ateşi, boyut, renk, etiket,
 * açıklama; lider değiştirir. /parti admin: tüm partiler, admin her partiyi düzenler.
 */
public final class PartyMenu {

    private static final int ROWS = 3;
    private static final int LIST_ROWS = 6;
    private static final int LIST_SIZE = 45;
    private static final int FIRST_SLOT = 10;
    private static final int MAX_HEADS = 7;
    private static final int CREATE_SLOT = 11;
    private static final int INVITES_SLOT = 13;
    private static final int LIST_SLOT = 15;
    private static final int BACK_SLOT = 18;
    private static final int LIST_BACK_SLOT = 49;
    private static final int LIST_EMPTY_SLOT = 22;
    private static final int ROW_SIZE = 9;
    private static final int PANEL_ROWS = 6;
    private static final int PANEL_INFO_SLOT = 4;
    /** Üye yerleri: iki sıra, 5'er (en fazla Parties.MAX_SIZE). */
    private static final int[] PANEL_HEAD_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private static final int PANEL_BACK_SLOT = 45;
    private static final int PANEL_CLOSE_SLOT = 48;
    private static final int PANEL_SETTINGS_SLOT = 50;
    private static final int[] SETTING_SLOTS = {10, 11, 12};
    private static final Material[] SETTING_ICONS = {Material.ENDER_EYE, Material.WRITABLE_BOOK, Material.IRON_SWORD};
    private static final int SIZE_SLOT = 13;
    private static final int COLOR_SLOT = 14;
    private static final int ADMIN_CLEAR_SLOT = 24;
    private static final int ADMIN_DISBAND_SLOT = 26;
    private static final int TAG_SLOT = 15;
    private static final int DESCRIPTION_SLOT = 16;

    private final Parties parties;
    private final PartyActions actions;
    private final Messages messages;
    private final Consumer<Player> back;
    private final BiConsumer<Player, OfflinePlayer> openProfile;
    private PartyInfo info = PartyInfo.NONE;

    public PartyMenu(Parties parties, PartyActions actions, Messages messages, Consumer<Player> back,
                     BiConsumer<Player, OfflinePlayer> openProfile) {
        this.parties = parties;
        this.actions = actions;
        this.messages = messages;
        this.back = back;
        this.openProfile = openProfile;
    }

    /** RPG tarafı bağlar: bilgi kitabına buff, stat, XP paylaşımı satırları. */
    public void setInfo(PartyInfo info) {
        this.info = info == null ? PartyInfo.NONE : info;
    }

    public void open(Player player) {
        parties.party(player.getUniqueId()).ifPresentOrElse(party -> panel(player, party), () -> lobby(player));
    }

    private void lobby(Player player) {
        Menu menu = new Menu(ROWS, messages.get("party-title-none"));
        menu.button(CREATE_SLOT, Icons.icon(Material.NETHER_STAR, messages.get("party-create"),
                messages.lines("party-create-lore")), click -> {
            actions.create(player);
            open(player);
        });
        int invites = parties.invitesTo(player.getUniqueId(), System.currentTimeMillis()).size();
        if (invites > 0) {
            menu.button(INVITES_SLOT, Icons.icon(Material.PAPER, messages.get("party-invites",
                    count(invites)), messages.lines("party-invites-lore"), true), click -> invites(player));
        }
        menu.button(LIST_SLOT, Icons.icon(Material.COMPASS, messages.get("party-list",
                count(parties.openParties().size())), messages.lines("party-list-lore")), click -> list(player));
        menu.button(BACK_SLOT, backIcon(), click -> back.accept(player));
        menu.open(player);
    }

    /** Açık partiler: liderin kafası, tıklayınca katılır. */
    private void list(Player player) {
        Menu menu = new Menu(LIST_ROWS, messages.get("party-list-title"));
        List<Parties.Party> open = parties.openParties();
        for (int i = 0; i < Math.min(open.size(), LIST_SIZE); i++) {
            Parties.Party party = open.get(i);
            List<Component> lore = new ArrayList<>(messages.lines("party-list-entry-lore",
                    Placeholder.unparsed("leader", FriendActions.nameOf(party.leader())),
                    count(party.members().size()), max(party)));
            lore.addAll(description(party));
            lore.addAll(messages.lines("party-list-join"));
            menu.button(i, Icons.head(Bukkit.getOfflinePlayer(party.leader()), actions.displayName(party), lore),
                    click -> {
                actions.joinOpen(player, party.id());
                open(player);
            });
        }
        if (open.isEmpty()) {
            menu.set(LIST_EMPTY_SLOT, Icons.icon(Material.BARRIER, messages.get("party-list-empty"),
                    messages.lines("party-list-empty-lore")));
        }
        menu.button(LIST_BACK_SLOT, backIcon(), click -> open(player));
        menu.open(player);
    }

    private void invites(Player player) {
        Menu menu = new Menu(ROWS, messages.get("party-invites-title"));
        List<UUID> inviters = List.copyOf(parties.invitesTo(player.getUniqueId(), System.currentTimeMillis()));
        for (int i = 0; i < Math.min(inviters.size(), MAX_HEADS); i++) {
            UUID id = inviters.get(i);
            menu.button(FIRST_SLOT + i, Icons.head(Bukkit.getOfflinePlayer(id),
                    messages.get("party-invite-head", Placeholder.unparsed("name", FriendActions.nameOf(id))),
                    messages.lines("party-invite-lore")), click -> {
                if (click.isLeftClick()) {
                    actions.accept(player, id);
                } else if (click.isRightClick()) {
                    actions.deny(player, id);
                }
                open(player);
            });
        }
        menu.button(BACK_SLOT, backIcon(), click -> open(player));
        menu.open(player);
    }

    /**
     * Parti paneli (6 sıra, sahibin çizimi): üstte bilgi kitabı, altında üyelerin kafaları (boş yer: tıkla davet et),
     * altta geri, kapat/ayrıl (lider partiyi kapatır, üye ayrılır) ve ayarlar.
     */
    private void panel(Player viewer, Parties.Party party) {
        Menu menu = new Menu(PANEL_ROWS, messages.get("party-title",
                Placeholder.component("party", actions.displayName(party)), count(party.members().size()), max(party)));
        for (int slot = 0; slot < PANEL_ROWS * ROW_SIZE; slot++) {
            int row = slot / ROW_SIZE;
            int column = slot % ROW_SIZE;
            boolean inner = row >= 2 && row <= 4 && column >= 2 && column <= 6;
            menu.set(slot, Icons.filler(inner ? Material.LIGHT_GRAY_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
        }
        boolean leader = party.leader().equals(viewer.getUniqueId());
        menu.set(PANEL_INFO_SLOT, Icons.icon(Material.BOOK, messages.get("party-info"), infoLore(party)));
        List<UUID> members = party.members();
        for (int i = 0; i < Math.min(party.size(), PANEL_HEAD_SLOTS.length); i++) {
            int slot = PANEL_HEAD_SLOTS[i];
            if (i >= members.size()) {
                menu.button(slot, Icons.icon(Material.LIME_STAINED_GLASS_PANE, messages.get("party-free-slot"),
                        messages.lines("party-free-slot-lore")), click -> invitePicker(viewer));
                continue;
            }
            UUID id = members.get(i);
            OfflinePlayer member = Bukkit.getOfflinePlayer(id);
            List<Component> lore = new ArrayList<>();
            if (id.equals(party.leader())) {
                lore.addAll(messages.lines("party-leader-tag"));
            }
            lore.addAll(messages.lines(member.isOnline() ? "friend-online" : "friend-offline"));
            lore.add(Component.empty());
            lore.addAll(messages.lines("friend-click-profile"));
            if (leader && !id.equals(viewer.getUniqueId())) {
                lore.addAll(messages.lines("party-member-leader-lore"));
            }
            menu.button(slot, Icons.head(member, messages.get("friend-head",
                            Placeholder.unparsed("name", FriendActions.nameOf(id))), lore),
                    click -> onMemberClick(viewer, member, leader, click));
        }
        menu.button(PANEL_BACK_SLOT, backIcon(), click -> back.accept(viewer));
        menu.button(PANEL_CLOSE_SLOT, Icons.icon(Material.BARRIER, messages.get(leader ? "party-disband" : "party-leave"),
                messages.lines(leader ? "party-disband-lore" : "party-leave-lore")), click -> {
            if (leader && click != ClickType.SHIFT_LEFT) {
                viewer.sendMessage(messages.get("party-disband-confirm"));
                return;
            }
            if (leader) {
                actions.disband(viewer, party.id());
            } else {
                actions.leave(viewer);
            }
            open(viewer);
        });
        menu.button(PANEL_SETTINGS_SLOT, Icons.icon(Material.COMPARATOR, messages.get("party-settings"),
                messages.lines(leader ? "party-settings-lore" : "party-settings-view-lore")),
                click -> settings(viewer, party.id(), () -> open(viewer)));
        menu.open(viewer);
    }

    /** Kitap: sosyal bilgiler (lider, kişi, ayarlar, açıklama) + RPG'nin verdiği satırlar (buff, stat, XP). */
    private List<Component> infoLore(Parties.Party party) {
        List<Component> lore = new ArrayList<>(messages.lines("party-info-lore",
                Placeholder.unparsed("leader", FriendActions.nameOf(party.leader())),
                count(party.members().size()), max(party),
                Placeholder.component("visibility", messages.get(party.is(Parties.Setting.OPEN)
                        ? "party-info-open" : "party-info-hidden")),
                Placeholder.component("friendly", messages.get(party.is(Parties.Setting.FRIENDLY_FIRE)
                        ? "party-setting-on" : "party-setting-off")),
                Placeholder.component("color", messages.get("party-color-" + PartyActions.color(party).key()))));
        lore.addAll(description(party));
        List<Component> rpg = info.lines(party);
        if (!rpg.isEmpty()) {
            lore.add(Component.empty());
            lore.addAll(rpg);
        }
        return lore;
    }

    /** Boş yere tıklayınca: partide olmayan çevrimiçi oyuncular, kafaya tıkla davet et. */
    private void invitePicker(Player viewer) {
        Menu menu = new Menu(LIST_ROWS, messages.get("party-invite-picker-title"));
        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(player -> !player.equals(viewer) && parties.party(player.getUniqueId()).isEmpty())
                .map(Player.class::cast)
                .limit(LIST_SIZE)
                .toList();
        for (int i = 0; i < candidates.size(); i++) {
            Player target = candidates.get(i);
            menu.button(i, Icons.head(target, messages.get("friend-head", Placeholder.unparsed("name", target.getName())),
                    messages.lines("party-invite-picker-lore")), click -> {
                if (target.isOnline()) {
                    actions.invite(viewer, target);
                }
                open(viewer);
            });
        }
        if (candidates.isEmpty()) {
            menu.set(LIST_EMPTY_SLOT, Icons.icon(Material.BARRIER, messages.get("party-invite-picker-empty"), List.of()));
        }
        menu.button(LIST_BACK_SLOT, backIcon(), click -> open(viewer));
        menu.open(viewer);
    }

    private void onMemberClick(Player viewer, OfflinePlayer member, boolean leader, ClickType click) {
        boolean self = member.getUniqueId().equals(viewer.getUniqueId());
        if (click == ClickType.SHIFT_RIGHT && leader && !self) {
            actions.kick(viewer, member.getUniqueId());
            open(viewer);
        } else if (click.isRightClick() && leader && !self) {
            actions.promote(viewer, member.getUniqueId());
            open(viewer);
        } else if (click.isLeftClick()) {
            openProfile.accept(viewer, member);
        }
    }

    /** /parti ayarlar: kendi partisinin ayarları. */
    public void openSettings(Player player) {
        parties.party(player.getUniqueId()).ifPresentOrElse(party -> settings(player, party.id(), () -> open(player)),
                () -> player.sendMessage(messages.get("party-none")));
    }

    /** /parti admin: sunucudaki tüm partiler (gizliler dahil); tıkla, o partinin ayarlarını düzenle. */
    public void openAdmin(Player admin) {
        Menu menu = new Menu(LIST_ROWS, messages.get("party-admin-title"));
        List<Parties.Party> all = parties.all();
        for (int i = 0; i < Math.min(all.size(), LIST_SIZE); i++) {
            Parties.Party party = all.get(i);
            List<Component> lore = new ArrayList<>(messages.lines("party-list-entry-lore",
                    Placeholder.unparsed("leader", FriendActions.nameOf(party.leader())),
                    count(party.members().size()), max(party)));
            lore.add(messages.get(party.is(Parties.Setting.OPEN) ? "party-info-open" : "party-info-hidden"));
            lore.addAll(description(party));
            lore.addAll(messages.lines("party-admin-click"));
            menu.button(i, Icons.head(Bukkit.getOfflinePlayer(party.leader()), actions.displayName(party), lore),
                    click -> settings(admin, party.id(), () -> openAdmin(admin)));
        }
        if (all.isEmpty()) {
            menu.set(LIST_EMPTY_SLOT, Icons.icon(Material.BARRIER, messages.get("party-list-empty"), List.of()));
        }
        menu.open(admin);
    }

    /**
     * Her ayar bir buton: açıksa parlar. Lider (ya da admin) değiştirir, diğer üyeler sadece görür. Admin için ek:
     * etiket/açıklamayı temizle ve partiyi kapat. back: geri butonu nereye döner.
     */
    private void settings(Player player, long partyId, Runnable back) {
        Optional<Parties.Party> found = parties.byId(partyId);
        if (found.isEmpty()) {
            player.sendMessage(messages.get("party-gone"));
            back.run();
            return;
        }
        Parties.Party party = found.get();
        boolean editor = actions.canEdit(player, party);
        Runnable reopen = () -> settings(player, partyId, back);
        Menu menu = new Menu(ROWS, messages.get("party-settings-title"));
        Parties.Setting[] settings = Parties.Setting.values();
        for (int i = 0; i < settings.length; i++) {
            Parties.Setting setting = settings[i];
            boolean on = party.is(setting);
            List<Component> lore = new ArrayList<>(messages.lines(PartyActions.settingKey(setting) + "-lore"));
            lore.add(Component.empty());
            lore.add(messages.get(on ? "party-setting-on" : "party-setting-off"));
            lore.addAll(messages.lines(editor ? "party-setting-click" : "party-setting-leader-only"));
            menu.button(SETTING_SLOTS[i], Icons.hideAttributes(Icons.icon(SETTING_ICONS[i],
                            messages.get(PartyActions.settingKey(setting)), lore, on)),
                    click -> {
                        actions.toggle(player, partyId, setting);
                        reopen.run();
                    });
        }
        sizeButton(menu, player, party, editor, reopen);
        styleButtons(menu, player, party, editor, reopen);
        if (player.hasPermission(PartyActions.ADMIN)) {
            adminButtons(menu, player, party, back);
        }
        menu.button(BACK_SLOT, backIcon(), click -> back.run());
        menu.open(player);
    }

    /** Sandık, yığın sayısı parti boyutunu gösterir. Sol tık +1, sağ tık -1. */
    private void sizeButton(Menu menu, Player player, Parties.Party party, boolean editor, Runnable reopen) {
        List<Component> lore = new ArrayList<>(messages.lines("party-size-lore",
                Placeholder.unparsed("size", String.valueOf(party.size())),
                Placeholder.unparsed("limit", String.valueOf(actions.sizeLimit(player)))));
        lore.addAll(messages.lines(editor ? "party-size-click" : "party-setting-leader-only"));
        ItemStack icon = Icons.icon(Material.CHEST, messages.get("party-size"), lore);
        icon.setAmount(party.size());
        menu.button(SIZE_SLOT, icon, click -> {
            actions.changeSize(player, party.id(), click.isRightClick() ? -1 : 1);
            reopen.run();
        });
    }

    private void adminButtons(Menu menu, Player admin, Parties.Party party, Runnable back) {
        menu.button(ADMIN_CLEAR_SLOT, Icons.icon(Material.SPONGE, messages.get("party-admin-clear"),
                messages.lines("party-admin-clear-lore")), click -> {
            actions.clearTexts(admin, party.id());
            settings(admin, party.id(), back);
        });
        menu.button(ADMIN_DISBAND_SLOT, Icons.icon(Material.TNT, messages.get("party-disband"),
                messages.lines("party-disband-lore")), click -> {
            if (click != ClickType.SHIFT_LEFT) {
                admin.sendMessage(messages.get("party-disband-confirm"));
                return;
            }
            actions.disband(admin, party.id());
            back.run();
        });
    }

    /** Renk boyayla (sol tık sonraki, sağ tık önceki); etiket ve açıklama komutla yazılır, buton komutu hazırlar. */
    private void styleButtons(Menu menu, Player player, Parties.Party party, boolean editor, Runnable reopen) {
        PartyColor color = PartyActions.color(party);
        List<Component> colorLore = new ArrayList<>(messages.lines("party-color-lore",
                Placeholder.component("color", messages.get("party-color-" + color.key()))));
        colorLore.addAll(messages.lines(editor ? "party-color-click" : "party-setting-leader-only"));
        menu.button(COLOR_SLOT, Icons.icon(color.dye(), messages.get("party-color"), colorLore), click -> {
            actions.cycleColor(player, party.id(), !click.isRightClick());
            reopen.run();
        });
        // Etiket ve açıklama komutu kendi partisine yazar: başkasının partisinde (admin) sadece temizleme var.
        boolean own = party.leader().equals(player.getUniqueId());
        menu.button(TAG_SLOT, textButton(Material.NAME_TAG, "party-tag-button", party.tag(), own), click -> {
            if (own) {
                suggest(player, "party-tag-prompt", PartyActions.COMMAND + " etiket ");
            }
        });
        menu.button(DESCRIPTION_SLOT, textButton(Material.OAK_SIGN, "party-description-button", party.description(),
                own), click -> {
            if (own) {
                suggest(player, "party-description-prompt", PartyActions.COMMAND + " açıklama ");
            }
        });
    }

    private ItemStack textButton(Material icon, String key, String current, boolean leader) {
        List<Component> lore = new ArrayList<>(messages.lines(key + "-lore"));
        lore.add(Component.empty());
        lore.addAll(messages.lines(current == null ? "party-text-none" : "party-text-current",
                Placeholder.unparsed("text", current == null ? "" : current)));
        lore.addAll(messages.lines(leader ? "party-text-click" : "party-setting-leader-only"));
        return Icons.icon(icon, messages.get(key), lore);
    }

    private void suggest(Player player, String key, String command) {
        player.closeInventory();
        player.sendMessage(messages.get(key).clickEvent(ClickEvent.suggestCommand(command)));
    }

    private List<Component> description(Parties.Party party) {
        return party.description() == null ? List.of()
                : messages.lines("party-description-line", Placeholder.unparsed("text", party.description()));
    }

    private ItemStack backIcon() {
        return Icons.icon(Material.ARROW, messages.get("menu-back"), messages.lines("menu-back-lore"));
    }

    private static TagResolver max(Parties.Party party) {
        return Placeholder.unparsed("max", String.valueOf(party.size()));
    }

    private static TagResolver count(int count) {
        return Placeholder.unparsed("count", String.valueOf(count));
    }
}
