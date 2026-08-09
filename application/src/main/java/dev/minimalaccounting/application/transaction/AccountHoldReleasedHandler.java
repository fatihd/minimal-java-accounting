package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.transaction.TransactionProcessManager;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class AccountHoldReleasedHandler implements Handler<AccountHoldReleased> {
    @NonNull
    private final TransactionRepository transactions;

    @Override
    public void handle(AccountHoldReleased event) {
        var transaction = transactions.get(TransactionReference.id(event.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        new TransactionProcessManager(transaction).accountHoldReleased(event);
        transactions.update(transaction);
    }
}
