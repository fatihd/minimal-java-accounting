package dev.minimalaccounting.ports.persistence;

import lombok.NonNull;

public interface TransactionBoundary {
    void inTransaction(@NonNull Runnable work);
}
