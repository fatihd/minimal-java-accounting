package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.transaction.TransactionProcessManager;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Handler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccountHoldReleasedHandler implements Handler<AccountHoldReleased> {
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;

    @Override
    public void handle(AccountHoldReleased event) {
        var transaction = transactions.get(TransactionIdentifier.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        new TransactionProcessManager(transaction).accountHoldReleased(event);
        transactions.update(transaction);
    }
}
