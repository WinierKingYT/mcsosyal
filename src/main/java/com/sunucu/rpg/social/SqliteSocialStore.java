package com.sunucu.rpg.social;

import com.sunucu.rpg.core.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Sosyal tablolar: social_settings (oyuncu ayarları), social_ignores, social_mutes. */
public final class SqliteSocialStore implements SocialStore {

    private static final long LOAD_TIMEOUT_SECONDS = 5;
    private static final String LIST_SEPARATOR = ",";

    private final Database database;

    private SqliteSocialStore(Database database) {
        this.database = database;
    }

    // shortcut: şema "yoksa oluştur" ile kuruluyor; sütun değişikliği gerekince sürüm tablosu ve geçiş adımları ekle.
    public static SqliteSocialStore create(Database database) throws SQLException {
        try {
            database.submit(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS social_settings ("
                            + "player TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (player, key))");
                    statement.execute("CREATE TABLE IF NOT EXISTS social_ignores ("
                            + "player TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY (player, target))");
                    statement.execute("CREATE TABLE IF NOT EXISTS social_mutes ("
                            + "player TEXT PRIMARY KEY, until INTEGER NOT NULL)");
                }
                return null;
            }).get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SQLException("Sosyal tablolar oluşturulamadı", e);
        }
        return new SqliteSocialStore(database);
    }

    @Override
    public PlayerData load(UUID player) {
        try {
            return database.submit(connection -> read(connection, player.toString()))
                    .get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Oyuncu verisi yüklenirken kesildi", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Oyuncu verisi yüklenemedi: " + player, e);
        }
    }

    private static PlayerData read(Connection connection, String player) throws SQLException {
        Map<String, String> settings = new HashMap<>();
        try (PreparedStatement statement = Database.prepare(connection,
                "SELECT key, value FROM social_settings WHERE player = ?", player);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                settings.put(rows.getString(1), rows.getString(2));
            }
        }
        Set<UUID> ignores = new HashSet<>();
        try (PreparedStatement statement = Database.prepare(connection,
                "SELECT target FROM social_ignores WHERE player = ?", player);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                ignores.add(UUID.fromString(rows.getString(1)));
            }
        }
        long mutedUntil = 0;
        try (PreparedStatement statement = Database.prepare(connection,
                "SELECT until FROM social_mutes WHERE player = ?", player);
             ResultSet rows = statement.executeQuery()) {
            if (rows.next()) {
                mutedUntil = rows.getLong(1);
            }
        }
        return new PlayerData(settings.get(ACTIVE_CHANNEL), list(settings.get(HIDDEN_CHANNELS)),
                list(settings.get(MUTED_NOTIFICATIONS)), ignores, mutedUntil);
    }

    private static Set<String> list(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(LIST_SEPARATOR)).filter(item -> !item.isBlank()).collect(Collectors.toSet());
    }

    @Override
    public void saveSetting(UUID player, String key, String value) {
        if (value == null || value.isEmpty()) {
            database.update("DELETE FROM social_settings WHERE player = ? AND key = ?", player.toString(), key);
        } else {
            database.update("INSERT INTO social_settings (player, key, value) VALUES (?, ?, ?) "
                    + "ON CONFLICT (player, key) DO UPDATE SET value = excluded.value", player.toString(), key, value);
        }
    }

    @Override
    public void setIgnored(UUID player, UUID target, boolean ignored) {
        if (ignored) {
            database.update("INSERT OR IGNORE INTO social_ignores (player, target) VALUES (?, ?)",
                    player.toString(), target.toString());
        } else {
            database.update("DELETE FROM social_ignores WHERE player = ? AND target = ?",
                    player.toString(), target.toString());
        }
    }

    @Override
    public void saveMute(UUID player, long untilMillis) {
        if (untilMillis <= 0) {
            database.update("DELETE FROM social_mutes WHERE player = ?", player.toString());
        } else {
            database.update("INSERT INTO social_mutes (player, until) VALUES (?, ?) "
                    + "ON CONFLICT (player) DO UPDATE SET until = excluded.until", player.toString(), untilMillis);
        }
    }
}
