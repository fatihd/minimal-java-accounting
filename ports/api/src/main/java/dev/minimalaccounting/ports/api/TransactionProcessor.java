package dev.minimalaccounting.ports.api;

@FunctionalInterface
public interface TransactionProcessor {
    void submit(Transaction transaction);
}
