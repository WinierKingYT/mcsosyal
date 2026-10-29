package com.sunucu.rpg.social.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PartiesTest {

    private final Parties parties = new Parties(3, 60_000, Set.of(Parties.Setting.OPEN), "aqua");
    private final UUID ali = UUID.randomUUID();
    private final UUID bora = UUID.randomUUID();
    private final UUID can = UUID.randomUUID();
    private final UUID dora = UUID.randomUUID();

    private long create(UUID leader) {
        assertEquals(Parties.CreateResult.CREATED, parties.create(leader, "Parti"));
        return parties.party(leader).orElseThrow().id();
    }

    private void invited(UUID leader, UUID member) {
        assertEquals(Parties.InviteResult.SENT, parties.invite(leader, member, 0));
        assertEquals(Parties.JoinResult.JOINED, parties.accept(member, leader, 0));
    }

    @Test
    void creatorIsLeaderAloneWithDefaults() {
        create(ali);
        Parties.Party party = parties.party(ali).orElseThrow();
        assertEquals(ali, party.leader());
        assertEquals(List.of(ali), party.members());
        assertTrue(party.is(Parties.Setting.OPEN));
        assertFalse(party.is(Parties.Setting.FRIENDLY_FIRE));
        assertEquals(Parties.CreateResult.ALREADY_IN_PARTY, parties.create(ali, "İkinci"));
    }

    @Test
    void inviteNeedsPartyAndPermission() {
        assertEquals(Parties.InviteResult.NOT_IN_PARTY, parties.invite(ali, bora, 0));
        create(ali);
        invited(ali, bora);
        assertEquals(Parties.InviteResult.NO_PERMISSION, parties.invite(bora, can, 0));
        long id = parties.party(ali).orElseThrow().id();
        assertEquals(Parties.ToggleResult.ON, parties.toggle(id, Parties.Setting.MEMBERS_INVITE));
        assertEquals(Parties.InviteResult.SENT, parties.invite(bora, can, 0));
        assertEquals(Parties.JoinResult.JOINED, parties.accept(can, bora, 0));
        assertEquals(Parties.InviteResult.FULL, parties.invite(ali, dora, 0));
    }

    @Test
    void invitesExpire() {
        create(ali);
        parties.invite(ali, bora, 0);
        assertEquals(Set.of(ali), parties.invitesTo(bora, 59_999));
        assertEquals(Set.of(), parties.invitesTo(bora, 60_000));
        assertEquals(Parties.JoinResult.NO_INVITE, parties.accept(bora, ali, 60_000));
    }

    @Test
    void inviteFromLeftMemberIsGone() {
        create(ali);
        parties.invite(ali, bora, 0);
        parties.leave(ali);
        assertEquals(Parties.JoinResult.GONE, parties.accept(bora, ali, 0));
    }

    @Test
    void openPartiesAreListedAndJoinable() {
        long id = create(ali);
        assertEquals(1, parties.openParties().size());
        assertEquals(Parties.JoinResult.JOINED, parties.joinOpen(bora, id));
        assertEquals(Parties.ToggleResult.OFF, parties.toggle(id, Parties.Setting.OPEN));
        assertTrue(parties.openParties().isEmpty(), "gizli parti listede görünmez");
        assertEquals(Parties.JoinResult.CLOSED, parties.joinOpen(can, id));
    }

    @Test
    void fullPartyLeavesList() {
        long id = create(ali);
        parties.joinOpen(bora, id);
        parties.joinOpen(can, id);
        assertTrue(parties.openParties().isEmpty());
        assertEquals(Parties.JoinResult.FULL, parties.joinOpen(dora, id));
    }

    @Test
    void leaderLeavingPassesLeadershipAndLastOneDisbands() {
        long id = create(ali);
        parties.joinOpen(bora, id);
        assertEquals(Parties.LeaveResult.LEFT_NEW_LEADER, parties.leave(ali));
        assertEquals(bora, parties.party(bora).orElseThrow().leader());
        assertEquals(Parties.LeaveResult.DISBANDED, parties.leave(bora));
        assertTrue(parties.all().isEmpty());
        assertEquals(Parties.JoinResult.GONE, parties.joinOpen(can, id));
    }

    @Test
    void kickAndPromoteNeedLeader() {
        create(ali);
        invited(ali, bora);
        invited(ali, can);
        assertEquals(Parties.KickResult.NOT_LEADER, parties.kick(bora, can));
        assertEquals(Parties.PromoteResult.PROMOTED, parties.promote(ali, bora));
        assertEquals(Parties.KickResult.KICKED, parties.kick(bora, ali));
        assertEquals(List.of(bora, can), parties.party(can).orElseThrow().members());
    }

    @Test
    void disbandRemovesEveryone() {
        long id = create(ali);
        parties.joinOpen(bora, id);
        assertTrue(parties.disband(id));
        assertTrue(parties.party(bora).isEmpty());
        assertTrue(parties.all().isEmpty());
        assertFalse(parties.disband(id));
    }

    @Test
    void sizeCanGrowAndShrinkWithinLimits() {
        long id = create(ali);
        parties.joinOpen(bora, id);
        parties.joinOpen(can, id);
        assertEquals(Parties.JoinResult.FULL, parties.joinOpen(dora, id));
        assertEquals(Parties.EditResult.SET, parties.setSize(id, 4));
        assertEquals(Parties.JoinResult.JOINED, parties.joinOpen(dora, id));
        assertEquals(Parties.EditResult.INVALID, parties.setSize(id, 3), "üye sayısının altına inemez");
        assertEquals(Parties.EditResult.INVALID, parties.setSize(id, Parties.MAX_SIZE + 1));
        assertEquals(Parties.EditResult.SET, parties.setSize(id, Parties.MAX_SIZE));
        assertEquals(Parties.MAX_SIZE, parties.byId(id).orElseThrow().size());
    }

    @Test
    void friendlyFireProtectionFollowsSetting() {
        long id = create(ali);
        parties.joinOpen(bora, id);
        assertTrue(parties.protects(ali, bora));
        assertFalse(parties.protects(ali, can), "partide olmayan korunmaz");
        parties.toggle(id, Parties.Setting.FRIENDLY_FIRE);
        assertFalse(parties.protects(ali, bora));
    }

    @Test
    void tagIsValidatedAndUnique() {
        long first = create(ali);
        long second = create(bora);
        assertEquals(Parties.EditResult.SET, parties.setTag(first, "Ejder"));
        assertEquals(Parties.EditResult.TAKEN, parties.setTag(second, "EJDER"));
        assertEquals(Parties.EditResult.INVALID, parties.setTag(second, "A"));
        assertEquals(Parties.EditResult.INVALID, parties.setTag(second, "çok uzun"));
        assertEquals(Parties.EditResult.SET, parties.setTag(second, "Kurt1"));
        assertEquals(Parties.EditResult.CLEARED, parties.setTag(first, null));
        assertEquals(null, parties.party(ali).orElseThrow().tag());
    }

    @Test
    void descriptionAndColor() {
        long id = create(ali);
        parties.joinOpen(bora, id);
        assertEquals(Parties.EditResult.INVALID, parties.setDescription(id, "a".repeat(61)));
        assertEquals(Parties.EditResult.SET, parties.setDescription(id, "  Zindan ekibi  "));
        assertEquals("Zindan ekibi", parties.party(bora).orElseThrow().description());
        assertEquals("aqua", parties.sharedColor(ali, bora));
        assertEquals(Parties.EditResult.SET, parties.setColor(id, "gold"));
        assertEquals("gold", parties.sharedColor(bora, ali));
        assertEquals(null, parties.sharedColor(ali, can), "partide olmayana renk yok");
    }

    @Test
    void joiningClearsOtherInvites() {
        create(ali);
        create(bora);
        parties.invite(ali, can, 0);
        parties.invite(bora, can, 0);
        assertEquals(Parties.JoinResult.JOINED, parties.accept(can, ali, 0));
        assertEquals(Set.of(), parties.invitesTo(can, 0));
    }
}
