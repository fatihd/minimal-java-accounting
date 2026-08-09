package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ObservedSender<T> implements Sender<T> {
    @NonNull
    private final String name;
    @NonNull
    private final Sender<? super T> delegate;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final System.Logger logger;

    @Override
    public void send(T message) {
        long started = System.nanoTime();
        try {
            delegate.send(message);
            metrics.success(name, System.nanoTime() - started, 1);
            logger.log(System.Logger.Level.DEBUG, "{0} succeeded", name);
        } catch (RuntimeException failure) {
            metrics.failure(name, System.nanoTime() - started);
            logger.log(System.Logger.Level.WARNING, "{0} failed", name);
            throw failure;
        }
    }
}
