package dev.minimalaccounting.app;

import dev.minimalaccounting.observability.MetricsRegistry;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import dev.minimalaccounting.ports.persistence.AccountRepository;

interface AccountingRuntime extends AutoCloseable {
    AccountRepository accounts();

    TransactionProcessor transactions();

    MetricsRegistry metrics();

    WorkerGroup startWorkers();

    @Override
    default void close() {
    }
}
