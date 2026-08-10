package dev.minimalaccounting.app.adapters.real;

import dev.minimalaccounting.adapters.messaging.outbox.ArtemisConfiguration;
import dev.minimalaccounting.adapters.messaging.outbox.ArtemisConnection;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxPublisher;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxQueue;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxSchema;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxTopic;
import dev.minimalaccounting.app.AccountingDestinations;
import dev.minimalaccounting.app.adapters.MessagingComponents;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.shared.adapters.postgresql.JdbcTransactions;
import dev.minimalaccounting.shared.ports.Receiver;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class OutboxMessagingModuleFactory implements MessagingComponents {
    private final PostgresqlModuleFactory postgresql;
    private final ArtemisConfiguration configuration;
    private final AccountingDestinations destinations;
    private final int batchSize;

    @Getter(lazy = true)
    private final JdbcTransactions database = initializeDatabase();

    @Getter(lazy = true)
    private final ArtemisConnection connection = new ArtemisConnection(configuration);

    @Getter(lazy = true)
    private final OutboxQueue<PlaceAccountHold> placeAccountHolds = new OutboxQueue<>(
            getDatabase(), getConnection(),
            destinations.placeAccountHold(), PlaceAccountHold.class);

    @Getter(lazy = true)
    private final OutboxQueue<ReleaseAccountHold> releaseAccountHolds = new OutboxQueue<>(
            getDatabase(), getConnection(),
            destinations.releaseAccountHold(), ReleaseAccountHold.class);

    @Getter(lazy = true)
    private final OutboxQueue<JournalizeTransaction> journalizeTransactions =
            new OutboxQueue<>(
                    getDatabase(), getConnection(),
                    destinations.journalizeTransaction(), JournalizeTransaction.class);

    @Getter(lazy = true)
    private final OutboxQueue<SettleAccountHold> settleAccountHolds = new OutboxQueue<>(
            getDatabase(), getConnection(),
            destinations.settleAccountHold(), SettleAccountHold.class);

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldPlaced> accountHoldsPlaced = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldPlaced(), AccountHoldPlaced.class);

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldDeclined> accountHoldsDeclined = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldDeclined(), AccountHoldDeclined.class);

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldReleased> accountHoldsReleased = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldReleased(), AccountHoldReleased.class);

    @Getter(lazy = true)
    private final OutboxTopic<TransactionJournalized> transactionsJournalized =
            new OutboxTopic<>(
                    getDatabase(), getConnection(),
                    destinations.transactionJournalized(), TransactionJournalized.class);

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldSettled> accountHoldsSettled = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldSettled(), AccountHoldSettled.class);

    @Getter(lazy = true)
    private final Receiver<AccountHoldPlaced> accountHoldPlacedWorkflow =
            getAccountHoldsPlaced().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldDeclined> accountHoldDeclinedWorkflow =
            getAccountHoldsDeclined().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldReleased> accountHoldReleasedWorkflow =
            getAccountHoldsReleased().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<TransactionJournalized> transactionJournalizedWorkflow =
            getTransactionsJournalized().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldSettled> accountHoldSettledWorkflow =
            getAccountHoldsSettled().subscribe("transaction");

    @Getter(lazy = true)
    private final OutboxPublisher publisher = new OutboxPublisher(
            getDatabase(),
            List.of(
                    getPlaceAccountHolds().route(),
                    getReleaseAccountHolds().route(),
                    getJournalizeTransactions().route(),
                    getSettleAccountHolds().route(),
                    getAccountHoldsPlaced().route(),
                    getAccountHoldsDeclined().route(),
                    getAccountHoldsReleased().route(),
                    getTransactionsJournalized().route(),
                    getAccountHoldsSettled().route()),
            batchSize);

    private JdbcTransactions initializeDatabase() {
        JdbcTransactions transactions = postgresql.getTransactions();
        OutboxSchema.install(transactions);
        return transactions;
    }

    @Override
    public Receiver<PlaceAccountHold> getPlaceAccountHoldReceiver() {
        return getPlaceAccountHolds();
    }

    @Override
    public Receiver<ReleaseAccountHold> getReleaseAccountHoldReceiver() {
        return getReleaseAccountHolds();
    }

    @Override
    public Receiver<JournalizeTransaction> getJournalizeTransactionReceiver() {
        return getJournalizeTransactions();
    }

    @Override
    public Receiver<SettleAccountHold> getSettleAccountHoldReceiver() {
        return getSettleAccountHolds();
    }
}
