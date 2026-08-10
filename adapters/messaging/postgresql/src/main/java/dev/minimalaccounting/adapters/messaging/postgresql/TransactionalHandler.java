package dev.minimalaccounting.adapters.messaging.postgresql;

import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.TransactionBoundary;

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
