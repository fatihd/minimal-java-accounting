package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class ObservedReceiver<T> implements Receiver<T> {
    @NonNull
    private final String name;
    @NonNull
    private final Receiver<T> delegate;
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final System.Logger logger;

    @Override
    public List<Received<T>> receive(int limit) {
        long started = System.nanoTime();
        try {
            List<Received<T>> messages = delegate.receive(limit);
            metrics.success(name + ".receive", System.nanoTime() - started, messages.size());
            return messages;
        } catch (RuntimeException failure) {
            failed(name + ".receive", started, failure);
            throw failure;
        }
    }

    @Override
    public void ack(String messageId) {
        disposition(name + ".ack", messageId, true);
    }

    @Override
    public void nack(String messageId) {
        disposition(name + ".nack", messageId, false);
    }

    private void disposition(String operation, String messageId, boolean acknowledge) {
        long started = System.nanoTime();
        try {
            if (acknowledge) {
                delegate.ack(messageId);
            } else {
                delegate.nack(messageId);
            }
            metrics.success(operation, System.nanoTime() - started, 1);
        } catch (RuntimeException failure) {
            failed(operation, started, failure);
            throw failure;
        }
    }

    private void failed(String operation, long started, RuntimeException failure) {
        metrics.failure(operation, System.nanoTime() - started);
        logger.log(System.Logger.Level.WARNING, "{0} failed", operation);
    }
}
