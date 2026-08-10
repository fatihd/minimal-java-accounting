package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.transaction.Transaction;

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
