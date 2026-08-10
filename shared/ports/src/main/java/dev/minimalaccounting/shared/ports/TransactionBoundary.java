package dev.minimalaccounting.shared.ports;

import java.util.function.Supplier;

public interface TransactionBoundary {
    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
