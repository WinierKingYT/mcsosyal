package com.sunucu.rpg.core.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ortak SQLite veritabanı (plugins/RPG/data.db). Tüm SQL tek bir arka plan thread'inde, sırayla çalışır:
 * ana thread ve async sohbet hiç beklemez. Her modül kendi tablolarını kendisi oluşturur.
 * SQLite sürücüsü Paper ile geliyor; ayrı bağımlılık yok.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final long CLOSE_TIMEOUT_SECONDS = 10;

    private final Connection connection;
    private final Logger logger;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RPG-Database");
        thread.setDaemon(true);
        return thread;
    });

    private Database(Connection connection, Logger logger) {
        this.connection = connection;
        this.logger = logger;
    }

    public static Database open(Path file, Logger logger) throws SQLException {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (java.io.IOException e) {
            throw new SQLException("Veritabanı klasörü oluşturulamadı: " + file.getParent(), e);
        }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            // WAL: okuma ve yazma birbirini daha az bekler, çökmeye karşı daha dayanıklı.
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
        }
        return new Database(connection, logger);
    }

    /** İşi veritabanı thread'inde çalıştırır. Sonucu beklemek zorunda değilsin. */
    public <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.run(connection);
            } catch (SQLException e) {
                throw new DatabaseException(e);
            }
        }, executor);
    }

    /** Yazma (INSERT/UPDATE/DELETE). Hata olursa loglanır; çağıran beklemez. */
    public CompletableFuture<Void> update(String sql, Object... params) {
        return submit(connection -> {
            try (PreparedStatement statement = prepare(connection, sql, params)) {
                statement.executeUpdate();
            }
            return (Void) null;
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Veritabanına yazılamadı: " + sql, error);
            }
        });
    }

    public static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }

    /** Bekleyen yazımlar bitince kapanır; plugin kapanırken veri kaybolmasın. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warning("Veritabanı yazımları " + CLOSE_TIMEOUT_SECONDS + " sn içinde bitmedi");
            }
            connection.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Veritabanı kapatılamadı", e);
        }
    }

    public static final class DatabaseException extends RuntimeException {
        DatabaseException(SQLException cause) {
            super(cause);
        }
    }
}
