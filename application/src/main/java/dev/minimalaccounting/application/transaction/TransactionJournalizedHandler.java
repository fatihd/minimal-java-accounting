package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.core.transaction.TransactionProcessManager;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.persistence.JournalRepository;
import dev.minimalaccounting.ports.messaging.Sender;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class TransactionJournalizedHandler implements Handler<TransactionJournalized> {
    @NonNull
    private final TransactionRepository transactions;
    @NonNull
    private final JournalRepository journals;
    @NonNull
    private final Sender<SettleAccountHold> settlements;

    @Override
    public void handle(TransactionJournalized event) {
        var transaction = transactions.get(TransactionReference.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var journal = journals.get(JournalReference.id(event.journalEntryId()))
                .orElseThrow(() -> new IllegalStateException(
                        "Journal entry is not available: " + event.journalEntryId()));
        var process = new TransactionProcessManager(transaction);
        process.transactionJournalized(event, journal.getRequestId());
        var settlementCommands = process.accountHoldsToSettle();
        if (process.completed()) {
            transactions.remove(TransactionReference.id(transaction.getId()));
        } else {
            transactions.update(transaction);
        }
        for (SettleAccountHold command : settlementCommands) {
            settlements.send(command);
        }
    }
}
