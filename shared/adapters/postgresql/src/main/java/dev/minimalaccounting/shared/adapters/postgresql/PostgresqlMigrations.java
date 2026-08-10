package dev.minimalaccounting.shared.adapters.postgresql;

import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;

public final class PostgresqlMigrations {
    private static final long MIGRATION_LOCK = 6_204_202_608_09L;

    private PostgresqlMigrations() {
    }

    public static void install(
            @NonNull JdbcTransactions transactions,
            int version,
            @NonNull String description,
            @NonNull String sql) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        String checksum = checksum(sql);
        transactions.inTransaction(() -> transactions.withRequiredConnection(connection -> {
            lock(connection);
            createHistory(connection);
            String installed = installedChecksum(connection, version);
            if (installed != null) {
                if (!installed.equals(checksum)) {
                    throw new IllegalStateException(
                            "Database migration V%03d checksum changed".formatted(version));
                }
                return null;
            }
            execute(connection, sql);
            record(connection, version, description, checksum);
            return null;
        }));
    }

    private static void lock(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, MIGRATION_LOCK);
            statement.execute();
        }
    }

    private static void createHistory(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS accounting_schema_history (
                        version INTEGER PRIMARY KEY,
                        description VARCHAR(200) NOT NULL,
                        checksum CHAR(64) NOT NULL,
                        installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private static String installedChecksum(Connection connection, int version)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT checksum FROM accounting_schema_history WHERE version = ?")) {
            statement.setInt(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        for (String command : sql.split(";\\s*")) {
            if (!command.isBlank()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(command);
                }
            }
        }
    }

    private static void record(
            Connection connection,
            int version,
            String description,
            String checksum) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO accounting_schema_history(version, description, checksum)
                VALUES (?, ?, ?)
                """)) {
            statement.setInt(1, version);
            statement.setString(2, description);
            statement.setString(3, checksum);
            statement.executeUpdate();
        }
    }

    private static String checksum(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
