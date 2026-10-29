package com.sunucu.rpg.social.party;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Partiler. Geçici: sadece bellekte, sunucu yeniden başlayınca dağılır. Oyundan çıkan üye partide kalır.
 * Parti /parti kur ile kurulur, kurucu lider olur. Açık parti listede görünür ve herkes katılabilir; gizli partiye
 * sadece davetle girilir. Son üye ayrılınca parti dağılır. Her partinin kendi boyutu var (MIN_SIZE-MAX_SIZE).
 * Ayar, boyut, etiket gibi düzenlemeler parti numarasıyla yapılır; kimin yapabileceğini (lider ya da admin)
 * PartyActions kontrol eder. Bukkit'e bağımlı değil, test edilebilir.
 * Parti sohbeti ve dost ateşi async/olay thread'inden okuduğu için metotlar synchronized.
 * RPG tarafı (XP/ganimet paylaşımı, zindan) party ve sameParty ile okur.
 */
public final class Parties {

    public enum CreateResult { CREATED, ALREADY_IN_PARTY }

    public enum InviteResult { SENT, SELF, ALREADY_IN_PARTY, NOT_IN_PARTY, NO_PERMISSION, ALREADY_INVITED, FULL }

    public enum JoinResult { JOINED, NO_INVITE, GONE, CLOSED, ALREADY_IN_PARTY, FULL }

    public enum LeaveResult { NOT_IN_PARTY, LEFT, LEFT_NEW_LEADER, DISBANDED }

    public enum KickResult { KICKED, NOT_LEADER, NOT_MEMBER, SELF }


    public enum PromoteResult { PROMOTED, NOT_LEADER, NOT_MEMBER, SELF }

    public enum ToggleResult { ON, OFF, GONE }

    public enum EditResult { SET, CLEARED, INVALID, TAKEN, GONE }

    /** Parti boyutu sınırları; panelde 10 kafa yeri var. */
    public static final int MIN_SIZE = 2;
    public static final int MAX_SIZE = 10;

    /** Etiket: 2-5 harf ya da rakam, partiler arasında tek (büyük/küçük harf fark etmez). */
    public static final Pattern TAG = Pattern.compile("[\\p{L}\\p{N}]{2,5}");
    public static final int MAX_DESCRIPTION = 60;

    /** Parti ayarları; değiştirmeyi sadece lider yapar. */
    public enum Setting {
        /** Açık: listede görünür, herkes katılır. Kapalı (gizli): sadece davetle. */
        OPEN,
        /** Açık: üyeler de davet edebilir. Kapalı: sadece lider. */
        MEMBERS_INVITE,
        /** Açık: üyeler birbirine vurabilir. Kapalı: parti içi hasar engellenir. */
        FRIENDLY_FIRE
    }

    /**
     * Değişmez kopya: members katılma sırasıyla, lider dahil. tag ve description yoksa null. color: partinin rengi
     * (Adventure renk adı, ör. "aqua"); üyeler birbirinin sohbet mesajını bu renkte görür.
     */
    public record Party(long id, String name, UUID leader, List<UUID> members, Set<Setting> settings, String tag,
                        String description, String color, int size) {

        public boolean is(Setting setting) {
            return settings.contains(setting);
        }
    }

    private static final class Group {
        private final long id;
        private final String name;
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();
        private final Set<Setting> settings = new LinkedHashSet<>();
        private String tag;
        private String description;
        private String color;
        private int size;

        private Group(long id, String name, String color, int size) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.size = size;
        }

        private Party snapshot() {
            return new Party(id, name, leader, List.copyOf(members), Set.copyOf(settings), tag, description, color,
                    size);
        }
    }

    private final int defaultSize;
    private final long inviteMillis;
    private final Set<Setting> defaults;
    private final String defaultColor;
    private final Map<UUID, Group> byMember = new HashMap<>();
    private final Map<Long, Group> byId = new LinkedHashMap<>();
    // davet edilen → (davet eden → bitiş zamanı)
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();
    private long nextId = 1;

    /** Yeni partinin boyutu, açık gelen ayarları ve rengi. */
    public Parties(int defaultSize, long inviteMillis, Set<Setting> defaults, String defaultColor) {
        this.defaultSize = Math.clamp(defaultSize, MIN_SIZE, MAX_SIZE);
        this.inviteMillis = inviteMillis;
        this.defaults = Set.copyOf(defaults);
        this.defaultColor = defaultColor;
    }

    public synchronized Optional<Party> byId(long partyId) {
        Group group = byId.get(partyId);
        return group == null ? Optional.empty() : Optional.of(group.snapshot());
    }

    public synchronized Optional<Party> party(UUID player) {
        Group group = byMember.get(player);
        return group == null ? Optional.empty() : Optional.of(group.snapshot());
    }

    public synchronized boolean sameParty(UUID a, UUID b) {
        Group group = byMember.get(a);
        return group != null && group == byMember.get(b);
    }

    /** Açık ve dolu olmayan partiler, kuruluş sırasıyla. */
    public synchronized List<Party> openParties() {
        return byId.values().stream()
                .filter(group -> group.settings.contains(Setting.OPEN) && group.members.size() < group.size)
                .map(Group::snapshot)
                .toList();
    }

    public synchronized CreateResult create(UUID leader, String name) {
        if (byMember.containsKey(leader)) {
            return CreateResult.ALREADY_IN_PARTY;
        }
        Group group = new Group(nextId++, name, defaultColor, defaultSize);
        group.leader = leader;
        group.members.add(leader);
        group.settings.addAll(defaults);
        byMember.put(leader, group);
        byId.put(group.id, group);
        invites.remove(leader);
        return CreateResult.CREATED;
    }

    public synchronized InviteResult invite(UUID from, UUID to, long now) {
        if (from.equals(to)) {
            return InviteResult.SELF;
        }
        Group group = byMember.get(from);
        if (group == null) {
            return InviteResult.NOT_IN_PARTY;
        }
        if (!group.leader.equals(from) && !group.settings.contains(Setting.MEMBERS_INVITE)) {
            return InviteResult.NO_PERMISSION;
        }
        if (byMember.containsKey(to)) {
            return InviteResult.ALREADY_IN_PARTY;
        }
        if (group.members.size() >= group.size) {
            return InviteResult.FULL;
        }
        Map<UUID, Long> received = invites.computeIfAbsent(to, id -> new HashMap<>());
        Long expires = received.get(from);
        if (expires != null && expires > now) {
            return InviteResult.ALREADY_INVITED;
        }
        received.put(from, now + inviteMillis);
        return InviteResult.SENT;
    }

    /** Süresi dolmamış davetleri gönderenler. */
    public synchronized Set<UUID> invitesTo(UUID player, long now) {
        Map<UUID, Long> received = invites.get(player);
        if (received == null) {
            return Set.of();
        }
        received.values().removeIf(expires -> expires <= now);
        return Set.copyOf(received.keySet());
    }

    /** Davet edenin şu anki partisine katılır; davet eden partiden çıktıysa GONE. */
    public synchronized JoinResult accept(UUID player, UUID inviter, long now) {
        Map<UUID, Long> received = invites.get(player);
        Long expires = received == null ? null : received.remove(inviter);
        if (expires == null || expires <= now) {
            return JoinResult.NO_INVITE;
        }
        Group group = byMember.get(inviter);
        return group == null ? JoinResult.GONE : join(player, group);
    }

    /** Açık partiye davetsiz katılma (listeden ya da /parti katıl). */
    public synchronized JoinResult joinOpen(UUID player, long partyId) {
        Group group = byId.get(partyId);
        if (group == null) {
            return JoinResult.GONE;
        }
        if (!group.settings.contains(Setting.OPEN)) {
            return JoinResult.CLOSED;
        }
        return join(player, group);
    }

    private JoinResult join(UUID player, Group group) {
        if (byMember.containsKey(player)) {
            return JoinResult.ALREADY_IN_PARTY;
        }
        if (group.members.size() >= group.size) {
            return JoinResult.FULL;
        }
        group.members.add(player);
        byMember.put(player, group);
        // Partiye girdi: diğer davetler geçersiz.
        invites.remove(player);
        return JoinResult.JOINED;
    }

    public synchronized boolean deny(UUID player, UUID inviter) {
        Map<UUID, Long> received = invites.get(player);
        return received != null && received.remove(inviter) != null;
    }

    public synchronized LeaveResult leave(UUID player) {
        Group group = byMember.remove(player);
        if (group == null) {
            return LeaveResult.NOT_IN_PARTY;
        }
        group.members.remove(player);
        if (group.members.isEmpty()) {
            byId.remove(group.id);
            return LeaveResult.DISBANDED;
        }
        if (!group.leader.equals(player)) {
            return LeaveResult.LEFT;
        }
        group.leader = group.members.iterator().next();
        return LeaveResult.LEFT_NEW_LEADER;
    }

    /** Partiyi kapatır: herkes çıkar. @return false ise parti yok. */
    public synchronized boolean disband(long partyId) {
        Group group = byId.remove(partyId);
        if (group == null) {
            return false;
        }
        group.members.forEach(byMember::remove);
        group.members.clear();
        return true;
    }

    public synchronized KickResult kick(UUID leader, UUID target) {
        if (leader.equals(target)) {
            return KickResult.SELF;
        }
        Group group = byMember.get(leader);
        if (group == null || !group.leader.equals(leader)) {
            return KickResult.NOT_LEADER;
        }
        if (!group.members.remove(target)) {
            return KickResult.NOT_MEMBER;
        }
        byMember.remove(target);
        return KickResult.KICKED;
    }

    public synchronized PromoteResult promote(UUID leader, UUID target) {
        if (leader.equals(target)) {
            return PromoteResult.SELF;
        }
        Group group = byMember.get(leader);
        if (group == null || !group.leader.equals(leader)) {
            return PromoteResult.NOT_LEADER;
        }
        if (!group.members.contains(target)) {
            return PromoteResult.NOT_MEMBER;
        }
        group.leader = target;
        return PromoteResult.PROMOTED;
    }

    public synchronized ToggleResult toggle(long partyId, Setting setting) {
        Group group = byId.get(partyId);
        if (group == null) {
            return ToggleResult.GONE;
        }
        if (group.settings.remove(setting)) {
            return ToggleResult.OFF;
        }
        group.settings.add(setting);
        return ToggleResult.ON;
    }

    /** Boyut MIN_SIZE ile MAX_SIZE arası ve şu anki üye sayısından az olamaz. Kişiye özel sınır PartyActions'ta. */
    public synchronized EditResult setSize(long partyId, int size) {
        Group group = byId.get(partyId);
        if (group == null) {
            return EditResult.GONE;
        }
        if (size < Math.max(MIN_SIZE, group.members.size()) || size > MAX_SIZE) {
            return EditResult.INVALID;
        }
        group.size = size;
        return EditResult.SET;
    }

    /** tag null ya da boşsa etiket silinir. Küfür/reklam kontrolü çağıranda (PartyActions). */
    public synchronized EditResult setTag(long partyId, String tag) {
        Group group = byId.get(partyId);
        if (group == null) {
            return EditResult.GONE;
        }
        if (tag == null || tag.isBlank()) {
            group.tag = null;
            return EditResult.CLEARED;
        }
        if (!TAG.matcher(tag).matches()) {
            return EditResult.INVALID;
        }
        String wanted = tag.toLowerCase(Locale.ROOT);
        boolean taken = byId.values().stream().anyMatch(other -> other != group && other.tag != null
                && other.tag.toLowerCase(Locale.ROOT).equals(wanted));
        if (taken) {
            return EditResult.TAKEN;
        }
        group.tag = tag;
        return EditResult.SET;
    }

    public synchronized EditResult setDescription(long partyId, String description) {
        Group group = byId.get(partyId);
        if (group == null) {
            return EditResult.GONE;
        }
        if (description == null || description.isBlank()) {
            group.description = null;
            return EditResult.CLEARED;
        }
        if (description.strip().length() > MAX_DESCRIPTION) {
            return EditResult.INVALID;
        }
        group.description = description.strip();
        return EditResult.SET;
    }

    /** Rengin geçerli olduğunu çağıran bilir (PartyColor). */
    public synchronized EditResult setColor(long partyId, String color) {
        Group group = byId.get(partyId);
        if (group == null) {
            return EditResult.GONE;
        }
        group.color = color;
        return EditResult.SET;
    }

    /** İkisi aynı partideyse partinin rengi; değilse null. Sohbetteki renk için (async). */
    public synchronized String sharedColor(UUID a, UUID b) {
        Group group = byMember.get(a);
        return group != null && group == byMember.get(b) ? group.color : null;
    }

    /** Dost ateşi kapalıysa ve ikisi aynı partideyse true: hasar engellenir. */
    public synchronized boolean protects(UUID attacker, UUID victim) {
        Group group = byMember.get(attacker);
        return group != null && group == byMember.get(victim) && !group.settings.contains(Setting.FRIENDLY_FIRE);
    }

    /** Kurulu partilerin hepsi (gizliler dahil): RPG tarafı ve testler için. */
    public synchronized List<Party> all() {
        return byId.values().stream().map(Group::snapshot).toList();
    }
}
