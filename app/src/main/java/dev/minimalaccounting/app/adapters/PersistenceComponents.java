package dev.minimalaccounting.app.adapters;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Repository;

public interface PersistenceComponents {
    Repository<AccountIdentifier, Account> getAccountRepository();

    Repository<TransactionIdentifier, Transaction> getTransactionRepository();

    Repository<JournalIdentifier, JournalEntry> getJournalRepository();
}
