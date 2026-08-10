package dev.minimalaccounting.app.adapters.real;

import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlDataSourceFactory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.sql.DataSource;

@RequiredArgsConstructor
public final class PostgresqlModuleFactory {
    private final PostgresqlConfiguration configuration;

    @Getter(lazy = true)
    private final DataSource dataSource = PostgresqlDataSourceFactory.create(configuration);

    @Getter(lazy = true)
    private final JdbcTransactions transactions = new JdbcTransactions(getDataSource());
}
