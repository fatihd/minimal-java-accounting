package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.transaction.TransactionProcessManager;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccountHoldSettledHandler implements Handler<AccountHoldSettled> {
    @NonNull
    private final TransactionRepository transactions;

    @Override
    public void handle(AccountHoldSettled event) {
        var transaction = transactions.get(TransactionReference.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var process = new TransactionProcessManager(transaction);
        process.accountHoldSettled(event);
        if (process.completed()) {
            transactions.remove(TransactionReference.id(transaction.getId()));
        } else {
            transactions.update(transaction);
        }
    }
}
