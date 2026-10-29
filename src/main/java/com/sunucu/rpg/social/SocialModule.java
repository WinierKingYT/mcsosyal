package com.sunucu.rpg.social;

import com.sunucu.rpg.core.Messages;
import com.sunucu.rpg.core.Module;
import com.sunucu.rpg.core.db.Database;
import com.sunucu.rpg.social.announce.Announcements;
import com.sunucu.rpg.social.chat.ChannelGate;
import com.sunucu.rpg.social.chat.ChannelPreferences;
import com.sunucu.rpg.social.chat.ChatCommands;
import com.sunucu.rpg.social.chat.ChatFilter;
import com.sunucu.rpg.social.chat.ChatListener;
import com.sunucu.rpg.social.chat.ChatLog;
import com.sunucu.rpg.social.chat.ChatMutes;
import com.sunucu.rpg.social.chat.LiveInventoryView;
import com.sunucu.rpg.social.chat.NewPlayerRule;
import com.sunucu.rpg.social.chat.Notifications;
import com.sunucu.rpg.social.chat.PlayerCard;
import com.sunucu.rpg.social.chat.PrivateMessages;
import com.sunucu.rpg.social.chat.Showcase;
import com.sunucu.rpg.social.chat.VanillaCommandGuard;
import com.sunucu.rpg.social.friends.FriendActions;
import com.sunucu.rpg.social.friends.FriendCommands;
import com.sunucu.rpg.social.friends.Friends;
import com.sunucu.rpg.social.moderation.Moderation;
import com.sunucu.rpg.social.moderation.ModerationCommands;
import com.sunucu.rpg.social.moderation.ModerationStore;
import com.sunucu.rpg.social.moderation.ProfileStaffButtons;
import com.sunucu.rpg.social.moderation.Reports;
import com.sunucu.rpg.social.moderation.WarnEscalation;
import com.sunucu.rpg.social.party.Parties;
import com.sunucu.rpg.social.party.PartyActions;
import com.sunucu.rpg.social.party.PartyColor;
import com.sunucu.rpg.social.party.PartyCommands;
import com.sunucu.rpg.social.party.PartyInfo;
import com.sunucu.rpg.social.party.PartyMenu;
import com.sunucu.rpg.social.profile.PlayersMenu;
import com.sunucu.rpg.social.profile.ProfileCommand;
import com.sunucu.rpg.social.profile.ProfileMenu;
import com.sunucu.rpg.world.Regions;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class SocialModule implements Module {

    // Arkadaş paneli tek sayfa: 45 kafa sığıyor.
    private static final int MAX_FRIENDS = 45;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long MILLIS_PER_MINUTE = 60_000;
    private static final long MILLIS_PER_DAY = 86_400_000;

    private final JavaPlugin plugin;
    private final Database database;
    private ChatListener chat;
    private ProfileMenu profile;
    private Parties parties;
    private PartyMenu partyMenu;

    /** database null ise (açılamadıysa) sosyal veriler sadece bellekte tutulur. */
    public SocialModule(JavaPlugin plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public void enable() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("chat");
        if (config == null) {
            throw new IllegalStateException(
                    "config.yml okunamadı ya da 'chat' bölümü yok; yukarıdaki YAML hatasına bak ve dosyayı düzelt");
        }
        Messages messages = new Messages(plugin.getConfig().getConfigurationSection("messages"));
        SocialStore store = openStore();
        ChatFilter filter = ChatFilter.fromConfig(config);
        Notifications notifications = new Notifications(config.getConfigurationSection("sounds"), messages, store,
                config.getLong("mention-cooldown-seconds", 10));
        AfkTracker afk = new AfkTracker(messages, config.getLong("afk-after-seconds", 300));
        ChatMutes mutes = new ChatMutes(store);
        PrivateMessages privateMessages = new PrivateMessages(messages, filter, notifications, afk, mutes, store);
        // shortcut: bölgeler şimdilik sadece [konum] için burada yükleniyor; bölge giriş mesajları gelince world modülüne taşı.
        Regions regions = Regions.fromConfig(plugin.getConfig().getMapList("regions"), plugin.getLogger()::warning);
        Showcase showcase = new Showcase(plugin, config, messages, filter, regions);
        ChannelPreferences preferences = new ChannelPreferences(store);
        PlayerCard card = new PlayerCard(config.getMapList("player-card"), messages);
        chat = new ChatListener(plugin, config, messages, filter, privateMessages, notifications, showcase,
                preferences, card, mutes);
        Friends friends = new Friends(store, Math.min(config.getInt("friends.max", 45), MAX_FRIENDS));
        FriendActions friendActions = new FriendActions(friends, messages, notifications);
        ConfigurationSection partyConfig = plugin.getConfig().getConfigurationSection("party");
        parties = new Parties(partyConfig == null ? 5 : partyConfig.getInt("default-size", 5),
                (partyConfig == null ? 60 : partyConfig.getLong("invite-seconds", 60)) * MILLIS_PER_SECOND,
                partyDefaults(partyConfig), PartyColor.from(partyConfig == null ? null
                        : partyConfig.getString("defaults.color")).orElse(PartyColor.AQUA).key());
        PartyActions partyActions = new PartyActions(parties, messages, notifications, privateMessages, filter,
                partyConfig == null ? 5 : partyConfig.getInt("size-limit", 5));
        chat.setMessageTint((sender, viewer) -> PartyColor.from(
                parties.sharedColor(sender.getUniqueId(), viewer.getUniqueId())).map(PartyColor::color).orElse(null));
        chat.setChannelGate("party", new ChannelGate() {
            @Override
            public boolean canSend(Player sender) {
                return parties.party(sender.getUniqueId()).isPresent();
            }

            @Override
            public boolean canSee(Player sender, Player viewer) {
                return parties.sameParty(sender.getUniqueId(), viewer.getUniqueId());
            }

            @Override
            public String deniedKey() {
                return "party-none";
            }
        });
        profile = new ProfileMenu(messages, friends, friendActions, privateMessages, card, chat::stats,
                parties, partyActions);
        SocialMenu menu = new SocialMenu(messages, chat, preferences, notifications, friends, friendActions,
                parties, partyActions, profile);
        profile.setSettings(menu::openMain);
        SocialData data = new SocialData(store, plugin.getLogger(), messages, preferences, notifications,
                privateMessages, mutes, friends);

        plugin.getServer().getPluginManager().registerEvents(data, plugin);
        data.loadOnline();
        plugin.getServer().getPluginManager().registerEvents(chat, plugin);
        plugin.getServer().getPluginManager().registerEvents(new VanillaCommandGuard(messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(afk, plugin);
        plugin.getServer().getPluginManager().registerEvents(friendActions, plugin);
        plugin.getServer().getPluginManager().registerEvents(partyActions, plugin);
        partyMenu = menu.partyMenu();
        new PlayersMenu(messages, afk, regions, parties, partyActions, profile).register(plugin);
        PartyCommands.register(plugin, parties, partyActions, partyMenu, messages);
        FriendCommands.register(plugin, friends, friendActions, menu.friendsMenu(), messages);
        ProfileCommand.register(plugin, profile, messages);
        Announcements announcements = new Announcements(messages, notifications,
                plugin.getConfig().getConfigurationSection("announcements"),
                Announcements.sound(config.getConfigurationSection("sounds")));
        announcements.register(plugin);
        announcements.start(plugin);
        plugin.getServer().getPluginManager().registerEvents(new LiveInventoryView.Protection(plugin), plugin);
        afk.start(plugin);
        ChatCommands.register(plugin, chat, privateMessages, afk, menu, messages);
        NewPlayerRule newPlayers = new NewPlayerRule(
                plugin.getConfig().getLong("moderation.new-player-minutes", 10) * MILLIS_PER_MINUTE);
        chat.setNewPlayerRule(newPlayers);
        privateMessages.setNewPlayerRule(newPlayers);
        enableModeration(config, messages, mutes, privateMessages, profile);
    }

    /** Moderasyon geçmişi ve yasaklar veritabanı ister; veritabanı yoksa moderasyon komutları da yok (SEVERE log). */
    private void enableModeration(ConfigurationSection chatConfig, Messages messages, ChatMutes mutes,
                                  PrivateMessages privateMessages, ProfileMenu profile) {
        if (database == null) {
            plugin.getLogger().severe("Veritabanı yok: moderasyon (ban, ceza geçmişi, şikayet, sohbet kaydı) kapalı");
            return;
        }
        ModerationStore store;
        try {
            store = ModerationStore.create(database);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Moderasyon tabloları kurulamadı, moderasyon kapalı", e);
            return;
        }
        ConfigurationSection settings = plugin.getConfig().getConfigurationSection("moderation");
        long chatLogDays = settings == null ? 30 : settings.getLong("chat-log-days", 30);
        long reportCooldown = settings == null ? 60 : settings.getLong("report-cooldown-seconds", 60);
        String defaultReason = messages.plain("mod-default-reason");

        WarnEscalation escalation = WarnEscalation.fromConfig(
                settings == null ? List.of() : settings.getMapList("warn-escalation"), plugin.getLogger()::warning);
        long warnWindowDays = settings == null ? 30 : settings.getLong("warn-window-days", 30);
        Moderation moderation = new Moderation(plugin, store, mutes, messages, escalation,
                warnWindowDays * MILLIS_PER_DAY);
        Reports reports = new Reports(moderation, store, messages, reportCooldown);
        ChatLog log = (player, channel, text) ->
                store.logChat(player.getUniqueId(), player.getName(), channel, text, System.currentTimeMillis());
        chat.setChatLog(log);
        privateMessages.setChatLog(log);
        long spamMuteMillis = chatConfig.getLong("spam.mute-minutes", 5) * MILLIS_PER_MINUTE;
        chat.setOnAutoMute(player -> moderation.recordAutoMute(player, spamMuteMillis));

        plugin.getServer().getPluginManager().registerEvents(moderation, plugin);
        plugin.getServer().getPluginManager().registerEvents(reports, plugin);
        ModerationCommands.register(plugin, moderation, reports, messages, defaultReason);
        profile.setStaffButtons(new ProfileStaffButtons(plugin, moderation, messages));
        store.purgeChatLog(System.currentTimeMillis() - chatLogDays * MILLIS_PER_DAY).whenComplete((deleted, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Eski sohbet kayıtları silinemedi", error);
            } else if (deleted > 0) {
                plugin.getLogger().info(deleted + " eski sohbet kaydı silindi (" + chatLogDays + " günden eski)");
            }
        });
    }

    /** config → party.defaults: yeni partide açık gelen ayarlar. */
    private static Set<Parties.Setting> partyDefaults(ConfigurationSection party) {
        Set<Parties.Setting> defaults = EnumSet.noneOf(Parties.Setting.class);
        if (party == null || party.getBoolean("defaults.open", true)) {
            defaults.add(Parties.Setting.OPEN);
        }
        if (party != null && party.getBoolean("defaults.members-invite", false)) {
            defaults.add(Parties.Setting.MEMBERS_INVITE);
        }
        if (party != null && party.getBoolean("defaults.friendly-fire", false)) {
            defaults.add(Parties.Setting.FRIENDLY_FIRE);
        }
        return defaults;
    }

    private SocialStore openStore() {
        if (database == null) {
            return SocialStore.NONE;
        }
        try {
            return SqliteSocialStore.create(database, plugin.getLogger());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Sosyal tablolar kurulamadı! Ayarlar bu oturumda kaydedilmeyecek", e);
            return SocialStore.NONE;
        }
    }

    public void setTagProvider(TagProvider provider) {
        chat.setTagProvider(provider);
    }

    /** RPG tarafı parti panelindeki bilgi kitabına buff, stat, XP paylaşımı satırlarını buradan verir. */
    public void setPartyInfo(PartyInfo info) {
        partyMenu.setInfo(info);
    }

    /** RPG tarafı için: kim kiminle partide (XP ve ganimet paylaşımı, zindan). Ana thread'den ya da async okunur. */
    public Parties parties() {
        return parties;
    }

    /** RPG tarafı profildeki ustalıkları (kazma, kürek, balta, çapa), peti ve bineği buradan verir. */
    public void setProfileProvider(ProfileProvider provider) {
        profile.setProvider(provider);
    }

    /** RPG tarafı oyuncu kartındaki değerleri (seviye, sınıf, lonca...) buradan verir. */
    public void setStatsProvider(StatsProvider provider) {
        chat.setStatsProvider(provider);
    }
}
