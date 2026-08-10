package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlAccountRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlJournalRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlSchema;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlTransactionRepository;
import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class PostgresqlPersistenceModuleFactory {
    private final PostgresqlModuleFactory postgresql;

    @Getter(lazy = true)
    private final JdbcTransactions database = initializeDatabase();

    @Getter(lazy = true)
    private final PostgresqlAccountRepository accountRepository =
            new PostgresqlAccountRepository(getDatabase());

    @Getter(lazy = true)
    private final PostgresqlTransactionRepository transactionRepository =
            new PostgresqlTransactionRepository(getDatabase());

    @Getter(lazy = true)
    private final PostgresqlJournalRepository journalRepository =
            new PostgresqlJournalRepository(getDatabase());

    private JdbcTransactions initializeDatabase() {
        var transactions = postgresql.getTransactions();
        PostgresqlSchema.install(transactions);
        return transactions;
    }
}
