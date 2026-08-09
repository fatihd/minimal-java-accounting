package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.ports.persistence.TransactionBoundary;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Supplier;

@RequiredArgsConstructor
public final class JdbcTransactions implements TransactionBoundary {
    @NonNull
    private final DataSource dataSource;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    @Override
    public void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    public <T> T inTransaction(@NonNull Supplier<T> work) {
        if (current.get() != null) {
            return work.get();
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            current.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                current.remove();
            }
        } catch (SQLException failure) {
            throw new PostgresqlFailure("database transaction", failure);
        }
    }

    public <T> T withConnection(@NonNull SqlFunction<T> work) {
        Connection existing = current.get();
        if (existing != null) {
            return apply(work, existing);
        }
        try (Connection connection = dataSource.getConnection()) {
            return apply(work, connection);
        } catch (SQLException failure) {
            throw new PostgresqlFailure("database connection", failure);
        }
    }

    public <T> T withRequiredConnection(@NonNull SqlFunction<T> work) {
        return apply(work, requiredConnection());
    }

    public Connection requiredConnection() {
        Connection connection = current.get();
        if (connection == null) {
            throw new IllegalStateException("An active database transaction is required");
        }
        return connection;
    }

    private static <T> T apply(SqlFunction<T> work, Connection connection) {
        try {
            return work.apply(connection);
        } catch (SQLException failure) {
            throw new PostgresqlFailure("database operation", failure);
        }
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }
}
