package com.sunucu.rpg.core;

/** Süreyi Türkçe okunur yazar: en büyük iki birim ("2 gün 3 saat", "3 saat 12 dk", "45 dk"). */
public final class Durations {

    private static final long MINUTE = 60_000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private Durations() {
    }

    public static String format(long millis) {
        if (millis < MINUTE) {
            return "1 dk'dan az";
        }
        long days = millis / DAY;
        long hours = millis % DAY / HOUR;
        long minutes = millis % HOUR / MINUTE;
        if (days > 0) {
            return hours > 0 ? days + " gün " + hours + " saat" : days + " gün";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + " saat " + minutes + " dk" : hours + " saat";
        }
        return minutes + " dk";
    }
}
