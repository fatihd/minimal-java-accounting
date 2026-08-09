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
import dev.minimalaccounting.observability.Observability;
import lombok.NonNull;

import java.time.Clock;

final class AccountingComponentsFactory {
    private AccountingComponentsFactory() {
    }

    static AccountingComponents create(
            @NonNull AccountingRepositories repositories,
            @NonNull AccountingSenders senders,
            @NonNull Clock clock,
            @NonNull Observability observability) {
        var observed = observed(senders, observability);
        var components = new AccountingComponents(
                new TransactionIntake(
                        repositories.accounts(),
                        repositories.transactions(),
                        repositories.journals(),
                        observed.placeAccountHolds(),
                        observed.journalizeTransactions(),
                        clock),
                new PlaceAccountHoldHandler(
                        repositories.accounts(),
                        observed.accountHoldsPlaced(),
                        observed.accountHoldsDeclined()),
                new AccountHoldPlacedHandler(
                        repositories.transactions(),
                        observed.releaseAccountHolds(),
                        observed.journalizeTransactions(),
                        clock),
                new AccountHoldDeclinedHandler(
                        repositories.transactions(), observed.releaseAccountHolds()),
                new ReleaseAccountHoldHandler(
                        repositories.accounts(), observed.accountHoldsReleased()),
                new AccountHoldReleasedHandler(repositories.transactions()),
                new JournalizeTransactionHandler(
                        repositories.accounts(),
                        repositories.transactions(),
                        repositories.journals(),
                        observed.transactionsJournalized(),
                        clock),
                new TransactionJournalizedHandler(
                        repositories.transactions(),
                        repositories.journals(),
                        observed.settleAccountHolds()),
                new SettleAccountHoldHandler(
                        repositories.accounts(), observed.accountHoldsSettled()),
                new AccountHoldSettledHandler(repositories.transactions()));
        return new AccountingComponents(
                observability.processor("transaction.submit", components.transactions()),
                observability.handler(
                        "command.place-account-hold", components.placeAccountHold()),
                observability.handler(
                        "event.account-hold-placed", components.accountHoldPlaced()),
                observability.handler(
                        "event.account-hold-declined", components.accountHoldDeclined()),
                observability.handler(
                        "command.release-account-hold", components.releaseAccountHold()),
                observability.handler(
                        "event.account-hold-released", components.accountHoldReleased()),
                observability.handler(
                        "command.journalize-transaction", components.journalizeTransaction()),
                observability.handler(
                        "event.transaction-journalized", components.transactionJournalized()),
                observability.handler(
                        "command.settle-account-hold", components.settleAccountHold()),
                observability.handler(
                        "event.account-hold-settled", components.accountHoldSettled()));
    }

    private static AccountingSenders observed(
            AccountingSenders senders,
            Observability observability) {
        return new AccountingSenders(
                observability.sender(
                        "send.place-account-hold", senders.placeAccountHolds()),
                observability.sender(
                        "send.release-account-hold", senders.releaseAccountHolds()),
                observability.sender(
                        "send.journalize-transaction", senders.journalizeTransactions()),
                observability.sender(
                        "send.settle-account-hold", senders.settleAccountHolds()),
                observability.sender(
                        "publish.account-hold-placed", senders.accountHoldsPlaced()),
                observability.sender(
                        "publish.account-hold-declined", senders.accountHoldsDeclined()),
                observability.sender(
                        "publish.account-hold-released", senders.accountHoldsReleased()),
                observability.sender(
                        "publish.transaction-journalized", senders.transactionsJournalized()),
                observability.sender(
                        "publish.account-hold-settled", senders.accountHoldsSettled()));
    }
}
