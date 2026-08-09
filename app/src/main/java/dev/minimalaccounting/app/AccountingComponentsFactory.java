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
import lombok.NonNull;

import java.time.Clock;

final class AccountingComponentsFactory {
    private AccountingComponentsFactory() {
    }

    static AccountingComponents create(
            @NonNull AccountingRepositories repositories,
            @NonNull AccountingSenders senders,
            @NonNull Clock clock) {
        return new AccountingComponents(
                new TransactionIntake(
                        repositories.accounts(),
                        repositories.transactions(),
                        repositories.journals(),
                        senders.placeAccountHolds(),
                        senders.journalizeTransactions(),
                        clock),
                new PlaceAccountHoldHandler(
                        repositories.accounts(),
                        senders.accountHoldsPlaced(),
                        senders.accountHoldsDeclined()),
                new AccountHoldPlacedHandler(
                        repositories.transactions(),
                        senders.releaseAccountHolds(),
                        senders.journalizeTransactions(),
                        clock),
                new AccountHoldDeclinedHandler(
                        repositories.transactions(), senders.releaseAccountHolds()),
                new ReleaseAccountHoldHandler(
                        repositories.accounts(), senders.accountHoldsReleased()),
                new AccountHoldReleasedHandler(repositories.transactions()),
                new JournalizeTransactionHandler(
                        repositories.accounts(),
                        repositories.transactions(),
                        repositories.journals(),
                        senders.transactionsJournalized(),
                        clock),
                new TransactionJournalizedHandler(
                        repositories.transactions(),
                        repositories.journals(),
                        senders.settleAccountHolds()),
                new SettleAccountHoldHandler(
                        repositories.accounts(), senders.accountHoldsSettled()),
                new AccountHoldSettledHandler(repositories.transactions()));
    }
}
