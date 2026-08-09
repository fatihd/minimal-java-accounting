package dev.minimalaccounting.adapters.persistence.postgresql;

public final class PostgresqlFailure extends IllegalStateException {
    private final String sqlState;

    PostgresqlFailure(String operation, java.sql.SQLException cause) {
        super(operation + " failed", cause);
        this.sqlState = cause.getSQLState();
    }

    public String sqlState() {
        return sqlState;
    }
}
