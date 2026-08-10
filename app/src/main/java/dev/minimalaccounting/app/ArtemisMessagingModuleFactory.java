package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConfiguration;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConnection;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisQueue;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisTopic;
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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class ArtemisMessagingModuleFactory {
    private final ArtemisConfiguration configuration;
    private final AccountingDestinations destinations;

    @Getter(lazy = true)
    private final ArtemisConnection connection = new ArtemisConnection(configuration);

    @Getter(lazy = true)
    private final ArtemisQueue<PlaceAccountHold> placeAccountHolds = new ArtemisQueue<>(
            getConnection(), destinations.placeAccountHold(), PlaceAccountHold.class);

    @Getter(lazy = true)
    private final ArtemisQueue<ReleaseAccountHold> releaseAccountHolds = new ArtemisQueue<>(
            getConnection(), destinations.releaseAccountHold(), ReleaseAccountHold.class);

    @Getter(lazy = true)
    private final ArtemisQueue<JournalizeTransaction> journalizeTransactions =
            new ArtemisQueue<>(
                    getConnection(),
                    destinations.journalizeTransaction(),
                    JournalizeTransaction.class);

    @Getter(lazy = true)
    private final ArtemisQueue<SettleAccountHold> settleAccountHolds = new ArtemisQueue<>(
            getConnection(), destinations.settleAccountHold(), SettleAccountHold.class);

    @Getter(lazy = true)
    private final ArtemisTopic<AccountHoldPlaced> accountHoldsPlaced = new ArtemisTopic<>(
            getConnection(), destinations.accountHoldPlaced(), AccountHoldPlaced.class);

    @Getter(lazy = true)
    private final ArtemisTopic<AccountHoldDeclined> accountHoldsDeclined = new ArtemisTopic<>(
            getConnection(), destinations.accountHoldDeclined(), AccountHoldDeclined.class);

    @Getter(lazy = true)
    private final ArtemisTopic<AccountHoldReleased> accountHoldsReleased = new ArtemisTopic<>(
            getConnection(), destinations.accountHoldReleased(), AccountHoldReleased.class);

    @Getter(lazy = true)
    private final ArtemisTopic<TransactionJournalized> transactionsJournalized =
            new ArtemisTopic<>(
                    getConnection(),
                    destinations.transactionJournalized(),
                    TransactionJournalized.class);

    @Getter(lazy = true)
    private final ArtemisTopic<AccountHoldSettled> accountHoldsSettled = new ArtemisTopic<>(
            getConnection(), destinations.accountHoldSettled(), AccountHoldSettled.class);

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
}
