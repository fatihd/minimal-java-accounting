package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.transaction.TransactionProcessManager;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Sender;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccountHoldDeclinedHandler implements Handler<AccountHoldDeclined> {
    @NonNull
    private final TransactionRepository transactions;
    @NonNull
    private final Sender<ReleaseAccountHold> releases;

    @Override
    public void handle(AccountHoldDeclined event) {
        var transaction = transactions.get(TransactionReference.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var process = new TransactionProcessManager(transaction);
        process.accountHoldDeclined(event);
        var releaseCommands = process.accountHoldsToRelease();
        transactions.update(transaction);
        for (ReleaseAccountHold release : releaseCommands) {
            releases.send(release);
        }
    }
}
