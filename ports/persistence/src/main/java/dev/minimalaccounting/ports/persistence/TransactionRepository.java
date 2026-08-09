package dev.minimalaccounting.ports.persistence;

import dev.minimalaccounting.core.transaction.Transaction;
import dev.minimalaccounting.core.transaction.TransactionReference;

import java.util.Optional;

public interface TransactionRepository {
    Optional<Transaction> get(TransactionReference key);

    long add(Transaction value);

    void update(Transaction value);

    void remove(TransactionReference key);
}
