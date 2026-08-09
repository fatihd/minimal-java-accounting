package dev.minimalaccounting.core.transaction;

import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
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
        transaction.placeEffect(
                event.accountId(), event.effectNumber(), occurredAt);
    }

    public void accountHoldDeclined(@NonNull AccountHoldDeclined event) {
        requireTransaction(event.transactionId());
        transaction.declineEffect(
                event.accountId(), event.effectNumber(), event.reason());
    }

    public void accountHoldReleased(@NonNull AccountHoldReleased event) {
        requireTransaction(event.transactionId());
        transaction.releaseEffect(event.accountId(), event.effectNumber());
    }

    public void transactionJournalized(
            @NonNull TransactionJournalized event,
            String journalRequestId) {
        requireTransaction(event.transactionId());
        transaction.beginSettlement(journalRequestId);
    }

    public void accountHoldSettled(@NonNull AccountHoldSettled event) {
        requireTransaction(event.transactionId());
        transaction.settleEffect(event.accountId(), event.effectNumber());
    }

    public List<PlaceAccountHold> accountHoldsToPlace() {
        if (transaction.getState() != TransactionState.PLACING_HOLDS) {
            return List.of();
        }
        List<AccountEffect> effects = transaction.getEffects();
        List<PlaceAccountHold> commands = new ArrayList<>();
        for (int effectNumber = 0; effectNumber < effects.size(); effectNumber++) {
            AccountEffect effect = effects.get(effectNumber);
            if (effect.getState() == AccountEffectState.PENDING) {
                commands.add(new PlaceAccountHold(
                        effect.getAccountId(), transactionId(), effectNumber,
                        effect.getSide(), effect.getLedgerAmount()));
            }
        }
        return List.copyOf(commands);
    }

    public Optional<ReleaseAccountHold> accountHoldToRelease(int effectNumber) {
        if (transaction.getState() != TransactionState.RELEASING_HOLDS) {
            return Optional.empty();
        }
        AccountEffect effect = effect(effectNumber);
        if (effect.getState() != AccountEffectState.RELEASING) {
            return Optional.empty();
        }
        return Optional.of(release(effect, effectNumber));
    }

    public List<ReleaseAccountHold> accountHoldsToRelease() {
        if (transaction.getState() != TransactionState.RELEASING_HOLDS) {
            return List.of();
        }
        List<AccountEffect> effects = transaction.getEffects();
        List<ReleaseAccountHold> commands = new ArrayList<>();
        for (int effectNumber = 0; effectNumber < effects.size(); effectNumber++) {
            AccountEffect effect = effects.get(effectNumber);
            if (effect.getState() == AccountEffectState.RELEASING) {
                commands.add(release(effect, effectNumber));
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
        List<AccountEffect> effects = transaction.getEffects();
        List<SettleAccountHold> commands = new ArrayList<>();
        for (int effectNumber = 0; effectNumber < effects.size(); effectNumber++) {
            AccountEffect effect = effects.get(effectNumber);
            if (effect.getState() == AccountEffectState.SETTLING) {
                commands.add(new SettleAccountHold(
                        effect.getAccountId(), transactionId(), effectNumber,
                        effect.getSide(), effect.getLedgerAmount()));
            }
        }
        return List.copyOf(commands);
    }

    public boolean completed() {
        return transaction.getState() == TransactionState.COMPLETED;
    }

    private AccountEffect effect(int effectNumber) {
        List<AccountEffect> effects = transaction.getEffects();
        if (effectNumber < 0 || effectNumber >= effects.size()) {
            throw new IllegalArgumentException("Transaction effect does not exist");
        }
        return effects.get(effectNumber);
    }

    private ReleaseAccountHold release(AccountEffect effect, int effectNumber) {
        return new ReleaseAccountHold(
                effect.getAccountId(), transactionId(), effectNumber,
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
