package com.sunucu.rpg.social.party;

import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * Parti panelindeki bilgi kitabına RPG satırları: parti buffları, verdiği statlar, XP paylaşımı gibi. RPG sistemi
 * SocialModule#setPartyInfo ile verir; verilmezse kitapta sadece sosyal bilgiler olur. Ana thread'den çağrılır.
 */
@FunctionalInterface
public interface PartyInfo {

    PartyInfo NONE = party -> List.of();

    List<Component> lines(Parties.Party party);
}
