package com.sunucu.rpg.social.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryLayoutTest {

    @Test
    void storageGoesOnTopAndHotbarBelowIt() {
        assertEquals(27, InventoryLayout.viewSlotOfStorage(0));
        assertEquals(35, InventoryLayout.viewSlotOfStorage(8));
        assertEquals(0, InventoryLayout.viewSlotOfStorage(9));
        assertEquals(26, InventoryLayout.viewSlotOfStorage(35));
    }

    @Test
    void storageMappingRoundTrips() {
        for (int slot = 0; slot < 36; slot++) {
            assertEquals(slot, InventoryLayout.storageSlotOfView(InventoryLayout.viewSlotOfStorage(slot)));
        }
    }

    @Test
    void bottomRowHasCenteredArmorOffhandAndFillers() {
        assertEquals(-1, InventoryLayout.storageSlotOfView(36));
        assertEquals(0, InventoryLayout.armorPieceOfView(38));
        assertEquals(3, InventoryLayout.armorPieceOfView(41));
        assertTrue(InventoryLayout.isOffhand(42));
        Set<Integer> fillers = new HashSet<>();
        for (int slot = 0; slot < InventoryLayout.SIZE; slot++) {
            if (InventoryLayout.isFiller(slot)) {
                fillers.add(slot);
            }
        }
        assertEquals(Set.of(36, 37, 43, 44), fillers);
        assertFalse(InventoryLayout.isFiller(42));
    }

    @Test
    void everyViewSlotHasExactlyOneRole() {
        for (int slot = 0; slot < InventoryLayout.SIZE; slot++) {
            int roles = (InventoryLayout.storageSlotOfView(slot) >= 0 ? 1 : 0)
                    + (InventoryLayout.armorPieceOfView(slot) >= 0 ? 1 : 0)
                    + (InventoryLayout.isOffhand(slot) ? 1 : 0)
                    + (InventoryLayout.isFiller(slot) ? 1 : 0);
            assertEquals(1, roles, "yuva " + slot);
        }
    }
}
