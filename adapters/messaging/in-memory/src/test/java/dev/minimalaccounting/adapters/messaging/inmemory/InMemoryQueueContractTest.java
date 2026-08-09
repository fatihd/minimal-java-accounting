package dev.minimalaccounting.adapters.messaging.inmemory;

import dev.minimalaccounting.ports.messaging.QueueContract;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;

class InMemoryQueueContractTest extends QueueContract<String> {
    private final InMemoryQueue<String> queue = new InMemoryQueue<>();

    @Override
    protected Sender<String> sender() {
        return queue;
    }

    @Override
    protected Receiver<String> receiver() {
        return queue;
    }

    @Override
    protected String message(String value) {
        return value;
    }
}
