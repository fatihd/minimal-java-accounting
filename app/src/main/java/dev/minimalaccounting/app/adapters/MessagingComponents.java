package dev.minimalaccounting.app.adapters;

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

public interface MessagingComponents {
    Sender<PlaceAccountHold> getPlaceAccountHolds();

    Receiver<PlaceAccountHold> getPlaceAccountHoldReceiver();

    Sender<ReleaseAccountHold> getReleaseAccountHolds();

    Receiver<ReleaseAccountHold> getReleaseAccountHoldReceiver();

    Sender<JournalizeTransaction> getJournalizeTransactions();

    Receiver<JournalizeTransaction> getJournalizeTransactionReceiver();

    Sender<SettleAccountHold> getSettleAccountHolds();

    Receiver<SettleAccountHold> getSettleAccountHoldReceiver();

    Sender<AccountHoldPlaced> getAccountHoldsPlaced();

    Receiver<AccountHoldPlaced> getAccountHoldPlacedWorkflow();

    Sender<AccountHoldDeclined> getAccountHoldsDeclined();

    Receiver<AccountHoldDeclined> getAccountHoldDeclinedWorkflow();

    Sender<AccountHoldReleased> getAccountHoldsReleased();

    Receiver<AccountHoldReleased> getAccountHoldReleasedWorkflow();

    Sender<TransactionJournalized> getTransactionsJournalized();

    Receiver<TransactionJournalized> getTransactionJournalizedWorkflow();

    Sender<AccountHoldSettled> getAccountHoldsSettled();

    Receiver<AccountHoldSettled> getAccountHoldSettledWorkflow();
}
