package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.api.Transaction;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ObservedTransactionProcessor implements TransactionProcessor {
    @NonNull
    private final String name;
    @NonNull
    private final TransactionProcessor delegate;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final System.Logger logger;

    @Override
    public void submit(Transaction transaction) {
        long started = System.nanoTime();
        try {
            delegate.submit(transaction);
            metrics.success(name, System.nanoTime() - started, 1);
            logger.log(System.Logger.Level.DEBUG, "{0} succeeded", name);
        } catch (RuntimeException failure) {
            metrics.failure(name, System.nanoTime() - started);
            logger.log(System.Logger.Level.WARNING, "{0} failed", name);
            throw failure;
        }
    }
}
