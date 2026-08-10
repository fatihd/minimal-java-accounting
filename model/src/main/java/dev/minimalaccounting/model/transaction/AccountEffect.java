package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public class AccountEffect {
    private DebitCredit side;
    private Money ledgerAmount;
    @Setter(AccessLevel.NONE)
    private AccountEffectState state;

    public AccountEffect(
            @NonNull DebitCredit side,
            @NonNull Money ledgerAmount,
            @NonNull AccountEffectState state) {
        if (ledgerAmount.minor() <= 0) {
            throw new IllegalArgumentException("ledgerAmount must be positive");
        }
        this.side = side;
        this.ledgerAmount = ledgerAmount;
        this.state = state;
    }

    static AccountEffect pending(@NonNull Money netChange) {
        if (netChange.minor() == 0) {
            throw new IllegalArgumentException("netChange must not be zero");
        }
        boolean debit = netChange.minor() > 0;
        return new AccountEffect(
                debit ? DebitCredit.DEBIT : DebitCredit.CREDIT,
                new Money(
                        debit
                                ? netChange.minor()
                                : Math.negateExact(netChange.minor()),
                        netChange.currency()),
                AccountEffectState.PENDING);
    }

    boolean hold() {
        return switch (state) {
            case PENDING -> {
                state = AccountEffectState.HELD;
                yield true;
            }
            case HELD -> true;
            case RELEASING, RELEASED, SETTLING, SETTLED -> false;
            case DECLINED -> throw new IllegalStateException(
                    "Account hold cannot be both placed and declined");
            case null -> throw invalidTransition("hold");
        };
    }

    boolean decline() {
        return switch (state) {
            case PENDING -> {
                state = AccountEffectState.DECLINED;
                yield true;
            }
            case DECLINED -> false;
            case null, default -> throw new IllegalStateException(
                    "Account hold cannot be both declined and " + stateName());
        };
    }

    void beginRelease() {
        state = switch (state) {
            case HELD, RELEASING -> AccountEffectState.RELEASING;
            case null, default -> throw invalidTransition("begin release");
        };
    }

    void beginReleaseIfHeld() {
        if (isHeld()) {
            beginRelease();
        }
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

    boolean isAwaitingHold() {
        return state == AccountEffectState.PENDING;
    }

    boolean isHeld() {
        return state == AccountEffectState.HELD;
    }

    boolean isAwaitingRelease() {
        return state == AccountEffectState.RELEASING;
    }

    boolean isAwaitingSettlement() {
        return state == AccountEffectState.SETTLING;
    }

    boolean isSettled() {
        return state == AccountEffectState.SETTLED;
    }

    boolean isReleaseFinished() {
        return switch (state) {
            case DECLINED, RELEASED -> true;
            case null, default -> false;
        };
    }

    private IllegalStateException invalidTransition(String transition) {
        return new IllegalStateException(
                "Cannot " + transition + " account effect in " + state + " state");
    }

    private String stateName() {
        return state == null ? "null" : state.name().toLowerCase();
    }
}
