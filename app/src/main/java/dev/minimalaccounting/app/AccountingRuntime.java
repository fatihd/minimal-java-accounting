package dev.minimalaccounting.app;

import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import dev.minimalaccounting.ports.persistence.AccountRepository;

interface AccountingRuntime extends AutoCloseable {
    AccountRepository accounts();

    SubmitTransactionHandler transactions();

    MetricsRegistry metrics();

    WorkerGroup startWorkers();

    @Override
    default void close() {
    }
}
