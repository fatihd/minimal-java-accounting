package dev.minimalaccounting.adapters.messaging.artemis;

import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import dev.minimalaccounting.ports.messaging.TopicContract;
import org.junit.jupiter.api.AfterEach;

class ArtemisTopicContractTest extends TopicContract<ExampleMessage> {
    private final ArtemisConnection connection = ArtemisTestBroker.connect();
    private final ArtemisTopic<ExampleMessage> topic = new ArtemisTopic<>(
            connection,
            ArtemisTestBroker.uniqueName("topic"),
            ExampleMessage.class);
    private final Receiver<ExampleMessage> first = topic.subscribe("first");
    private final Receiver<ExampleMessage> second = topic.subscribe("second");

    @Override
    protected Sender<ExampleMessage> sender() {
        return topic;
    }

    @Override
    protected Receiver<ExampleMessage> firstSubscription() {
        return first;
    }

    @Override
    protected Receiver<ExampleMessage> secondSubscription() {
        return second;
    }

    @Override
    protected ExampleMessage message(String value) {
        return new ExampleMessage(41, value);
    }

    @AfterEach
    void closeConnection() throws Exception {
        ((AutoCloseable) first).close();
        ((AutoCloseable) second).close();
        connection.close();
    }
}
