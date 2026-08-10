package dev.minimalaccounting.app;

import lombok.NonNull;

record AccountingDestinations(
        @NonNull String placeAccountHold,
        @NonNull String releaseAccountHold,
        @NonNull String journalizeTransaction,
        @NonNull String settleAccountHold,
        @NonNull String accountHoldPlaced,
        @NonNull String accountHoldDeclined,
        @NonNull String accountHoldReleased,
        @NonNull String transactionJournalized,
        @NonNull String accountHoldSettled) {

    static AccountingDestinations prefixed(@NonNull String prefix) {
        return new AccountingDestinations(
                name(prefix, "command.place-account-hold"),
                name(prefix, "command.release-account-hold"),
                name(prefix, "command.journalize-transaction"),
                name(prefix, "command.settle-account-hold"),
                name(prefix, "event.account-hold-placed"),
                name(prefix, "event.account-hold-declined"),
                name(prefix, "event.account-hold-released"),
                name(prefix, "event.transaction-journalized"),
                name(prefix, "event.account-hold-settled"));
    }

    private static String name(String prefix, String destination) {
        return prefix + '.' + destination;
    }
}
