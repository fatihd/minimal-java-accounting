package dev.minimalaccounting.adapters.messaging.outbox;

public final class ArtemisFailure extends IllegalStateException {
    ArtemisFailure(String operation, Throwable cause) {
        super(operation + " failed", cause);
    }
}
