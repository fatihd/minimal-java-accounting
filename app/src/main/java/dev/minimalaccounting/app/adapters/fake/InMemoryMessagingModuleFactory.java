package dev.minimalaccounting.app.adapters.fake;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
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
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class InMemoryMessagingModuleFactory implements MessagingComponents {
    private final SenderWrapper senderWrapper;
    private final ReceiverWrapper receiverWrapper;

    @Getter(lazy = true)
    private final InMemoryQueue<PlaceAccountHold> placeAccountHoldQueue =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final Sender<PlaceAccountHold> placeAccountHolds = senderWrapper.apply(
            "send.place-account-hold", getPlaceAccountHoldQueue());

    @Getter(lazy = true)
    private final Receiver<PlaceAccountHold> placeAccountHoldReceiver = receiverWrapper.apply(
            "receive.place-account-hold", getPlaceAccountHoldQueue());

    @Getter(lazy = true)
    private final InMemoryQueue<ReleaseAccountHold> releaseAccountHoldQueue =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final Sender<ReleaseAccountHold> releaseAccountHolds = senderWrapper.apply(
            "send.release-account-hold", getReleaseAccountHoldQueue());

    @Getter(lazy = true)
    private final Receiver<ReleaseAccountHold> releaseAccountHoldReceiver =
            receiverWrapper.apply(
                    "receive.release-account-hold", getReleaseAccountHoldQueue());

    @Getter(lazy = true)
    private final InMemoryQueue<JournalizeTransaction> journalizeTransactionQueue =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final Sender<JournalizeTransaction> journalizeTransactions = senderWrapper.apply(
            "send.journalize-transaction", getJournalizeTransactionQueue());

    @Getter(lazy = true)
    private final Receiver<JournalizeTransaction> journalizeTransactionReceiver =
            receiverWrapper.apply(
                    "receive.journalize-transaction", getJournalizeTransactionQueue());

    @Getter(lazy = true)
    private final InMemoryQueue<SettleAccountHold> settleAccountHoldQueue =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final Sender<SettleAccountHold> settleAccountHolds = senderWrapper.apply(
            "send.settle-account-hold", getSettleAccountHoldQueue());

    @Getter(lazy = true)
    private final Receiver<SettleAccountHold> settleAccountHoldReceiver =
            receiverWrapper.apply(
                    "receive.settle-account-hold", getSettleAccountHoldQueue());

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldPlaced> accountHoldPlacedTopic =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final Sender<AccountHoldPlaced> accountHoldsPlaced = senderWrapper.apply(
            "publish.account-hold-placed", getAccountHoldPlacedTopic());

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldPlaced> accountHoldPlacedSubscription =
            getAccountHoldPlacedTopic().subscribe("transaction-holds");

    @Getter(lazy = true)
    private final Receiver<AccountHoldPlaced> accountHoldPlacedWorkflow =
            receiverWrapper.apply(
                    "receive.account-hold-placed", getAccountHoldPlacedSubscription());

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldDeclined> accountHoldDeclinedTopic =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final Sender<AccountHoldDeclined> accountHoldsDeclined = senderWrapper.apply(
            "publish.account-hold-declined", getAccountHoldDeclinedTopic());

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldDeclined> accountHoldDeclinedSubscription =
            getAccountHoldDeclinedTopic().subscribe("transaction-holds");

    @Getter(lazy = true)
    private final Receiver<AccountHoldDeclined> accountHoldDeclinedWorkflow =
            receiverWrapper.apply(
                    "receive.account-hold-declined", getAccountHoldDeclinedSubscription());

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldReleased> accountHoldReleasedTopic =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final Sender<AccountHoldReleased> accountHoldsReleased = senderWrapper.apply(
            "publish.account-hold-released", getAccountHoldReleasedTopic());

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldReleased> accountHoldReleasedSubscription =
            getAccountHoldReleasedTopic().subscribe("transaction-holds");

    @Getter(lazy = true)
    private final Receiver<AccountHoldReleased> accountHoldReleasedWorkflow =
            receiverWrapper.apply(
                    "receive.account-hold-released", getAccountHoldReleasedSubscription());

    @Getter(lazy = true)
    private final InMemoryTopic<TransactionJournalized> transactionJournalizedTopic =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final Sender<TransactionJournalized> transactionsJournalized = senderWrapper.apply(
            "publish.transaction-journalized", getTransactionJournalizedTopic());

    @Getter(lazy = true)
    private final InMemoryQueue<TransactionJournalized> transactionJournalizedSubscription =
            getTransactionJournalizedTopic().subscribe("transaction-settlement");

    @Getter(lazy = true)
    private final Receiver<TransactionJournalized> transactionJournalizedWorkflow =
            receiverWrapper.apply(
                    "receive.transaction-journalized",
                    getTransactionJournalizedSubscription());

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldSettled> accountHoldSettledTopic =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final Sender<AccountHoldSettled> accountHoldsSettled = senderWrapper.apply(
            "publish.account-hold-settled", getAccountHoldSettledTopic());

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldSettled> accountHoldSettledSubscription =
            getAccountHoldSettledTopic().subscribe("transaction-settlement");

    @Getter(lazy = true)
    private final Receiver<AccountHoldSettled> accountHoldSettledWorkflow =
            receiverWrapper.apply(
                    "receive.account-hold-settled", getAccountHoldSettledSubscription());

    @Getter(lazy = true)
    private final List<InMemoryQueue<?>> queues = List.of(
            getPlaceAccountHoldQueue(),
            getReleaseAccountHoldQueue(),
            getJournalizeTransactionQueue(),
            getSettleAccountHoldQueue(),
            getAccountHoldPlacedSubscription(),
            getAccountHoldDeclinedSubscription(),
            getAccountHoldReleasedSubscription(),
            getTransactionJournalizedSubscription(),
            getAccountHoldSettledSubscription());
}
