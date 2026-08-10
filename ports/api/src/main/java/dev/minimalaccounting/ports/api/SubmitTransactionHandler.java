package dev.minimalaccounting.ports.api;

@FunctionalInterface
public interface SubmitTransactionHandler {
    void handle(SubmitTransaction command);
}
