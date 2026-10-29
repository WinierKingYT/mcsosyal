package com.sunucu.rpg.social.profile;

import com.mojang.brigadier.Command;
import com.sunucu.rpg.core.Durations;
import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.gui.Icons;
import com.sunucu.rpg.core.gui.Menu;
import com.sunucu.rpg.social.AfkTracker;
import com.sunucu.rpg.social.party.Parties;
import com.sunucu.rpg.social.party.PartyActions;
import com.sunucu.rpg.world.Regions;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * /oyuncular: önce çevrimiçiler (isim sırasıyla), sonra yeni çıkandan eskiye çevrimdışılar. Üstüne gelince
 * çevrimiçide bölge, AFK ve parti; çevrimdışında son görülme. Tıklayınca profil. 45'erli sayfa.
 */
public final class PlayersMenu {

    public static final String COMMAND = "oyuncular";

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final long RECENT_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final Messages messages;
    private final AfkTracker afk;
    private final Regions regions;
    private final Parties parties;
    private final PartyActions partyActions;
    private final ProfileMenu profile;

    public PlayersMenu(Messages messages, AfkTracker afk, Regions regions, Parties parties, PartyActions partyActions,
                       ProfileMenu profile) {
        this.messages = messages;
        this.afk = afk;
        this.regions = regions;
        this.parties = parties;
        this.partyActions = partyActions;
        this.profile = profile;
    }

    public void register(JavaPlugin plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(Commands.literal(COMMAND)
                        .requires(src -> src.getSender() instanceof Player)
                        .executes(ctx -> {
                            open((Player) ctx.getSource().getSender(), 0);
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(), "Çevrimiçi ve son giren oyuncular"));
    }

    public void open(Player viewer, int page) {
        List<OfflinePlayer> players = sorted(System.currentTimeMillis());
        int pages = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.clamp(page, 0, pages - 1);
        long online = players.stream().filter(OfflinePlayer::isOnline).count();
        Menu menu = new Menu(ROWS, messages.get("players-title",
                Placeholder.unparsed("online", String.valueOf(online))));
        for (int slot = PAGE_SIZE; slot < ROWS * 9; slot++) {
            menu.set(slot, Icons.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        int first = current * PAGE_SIZE;
        for (int i = 0; i < Math.min(PAGE_SIZE, players.size() - first); i++) {
            OfflinePlayer target = players.get(first + i);
            menu.button(i, head(target), click -> profile.open(viewer, target));
        }
        menu.set(INFO_SLOT, Icons.icon(Material.COMPASS, messages.get("players-info"), messages.lines("players-info-lore",
                Placeholder.unparsed("online", String.valueOf(online)),
                Placeholder.unparsed("page", String.valueOf(current + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages)))));
        if (current > 0) {
            menu.button(PREVIOUS_SLOT, Icons.icon(Material.ARROW, messages.get("players-previous"), List.of()),
                    click -> open(viewer, current - 1));
        }
        if (current < pages - 1) {
            menu.button(NEXT_SLOT, Icons.icon(Material.ARROW, messages.get("players-next"), List.of()),
                    click -> open(viewer, current + 1));
        }
        menu.open(viewer);
    }

    /** Çevrimiçiler isim sırasıyla, sonra son 7 günde girenler yeni çıkandan eskiye. */
    // shortcut: getOfflinePlayers tüm oyuncu dosyalarını tarar; binlerce oyuncuda yavaşlarsa çıkışları kendimiz kaydedelim.
    private static List<OfflinePlayer> sorted(long now) {
        List<OfflinePlayer> result = new ArrayList<>(Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(player -> player.getName().toLowerCase(Locale.ROOT)))
                .map(OfflinePlayer.class::cast)
                .toList());
        Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(player -> !player.isOnline() && player.getName() != null
                        && now - player.getLastSeen() < RECENT_MILLIS)
                .sorted(Comparator.comparingLong(OfflinePlayer::getLastSeen).reversed())
                .forEach(result::add);
        return result;
    }

    private ItemStack head(OfflinePlayer target) {
        List<Component> lore = new ArrayList<>();
        Player online = target.getPlayer();
        if (online != null) {
            lore.addAll(messages.lines("friend-online"));
            lore.addAll(messages.lines("players-place", Placeholder.unparsed("place", place(online))));
            if (afk.isAfk(online)) {
                lore.addAll(messages.lines("players-afk"));
            }
            parties.party(online.getUniqueId()).ifPresent(party -> lore.addAll(messages.lines("players-party",
                    Placeholder.component("party", partyActions.displayName(party)))));
        } else {
            lore.addAll(messages.lines("friend-offline"));
            lore.addAll(messages.lines("players-last-seen", Placeholder.unparsed("ago",
                    Durations.format(System.currentTimeMillis() - target.getLastSeen()))));
        }
        lore.add(Component.empty());
        lore.addAll(messages.lines("friend-click-profile"));
        return Icons.head(target, messages.get("friend-head",
                Placeholder.unparsed("name", target.getName() == null ? "?" : target.getName())), lore);
    }

    private String place(Player player) {
        Location location = player.getLocation();
        String world = location.getWorld().getName();
        return regions.at(world, location.getBlockX(), location.getBlockZ()).map(Regions.Region::name).orElse(world);
    }
}
