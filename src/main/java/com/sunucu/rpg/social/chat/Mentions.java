package com.sunucu.rpg.social.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mesajdaki oyuncu isimlerini bulur. Bukkit'e bağımlı değil, test edilebilir. */
public final class Mentions {

    /**
     * Mesajda bulunan bir isim. explicit: "@Ahmet" şeklinde yazılmış (ses gider),
     * değilse sadece isim geçmiş (o kişide vurgulanır).
     */
    public record Span(int start, int end, String name, boolean explicit) {
    }

    // Minecraft isimleri 3–16 karakter [A-Za-z0-9_]. "mail@Ahmet" gibi kelime içindeki @ sayılmaz.
    private static final Pattern NAME = Pattern.compile("(?<![A-Za-z0-9_@])(@?)([A-Za-z0-9_]{3,16})(?![A-Za-z0-9_])");

    private Mentions() {
    }

    /** @param onlineNames küçük harfli isim → gerçek isim */
    public static List<Span> find(String text, Map<String, String> onlineNames) {
        List<Span> spans = new ArrayList<>();
        Matcher matcher = NAME.matcher(text);
        while (matcher.find()) {
            String name = onlineNames.get(matcher.group(2).toLowerCase(Locale.ROOT));
            if (name != null) {
                spans.add(new Span(matcher.start(), matcher.end(), name, !matcher.group(1).isEmpty()));
            }
        }
        return spans;
    }
}
