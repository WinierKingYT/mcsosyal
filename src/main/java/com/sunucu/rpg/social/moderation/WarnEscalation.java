package com.sunucu.rpg.social.moderation;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Uyarı birikince otomatik ceza (config → moderation.warn-escalation). Uyarı sayısı tam olarak "warnings" olunca
 * kural çalışır: 3. uyarıda susturma, 5. uyarıda ban gibi. Bukkit'e bağımlı değil, test edilebilir.
 */
public final class WarnEscalation {

    /** durationMillis 0: kalıcı (kick'te kullanılmaz). */
    public record Rule(int warnings, Punishment.Type action, long durationMillis) {
    }

    public static final WarnEscalation NONE = new WarnEscalation(Map.of());

    private final Map<Integer, Rule> rules;

    private WarnEscalation(Map<Integer, Rule> rules) {
        this.rules = rules;
    }

    /** Hatalı kural atlanır, sebebi warn'a yazılır. duration yoksa kalıcı. */
    public static WarnEscalation fromConfig(List<Map<?, ?>> entries, Consumer<String> warn) {
        Map<Integer, Rule> rules = new HashMap<>();
        for (Map<?, ?> entry : entries) {
            Punishment.Type action = switch (String.valueOf(entry.get("action")).toLowerCase(Locale.ROOT)) {
                case "mute" -> Punishment.Type.MUTE;
                case "ban" -> Punishment.Type.BAN;
                case "kick" -> Punishment.Type.KICK;
                default -> null;
            };
            if (!(entry.get("warnings") instanceof Number warnings) || warnings.intValue() < 1 || action == null) {
                warn.accept("moderation.warn-escalation: hatalı kural atlandı (warnings 1+, action mute/ban/kick): " + entry);
                continue;
            }
            long duration = 0;
            Object value = entry.get("duration");
            if (value != null && action != Punishment.Type.KICK) {
                duration = DurationParser.parse(String.valueOf(value));
                if (duration < 0) {
                    warn.accept("moderation.warn-escalation: süre anlaşılmadı, kural atlandı: " + entry);
                    continue;
                }
            }
            rules.put(warnings.intValue(), new Rule(warnings.intValue(), action, duration));
        }
        return new WarnEscalation(Map.copyOf(rules));
    }

    public Optional<Rule> at(int warnings) {
        return Optional.ofNullable(rules.get(warnings));
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
