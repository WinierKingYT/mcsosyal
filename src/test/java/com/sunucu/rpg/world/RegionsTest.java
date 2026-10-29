package com.sunucu.rpg.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RegionsTest {

    private final List<String> warnings = new ArrayList<>();

    private Regions load(List<Map<?, ?>> entries) {
        return Regions.fromConfig(entries, warnings::add);
    }

    @Test
    void findsRegionIncludingEdgesAndNormalizesCorners() {
        Regions regions = load(List.of(
                Map.of("name", "Köy", "world", "world", "from", List.of(50, 50), "to", List.of(-50, -50))));
        assertEquals("Köy", regions.at("world", -50, 50).orElseThrow().name());
        assertEquals("Köy", regions.at("world", 0, 0).orElseThrow().name());
        assertTrue(regions.at("world", 51, 0).isEmpty());
        assertTrue(regions.at("world_nether", 0, 0).isEmpty());
    }

    @Test
    void firstMatchWinsForNestedRegions() {
        Regions regions = load(List.of(
                Map.of("name", "Meydan", "world", "world", "from", List.of(-10, -10), "to", List.of(10, 10)),
                Map.of("name", "Köy", "world", "world", "from", List.of(-50, -50), "to", List.of(50, 50))));
        assertEquals("Meydan", regions.at("world", 5, 5).orElseThrow().name());
        assertEquals("Köy", regions.at("world", 30, 30).orElseThrow().name());
    }

    @Test
    void skipsBrokenEntriesWithWarning() {
        Regions regions = load(List.of(
                Map.of("name", "Eksik", "world", "world", "from", List.of(0, 0)),
                Map.of("name", "Bozuk", "world", "world", "from", List.of(0), "to", List.of(5, 5)),
                Map.of("name", "İyi", "world", "world", "from", List.of(0, 0), "to", List.of(5, 5))));
        assertEquals(2, warnings.size());
        assertEquals("İyi", regions.at("world", 1, 1).orElseThrow().name());
    }
}
