package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountHold;
import dev.minimalaccounting.model.account.AccountHoldState;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Optional;

@RequiredArgsConstructor
public final class PostgresqlAccountRepository implements Repository<AccountIdentifier, Account> {
    @NonNull
    private final JdbcTransactions transactions;

    @Override
    public Optional<Account> get(AccountIdentifier key) {
        return transactions.withConnection(connection -> get(connection, key));
    }

    @Override
    public long add(Account value) {
        return transactions.inTransaction(() -> transactions.withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO account(
                        revision, account_number, name, balance_minor, currency,
                        minimum_balance_minor, maximum_balance_minor)
                    VALUES (0, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, value.getAccountNumber().value());
                statement.setString(2, value.getName());
                statement.setLong(3, value.getBalance().minor());
                statement.setString(4, value.getBalance().currency().getCurrencyCode());
                JdbcValues.nullableLong(statement, 5, value.getMinimumBalance());
                JdbcValues.nullableLong(statement, 6, value.getMaximumBalance());
                statement.executeUpdate();
                long id = generatedId(statement);
                insertHolds(connection, id, value);
                value.setId(id);
                value.setRevision(0L);
                return id;
            }
        }));
    }

    @Override
    public void update(Account value) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            long id = required(value.getId(), "Account has no identity");
            long revision = required(value.getRevision(), "Account has no revision");
            try (var statement = connection.prepareStatement("""
                    UPDATE account
                    SET revision = revision + 1,
                        account_number = ?, name = ?, balance_minor = ?, currency = ?,
                        minimum_balance_minor = ?, maximum_balance_minor = ?
                    WHERE id = ? AND revision = ?
                    """)) {
                statement.setString(1, value.getAccountNumber().value());
                statement.setString(2, value.getName());
                statement.setLong(3, value.getBalance().minor());
                statement.setString(4, value.getBalance().currency().getCurrencyCode());
                JdbcValues.nullableLong(statement, 5, value.getMinimumBalance());
                JdbcValues.nullableLong(statement, 6, value.getMaximumBalance());
                statement.setLong(7, id);
                statement.setLong(8, revision);
                requireUpdated(statement.executeUpdate(), "account", id);
            }
            deleteChildren(connection, id);
            insertHolds(connection, id, value);
            value.setRevision(revision + 1);
            return null;
        }));
    }

    @Override
    public void remove(AccountIdentifier key) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            String sql = key.id() != null
                    ? "DELETE FROM account WHERE id = ?"
                    : "DELETE FROM account WHERE account_number = ?";
            try (var statement = connection.prepareStatement(sql)) {
                if (key.id() != null) {
                    statement.setLong(1, key.id());
                } else {
                    statement.setString(1, key.accountNumber().value());
                }
                statement.executeUpdate();
            }
            return null;
        }));
    }

    private static Optional<Account> get(Connection connection, AccountIdentifier key)
            throws SQLException {
        String sql = key.id() != null
                ? "SELECT * FROM account WHERE id = ?"
                : "SELECT * FROM account WHERE account_number = ?";
        try (var statement = connection.prepareStatement(sql)) {
            if (key.id() != null) {
                statement.setLong(1, key.id());
            } else {
                statement.setString(1, key.accountNumber().value());
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long id = result.getLong("id");
                String currency = result.getString("currency");
                return Optional.of(new Account(
                        id,
                        result.getLong("revision"),
                        new AccountNumber(result.getString("account_number")),
                        result.getString("name"),
                        JdbcValues.money(result.getLong("balance_minor"), currency),
                        nullableBalance(result, "minimum_balance_minor", currency),
                        nullableBalance(result, "maximum_balance_minor", currency),
                        holds(connection, id, currency)));
            }
        }
    }

    private static java.util.List<AccountHold> holds(
            Connection connection,
            long accountId,
            String currency) throws SQLException {
        var values = new ArrayList<AccountHold>();
        try (var statement = connection.prepareStatement("""
                SELECT transaction_id, effect_number, side, amount_minor, state,
                       failure_reason
                FROM account_hold
                WHERE account_id = ?
                ORDER BY transaction_id, effect_number
                """)) {
            statement.setLong(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new AccountHold(
                            result.getLong("transaction_id"),
                            result.getInt("effect_number"),
                            DebitCredit.valueOf(result.getString("side")),
                            JdbcValues.money(result.getLong("amount_minor"), currency),
                            AccountHoldState.valueOf(result.getString("state")),
                            result.getString("failure_reason")));
                }
            }
        }
        return values;
    }

    private static dev.minimalaccounting.model.Money nullableBalance(
            ResultSet result,
            String column,
            String currency) throws SQLException {
        Long value = result.getObject(column, Long.class);
        return value == null ? null : JdbcValues.money(value, currency);
    }

    private static void insertHolds(Connection connection, long accountId, Account account)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO account_hold(
                    account_id, transaction_id, effect_number, side,
                    amount_minor, state, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (AccountHold hold : account.getHolds()) {
                statement.setLong(1, accountId);
                statement.setLong(2, hold.getTransactionId());
                statement.setInt(3, hold.getEffectNumber());
                statement.setString(4, hold.getSide().name());
                statement.setLong(5, hold.getAmount().minor());
                statement.setString(6, hold.getState().name());
                statement.setString(7, hold.getFailureReason());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteChildren(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM account_hold WHERE account_id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    static long generatedId(java.sql.PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("Insert did not return an identity");
            }
            return keys.getLong(1);
        }
    }

    static long required(Long value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    static void requireUpdated(int count, String type, long id) {
        if (count != 1) {
            throw new IllegalStateException("Stale or missing " + type + ": " + id);
        }
    }
}
