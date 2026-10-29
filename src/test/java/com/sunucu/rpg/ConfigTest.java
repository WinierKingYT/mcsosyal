package com.sunucu.rpg;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.sunucu.rpg.world.Regions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** config.yml bozuksa plugin hiç açılmaz; bunu sunucuya gitmeden yakala. */
class ConfigTest {

    // messages.get/lines/plain("anahtar"), x ? "a" : "b" ve switch içindeki -> "anahtar" biçimleri.
    // Anahtardan sonra ")" ya da "," gelmeli: "mod-type-" + tür gibi birleştirilen önekler sayılmasın.
    private static final Pattern MESSAGE_KEY = Pattern.compile(
            "messages\\.(?:get|lines|plain)\\(\"([a-z-]+)\"(?=[,)])|\\? \"([a-z-]+)\" : \"([a-z-]+)\"|-> \"([a-z-]+)\";");
    // Kodda birleştirilerek kurulan anahtarlar: regex göremez, elle kontrol edilir.
    private static final List<String> COMPOSED_KEYS = List.of(
            "mod-type-ban", "mod-type-mute", "mod-type-shadow_mute", "mod-type-kick", "mod-type-warn",
            "profile-mastery-mining", "profile-mastery-excavation", "profile-mastery-farming",
            "profile-mastery-woodcutting",
            "party-color-aqua", "party-color-dark_aqua", "party-color-green", "party-color-yellow",
            "party-color-gold", "party-color-red", "party-color-light_purple", "party-color-white",
            "mod-status-active", "mod-status-expired", "mod-status-revoked", "mod-status-done");

    private static YamlConfiguration load() throws IOException, InvalidConfigurationException {
        try (Reader reader = new InputStreamReader(
                ConfigTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(reader);
            return config;
        }
    }

    @Test
    void configParsesWithRequiredSections() throws Exception {
        YamlConfiguration config = load();
        assertNotNull(config.getConfigurationSection("chat"), "chat bölümü yok");
        assertNotNull(config.getConfigurationSection("chat.channels.global"), "global kanal yok");
        assertNotNull(config.getConfigurationSection("chat.channels.local"), "yerel kanal yok");
        assertNotNull(config.getConfigurationSection("messages"), "messages bölümü yok");
    }

    @Test
    void pluginDescriptorParses() throws Exception {
        YamlConfiguration descriptor = new YamlConfiguration();
        try (Reader reader = new InputStreamReader(
                ConfigTest.class.getResourceAsStream("/paper-plugin.yml"), StandardCharsets.UTF_8)) {
            descriptor.load(reader);
        }
        assertNotNull(descriptor.getConfigurationSection("permissions"), "paper-plugin.yml izinleri okunamadı");
    }

    @Test
    void sampleRegionsLoadWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        Regions.fromConfig(load().getMapList("regions"), warnings::add);
        assertTrue(warnings.isEmpty(), "regions hatalı: " + warnings);
    }

    @Test
    void everyMessageKeyUsedInCodeExists() throws Exception {
        ConfigurationSection messages = load().getConfigurationSection("messages");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = MESSAGE_KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    for (int group = 1; group <= matcher.groupCount(); group++) {
                        String key = matcher.group(group);
                        if (key != null && !messages.contains(key)) {
                            missing.add(key + " (" + file.getFileName() + ")");
                        }
                    }
                }
            }
        }
        COMPOSED_KEYS.stream().filter(key -> !messages.contains(key)).forEach(missing::add);
        assertTrue(missing.isEmpty(), "config.yml → messages altında eksik: " + missing);
    }
}
