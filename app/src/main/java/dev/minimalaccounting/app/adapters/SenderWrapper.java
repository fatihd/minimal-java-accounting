package dev.minimalaccounting.app.adapters;

import dev.minimalaccounting.shared.ports.Sender;

public interface SenderWrapper {
    <T> Sender<T> apply(String name, Sender<T> sender);
}
