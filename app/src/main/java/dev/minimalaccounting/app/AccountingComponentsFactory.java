package dev.minimalaccounting.app;

import dev.minimalaccounting.application.account.PlaceAccountHoldHandler;
import dev.minimalaccounting.application.account.ReleaseAccountHoldHandler;
import dev.minimalaccounting.application.account.SettleAccountHoldHandler;
import dev.minimalaccounting.application.journal.JournalizeTransactionHandler;
import dev.minimalaccounting.application.transaction.AccountHoldDeclinedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldPlacedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldReleasedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldSettledHandler;
import dev.minimalaccounting.application.transaction.TransactionIntake;
import dev.minimalaccounting.application.transaction.TransactionJournalizedHandler;
import dev.minimalaccounting.infrastructure.BoundaryDecorators;
import lombok.NonNull;

import java.time.Clock;

final class AccountingComponentsFactory {
    private AccountingComponentsFactory() {
    }

    static AccountingComponents create(
            @NonNull AccountingRepositories repositories,
            @NonNull AccountingSenders senders,
            @NonNull Clock clock,
            @NonNull BoundaryDecorators decorators) {
        var decorated = decorated(senders, decorators);
        var components = new AccountingComponents(
                new TransactionIntake(
                        repositories.accounts(),
                        repositories.transactions(),
                        repositories.journals(),
                        decorated.placeAccountHolds(),
                        decorated.journalizeTransactions(),
                        clock),
                new PlaceAccountHoldHandler(
                        repositories.accounts(),
                        decorated.accountHoldsPlaced(),
                        decorated.accountHoldsDeclined()),
                new AccountHoldPlacedHandler(
                        repositories.transactions(),
                        decorated.releaseAccountHolds(),
                        decorated.journalizeTransactions(),
                        clock),
                new AccountHoldDeclinedHandler(
                        repositories.transactions(), decorated.releaseAccountHolds()),
                new ReleaseAccountHoldHandler(
                        repositories.accounts(), decorated.accountHoldsReleased()),
                new AccountHoldReleasedHandler(repositories.transactions()),
                new JournalizeTransactionHandler(
                        repositories.accounts(),
                        repositories.transactions(),
                        repositories.journals(),
                        decorated.transactionsJournalized(),
                        clock),
                new TransactionJournalizedHandler(
                        repositories.transactions(),
                        repositories.journals(),
                        decorated.settleAccountHolds()),
                new SettleAccountHoldHandler(
                        repositories.accounts(), decorated.accountHoldsSettled()),
                new AccountHoldSettledHandler(repositories.transactions()));
        return new AccountingComponents(
                decorators.handler("transaction.submit", components.transactions()),
                decorators.handler(
                        "command.place-account-hold", components.placeAccountHold()),
                decorators.handler(
                        "event.account-hold-placed", components.accountHoldPlaced()),
                decorators.handler(
                        "event.account-hold-declined", components.accountHoldDeclined()),
                decorators.handler(
                        "command.release-account-hold", components.releaseAccountHold()),
                decorators.handler(
                        "event.account-hold-released", components.accountHoldReleased()),
                decorators.handler(
                        "command.journalize-transaction", components.journalizeTransaction()),
                decorators.handler(
                        "event.transaction-journalized", components.transactionJournalized()),
                decorators.handler(
                        "command.settle-account-hold", components.settleAccountHold()),
                decorators.handler(
                        "event.account-hold-settled", components.accountHoldSettled()));
    }

    private static AccountingSenders decorated(
            AccountingSenders senders,
            BoundaryDecorators decorators) {
        return new AccountingSenders(
                decorators.sender(
                        "send.place-account-hold", senders.placeAccountHolds()),
                decorators.sender(
                        "send.release-account-hold", senders.releaseAccountHolds()),
                decorators.sender(
                        "send.journalize-transaction", senders.journalizeTransactions()),
                decorators.sender(
                        "send.settle-account-hold", senders.settleAccountHolds()),
                decorators.sender(
                        "publish.account-hold-placed", senders.accountHoldsPlaced()),
                decorators.sender(
                        "publish.account-hold-declined", senders.accountHoldsDeclined()),
                decorators.sender(
                        "publish.account-hold-released", senders.accountHoldsReleased()),
                decorators.sender(
                        "publish.transaction-journalized", senders.transactionsJournalized()),
                decorators.sender(
                        "publish.account-hold-settled", senders.accountHoldsSettled()));
    }
}
