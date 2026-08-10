package dev.minimalaccounting.adapters.messaging.inmemory;

import dev.minimalaccounting.shared.ports.QueueContract;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;

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
