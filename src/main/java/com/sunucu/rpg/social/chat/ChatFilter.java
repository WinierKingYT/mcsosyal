package com.sunucu.rpg.social.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

/** Bekleme süresi, tekrar, büyük harf, yasaklı kelime ve reklam kontrolü. Bukkit'e bağımlı değil, test edilebilir. */
public final class ChatFilter {

    public enum Verdict { OK, COOLDOWN, DUPLICATE, BANNED_WORD, ADVERTISEMENT }

    public record Result(Verdict verdict, String text) {
    }

    private record Key(UUID sender, String channel) {
    }

    private record Last(long at, String normalized) {
    }

    private static final long DUPLICATE_WINDOW_MILLIS = 30_000;
    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\s*[.,]\\s*\\d{1,3}){3}\\b");
    // "sunucu.com", "sunucu . net", "sunucu nokta com", "sunucu(dot)gg".
    // Noktadan sonra boşluk sadece önünde de boşluk varsa sayılır: "tamam. com da olur" cümlesi reklam değil.
    private static final Pattern DOMAIN = Pattern.compile(
            "\\b([a-z0-9-]+)(?:\\.|\\s+\\.\\s*|\\s*\\(dot\\)\\s*|\\s*\\[dot\\]\\s*|\\s+nokta\\s+)"
                    + "(com|net|org|tr|me|gg|io|xyz|club|pro|co|eu|online|fun|world)\\b",
            Pattern.CASE_INSENSITIVE);

    private final int capsMinLetters;
    private final double capsRatio;
    private final Set<String> bannedWords;
    private final Set<String> allowedDomains;
    private final Map<Key, Last> lastMessages = new ConcurrentHashMap<>();

    public ChatFilter(int capsMinLetters, double capsRatio, List<String> bannedWords, List<String> allowedDomains) {
        this.capsMinLetters = capsMinLetters;
        this.capsRatio = capsRatio;
        this.bannedWords = bannedWords.stream().map(ChatFilter::normalize).collect(Collectors.toUnmodifiableSet());
        this.allowedDomains = allowedDomains.stream()
                .map(domain -> domain.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static ChatFilter fromConfig(ConfigurationSection chat) {
        return new ChatFilter(
                chat.getInt("caps-min-letters", 6),
                chat.getDouble("caps-ratio", 0.7),
                chat.getStringList("banned-words"),
                chat.getStringList("allowed-domains"));
    }

    /** Bekleme süresi ve tekrar kontrolü kanal başınadır: ticarete yazmak globali beklemeye sokmaz. */
    public Result check(UUID sender, String channel, long cooldownMillis, String text, long now) {
        Key key = new Key(sender, channel);
        String normalized = normalize(text);
        Last previous = lastMessages.get(key);
        if (previous != null && now - previous.at() < cooldownMillis) {
            return new Result(Verdict.COOLDOWN, text);
        }
        if (previous != null && now - previous.at() < DUPLICATE_WINDOW_MILLIS
                && previous.normalized().equals(normalized)) {
            return new Result(Verdict.DUPLICATE, text);
        }
        Verdict content = checkContent(text);
        if (content != Verdict.OK) {
            return new Result(content, text);
        }
        lastMessages.put(key, new Last(now, normalized));
        return new Result(Verdict.OK, fixCaps(text));
    }

    /** Sadece içerik: yasaklı kelime ve reklam. Özel mesajlar da bunu kullanır. */
    public Verdict checkContent(String text) {
        if (containsBannedWord(text)) {
            return Verdict.BANNED_WORD;
        }
        if (containsAdvertisement(text)) {
            return Verdict.ADVERTISEMENT;
        }
        return Verdict.OK;
    }

    public void forget(UUID sender) {
        lastMessages.keySet().removeIf(key -> key.sender().equals(sender));
    }

    /** "s.i.k" ve "a m k" gibi harf oyunlarını da yakalar; kelimenin içinde geçmesine bakmaz ("ambulans" geçer). */
    boolean containsBannedWord(String text) {
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

    boolean containsAdvertisement(String text) {
        if (IP.matcher(text).find()) {
            return true;
        }
        Matcher domain = DOMAIN.matcher(text);
        while (domain.find()) {
            String found = (domain.group(1) + "." + domain.group(2)).toLowerCase(Locale.ROOT);
            if (!allowedDomains.contains(found)) {
                return true;
            }
        }
        return false;
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
