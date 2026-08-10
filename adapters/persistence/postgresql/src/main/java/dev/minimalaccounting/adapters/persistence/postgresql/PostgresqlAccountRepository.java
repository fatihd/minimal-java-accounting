package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountHold;
import dev.minimalaccounting.model.account.AccountHoldState;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

@RequiredArgsConstructor
public final class PostgresqlAccountRepository implements Repository<AccountNumber, Account> {
    @NonNull
    private final JdbcTransactions transactions;

    @Override
    public Optional<Account> get(AccountNumber accountNumber) {
        return transactions.withConnection(connection -> get(connection, accountNumber));
    }

    @Override
    public AccountNumber add(Account value) {
        return transactions.inTransaction(() -> transactions.withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO account(
                        revision, account_number, name, balance_minor, currency,
                        minimum_balance_minor, maximum_balance_minor)
                    VALUES (0, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, value.getAccountNumber().value());
                statement.setString(2, value.getName());
                statement.setLong(3, value.getBalance().minor());
                statement.setString(4, value.getBalance().currency().getCurrencyCode());
                JdbcValues.nullableLong(statement, 5, value.getMinimumBalance());
                JdbcValues.nullableLong(statement, 6, value.getMaximumBalance());
                statement.executeUpdate();
                insertHolds(connection, value.getAccountNumber(), value);
                value.setRevision(0L);
                return value.getAccountNumber();
            }
        }));
    }

    @Override
    public void update(Account value) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            long revision = required(value.getRevision(), "Account has no revision");
            try (var statement = connection.prepareStatement("""
                    UPDATE account
                    SET revision = revision + 1, name = ?, balance_minor = ?, currency = ?,
                        minimum_balance_minor = ?, maximum_balance_minor = ?
                    WHERE account_number = ? AND revision = ?
                    """)) {
                statement.setString(1, value.getName());
                statement.setLong(2, value.getBalance().minor());
                statement.setString(3, value.getBalance().currency().getCurrencyCode());
                JdbcValues.nullableLong(statement, 4, value.getMinimumBalance());
                JdbcValues.nullableLong(statement, 5, value.getMaximumBalance());
                statement.setString(6, value.getAccountNumber().value());
                statement.setLong(7, revision);
                requireUpdated(statement.executeUpdate(), "account", value.getAccountNumber());
            }
            deleteChildren(connection, value.getAccountNumber());
            insertHolds(connection, value.getAccountNumber(), value);
            value.setRevision(revision + 1);
            return null;
        }));
    }

    @Override
    public void remove(AccountNumber accountNumber) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "DELETE FROM account WHERE account_number = ?")) {
                statement.setString(1, accountNumber.value());
                statement.executeUpdate();
            }
            return null;
        }));
    }

    private static Optional<Account> get(Connection connection, AccountNumber accountNumber)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM account WHERE account_number = ?")) {
            statement.setString(1, accountNumber.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String currency = result.getString("currency");
                return Optional.of(new Account(
                        result.getLong("revision"),
                        new AccountNumber(result.getString("account_number")),
                        result.getString("name"),
                        JdbcValues.money(result.getLong("balance_minor"), currency),
                        nullableBalance(result, "minimum_balance_minor", currency),
                        nullableBalance(result, "maximum_balance_minor", currency),
                        holds(connection, accountNumber, currency)));
            }
        }
    }

    private static java.util.List<AccountHold> holds(
            Connection connection,
            AccountNumber accountNumber,
            String currency) throws SQLException {
        var values = new ArrayList<AccountHold>();
        try (var statement = connection.prepareStatement("""
                SELECT transaction_id, effect_number, side, amount_minor, state,
                       failure_reason
                FROM account_hold
                WHERE account_number = ?
                ORDER BY transaction_id, effect_number
                """)) {
            statement.setString(1, accountNumber.value());
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

    private static void insertHolds(
            Connection connection,
            AccountNumber accountNumber,
            Account account)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO account_hold(
                    account_number, transaction_id, effect_number, side,
                    amount_minor, state, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (AccountHold hold : account.getHolds()) {
                statement.setString(1, accountNumber.value());
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

    private static void deleteChildren(
            Connection connection,
            AccountNumber accountNumber) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM account_hold WHERE account_number = ?")) {
            statement.setString(1, accountNumber.value());
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

    static void requireUpdated(int count, String type, Object identifier) {
        if (count != 1) {
            throw new IllegalStateException("Stale or missing " + type + ": " + identifier);
        }
    }
}
