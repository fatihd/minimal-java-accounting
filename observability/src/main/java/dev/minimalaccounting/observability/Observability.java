package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.api.TransactionProcessor;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Observability {
    @NonNull
    private final MetricsRegistry metrics;

    public TransactionProcessor processor(String name, TransactionProcessor processor) {
        return new ObservedTransactionProcessor(name, processor, metrics, logger(name));
    }

    public <T> Handler<T> handler(String name, Handler<T> handler) {
        return new ObservedHandler<>(name, handler, metrics, logger(name));
    }

    public <T> Sender<T> sender(String name, Sender<T> sender) {
        return new ObservedSender<>(name, sender, metrics, logger(name));
    }

    public <T> Receiver<T> receiver(String name, Receiver<T> receiver) {
        return new ObservedReceiver<>(name, receiver, metrics, logger(name));
    }

    private static System.Logger logger(String name) {
        return System.getLogger("dev.minimalaccounting." + name);
    }
}
