package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.transaction.TransactionProcessManager;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Sender;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public final class AccountHoldPlacedHandler implements Handler<AccountHoldPlaced> {
    @NonNull
    private final TransactionRepository transactions;
    @NonNull
    private final Sender<ReleaseAccountHold> releases;
    @NonNull
    private final Sender<JournalizeTransaction> journalizations;
    @NonNull
    private final Clock clock;

    @Override
    public void handle(AccountHoldPlaced event) {
        var transaction = transactions.get(TransactionReference.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var process = new TransactionProcessManager(transaction);
        process.accountHoldPlaced(event, clock.instant());
        var release = process.accountHoldToRelease(event.effectNumber());
        var journalization = process.transactionToJournalize();
        transactions.update(transaction);
        release.ifPresent(releases::send);
        journalization.ifPresent(journalizations::send);
    }
}
