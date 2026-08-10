package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxSchema;
import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxSender;
import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class PostgresqlMessagingModuleFactory {
    private final PostgresqlModuleFactory postgresql;
    private final AccountingDestinations destinations;

    @Getter(lazy = true)
    private final JdbcTransactions outboxDatabase = initializeOutbox();

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<PlaceAccountHold> placeAccountHolds =
            sender(destinations.placeAccountHold());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<ReleaseAccountHold> releaseAccountHolds =
            sender(destinations.releaseAccountHold());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<JournalizeTransaction> journalizeTransactions =
            sender(destinations.journalizeTransaction());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<SettleAccountHold> settleAccountHolds =
            sender(destinations.settleAccountHold());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<AccountHoldPlaced> accountHoldsPlaced =
            sender(destinations.accountHoldPlaced());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<AccountHoldDeclined> accountHoldsDeclined =
            sender(destinations.accountHoldDeclined());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<AccountHoldReleased> accountHoldsReleased =
            sender(destinations.accountHoldReleased());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<TransactionJournalized> transactionsJournalized =
            sender(destinations.transactionJournalized());

    @Getter(lazy = true)
    private final PostgresqlOutboxSender<AccountHoldSettled> accountHoldsSettled =
            sender(destinations.accountHoldSettled());

    private JdbcTransactions initializeOutbox() {
        JdbcTransactions transactions = postgresql.getTransactions();
        PostgresqlOutboxSchema.install(transactions);
        return transactions;
    }

    private <T> PostgresqlOutboxSender<T> sender(String destination) {
        return new PostgresqlOutboxSender<>(getOutboxDatabase(), destination);
    }
}
