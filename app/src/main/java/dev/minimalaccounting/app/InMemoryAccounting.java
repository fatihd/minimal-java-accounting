package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import dev.minimalaccounting.observability.MetricsRegistry;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
final class InMemoryAccounting {
    private static final int MAXIMUM_POLL_CYCLES = 1_000;

    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final TransactionProcessor transactions;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final List<InMemoryQueue<?>> queues;
    @NonNull
    private final List<InMemoryPoller<?>> pollers;

    AccountRepository accounts() {
        return accounts;
    }

    TransactionProcessor transactions() {
        return transactions;
    }

    MetricsRegistry metrics() {
        return metrics;
    }

    void processUntilIdle() {
        for (int cycle = 0; cycle < MAXIMUM_POLL_CYCLES; cycle++) {
            for (InMemoryPoller<?> poller : pollers) {
                poller.pollOnce();
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
