package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;

import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
@AllArgsConstructor
public class AccountEffect {
    private long accountId;
    private AccountNumber accountNumber;
    private DebitCredit side;
    private Money ledgerAmount;
    @Setter(AccessLevel.NONE)
    private AccountEffectState state;

    void hold() {
        state = switch (state) {
            case PENDING -> AccountEffectState.HELD;
            case null, default -> throw invalidTransition("hold");
        };
    }

    void decline() {
        state = switch (state) {
            case PENDING -> AccountEffectState.DECLINED;
            case null, default -> throw invalidTransition("decline");
        };
    }

    void beginRelease() {
        state = switch (state) {
            case PENDING, HELD, RELEASING -> AccountEffectState.RELEASING;
            case null, default -> throw invalidTransition("begin release");
        };
    }

    void release() {
        state = switch (state) {
            case RELEASING, RELEASED -> AccountEffectState.RELEASED;
            case null, default -> throw invalidTransition("release");
        };
    }

    void beginSettlement() {
        state = switch (state) {
            case HELD, SETTLING -> AccountEffectState.SETTLING;
            case null, default -> throw invalidTransition("begin settlement");
        };
    }

    void settle() {
        state = switch (state) {
            case SETTLING, SETTLED -> AccountEffectState.SETTLED;
            case null, default -> throw invalidTransition("settle");
        };
    }

    boolean releaseFinished() {
        return switch (state) {
            case DECLINED, RELEASED -> true;
            case null, default -> false;
        };
    }

    private IllegalStateException invalidTransition(String transition) {
        return new IllegalStateException(
                "Cannot " + transition + " account effect in " + state + " state");
    }
}
