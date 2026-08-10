package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.shared.ports.QueueContract;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;
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
