package dev.minimalaccounting.adapters.persistence.postgresql;

import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;

public final class PostgresqlSchema {
    private static final long MIGRATION_LOCK = 6_204_202_608_09L;
    private static final String MIGRATION = "/db/migration/V001__accounting.sql";

    private PostgresqlSchema() {
    }

    public static void install(@NonNull JdbcTransactions transactions) {
        String sql = resource(MIGRATION);
        String checksum = checksum(sql);
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            lock(connection);
            createHistory(connection);
            String installed = installedChecksum(connection, 1);
            if (installed != null) {
                if (!installed.equals(checksum)) {
                    throw new IllegalStateException("Database migration V001 checksum changed");
                }
                return null;
            }
            execute(connection, sql);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO accounting_schema_history(version, description, checksum) "
                            + "VALUES (1, 'accounting', ?)")) {
                statement.setString(1, checksum);
                statement.executeUpdate();
            }
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

    private static String resource(String name) {
        try (var input = PostgresqlSchema.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing database migration: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read database migration: " + name, failure);
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
