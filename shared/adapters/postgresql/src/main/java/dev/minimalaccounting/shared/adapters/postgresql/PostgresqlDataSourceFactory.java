package dev.minimalaccounting.shared.adapters.postgresql;

import lombok.NonNull;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

public final class PostgresqlDataSourceFactory {
    private PostgresqlDataSourceFactory() {
    }

    public static DataSource create(@NonNull PostgresqlConfiguration configuration) {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(configuration.jdbcUrl());
        dataSource.setUser(configuration.user());
        dataSource.setPassword(configuration.password());
        return dataSource;
    }
}
