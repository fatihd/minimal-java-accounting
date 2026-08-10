package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlAccountRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlDataSourceFactory;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlJournalRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlSchema;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlTransactionRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;

@RequiredArgsConstructor
final class PostgresqlPersistenceModuleFactory {
    private final PostgresqlConfiguration configuration;

    @Getter(lazy = true)
    private final DataSource dataSource = PostgresqlDataSourceFactory.create(configuration);

    @Getter(lazy = true)
    private final JdbcTransactions jdbcTransactions = initializeDatabase();

    @Getter(lazy = true)
    private final PostgresqlAccountRepository accountRepository =
            new PostgresqlAccountRepository(getJdbcTransactions());

    @Getter(lazy = true)
    private final PostgresqlTransactionRepository transactionRepository =
            new PostgresqlTransactionRepository(getJdbcTransactions());

    @Getter(lazy = true)
    private final PostgresqlJournalRepository journalRepository =
            new PostgresqlJournalRepository(getJdbcTransactions());

    private JdbcTransactions initializeDatabase() {
        var transactions = new JdbcTransactions(getDataSource());
        PostgresqlSchema.install(transactions);
        return transactions;
    }
}
