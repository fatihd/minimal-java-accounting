package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.transaction.TransactionProcessManager;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccountHoldDeclinedHandler implements Handler<AccountHoldDeclined> {
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;
    @NonNull
    private final Sender<ReleaseAccountHold> releases;

    @Override
    public void handle(AccountHoldDeclined event) {
        var transaction = transactions.get(TransactionIdentifier.id(event.transactionId()))
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
