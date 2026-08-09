package dev.minimalaccounting.adapters.messaging.inmemory;

import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Received;
import dev.minimalaccounting.ports.messaging.Receiver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryPollerTest {
    @Test
    void runtimeExceptionNacksMessage() {
        InMemoryQueue<Message> queue = new InMemoryQueue<>();
        queue.send(new Message("message-1"));
        Handler<Message> handler = message -> {
            throw new IllegalStateException("try again");
        };
        InMemoryPoller<Message> poller = new InMemoryPoller<>(queue, handler, 10);

        poller.pollOnce();
        assertEquals(1, queue.size());
    }

    @Test
    void normalReturnAcksMessage() {
        InMemoryQueue<Message> queue = new InMemoryQueue<>();
        queue.send(new Message("message-1"));
        InMemoryPoller<Message> poller = new InMemoryPoller<>(queue, message -> { }, 10);

        poller.pollOnce();
        assertEquals(0, queue.size());
    }

    @Test
    void nackedMessageIsDeliveredAgain() {
        InMemoryQueue<Message> queue = new InMemoryQueue<>();
        queue.send(new Message("message-1"));
        AtomicInteger attempts = new AtomicInteger();
        Handler<Message> handler = message -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("try again");
            }
        };
        InMemoryPoller<Message> poller = new InMemoryPoller<>(queue, handler, 10);

        poller.pollOnce();
        poller.pollOnce();
        assertEquals(2, attempts.get());
    }

    @Test
    void failureStopsTheCurrentBatch() {
        InMemoryQueue<Message> queue = new InMemoryQueue<>();
        queue.send(new Message("first"));
        queue.send(new Message("second"));
        List<String> attempts = new ArrayList<>();
        InMemoryPoller<Message> poller = new InMemoryPoller<>(queue, message -> {
            attempts.add(message.id());
            throw new IllegalStateException("try again");
        }, 10);

        poller.pollOnce();

        assertEquals(List.of("first"), attempts);
    }

    @Test
    void failureNacksTheUnhandledRemainderOfTheBatch() {
        InMemoryQueue<Message> queue = new InMemoryQueue<>();
        queue.send(new Message("first"));
        queue.send(new Message("second"));
        InMemoryPoller<Message> poller = new InMemoryPoller<>(queue, message -> {
            throw new IllegalStateException("try again");
        }, 10);

        poller.pollOnce();

        assertEquals(2, queue.size());
    }

    @Test
    void ackFailurePropagatesAsAdapterFailure() {
        Receiver<Message> receiver = new Receiver<>() {
            @Override
            public List<Received<Message>> receive(int limit) {
                return List.of(new Received<>("message-1", new Message("message-1")));
            }

            @Override
            public void ack(String messageId) {
                throw new IllegalStateException("broker unavailable");
            }

            @Override
            public void nack(String messageId) {
            }
        };
        InMemoryPoller<Message> poller = new InMemoryPoller<>(
                receiver, ignored -> { }, 10);

        assertThrows(IllegalStateException.class, poller::pollOnce);
    }

    private record Message(String id) {
    }
}
