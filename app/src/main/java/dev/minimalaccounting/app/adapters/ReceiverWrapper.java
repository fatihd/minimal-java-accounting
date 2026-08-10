package dev.minimalaccounting.app.adapters;

import dev.minimalaccounting.shared.ports.Receiver;

public interface ReceiverWrapper {
    <T> Receiver<T> apply(String name, Receiver<T> receiver);
}
