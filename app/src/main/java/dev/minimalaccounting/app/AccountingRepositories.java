package dev.minimalaccounting.app;

import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.ports.persistence.JournalRepository;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;

record AccountingRepositories(
        @NonNull AccountRepository accounts,
        @NonNull TransactionRepository transactions,
        @NonNull JournalRepository journals) {
}
