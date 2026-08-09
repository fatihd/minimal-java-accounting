package dev.minimalaccounting.adapters.messaging.inmemory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class InMemoryTopicTest {
    @Test
    void publishCopiesMessageToEverySubscription() {
        InMemoryTopic<String> topic = new InMemoryTopic<>();
        InMemoryQueue<String> first = topic.subscribe("first");
        InMemoryQueue<String> second = topic.subscribe("second");

        topic.send("event");

        assertEquals("event", first.receive(1).getFirst().message());
        assertEquals("event", second.receive(1).getFirst().message());
    }

    @Test
    void subscriptionNameIdentifiesOneReceiver() {
        InMemoryTopic<String> topic = new InMemoryTopic<>();

        assertSame(topic.subscribe("workflow"), topic.subscribe("workflow"));
    }
}
