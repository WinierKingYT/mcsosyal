package com.sunucu.rpg.social.friends;

import com.sunucu.rpg.social.SocialStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arkadaşlıklar ve bekleyen istekler. Bellekte tutulur, değişiklik SocialStore'a yazılır. Sadece verisi yüklü
 * (çevrimiçi) oyuncuların bellekteki kaydı güncellenir; çevrimdışı tarafın değişikliği zaten veritabanında,
 * girişte yüklenir. Bukkit'e bağımlı değil, test edilebilir. Ana thread'den kullanılır.
 */
public final class Friends {

    public enum RequestResult { SENT, ACCEPTED, SELF, ALREADY_FRIENDS, ALREADY_SENT, YOUR_LIMIT, THEIR_LIMIT }

    public enum AcceptResult { ACCEPTED, NO_REQUEST, YOUR_LIMIT }

    private final SocialStore store;
    private final int maxFriends;
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> incoming = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> outgoing = new ConcurrentHashMap<>();

    public Friends(SocialStore store, int maxFriends) {
        this.store = store;
        this.maxFriends = maxFriends;
    }

    public void load(UUID player, Set<UUID> friendIds, Set<UUID> incomingIds, Set<UUID> outgoingIds) {
        friends.put(player, copy(friendIds));
        incoming.put(player, copy(incomingIds));
        outgoing.put(player, copy(outgoingIds));
    }

    public void unload(UUID player) {
        friends.remove(player);
        incoming.remove(player);
        outgoing.remove(player);
    }

    public Set<UUID> friendsOf(UUID player) {
        return Set.copyOf(friends.getOrDefault(player, Set.of()));
    }

    /** Bu oyuncuya gelen, cevap bekleyen istekler. */
    public Set<UUID> requestsTo(UUID player) {
        return Set.copyOf(incoming.getOrDefault(player, Set.of()));
    }

    public boolean areFriends(UUID a, UUID b) {
        return friends.getOrDefault(a, Set.of()).contains(b);
    }

    public int maxFriends() {
        return maxFriends;
    }

    /** İki taraf da çevrimiçi olmalı. Karşı taraf zaten istek gönderdiyse doğrudan arkadaş olunur. */
    public RequestResult request(UUID from, UUID to) {
        if (from.equals(to)) {
            return RequestResult.SELF;
        }
        if (areFriends(from, to)) {
            return RequestResult.ALREADY_FRIENDS;
        }
        if (incoming.getOrDefault(from, Set.of()).contains(to)) {
            if (isFull(from)) {
                return RequestResult.YOUR_LIMIT;
            }
            befriend(from, to);
            return RequestResult.ACCEPTED;
        }
        if (outgoing.getOrDefault(from, Set.of()).contains(to)) {
            return RequestResult.ALREADY_SENT;
        }
        if (isFull(from)) {
            return RequestResult.YOUR_LIMIT;
        }
        if (isFull(to)) {
            return RequestResult.THEIR_LIMIT;
        }
        addIfLoaded(outgoing, from, to);
        addIfLoaded(incoming, to, from);
        store.addFriendRequest(from, to);
        return RequestResult.SENT;
    }

    /** İsteği gönderen çevrimdışı olabilir. */
    public AcceptResult accept(UUID player, UUID requester) {
        if (!incoming.getOrDefault(player, Set.of()).contains(requester)) {
            return AcceptResult.NO_REQUEST;
        }
        if (isFull(player)) {
            return AcceptResult.YOUR_LIMIT;
        }
        befriend(player, requester);
        return AcceptResult.ACCEPTED;
    }

    /** @return false ise böyle bir istek yoktu. */
    public boolean deny(UUID player, UUID requester) {
        if (!removeIfLoaded(incoming, player, requester)) {
            return false;
        }
        removeIfLoaded(outgoing, requester, player);
        store.removeFriendRequest(requester, player);
        return true;
    }

    /** @return false ise arkadaş değillerdi. Karşı taraf çevrimdışı olabilir. */
    public boolean remove(UUID player, UUID friend) {
        if (!removeIfLoaded(friends, player, friend)) {
            return false;
        }
        removeIfLoaded(friends, friend, player);
        store.removeFriendship(player, friend);
        return true;
    }

    private void befriend(UUID a, UUID b) {
        removeIfLoaded(incoming, a, b);
        removeIfLoaded(incoming, b, a);
        removeIfLoaded(outgoing, a, b);
        removeIfLoaded(outgoing, b, a);
        addIfLoaded(friends, a, b);
        addIfLoaded(friends, b, a);
        store.addFriendship(a, b);
    }

    /** Verisi yüklü değilse (çevrimdışı) sınırı bilemeyiz; o tarafın sınırı girişte değil, burada aşılabilir. */
    private boolean isFull(UUID player) {
        return friends.getOrDefault(player, Set.of()).size() >= maxFriends;
    }

    private static void addIfLoaded(Map<UUID, Set<UUID>> map, UUID owner, UUID value) {
        Set<UUID> set = map.get(owner);
        if (set != null) {
            set.add(value);
        }
    }

    private static boolean removeIfLoaded(Map<UUID, Set<UUID>> map, UUID owner, UUID value) {
        Set<UUID> set = map.get(owner);
        return set != null && set.remove(value);
    }

    private static Set<UUID> copy(Set<UUID> values) {
        Set<UUID> set = ConcurrentHashMap.newKeySet();
        set.addAll(values);
        return set;
    }
}
