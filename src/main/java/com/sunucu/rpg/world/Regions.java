package com.sunucu.rpg.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * config.yml → regions: isimli dikdörtgen bölgeler (x/z, tüm yükseklik). Şimdilik [konum] paylaşımında
 * kullanılıyor; bölge giriş mesajları da buna bağlanacak. Bukkit'e bağımlı değil, test edilebilir.
 */
public final class Regions {

    public record Region(String name, String world, int minX, int minZ, int maxX, int maxZ) {

        public boolean contains(String world, int x, int z) {
            return this.world.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private final List<Region> regions;

    public Regions(List<Region> regions) {
        this.regions = List.copyOf(regions);
    }

    /** Config sırasıyla ilk eşleşen bölge: iç içe bölgelerde küçük olanı üste yaz. */
    public Optional<Region> at(String world, int x, int z) {
        return regions.stream().filter(region -> region.contains(world, x, z)).findFirst();
    }

    /**
     * Hatalı bir bölge plugin'i kapatmaz: atlanır ve warn ile sebebi bildirilir.
     * Beklenen biçim: {name, world, from: [x, z], to: [x, z]}.
     */
    public static Regions fromConfig(List<Map<?, ?>> entries, Consumer<String> warn) {
        List<Region> regions = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Map<?, ?> entry = entries.get(i);
            Object name = entry.get("name");
            Object world = entry.get("world");
            int[] from = point(entry.get("from"));
            int[] to = point(entry.get("to"));
            if (name == null || world == null || from == null || to == null) {
                warn.accept("regions[" + i + "] atlandı: name, world, from: [x, z] ve to: [x, z] gerekli");
                continue;
            }
            regions.add(new Region(name.toString(), world.toString(),
                    Math.min(from[0], to[0]), Math.min(from[1], to[1]),
                    Math.max(from[0], to[0]), Math.max(from[1], to[1])));
        }
        return new Regions(regions);
    }

    private static int[] point(Object value) {
        if (value instanceof List<?> list && list.size() == 2
                && list.get(0) instanceof Number x && list.get(1) instanceof Number z) {
            return new int[] {x.intValue(), z.intValue()};
        }
        return null;
    }
}
