package dev.minimalaccounting.adapters.messaging.inmemory;

import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;
import dev.minimalaccounting.shared.ports.TopicContract;

class InMemoryTopicContractTest extends TopicContract<String> {
    private final InMemoryTopic<String> topic = new InMemoryTopic<>();
    private final InMemoryQueue<String> first = topic.subscribe("first");
    private final InMemoryQueue<String> second = topic.subscribe("second");

    @Override
    protected Sender<String> sender() {
        return topic;
    }

    @Override
    protected Receiver<String> firstSubscription() {
        return first;
    }

    @Override
    protected Receiver<String> secondSubscription() {
        return second;
    }

    @Override
    protected String message(String value) {
        return value;
    }
}
