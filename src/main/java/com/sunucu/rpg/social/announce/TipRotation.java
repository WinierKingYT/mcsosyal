package com.sunucu.rpg.social.announce;

import java.util.List;

/** İpuçlarını sırayla verir, sona gelince başa döner. Bukkit'e bağımlı değil, test edilebilir. Ana thread. */
final class TipRotation {

    private final List<String> tips;
    private int next;

    TipRotation(List<String> tips) {
        this.tips = List.copyOf(tips);
    }

    boolean isEmpty() {
        return tips.isEmpty();
    }

    /** Liste boşsa null. */
    String next() {
        if (tips.isEmpty()) {
            return null;
        }
        String tip = tips.get(next);
        next = (next + 1) % tips.size();
        return tip;
    }
}
