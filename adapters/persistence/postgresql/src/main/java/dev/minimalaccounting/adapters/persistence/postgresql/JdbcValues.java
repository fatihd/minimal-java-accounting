package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.model.Money;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Currency;

final class JdbcValues {
    private JdbcValues() {
    }

    static Money money(long minor, String currency) {
        return new Money(minor, Currency.getInstance(currency));
    }

    static Money nullableMoney(ResultSet result, String minor, String currency)
            throws SQLException {
        Long value = result.getObject(minor, Long.class);
        return value == null ? null : money(value, result.getString(currency));
    }

    static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column).toInstant();
    }

    static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static void instant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        statement.setTimestamp(index, Timestamp.from(value));
    }

    static void nullableInstant(PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            instant(statement, index, value);
        }
    }

    static void nullableLong(PreparedStatement statement, int index, Money value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value.minor());
        }
    }
}
