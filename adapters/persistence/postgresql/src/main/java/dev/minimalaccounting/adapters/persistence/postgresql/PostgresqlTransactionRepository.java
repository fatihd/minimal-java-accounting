package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.transaction.AccountEffect;
import dev.minimalaccounting.model.transaction.AccountEffectState;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionLine;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.TransactionState;
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
public final class PostgresqlTransactionRepository implements Repository<TransactionIdentifier, Transaction> {
    @NonNull
    private final JdbcTransactions transactions;

    @Override
    public Optional<Transaction> get(TransactionIdentifier key) {
        return transactions.withConnection(connection -> get(connection, key));
    }

    @Override
    public long add(Transaction value) {
        return transactions.inTransaction(() -> transactions.withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO accounting_transaction(
                        revision, state, request_id, document_type, document_number,
                        description, occurred_at, received_at, held_at, failure_reason)
                    VALUES (0, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                writeRoot(statement, value);
                statement.executeUpdate();
                long id = PostgresqlAccountRepository.generatedId(statement);
                insertLines(connection, id, value);
                insertEffects(connection, id, value);
                value.setId(id);
                value.setRevision(0L);
                return id;
            }
        }));
    }

    @Override
    public void update(Transaction value) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            long id = PostgresqlAccountRepository.required(
                    value.getId(), "Transaction has no identity");
            long revision = PostgresqlAccountRepository.required(
                    value.getRevision(), "Transaction has no revision");
            try (var statement = connection.prepareStatement("""
                    UPDATE accounting_transaction
                    SET revision = revision + 1, state = ?, request_id = ?,
                        document_type = ?, document_number = ?, description = ?,
                        occurred_at = ?, received_at = ?, held_at = ?, failure_reason = ?
                    WHERE id = ? AND revision = ?
                    """)) {
                writeRoot(statement, value);
                statement.setLong(10, id);
                statement.setLong(11, revision);
                PostgresqlAccountRepository.requireUpdated(
                        statement.executeUpdate(), "transaction", id);
            }
            deleteChildren(connection, id);
            insertLines(connection, id, value);
            insertEffects(connection, id, value);
            value.setRevision(revision + 1);
            return null;
        }));
    }

    @Override
    public void remove(TransactionIdentifier key) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            String sql = key.id() != null
                    ? "DELETE FROM accounting_transaction WHERE id = ?"
                    : "DELETE FROM accounting_transaction WHERE request_id = ?";
            try (var statement = connection.prepareStatement(sql)) {
                if (key.id() != null) {
                    statement.setLong(1, key.id());
                } else {
                    statement.setString(1, key.requestId());
                }
                statement.executeUpdate();
            }
            return null;
        }));
    }

    private static Optional<Transaction> get(Connection connection, TransactionIdentifier key)
            throws SQLException {
        String sql = key.id() != null
                ? "SELECT * FROM accounting_transaction WHERE id = ?"
                : "SELECT * FROM accounting_transaction WHERE request_id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            if (key.id() != null) {
                statement.setLong(1, key.id());
            } else {
                statement.setString(1, key.requestId());
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                long id = result.getLong("id");
                return Optional.of(new Transaction(
                        id,
                        result.getLong("revision"),
                        TransactionState.valueOf(result.getString("state")),
                        result.getString("request_id"),
                        result.getString("document_type"),
                        result.getString("document_number"),
                        result.getString("description"),
                        JdbcValues.instant(result, "occurred_at"),
                        JdbcValues.instant(result, "received_at"),
                        JdbcValues.nullableInstant(result, "held_at"),
                        result.getString("failure_reason"),
                        lines(connection, id),
                        effects(connection, id)));
            }
        }
    }

    private static ArrayList<TransactionLine> lines(Connection connection, long id)
            throws SQLException {
        var values = new ArrayList<TransactionLine>();
        try (var statement = connection.prepareStatement("""
                SELECT * FROM transaction_line
                WHERE transaction_id = ? ORDER BY line_number
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new TransactionLine(
                            result.getLong("account_id"),
                            new AccountNumber(result.getString("account_number")),
                            DebitCredit.valueOf(result.getString("side")),
                            JdbcValues.money(
                                    result.getLong("transaction_amount_minor"),
                                    result.getString("transaction_currency")),
                            JdbcValues.money(
                                    result.getLong("ledger_amount_minor"),
                                    result.getString("ledger_currency"))));
                }
            }
        }
        return values;
    }

    private static ArrayList<AccountEffect> effects(Connection connection, long id)
            throws SQLException {
        var values = new ArrayList<AccountEffect>();
        try (var statement = connection.prepareStatement("""
                SELECT * FROM account_effect
                WHERE transaction_id = ? ORDER BY effect_number
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new AccountEffect(
                            result.getLong("account_id"),
                            new AccountNumber(result.getString("account_number")),
                            DebitCredit.valueOf(result.getString("side")),
                            JdbcValues.money(
                                    result.getLong("ledger_amount_minor"),
                                    result.getString("ledger_currency")),
                            AccountEffectState.valueOf(result.getString("state"))));
                }
            }
        }
        return values;
    }

    private static void writeRoot(
            java.sql.PreparedStatement statement,
            Transaction value) throws SQLException {
        statement.setString(1, value.getState().name());
        statement.setString(2, value.getRequestId());
        statement.setString(3, value.getDocumentType());
        statement.setString(4, value.getDocumentNumber());
        statement.setString(5, value.getDescription());
        JdbcValues.instant(statement, 6, value.getOccurredAt());
        JdbcValues.instant(statement, 7, value.getReceivedAt());
        JdbcValues.nullableInstant(statement, 8, value.getHeldAt());
        statement.setString(9, value.getFailureReason());
    }

    private static void insertLines(Connection connection, long id, Transaction value)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO transaction_line(
                    transaction_id, line_number, account_id, account_number, side,
                    transaction_amount_minor, transaction_currency,
                    ledger_amount_minor, ledger_currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int lineNumber = 0;
            for (TransactionLine line : value.getLines()) {
                statement.setLong(1, id);
                statement.setInt(2, lineNumber++);
                statement.setLong(3, line.getAccountId());
                statement.setString(4, line.getAccountNumber().value());
                statement.setString(5, line.getSide().name());
                statement.setLong(6, line.getTransactionAmount().minor());
                statement.setString(7,
                        line.getTransactionAmount().currency().getCurrencyCode());
                statement.setLong(8, line.getLedgerAmount().minor());
                statement.setString(9, line.getLedgerAmount().currency().getCurrencyCode());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertEffects(Connection connection, long id, Transaction value)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO account_effect(
                    transaction_id, effect_number, account_id, account_number,
                    side, ledger_amount_minor, ledger_currency, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int effectNumber = 0;
            for (AccountEffect effect : value.getEffects()) {
                statement.setLong(1, id);
                statement.setInt(2, effectNumber++);
                statement.setLong(3, effect.getAccountId());
                statement.setString(4, effect.getAccountNumber().value());
                statement.setString(5, effect.getSide().name());
                statement.setLong(6, effect.getLedgerAmount().minor());
                statement.setString(7,
                        effect.getLedgerAmount().currency().getCurrencyCode());
                statement.setString(8, effect.getState().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteChildren(Connection connection, long id) throws SQLException {
        for (String table : java.util.List.of("account_effect", "transaction_line")) {
            try (var statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE transaction_id = ?")) {
                statement.setLong(1, id);
                statement.executeUpdate();
            }
        }
    }
}
