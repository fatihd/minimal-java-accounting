package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxSchema;
import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxSender;
import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class PostgresqlMessagingModuleFactory {
    private final PostgresqlPersistenceModuleFactory persistence;
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
        JdbcTransactions transactions = persistence.getJdbcTransactions();
        PostgresqlOutboxSchema.install(transactions);
        return transactions;
    }

    private <T> PostgresqlOutboxSender<T> sender(String destination) {
        return new PostgresqlOutboxSender<>(getOutboxDatabase(), destination);
    }
}
