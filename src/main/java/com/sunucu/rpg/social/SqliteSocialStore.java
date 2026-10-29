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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Sosyal tablolar: social_settings (oyuncu ayarları), social_ignores, social_mutes,
 * social_friends (her arkadaşlık iki satır, iki yönden), social_friend_requests.
 */
public final class SqliteSocialStore implements SocialStore {

    private static final long LOAD_TIMEOUT_SECONDS = 5;
    private static final String LIST_SEPARATOR = ",";

    private final Database database;
    private final Logger logger;

    private SqliteSocialStore(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    // shortcut: şema "yoksa oluştur" ile kuruluyor; sütun değişikliği gerekince sürüm tablosu ve geçiş adımları ekle.
    public static SqliteSocialStore create(Database database, Logger logger) throws SQLException {
        try {
            database.submit(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS social_settings ("
                            + "player TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY (player, key))");
                    statement.execute("CREATE TABLE IF NOT EXISTS social_ignores ("
                            + "player TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY (player, target))");
                    statement.execute("CREATE TABLE IF NOT EXISTS social_mutes ("
                            + "player TEXT PRIMARY KEY, until INTEGER NOT NULL)");
                    statement.execute("CREATE TABLE IF NOT EXISTS social_friends ("
                            + "player TEXT NOT NULL, friend TEXT NOT NULL, since INTEGER NOT NULL, "
                            + "PRIMARY KEY (player, friend))");
                    statement.execute("CREATE TABLE IF NOT EXISTS social_friend_requests ("
                            + "sender TEXT NOT NULL, target TEXT NOT NULL, created INTEGER NOT NULL, "
                            + "PRIMARY KEY (sender, target))");
                }
                return null;
            }).get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SQLException("Sosyal tablolar oluşturulamadı", e);
        }
        return new SqliteSocialStore(database, logger);
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
                list(settings.get(MUTED_NOTIFICATIONS)), ignores, mutedUntil,
                uuids(connection, "SELECT friend FROM social_friends WHERE player = ?", player),
                uuids(connection, "SELECT sender FROM social_friend_requests WHERE target = ?", player),
                uuids(connection, "SELECT target FROM social_friend_requests WHERE sender = ?", player));
    }

    private static Set<UUID> uuids(Connection connection, String sql, String player) throws SQLException {
        Set<UUID> result = new HashSet<>();
        try (PreparedStatement statement = Database.prepare(connection, sql, player);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(UUID.fromString(rows.getString(1)));
            }
        }
        return result;
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
    public void addFriendRequest(UUID from, UUID to) {
        database.update("INSERT OR IGNORE INTO social_friend_requests (sender, target, created) VALUES (?, ?, ?)",
                from.toString(), to.toString(), System.currentTimeMillis());
    }

    @Override
    public void removeFriendRequest(UUID from, UUID to) {
        database.update("DELETE FROM social_friend_requests WHERE sender = ? AND target = ?",
                from.toString(), to.toString());
    }

    /** Tek işlem (transaction): yarım arkadaşlık (tek yönlü satır) kalmasın. */
    @Override
    public void addFriendship(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        long now = System.currentTimeMillis();
        transaction(connection -> {
            run(connection, "INSERT OR IGNORE INTO social_friends (player, friend, since) VALUES (?, ?, ?)",
                    first, second, now);
            run(connection, "INSERT OR IGNORE INTO social_friends (player, friend, since) VALUES (?, ?, ?)",
                    second, first, now);
            run(connection, "DELETE FROM social_friend_requests WHERE (sender = ? AND target = ?) "
                    + "OR (sender = ? AND target = ?)", first, second, second, first);
        }, "arkadaşlık eklenemedi");
    }

    @Override
    public void removeFriendship(UUID a, UUID b) {
        String first = a.toString();
        String second = b.toString();
        transaction(connection -> run(connection, "DELETE FROM social_friends WHERE (player = ? AND friend = ?) "
                + "OR (player = ? AND friend = ?)", first, second, second, first), "arkadaşlık silinemedi");
    }

    @FunctionalInterface
    private interface Steps {
        void run(Connection connection) throws SQLException;
    }

    private void transaction(Steps steps, String failure) {
        database.submit(connection -> {
            connection.setAutoCommit(false);
            try {
                steps.run(connection);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
            return null;
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Veritabanı: " + failure, error);
            }
        });
    }

    private static void run(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = Database.prepare(connection, sql, params)) {
            statement.executeUpdate();
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
