package dev.minimalaccounting.adapters.messaging.postgresql;

import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.persistence.TransactionBoundary;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TransactionalHandler<T> implements Handler<T> {
    @NonNull
    private final TransactionBoundary transactions;
    @NonNull
    private final Handler<T> handler;

    @Override
    public void handle(@NonNull T message) {
        transactions.inTransaction(() -> handler.handle(message));
    }
}
