package dev.minimalaccounting.observability;

import dev.minimalaccounting.ports.api.Transaction;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

@RequiredArgsConstructor
public final class Observability {
    @NonNull
    private final MetricsRegistry metrics;

    public TransactionProcessor processor(
            @NonNull String name,
            @NonNull TransactionProcessor processor) {
        Consumer<Transaction> observed = observer(name).consumer(name, processor::submit);
        return observed::accept;
    }

    public <T> Handler<T> handler(@NonNull String name, @NonNull Handler<T> handler) {
        Consumer<T> observed = observer(name).consumer(name, handler::handle);
        return observed::accept;
    }

    public <T> Sender<T> sender(@NonNull String name, @NonNull Sender<T> sender) {
        Consumer<T> observed = observer(name).consumer(name, sender::send);
        return observed::accept;
    }

    public <T> Receiver<T> receiver(@NonNull String name, @NonNull Receiver<T> receiver) {
        OperationObserver observer = observer(name);
        return new FunctionalReceiver<>(
                observer.intFunction(
                        name + ".receive",
                        receiver::receive,
                        List::size),
                observer.consumer(name + ".ack", receiver::ack),
                observer.consumer(name + ".nack", receiver::nack));
    }

    private OperationObserver observer(String name) {
        return new OperationObserver(metrics, logger(name));
    }

    private static System.Logger logger(String name) {
        return System.getLogger("dev.minimalaccounting." + name);
    }

    private record FunctionalReceiver<T>(
            @NonNull IntFunction<List<Received<T>>> poll,
            @NonNull Consumer<String> acknowledge,
            @NonNull Consumer<String> reject) implements Receiver<T> {
        @Override
        public List<Received<T>> receive(int limit) {
            return poll.apply(limit);
        }

        @Override
        public void ack(String messageId) {
            acknowledge.accept(messageId);
        }

        @Override
        public void nack(String messageId) {
            reject.accept(messageId);
        }
    }
}
