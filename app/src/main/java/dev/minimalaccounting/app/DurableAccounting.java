package dev.minimalaccounting.app;

import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConnection;
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.function.IntSupplier;

@RequiredArgsConstructor
final class DurableAccounting implements AccountingRuntime {
    @NonNull
    private final Repository<AccountIdentifier, Account> accounts;
    @NonNull
    private final Handler<SubmitTransaction> transactions;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final List<IntSupplier> pollers;
    @NonNull
    private final ArtemisConnection broker;

    @Override
    public Repository<AccountIdentifier, Account> accounts() {
        return accounts;
    }

    @Override
    public Handler<SubmitTransaction> transactions() {
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
