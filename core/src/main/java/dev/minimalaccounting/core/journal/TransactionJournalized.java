package dev.minimalaccounting.core.journal;

public record TransactionJournalized(
        long transactionId,
        long journalEntryId) {
}
