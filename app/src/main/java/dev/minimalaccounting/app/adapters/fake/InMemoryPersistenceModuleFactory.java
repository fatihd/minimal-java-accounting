package dev.minimalaccounting.app.adapters.fake;

import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.app.adapters.PersistenceComponents;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class InMemoryPersistenceModuleFactory implements PersistenceComponents {
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
