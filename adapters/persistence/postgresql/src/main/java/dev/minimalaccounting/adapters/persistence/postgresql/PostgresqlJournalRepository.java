package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.core.journal.JournalEntry;
import dev.minimalaccounting.core.journal.JournalLine;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.ports.persistence.JournalRepository;
import dev.minimalaccounting.shared.DebitCredit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Optional;

@RequiredArgsConstructor
public final class PostgresqlJournalRepository implements JournalRepository {
    @NonNull
    private final JdbcTransactions transactions;

    @Override
    public Optional<JournalEntry> get(JournalReference key) {
        return transactions.withConnection(connection -> get(connection, key));
    }

    @Override
    public long add(JournalEntry value) {
        return transactions.inTransaction(() -> transactions.withConnection(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO journal_entry(
                        revision, request_id, document_type, document_number,
                        description, occurred_at, journalized_at)
                    VALUES (0, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                writeRoot(statement, value);
                statement.executeUpdate();
                long id = PostgresqlAccountRepository.generatedId(statement);
                insertLines(connection, id, value);
                value.setId(id);
                value.setRevision(0L);
                return id;
            }
        }));
    }

    @Override
    public void update(JournalEntry value) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            long id = PostgresqlAccountRepository.required(
                    value.getId(), "Journal entry has no identity");
            long revision = PostgresqlAccountRepository.required(
                    value.getRevision(), "Journal entry has no revision");
            try (var statement = connection.prepareStatement("""
                    UPDATE journal_entry
                    SET revision = revision + 1, request_id = ?, document_type = ?,
                        document_number = ?, description = ?, occurred_at = ?,
                        journalized_at = ?
                    WHERE id = ? AND revision = ?
                    """)) {
                writeRoot(statement, value);
                statement.setLong(7, id);
                statement.setLong(8, revision);
                PostgresqlAccountRepository.requireUpdated(
                        statement.executeUpdate(), "journal entry", id);
            }
            deleteLines(connection, id);
            insertLines(connection, id, value);
            value.setRevision(revision + 1);
            return null;
        }));
    }

    @Override
    public void remove(JournalReference key) {
        transactions.inTransaction(() -> transactions.withConnection(connection -> {
            String sql = key.id() != null
                    ? "DELETE FROM journal_entry WHERE id = ?"
                    : "DELETE FROM journal_entry WHERE request_id = ?";
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

    private static Optional<JournalEntry> get(Connection connection, JournalReference key)
            throws SQLException {
        String sql = key.id() != null
                ? "SELECT * FROM journal_entry WHERE id = ?"
                : "SELECT * FROM journal_entry WHERE request_id = ?";
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
                return Optional.of(new JournalEntry(
                        id,
                        result.getLong("revision"),
                        result.getString("request_id"),
                        result.getString("document_type"),
                        result.getString("document_number"),
                        result.getString("description"),
                        JdbcValues.instant(result, "occurred_at"),
                        JdbcValues.instant(result, "journalized_at"),
                        lines(connection, id)));
            }
        }
    }

    private static ArrayList<JournalLine> lines(Connection connection, long id)
            throws SQLException {
        var values = new ArrayList<JournalLine>();
        try (var statement = connection.prepareStatement("""
                SELECT * FROM journal_line
                WHERE journal_entry_id = ? ORDER BY line_number
                """)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.add(new JournalLine(
                            result.getLong("account_id"),
                            DebitCredit.valueOf(result.getString("side")),
                            JdbcValues.money(
                                    result.getLong("transaction_amount_minor"),
                                    result.getString("transaction_currency")),
                            JdbcValues.money(
                                    result.getLong("ledger_amount_minor"),
                                    result.getString("ledger_currency")),
                            JdbcValues.money(
                                    result.getLong("balance_after_minor"),
                                    result.getString("balance_currency"))));
                }
            }
        }
        return values;
    }

    private static void writeRoot(
            java.sql.PreparedStatement statement,
            JournalEntry value) throws SQLException {
        statement.setString(1, value.getRequestId());
        statement.setString(2, value.getDocumentType());
        statement.setString(3, value.getDocumentNumber());
        statement.setString(4, value.getDescription());
        JdbcValues.instant(statement, 5, value.getOccurredAt());
        JdbcValues.instant(statement, 6, value.getJournalizedAt());
    }

    private static void insertLines(Connection connection, long id, JournalEntry value)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO journal_line(
                    journal_entry_id, line_number, account_id, side,
                    transaction_amount_minor, transaction_currency,
                    ledger_amount_minor, ledger_currency,
                    balance_after_minor, balance_currency)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            int lineNumber = 0;
            for (JournalLine line : value.getLines()) {
                statement.setLong(1, id);
                statement.setInt(2, lineNumber++);
                statement.setLong(3, line.getAccountId());
                statement.setString(4, line.getSide().name());
                statement.setLong(5, line.getTransactionAmount().minor());
                statement.setString(6,
                        line.getTransactionAmount().currency().getCurrencyCode());
                statement.setLong(7, line.getLedgerAmount().minor());
                statement.setString(8, line.getLedgerAmount().currency().getCurrencyCode());
                statement.setLong(9, line.getBalanceAfter().minor());
                statement.setString(10, line.getBalanceAfter().currency().getCurrencyCode());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void deleteLines(Connection connection, long id) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM journal_line WHERE journal_entry_id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
