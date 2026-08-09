package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.messaging.Handler;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ObservedHandler<T> implements Handler<T> {
    @NonNull
    private final String name;
    @NonNull
    private final Handler<? super T> delegate;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final System.Logger logger;

    @Override
    public void handle(T message) {
        long started = System.nanoTime();
        try {
            delegate.handle(message);
            metrics.success(name, System.nanoTime() - started, 1);
            logger.log(System.Logger.Level.DEBUG, "{0} succeeded", name);
        } catch (RuntimeException failure) {
            metrics.failure(name, System.nanoTime() - started);
            logger.log(System.Logger.Level.WARNING, "{0} failed", name);
            throw failure;
        }
    }
}
