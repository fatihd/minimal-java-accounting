package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public final class TransactionProcessManager {
    @NonNull
    private final Transaction transaction;

    public void accountHoldPlaced(
            @NonNull AccountHoldPlaced event,
            Instant occurredAt) {
        requireTransaction(event.transactionId());
        transaction.placeEffect(event.accountNumber(), occurredAt);
    }

    public void accountHoldDeclined(@NonNull AccountHoldDeclined event) {
        requireTransaction(event.transactionId());
        transaction.declineEffect(event.accountNumber(), event.reason());
    }

    public void accountHoldReleased(@NonNull AccountHoldReleased event) {
        requireTransaction(event.transactionId());
        transaction.releaseEffect(event.accountNumber());
    }

    public void transactionJournalized(
            @NonNull TransactionJournalized event,
            String journalRequestId) {
        requireTransaction(event.transactionId());
        transaction.beginSettlement(journalRequestId);
    }

    public void accountHoldSettled(@NonNull AccountHoldSettled event) {
        requireTransaction(event.transactionId());
        transaction.settleEffect(event.accountNumber());
    }

    public List<PlaceAccountHold> accountHoldsToPlace() {
        if (transaction.getState() != TransactionState.PLACING_HOLDS) {
            return List.of();
        }
        List<PlaceAccountHold> commands = new ArrayList<>();
        for (var entry : transaction.getEffects().entrySet()) {
            AccountEffect effect = entry.getValue();
            if (effect.isAwaitingHold()) {
                commands.add(new PlaceAccountHold(
                        entry.getKey(), transactionId(),
                        effect.getSide(), effect.getLedgerAmount()));
            }
        }
        return List.copyOf(commands);
    }

    public Optional<ReleaseAccountHold> accountHoldToRelease(
            @NonNull AccountNumber accountNumber) {
        if (transaction.getState() != TransactionState.RELEASING_HOLDS) {
            return Optional.empty();
        }
        AccountEffect effect = effect(accountNumber);
        if (!effect.isAwaitingRelease()) {
            return Optional.empty();
        }
        return Optional.of(release(accountNumber, effect));
    }

    public List<ReleaseAccountHold> accountHoldsToRelease() {
        if (transaction.getState() != TransactionState.RELEASING_HOLDS) {
            return List.of();
        }
        List<ReleaseAccountHold> commands = new ArrayList<>();
        for (var entry : transaction.getEffects().entrySet()) {
            AccountEffect effect = entry.getValue();
            if (effect.isAwaitingRelease()) {
                commands.add(release(entry.getKey(), effect));
            }
        }
        return List.copyOf(commands);
    }

    public Optional<JournalizeTransaction> transactionToJournalize() {
        if (transaction.getState() != TransactionState.HELD) {
            return Optional.empty();
        }
        return Optional.of(new JournalizeTransaction(transactionId()));
    }

    public List<SettleAccountHold> accountHoldsToSettle() {
        if (transaction.getState() != TransactionState.SETTLING) {
            return List.of();
        }
        List<SettleAccountHold> commands = new ArrayList<>();
        for (var entry : transaction.getEffects().entrySet()) {
            AccountEffect effect = entry.getValue();
            if (effect.isAwaitingSettlement()) {
                commands.add(new SettleAccountHold(
                        entry.getKey(), transactionId(),
                        effect.getSide(), effect.getLedgerAmount()));
            }
        }
        return List.copyOf(commands);
    }

    public boolean completed() {
        return transaction.getState() == TransactionState.COMPLETED;
    }

    private AccountEffect effect(AccountNumber accountNumber) {
        AccountEffect effect = transaction.getEffects().get(accountNumber);
        if (effect == null) {
            throw new IllegalArgumentException("Transaction effect does not exist");
        }
        return effect;
    }

    private ReleaseAccountHold release(AccountNumber accountNumber, AccountEffect effect) {
        return new ReleaseAccountHold(
                accountNumber, transactionId(),
                effect.getSide(), effect.getLedgerAmount());
    }

    private void requireTransaction(long transactionId) {
        if (transactionId() != transactionId) {
            throw new IllegalArgumentException("Event does not belong to transaction");
        }
    }

    private long transactionId() {
        if (transaction.getId() == null) {
            throw new IllegalStateException("Transaction has no identity");
        }
        return transaction.getId();
    }
}
