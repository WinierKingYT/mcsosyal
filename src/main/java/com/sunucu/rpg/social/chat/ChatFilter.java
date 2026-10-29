package com.sunucu.rpg.social.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

/** Bekleme süresi, tekrar, büyük harf ve yasaklı kelime kontrolü. Bukkit'e bağımlı değil, test edilebilir. */
public final class ChatFilter {

    public enum Verdict { OK, COOLDOWN, DUPLICATE, BANNED_WORD }

    public record Result(Verdict verdict, String text) {
    }

    private record Last(long at, String normalized) {
    }

    private static final long DUPLICATE_WINDOW_MILLIS = 30_000;
    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private final long cooldownMillis;
    private final int capsMinLetters;
    private final double capsRatio;
    private final Set<String> bannedWords;
    private final Map<UUID, Last> lastMessages = new ConcurrentHashMap<>();

    public ChatFilter(long cooldownMillis, int capsMinLetters, double capsRatio, List<String> bannedWords) {
        this.cooldownMillis = cooldownMillis;
        this.capsMinLetters = capsMinLetters;
        this.capsRatio = capsRatio;
        this.bannedWords = bannedWords.stream().map(ChatFilter::normalize).collect(Collectors.toUnmodifiableSet());
    }

    public static ChatFilter fromConfig(ConfigurationSection chat) {
        return new ChatFilter(
                chat.getLong("cooldown-millis", 2000),
                chat.getInt("caps-min-letters", 6),
                chat.getDouble("caps-ratio", 0.7),
                chat.getStringList("banned-words"));
    }

    public Result check(UUID sender, String text, long now) {
        String normalized = normalize(text);
        Last previous = lastMessages.get(sender);
        if (previous != null && now - previous.at() < cooldownMillis) {
            return new Result(Verdict.COOLDOWN, text);
        }
        if (previous != null && now - previous.at() < DUPLICATE_WINDOW_MILLIS
                && previous.normalized().equals(normalized)) {
            return new Result(Verdict.DUPLICATE, text);
        }
        if (containsBannedWord(text)) {
            return new Result(Verdict.BANNED_WORD, text);
        }
        lastMessages.put(sender, new Last(now, normalized));
        return new Result(Verdict.OK, fixCaps(text));
    }

    public void forget(UUID sender) {
        lastMessages.remove(sender);
    }

    /** "s.i.k" ve "a m k" gibi harf oyunlarını da yakalar; kelimenin içinde geçmesine bakmaz ("ambulans" geçer). */
    public boolean containsBannedWord(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder spelled = new StringBuilder();
        for (String token : text.split("\\s+")) {
            String word = normalize(token);
            if (word.isEmpty()) {
                continue;
            }
            words.add(word);
            if (word.length() == 1) {
                spelled.append(word);
            } else {
                words.add(spelled.toString());
                spelled.setLength(0);
            }
        }
        words.add(spelled.toString());
        return words.stream().anyMatch(bannedWords::contains);
    }

    String fixCaps(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        long upper = text.chars().filter(Character::isUpperCase).count();
        if (letters >= capsMinLetters && upper >= letters * capsRatio) {
            return text.toLowerCase(TURKISH);
        }
        return text;
    }

    /** Karşılaştırma için: küçük harf (Locale.ROOT), Türkçe harfler sadeleşir, harf dışı silinir, tekrarlar teke iner. */
    static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('ı', 'i');
        String plain = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String leet = plain.replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('@', 'a').replace('$', 's');
        return leet.replaceAll("[^a-z]", "").replaceAll("(.)\\1+", "$1");
    }
}
