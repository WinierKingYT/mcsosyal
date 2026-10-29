package com.sunucu.rpg.social.moderation;

import com.sunucu.rpg.core.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Moderasyon tabloları: moderation_punishments (ceza geçmişi), moderation_reports (şikayetler),
 * moderation_chat_log (sohbet ve özel mesaj kaydı). Okumalar CompletableFuture döner; ana thread beklemez.
 */
public final class ModerationStore {

    public record Report(long id, UUID reporter, String reporterName, UUID target, String targetName, String reason,
                         long created) {
    }

    public record ChatLine(long time, String channel, String message) {
    }

    private static final long SETUP_TIMEOUT_SECONDS = 5;

    private final Database database;

    private ModerationStore(Database database) {
        this.database = database;
    }

    // shortcut: şema "yoksa oluştur"; sütun değişikliği gerekince sürüm tablosu ve geçiş adımları ekle.
    public static ModerationStore create(Database database) throws SQLException {
        try {
            database.submit(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS moderation_punishments ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, player TEXT NOT NULL, player_name TEXT NOT NULL, "
                            + "type TEXT NOT NULL, reason TEXT NOT NULL, issuer TEXT NOT NULL, created INTEGER NOT NULL, "
                            + "expires INTEGER NOT NULL, revoked_at INTEGER NOT NULL DEFAULT 0, revoked_by TEXT)");
                    statement.execute("CREATE INDEX IF NOT EXISTS moderation_punishments_player "
                            + "ON moderation_punishments (player, type)");
                    statement.execute("CREATE TABLE IF NOT EXISTS moderation_reports ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, reporter TEXT NOT NULL, reporter_name TEXT NOT NULL, "
                            + "target TEXT NOT NULL, target_name TEXT NOT NULL, reason TEXT NOT NULL, "
                            + "created INTEGER NOT NULL, closed_by TEXT, closed_at INTEGER NOT NULL DEFAULT 0)");
                    statement.execute("CREATE TABLE IF NOT EXISTS moderation_chat_log ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, player TEXT NOT NULL, "
                            + "name TEXT NOT NULL, channel TEXT NOT NULL, message TEXT NOT NULL)");
                    statement.execute("CREATE INDEX IF NOT EXISTS moderation_chat_log_player "
                            + "ON moderation_chat_log (player, time)");
                }
                return null;
            }).get(SETUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SQLException("Moderasyon tabloları oluşturulamadı", e);
        }
        return new ModerationStore(database);
    }

    /** @return yeni kaydın numarası. */
    public CompletableFuture<Long> addPunishment(UUID player, String playerName, Punishment.Type type, String reason,
                                                 String issuer, long created, long expires) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO moderation_punishments (player, player_name, type, reason, issuer, created, expires) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                Object[] params = {player.toString(), playerName, type.name(), reason, issuer, created, expires};
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    /** Aktif ban ya da mute'ları (süresi dolmamış, kaldırılmamış) kaldırır. @return kaldırılan sayısı. */
    public CompletableFuture<Integer> revokeActive(UUID player, Punishment.Type type, String revokedBy, long now) {
        return database.submit(connection -> {
            try (PreparedStatement statement = Database.prepare(connection,
                    "UPDATE moderation_punishments SET revoked_at = ?, revoked_by = ? WHERE player = ? AND type = ? "
                            + "AND revoked_at = 0 AND (expires = 0 OR expires > ?)",
                    now, revokedBy, player.toString(), type.name(), now)) {
                return statement.executeUpdate();
            }
        });
    }

    /** since'den bu yana kaldırılmamış cezaların sayısı (uyarı birikince otomatik ceza için). */
    public CompletableFuture<Integer> countSince(UUID player, Punishment.Type type, long since) {
        return database.submit(connection -> {
            try (PreparedStatement statement = Database.prepare(connection,
                    "SELECT COUNT(*) FROM moderation_punishments WHERE player = ? AND type = ? AND created >= ? "
                            + "AND revoked_at = 0", player.toString(), type.name(), since);
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        });
    }

    /** Girişte bakılır: en uzun süren aktif ban. */
    public CompletableFuture<Optional<Punishment>> activeBan(UUID player, long now) {
        return database.submit(connection -> {
            List<Punishment> found = query(connection, "SELECT * FROM moderation_punishments WHERE player = ? "
                    + "AND type = 'BAN' AND revoked_at = 0 AND (expires = 0 OR expires > ?) "
                    + "ORDER BY CASE WHEN expires = 0 THEN 1 ELSE 0 END DESC, expires DESC LIMIT 1",
                    player.toString(), now);
            return found.stream().findFirst();
        });
    }

    /** En yeniden eskiye. */
    public CompletableFuture<List<Punishment>> history(UUID player, int limit) {
        return database.submit(connection -> query(connection,
                "SELECT * FROM moderation_punishments WHERE player = ? ORDER BY created DESC, id DESC LIMIT ?",
                player.toString(), limit));
    }

    public void logChat(UUID player, String name, String channel, String message, long time) {
        database.update("INSERT INTO moderation_chat_log (time, player, name, channel, message) VALUES (?, ?, ?, ?, ?)",
                time, player.toString(), name, channel, message);
    }

    /** Eskiden yeniye (okuması kolay); en son limit kadar mesaj. */
    public CompletableFuture<List<ChatLine>> chatLog(UUID player, int limit) {
        return database.submit(connection -> {
            List<ChatLine> lines = new ArrayList<>();
            try (PreparedStatement statement = Database.prepare(connection,
                    "SELECT time, channel, message FROM moderation_chat_log WHERE player = ? "
                            + "ORDER BY time DESC, id DESC LIMIT ?", player.toString(), limit);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    lines.add(new ChatLine(rows.getLong(1), rows.getString(2), rows.getString(3)));
                }
            }
            return lines.reversed();
        });
    }

    public CompletableFuture<Integer> purgeChatLog(long olderThan) {
        return database.submit(connection -> {
            try (PreparedStatement statement = Database.prepare(connection,
                    "DELETE FROM moderation_chat_log WHERE time < ?", olderThan)) {
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Long> addReport(UUID reporter, String reporterName, UUID target, String targetName,
                                             String reason, long created) {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO moderation_reports (reporter, reporter_name, target, target_name, reason, created) "
                            + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                Object[] params = {reporter.toString(), reporterName, target.toString(), targetName, reason, created};
                for (int i = 0; i < params.length; i++) {
                    statement.setObject(i + 1, params[i]);
                }
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : -1L;
                }
            }
        });
    }

    /** Açık şikayetler, en yeniden eskiye. */
    public CompletableFuture<List<Report>> openReports(int limit) {
        return database.submit(connection -> {
            List<Report> reports = new ArrayList<>();
            try (PreparedStatement statement = Database.prepare(connection,
                    "SELECT id, reporter, reporter_name, target, target_name, reason, created FROM moderation_reports "
                            + "WHERE closed_at = 0 ORDER BY created DESC, id DESC LIMIT ?", limit);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    reports.add(new Report(rows.getLong(1), UUID.fromString(rows.getString(2)), rows.getString(3),
                            UUID.fromString(rows.getString(4)), rows.getString(5), rows.getString(6), rows.getLong(7)));
                }
            }
            return reports;
        });
    }

    public CompletableFuture<Integer> countOpenReports() {
        return database.submit(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM moderation_reports WHERE closed_at = 0");
                 ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getInt(1) : 0;
            }
        });
    }

    /** @return false ise böyle açık bir şikayet yok. */
    public CompletableFuture<Boolean> closeReport(long id, String closedBy, long now) {
        return database.submit(connection -> {
            try (PreparedStatement statement = Database.prepare(connection,
                    "UPDATE moderation_reports SET closed_by = ?, closed_at = ? WHERE id = ? AND closed_at = 0",
                    closedBy, now, id)) {
                return statement.executeUpdate() > 0;
            }
        });
    }

    private static List<Punishment> query(Connection connection, String sql, Object... params) throws SQLException {
        List<Punishment> result = new ArrayList<>();
        try (PreparedStatement statement = Database.prepare(connection, sql, params);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(new Punishment(rows.getLong("id"), UUID.fromString(rows.getString("player")),
                        rows.getString("player_name"), Punishment.Type.valueOf(rows.getString("type")),
                        rows.getString("reason"), rows.getString("issuer"), rows.getLong("created"),
                        rows.getLong("expires"), rows.getLong("revoked_at"), rows.getString("revoked_by")));
            }
        }
        return result;
    }
}
