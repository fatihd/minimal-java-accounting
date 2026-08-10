package dev.minimalaccounting.app.adapters.fake;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
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
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

import dev.minimalaccounting.shared.ports.Receiver;

@RequiredArgsConstructor
public final class InMemoryMessagingModuleFactory implements MessagingComponents {
    @Getter(lazy = true)
    private final InMemoryQueue<PlaceAccountHold> placeAccountHolds =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final InMemoryQueue<ReleaseAccountHold> releaseAccountHolds =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final InMemoryQueue<JournalizeTransaction> journalizeTransactions =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final InMemoryQueue<SettleAccountHold> settleAccountHolds =
            new InMemoryQueue<>();

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldPlaced> accountHoldsPlaced =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldDeclined> accountHoldsDeclined =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldReleased> accountHoldsReleased =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final InMemoryTopic<TransactionJournalized> transactionsJournalized =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final InMemoryTopic<AccountHoldSettled> accountHoldsSettled =
            new InMemoryTopic<>();

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldPlaced> accountHoldPlacedWorkflow =
            getAccountHoldsPlaced().subscribe("transaction-holds");

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldDeclined> accountHoldDeclinedWorkflow =
            getAccountHoldsDeclined().subscribe("transaction-holds");

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldReleased> accountHoldReleasedWorkflow =
            getAccountHoldsReleased().subscribe("transaction-holds");

    @Getter(lazy = true)
    private final InMemoryQueue<TransactionJournalized> transactionJournalizedWorkflow =
            getTransactionsJournalized().subscribe("transaction-settlement");

    @Getter(lazy = true)
    private final InMemoryQueue<AccountHoldSettled> accountHoldSettledWorkflow =
            getAccountHoldsSettled().subscribe("transaction-settlement");

    @Getter(lazy = true)
    private final List<InMemoryQueue<?>> queues = List.of(
            getPlaceAccountHolds(),
            getReleaseAccountHolds(),
            getJournalizeTransactions(),
            getSettleAccountHolds(),
            getAccountHoldPlacedWorkflow(),
            getAccountHoldDeclinedWorkflow(),
            getAccountHoldReleasedWorkflow(),
            getTransactionJournalizedWorkflow(),
            getAccountHoldSettledWorkflow());

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
