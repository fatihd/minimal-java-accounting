package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConnection;
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.function.IntSupplier;

@RequiredArgsConstructor
final class DurableAccounting implements AccountingRuntime {
    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final SubmitTransactionHandler transactions;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final List<IntSupplier> pollers;
    @NonNull
    private final ArtemisConnection broker;

    @Override
    public AccountRepository accounts() {
        return accounts;
    }

    @Override
    public SubmitTransactionHandler transactions() {
        return transactions;
    }

    @Override
    public MetricsRegistry metrics() {
        return metrics;
    }

    @Override
    public WorkerGroup startWorkers() {
        return WorkerGroup.start(pollers, Duration.ofMillis(25));
    }

    @Override
    public void close() {
        broker.close();
    }
}
