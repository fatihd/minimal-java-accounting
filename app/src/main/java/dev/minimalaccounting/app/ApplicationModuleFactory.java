package dev.minimalaccounting.app;

import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
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
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
final class ApplicationModuleFactory {
    private final Repository<AccountIdentifier, Account> accounts;
    private final Repository<TransactionIdentifier, Transaction> transactions;
    private final Repository<JournalIdentifier, JournalEntry> journals;
    private final Sender<PlaceAccountHold> placeAccountHolds;
    private final Sender<ReleaseAccountHold> releaseAccountHolds;
    private final Sender<JournalizeTransaction> journalizeTransactions;
    private final Sender<SettleAccountHold> settleAccountHolds;
    private final Sender<AccountHoldPlaced> accountHoldsPlaced;
    private final Sender<AccountHoldDeclined> accountHoldsDeclined;
    private final Sender<AccountHoldReleased> accountHoldsReleased;
    private final Sender<TransactionJournalized> transactionsJournalized;
    private final Sender<AccountHoldSettled> accountHoldsSettled;
    private final Clock clock;

    @Getter(lazy = true)
    private final TransactionIntake transactionIntake = new TransactionIntake(
            accounts,
            transactions,
            journals,
            placeAccountHolds,
            journalizeTransactions,
            clock);

    @Getter(lazy = true)
    private final PlaceAccountHoldHandler placeAccountHoldHandler =
            new PlaceAccountHoldHandler(
                    accounts, accountHoldsPlaced, accountHoldsDeclined);

    @Getter(lazy = true)
    private final AccountHoldPlacedHandler accountHoldPlacedHandler =
            new AccountHoldPlacedHandler(
                    transactions, releaseAccountHolds, journalizeTransactions, clock);

    @Getter(lazy = true)
    private final AccountHoldDeclinedHandler accountHoldDeclinedHandler =
            new AccountHoldDeclinedHandler(transactions, releaseAccountHolds);

    @Getter(lazy = true)
    private final ReleaseAccountHoldHandler releaseAccountHoldHandler =
            new ReleaseAccountHoldHandler(accounts, accountHoldsReleased);

    @Getter(lazy = true)
    private final AccountHoldReleasedHandler accountHoldReleasedHandler =
            new AccountHoldReleasedHandler(transactions);

    @Getter(lazy = true)
    private final JournalizeTransactionHandler journalizeTransactionHandler =
            new JournalizeTransactionHandler(
                    accounts,
                    transactions,
                    journals,
                    transactionsJournalized,
                    clock);

    @Getter(lazy = true)
    private final TransactionJournalizedHandler transactionJournalizedHandler =
            new TransactionJournalizedHandler(
                    transactions, journals, settleAccountHolds);

    @Getter(lazy = true)
    private final SettleAccountHoldHandler settleAccountHoldHandler =
            new SettleAccountHoldHandler(accounts, accountHoldsSettled);

    @Getter(lazy = true)
    private final AccountHoldSettledHandler accountHoldSettledHandler =
            new AccountHoldSettledHandler(transactions);
}
