package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Received;
import dev.minimalaccounting.shared.ports.Receiver;

import lombok.NonNull;

import java.util.List;

public final class OutboxPoller<T> {
    private final Receiver<T> receiver;
    private final Handler<? super T> handler;
    private final int batchSize;

    public OutboxPoller(
            @NonNull Receiver<T> receiver,
            @NonNull Handler<? super T> handler,
            int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.receiver = receiver;
        this.handler = handler;
        this.batchSize = batchSize;
    }

    public int pollOnce() {
        List<Received<T>> messages = receiver.receive(batchSize);
        int attempted = 0;
        for (int index = 0; index < messages.size(); index++) {
            Received<T> received = messages.get(index);
            attempted++;
            try {
                handler.handle(received.message());
            } catch (RuntimeException error) {
                receiver.nack(received.messageId());
                for (int unhandled = index + 1; unhandled < messages.size(); unhandled++) {
                    receiver.nack(messages.get(unhandled).messageId());
                }
                break;
            }
            receiver.ack(received.messageId());
        }
        return attempted;
    }
}
