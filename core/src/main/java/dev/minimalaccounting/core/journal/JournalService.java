package dev.minimalaccounting.core.journal;

import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.transaction.Transaction;

import java.time.Instant;
import java.util.Collection;

public final class JournalService {
    private final PostingService posting = new PostingService();

    public JournalEntry journalize(
            Transaction transaction,
            Collection<Account> accounts,
            Instant journalizedAt) {
        transaction.requireJournalizable();
        return posting.post(transaction, accounts, journalizedAt);
    }
}
