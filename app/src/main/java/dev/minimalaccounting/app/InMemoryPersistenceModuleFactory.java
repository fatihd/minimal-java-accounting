package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class InMemoryPersistenceModuleFactory {
    @Getter(lazy = true)
    private final ConcurrentAccountRepository accountRepository =
            new ConcurrentAccountRepository();

    @Getter(lazy = true)
    private final ConcurrentTransactionRepository transactionRepository =
            new ConcurrentTransactionRepository();

    @Getter(lazy = true)
    private final ConcurrentJournalRepository journalRepository =
            new ConcurrentJournalRepository();
}
