package com.sunucu.rpg.social.moderation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ceza süresi: "30s", "10m", "2h", "7d", "1w", birleşik "1d12h"; Türkçe "30sn", "10dk", "2sa", "7g" de olur.
 * Bukkit'e bağımlı değil, test edilebilir.
 */
public final class DurationParser {

    /** En uzun süre 10 yıl: daha uzunu kalıcı ceza olarak verilmeli. */
    public static final long MAX_MILLIS = 10L * 365 * 24 * 60 * 60 * 1000;

    private static final Pattern PART = Pattern.compile("(\\d{1,6})(sn|dk|sa|s|m|h|d|g|w)");

    private DurationParser() {
    }

    /** @return milisaniye; süre değilse -1. */
    public static long parse(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        Matcher matcher = PART.matcher(value);
        long total = 0;
        int position = 0;
        while (matcher.find()) {
            if (matcher.start() != position) {
                return -1;
            }
            total += Long.parseLong(matcher.group(1)) * unit(matcher.group(2));
            if (total > MAX_MILLIS) {
                return -1;
            }
            position = matcher.end();
        }
        return position == value.length() && total > 0 ? total : -1;
    }

    private static long unit(String unit) {
        return switch (unit) {
            case "s", "sn" -> 1000L;
            case "m", "dk" -> 60_000L;
            case "h", "sa" -> 3_600_000L;
            case "d", "g" -> 86_400_000L;
            default -> 7 * 86_400_000L;
        };
    }
}
