package dev.minimalaccounting.shared.ports;

import java.util.List;

public interface Receiver<T> {
    List<Received<T>> receive(int limit);

    void ack(String messageId);

    void nack(String messageId);
}
