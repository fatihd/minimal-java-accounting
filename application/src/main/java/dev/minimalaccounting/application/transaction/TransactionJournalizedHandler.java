package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.model.transaction.TransactionProcessManager;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TransactionJournalizedHandler implements Handler<TransactionJournalized> {
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;
    @NonNull
    private final Repository<JournalIdentifier, JournalEntry> journals;
    @NonNull
    private final Sender<SettleAccountHold> settlements;

    @Override
    public void handle(TransactionJournalized event) {
        var transaction = transactions.get(TransactionIdentifier.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var journal = journals.get(JournalIdentifier.id(event.journalEntryId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Journal entry is not available: " + event.journalEntryId()));
        var process = new TransactionProcessManager(transaction);
        process.transactionJournalized(event, journal.getRequestId());
        var settlementCommands = process.accountHoldsToSettle();
        if (process.completed()) {
            transactions.remove(TransactionIdentifier.id(transaction.getId()));
        } else {
            transactions.update(transaction);
        }
        for (SettleAccountHold command : settlementCommands) {
            settlements.send(command);
        }
    }
}
