package dev.minimalaccounting.app;

import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;

record AccountingSenders(
        @NonNull Sender<PlaceAccountHold> placeAccountHolds,
        @NonNull Sender<ReleaseAccountHold> releaseAccountHolds,
        @NonNull Sender<JournalizeTransaction> journalizeTransactions,
        @NonNull Sender<SettleAccountHold> settleAccountHolds,
        @NonNull Sender<AccountHoldPlaced> accountHoldsPlaced,
        @NonNull Sender<AccountHoldDeclined> accountHoldsDeclined,
        @NonNull Sender<AccountHoldReleased> accountHoldsReleased,
        @NonNull Sender<TransactionJournalized> transactionsJournalized,
        @NonNull Sender<AccountHoldSettled> accountHoldsSettled) {
}
