package dev.minimalaccounting.adapters.messaging.artemis;

import dev.minimalaccounting.ports.messaging.QueueContract;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import org.junit.jupiter.api.AfterEach;

class ArtemisQueueContractTest extends QueueContract<ExampleMessage> {
    private final ArtemisConnection connection = ArtemisTestBroker.connect();
    private final ArtemisQueue<ExampleMessage> queue = new ArtemisQueue<>(
            connection,
            ArtemisTestBroker.uniqueName("queue"),
            ExampleMessage.class);

    @Override
    protected Sender<ExampleMessage> sender() {
        return queue;
    }

    @Override
    protected Receiver<ExampleMessage> receiver() {
        return queue;
    }

    @Override
    protected ExampleMessage message(String value) {
        return new ExampleMessage(41, value);
    }

    @AfterEach
    void closeConnection() {
        queue.close();
        connection.close();
    }
}
