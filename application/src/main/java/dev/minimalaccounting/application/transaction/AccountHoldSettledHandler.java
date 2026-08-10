package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.transaction.TransactionProcessManager;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Handler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccountHoldSettledHandler implements Handler<AccountHoldSettled> {
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;

    @Override
    public void handle(AccountHoldSettled event) {
        var transaction = transactions.get(TransactionIdentifier.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var process = new TransactionProcessManager(transaction);
        process.accountHoldSettled(event);
        if (process.completed()) {
            transactions.remove(TransactionIdentifier.id(transaction.getId()));
        } else {
            transactions.update(transaction);
        }
    }
}
