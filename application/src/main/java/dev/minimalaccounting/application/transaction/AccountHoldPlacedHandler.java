package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.transaction.TransactionProcessManager;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public final class AccountHoldPlacedHandler implements Handler<AccountHoldPlaced> {
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;
    @NonNull
    private final Sender<ReleaseAccountHold> releases;
    @NonNull
    private final Sender<JournalizeTransaction> journalizations;
    @NonNull
    private final Clock clock;

    @Override
    public void handle(AccountHoldPlaced event) {
        var transaction = transactions.get(TransactionIdentifier.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var process = new TransactionProcessManager(transaction);
        process.accountHoldPlaced(event, clock.instant());
        var release = process.accountHoldToRelease(event.accountNumber());
        var journalization = process.transactionToJournalize();
        transactions.update(transaction);
        release.ifPresent(releases::send);
        journalization.ifPresent(journalizations::send);
    }
}
