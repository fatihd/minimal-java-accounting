package dev.minimalaccounting.app.adapters.real;

import dev.minimalaccounting.adapters.messaging.outbox.ArtemisConfiguration;
import dev.minimalaccounting.adapters.messaging.outbox.ArtemisConnection;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxPublisher;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxQueue;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxSchema;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxTopic;
import dev.minimalaccounting.app.AccountingDestinations;
import dev.minimalaccounting.app.adapters.MessagingComponents;
import dev.minimalaccounting.app.adapters.ReceiverWrapper;
import dev.minimalaccounting.app.adapters.SenderWrapper;
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
import dev.minimalaccounting.shared.ports.Sender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class OutboxMessagingModuleFactory implements MessagingComponents {
    private final PostgresqlModuleFactory postgresql;
    private final ArtemisConfiguration configuration;
    private final AccountingDestinations destinations;
    private final int batchSize;
    private final SenderWrapper senderWrapper;
    private final ReceiverWrapper receiverWrapper;

    @Getter(lazy = true)
    private final JdbcTransactions database = initializeDatabase();

    @Getter(lazy = true)
    private final ArtemisConnection connection = new ArtemisConnection(configuration);

    @Getter(lazy = true)
    private final OutboxQueue<PlaceAccountHold> placeAccountHoldQueue = new OutboxQueue<>(
            getDatabase(), getConnection(),
            destinations.placeAccountHold(), PlaceAccountHold.class);

    @Getter(lazy = true)
    private final Sender<PlaceAccountHold> placeAccountHolds = senderWrapper.apply(
            "send.place-account-hold", getPlaceAccountHoldQueue());

    @Getter(lazy = true)
    private final Receiver<PlaceAccountHold> placeAccountHoldReceiver = receiverWrapper.apply(
            receive(destinations.placeAccountHold()), getPlaceAccountHoldQueue());

    @Getter(lazy = true)
    private final OutboxQueue<ReleaseAccountHold> releaseAccountHoldQueue = new OutboxQueue<>(
            getDatabase(), getConnection(),
            destinations.releaseAccountHold(), ReleaseAccountHold.class);

    @Getter(lazy = true)
    private final Sender<ReleaseAccountHold> releaseAccountHolds = senderWrapper.apply(
            "send.release-account-hold", getReleaseAccountHoldQueue());

    @Getter(lazy = true)
    private final Receiver<ReleaseAccountHold> releaseAccountHoldReceiver =
            receiverWrapper.apply(
                    receive(destinations.releaseAccountHold()), getReleaseAccountHoldQueue());

    @Getter(lazy = true)
    private final OutboxQueue<JournalizeTransaction> journalizeTransactionQueue =
            new OutboxQueue<>(
                    getDatabase(), getConnection(),
                    destinations.journalizeTransaction(), JournalizeTransaction.class);

    @Getter(lazy = true)
    private final Sender<JournalizeTransaction> journalizeTransactions = senderWrapper.apply(
            "send.journalize-transaction", getJournalizeTransactionQueue());

    @Getter(lazy = true)
    private final Receiver<JournalizeTransaction> journalizeTransactionReceiver =
            receiverWrapper.apply(
                    receive(destinations.journalizeTransaction()),
                    getJournalizeTransactionQueue());

    @Getter(lazy = true)
    private final OutboxQueue<SettleAccountHold> settleAccountHoldQueue = new OutboxQueue<>(
            getDatabase(), getConnection(),
            destinations.settleAccountHold(), SettleAccountHold.class);

    @Getter(lazy = true)
    private final Sender<SettleAccountHold> settleAccountHolds = senderWrapper.apply(
            "send.settle-account-hold", getSettleAccountHoldQueue());

    @Getter(lazy = true)
    private final Receiver<SettleAccountHold> settleAccountHoldReceiver =
            receiverWrapper.apply(
                    receive(destinations.settleAccountHold()), getSettleAccountHoldQueue());

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldPlaced> accountHoldPlacedTopic = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldPlaced(), AccountHoldPlaced.class);

    @Getter(lazy = true)
    private final Sender<AccountHoldPlaced> accountHoldsPlaced = senderWrapper.apply(
            "publish.account-hold-placed", getAccountHoldPlacedTopic());

    @Getter(lazy = true)
    private final Receiver<AccountHoldPlaced> accountHoldPlacedSubscription =
            getAccountHoldPlacedTopic().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldPlaced> accountHoldPlacedWorkflow =
            receiverWrapper.apply(
                    receive(destinations.accountHoldPlaced()),
                    getAccountHoldPlacedSubscription());

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldDeclined> accountHoldDeclinedTopic = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldDeclined(), AccountHoldDeclined.class);

    @Getter(lazy = true)
    private final Sender<AccountHoldDeclined> accountHoldsDeclined = senderWrapper.apply(
            "publish.account-hold-declined", getAccountHoldDeclinedTopic());

    @Getter(lazy = true)
    private final Receiver<AccountHoldDeclined> accountHoldDeclinedSubscription =
            getAccountHoldDeclinedTopic().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldDeclined> accountHoldDeclinedWorkflow =
            receiverWrapper.apply(
                    receive(destinations.accountHoldDeclined()),
                    getAccountHoldDeclinedSubscription());

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldReleased> accountHoldReleasedTopic = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldReleased(), AccountHoldReleased.class);

    @Getter(lazy = true)
    private final Sender<AccountHoldReleased> accountHoldsReleased = senderWrapper.apply(
            "publish.account-hold-released", getAccountHoldReleasedTopic());

    @Getter(lazy = true)
    private final Receiver<AccountHoldReleased> accountHoldReleasedSubscription =
            getAccountHoldReleasedTopic().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldReleased> accountHoldReleasedWorkflow =
            receiverWrapper.apply(
                    receive(destinations.accountHoldReleased()),
                    getAccountHoldReleasedSubscription());

    @Getter(lazy = true)
    private final OutboxTopic<TransactionJournalized> transactionJournalizedTopic =
            new OutboxTopic<>(
                    getDatabase(), getConnection(),
                    destinations.transactionJournalized(), TransactionJournalized.class);

    @Getter(lazy = true)
    private final Sender<TransactionJournalized> transactionsJournalized =
            senderWrapper.apply(
                    "publish.transaction-journalized", getTransactionJournalizedTopic());

    @Getter(lazy = true)
    private final Receiver<TransactionJournalized> transactionJournalizedSubscription =
            getTransactionJournalizedTopic().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<TransactionJournalized> transactionJournalizedWorkflow =
            receiverWrapper.apply(
                    receive(destinations.transactionJournalized()),
                    getTransactionJournalizedSubscription());

    @Getter(lazy = true)
    private final OutboxTopic<AccountHoldSettled> accountHoldSettledTopic = new OutboxTopic<>(
            getDatabase(), getConnection(),
            destinations.accountHoldSettled(), AccountHoldSettled.class);

    @Getter(lazy = true)
    private final Sender<AccountHoldSettled> accountHoldsSettled = senderWrapper.apply(
            "publish.account-hold-settled", getAccountHoldSettledTopic());

    @Getter(lazy = true)
    private final Receiver<AccountHoldSettled> accountHoldSettledSubscription =
            getAccountHoldSettledTopic().subscribe("transaction");

    @Getter(lazy = true)
    private final Receiver<AccountHoldSettled> accountHoldSettledWorkflow =
            receiverWrapper.apply(
                    receive(destinations.accountHoldSettled()),
                    getAccountHoldSettledSubscription());

    @Getter(lazy = true)
    private final OutboxPublisher publisher = new OutboxPublisher(
            getDatabase(),
            List.of(
                    getPlaceAccountHoldQueue().route(),
                    getReleaseAccountHoldQueue().route(),
                    getJournalizeTransactionQueue().route(),
                    getSettleAccountHoldQueue().route(),
                    getAccountHoldPlacedTopic().route(),
                    getAccountHoldDeclinedTopic().route(),
                    getAccountHoldReleasedTopic().route(),
                    getTransactionJournalizedTopic().route(),
                    getAccountHoldSettledTopic().route()),
            batchSize);

    private JdbcTransactions initializeDatabase() {
        JdbcTransactions transactions = postgresql.getTransactions();
        OutboxSchema.install(transactions);
        return transactions;
    }

    private static String receive(String destination) {
        return "receive." + destination;
    }
}
