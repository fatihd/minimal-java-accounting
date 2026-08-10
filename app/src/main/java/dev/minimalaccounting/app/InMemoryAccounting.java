package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.time.Duration;
import java.util.function.IntSupplier;

@RequiredArgsConstructor
final class InMemoryAccounting implements AccountingRuntime {
    private static final int MAXIMUM_POLL_CYCLES = 1_000;

    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final SubmitTransactionHandler transactions;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final List<InMemoryQueue<?>> queues;
    @NonNull
    private final List<IntSupplier> pollers;

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
        return WorkerGroup.start(pollers, Duration.ofMillis(10));
    }

    void processUntilIdle() {
        for (int cycle = 0; cycle < MAXIMUM_POLL_CYCLES; cycle++) {
            for (IntSupplier poller : pollers) {
                poller.getAsInt();
            }
            if (idle()) {
                return;
            }
        }
        throw new IllegalStateException(
                "in-memory messaging did not become idle after "
                        + MAXIMUM_POLL_CYCLES + " poll cycles");
    }

    private boolean idle() {
        return queues.stream().allMatch(queue -> queue.size() == 0);
    }
}
