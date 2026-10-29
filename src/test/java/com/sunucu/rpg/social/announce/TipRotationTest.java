package com.sunucu.rpg.social.announce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TipRotationTest {

    @Test
    void cyclesInOrder() {
        TipRotation rotation = new TipRotation(List.of("a", "b", "c"));
        assertEquals("a", rotation.next());
        assertEquals("b", rotation.next());
        assertEquals("c", rotation.next());
        assertEquals("a", rotation.next());
    }

    @Test
    void emptyListGivesNothing() {
        assertNull(new TipRotation(List.of()).next());
    }
}
