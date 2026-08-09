package dev.minimalaccounting.adapters.messaging.postgresql;

import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import lombok.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;

public final class PostgresqlOutboxSchema {
    private static final long MIGRATION_LOCK = 6_204_202_608_09L;
    private static final String MIGRATION = "/db/migration/V002__outbox.sql";

    private PostgresqlOutboxSchema() {
    }

    public static void install(@NonNull JdbcTransactions transactions) {
        String sql = resource();
        String checksum = checksum(sql);
        transactions.inTransaction(() -> transactions.withRequiredConnection(connection -> {
            lock(connection);
            String installed = installedChecksum(connection);
            if (installed != null) {
                if (!installed.equals(checksum)) {
                    throw new IllegalStateException("Database migration V002 checksum changed");
                }
                return null;
            }
            execute(connection, sql);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO accounting_schema_history(version, description, checksum)
                    VALUES (2, 'outbox', ?)
                    """)) {
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

    private static String installedChecksum(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT checksum FROM accounting_schema_history WHERE version = 2");
             var result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
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

    private static String resource() {
        try (var input = PostgresqlOutboxSchema.class.getResourceAsStream(MIGRATION)) {
            if (input == null) {
                throw new IllegalStateException("Missing database migration: " + MIGRATION);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read database migration: " + MIGRATION,
                    failure);
        }
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
