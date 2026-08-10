package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
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

import java.util.List;

@RequiredArgsConstructor
final class InMemoryMessagingModuleFactory {
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
}
