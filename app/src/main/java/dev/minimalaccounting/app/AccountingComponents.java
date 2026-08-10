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
import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import dev.minimalaccounting.ports.messaging.Handler;
import lombok.NonNull;

record AccountingComponents(
        @NonNull SubmitTransactionHandler transactions,
        @NonNull Handler<PlaceAccountHold> placeAccountHold,
        @NonNull Handler<AccountHoldPlaced> accountHoldPlaced,
        @NonNull Handler<AccountHoldDeclined> accountHoldDeclined,
        @NonNull Handler<ReleaseAccountHold> releaseAccountHold,
        @NonNull Handler<AccountHoldReleased> accountHoldReleased,
        @NonNull Handler<JournalizeTransaction> journalizeTransaction,
        @NonNull Handler<TransactionJournalized> transactionJournalized,
        @NonNull Handler<SettleAccountHold> settleAccountHold,
        @NonNull Handler<AccountHoldSettled> accountHoldSettled) {
}
