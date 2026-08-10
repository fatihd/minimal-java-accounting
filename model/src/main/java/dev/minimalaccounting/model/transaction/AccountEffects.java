package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.Money;

import lombok.NonNull;

import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

final class AccountEffects {
    private final NavigableMap<AccountNumber, AccountEffect> values;

    AccountEffects(@NonNull Map<AccountNumber, AccountEffect> values) {
        this.values = new TreeMap<>(values);
    }

    static AccountEffects derive(@NonNull List<TransactionLine> lines) {
        NavigableMap<AccountNumber, Long> changes = new TreeMap<>();
        for (TransactionLine line : lines) {
            long signed = line.getSide().signed(line.getLedgerAmount().minor());
            changes.merge(line.getAccountNumber(), signed, Math::addExact);
        }

        NavigableMap<AccountNumber, AccountEffect> effects = new TreeMap<>();
        Currency currency = lines.getFirst().getLedgerAmount().currency();
        for (Map.Entry<AccountNumber, Long> change : changes.entrySet()) {
            if (change.getValue() != 0) {
                effects.put(change.getKey(), AccountEffect.pending(
                        new Money(change.getValue(), currency)));
            }
        }
        return new AccountEffects(effects);
    }

    NavigableMap<AccountNumber, AccountEffect> snapshot() {
        return Collections.unmodifiableNavigableMap(new TreeMap<>(values));
    }

    AccountEffect require(AccountNumber accountNumber) {
        AccountEffect effect = values.get(accountNumber);
        if (effect == null) {
            throw new IllegalArgumentException("Transaction effect does not exist");
        }
        return effect;
    }

    boolean isEmpty() {
        return values.isEmpty();
    }

    boolean areAllHeld() {
        return values.values().stream().allMatch(AccountEffect::isHeld);
    }

    boolean areAllSettled() {
        return values.values().stream().allMatch(AccountEffect::isSettled);
    }

    boolean isReleaseFinished() {
        return values.values().stream().allMatch(AccountEffect::isReleaseFinished);
    }

    void beginReleaseOfHeldEffects() {
        for (AccountEffect effect : values.values()) {
            effect.beginReleaseIfHeld();
        }
    }

    void beginSettlement() {
        for (AccountEffect effect : values.values()) {
            effect.beginSettlement();
        }
    }
}
